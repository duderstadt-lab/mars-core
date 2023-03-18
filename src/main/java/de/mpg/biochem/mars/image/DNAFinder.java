/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package de.mpg.biochem.mars.image;

import java.util.ArrayList;
import java.util.List;

import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Regions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.table.MarsTable;

public class DNAFinder<T extends RealType<T> & NativeType<T>> {

	private OpService opService;
	private boolean useDogFilter = true;
	private double dogFilterRadius = 1.8;
	private double gaussSigma = 2;
	private double threshold = 50;
	private int minimumDistance = 6;
	private boolean fit = false;
	private boolean fitSecondOrder = false;
	private int fitRadius = 3;
	private int optimalDNALength = 38;
	private int yDNAEndSearchRadius = 6;
	private int xDNAEndSearchRadius = 5;
	private boolean medianIntensityFilter = false;
	private int medianIntensityLowerBound = 0;
	private boolean varianceFilter = false;
	private int varianceUpperBound = 1_000_000;

	public DNAFinder(OpService opService) {
		this.opService = opService;
	}
	
	public List<DNASegment> findDNAs(RandomAccessibleInterval<T> img,
		IterableRegion<BoolType> iterableRegion, int theT)
	{
		return findDNAs(img, iterableRegion, theT, 1);
	}
	
	public List<DNASegment> findDNAs(RandomAccessibleInterval<T> img,
		IterableRegion<BoolType> iterableRegion, int theT, int numThreads)
	{
		List<IterableRegion<BoolType>> regionList =
			new ArrayList<IterableRegion<BoolType>>();
		regionList.add(iterableRegion);
		return findDNAs(img, regionList, theT, numThreads);
	}

	public List<DNASegment> findDNAs(RandomAccessibleInterval<T> img,
		List<IterableRegion<BoolType>> iterableRegions, int theT)
	{
		return findDNAs(img, iterableRegions, theT, 1);
	}
	
	public List<DNASegment> findDNAs(RandomAccessibleInterval<T> img,
		List<IterableRegion<BoolType>> iterableRegions, int theT, int numThreads)
	{
		List<DNASegment> DNASegments = new ArrayList<DNASegment>();

		Img<DoubleType> input = opService.convert().float64(Views.iterable(img));
		Img<DoubleType> gradImage = opService.create().img(input, new DoubleType());
		int[] derivatives = { 0, 1 };
		double[] sigma = { gaussSigma, gaussSigma };

		opService.filter().derivativeGauss(gradImage, input, derivatives, sigma);

		RandomAccessibleInterval<FloatType> filteredImg = null;
		if (useDogFilter) filteredImg = MarsImageUtils.dogFilter(gradImage,
			dogFilterRadius, numThreads);

		Img<DoubleType> secondOrderImage = null;
		double median = 0;
		if (fitSecondOrder) {
			secondOrderImage = opService.create().img(input, new DoubleType());

			int[] secondDerivatives = { 0, 2 };
			double[] sigma2 = { 1, 1 };

			opService.filter().derivativeGauss(secondOrderImage, input,
				secondDerivatives, sigma2);

			median = opService.stats().median(secondOrderImage).getRealDouble();

			// Remove positive peaks in preparation for fitting negative peaks.
			Cursor<DoubleType> cursor2 = secondOrderImage.cursor();
			while (cursor2.hasNext()) {
				cursor2.fwd();
				if (cursor2.get().getRealDouble() > median) cursor2.get().set(median);
			}
		}

		for (IterableRegion<BoolType> iterableRegion : iterableRegions) {
			List<Peak> topPeaks = new ArrayList<Peak>();
			List<Peak> bottomPeaks = new ArrayList<Peak>();

			if (useDogFilter) {
				topPeaks = MarsImageUtils.findPeaks(filteredImg, Regions.sample(
					iterableRegion, filteredImg), theT, threshold, minimumDistance,
					false);
				bottomPeaks = MarsImageUtils.findPeaks(filteredImg, Regions.sample(
					iterableRegion, filteredImg), theT, threshold, minimumDistance, true);
			}
			else {
				topPeaks = MarsImageUtils.findPeaks(gradImage, Regions.sample(
					iterableRegion, img), theT, threshold, minimumDistance, false);
				bottomPeaks = MarsImageUtils.findPeaks(gradImage, Regions.sample(
					iterableRegion, img), theT, threshold, minimumDistance, true);
			}

			if (!topPeaks.isEmpty() && !bottomPeaks.isEmpty()) {

				if (fit) {
					topPeaks = MarsImageUtils.fitPeaks(gradImage, gradImage, topPeaks,
						fitRadius, dogFilterRadius, false, 0);

					bottomPeaks = MarsImageUtils.fitPeaks(gradImage, gradImage,
						bottomPeaks, fitRadius, dogFilterRadius, true, 0);
				}

				if (!topPeaks.isEmpty() && !bottomPeaks.isEmpty()) {
					// make sure they are all valid
					// then we can remove them as we go.
					for (int i = 0; i < bottomPeaks.size(); i++)
						bottomPeaks.get(i).setValid(true);

					KDTree<Peak> bottomPeakTree = new KDTree<Peak>(bottomPeaks,
						bottomPeaks);
					RadiusNeighborSearchOnKDTree<Peak> radiusSearch =
						new RadiusNeighborSearchOnKDTree<Peak>(bottomPeakTree);

					// RandomAccessibleInterval<T> view = Views.interval(img, interval);
					RandomAccess<T> ra = Views.extendMirrorSingle(img).randomAccess();

					for (Peak p : topPeaks) {
						double xTOP = p.getDoublePosition(0);
						double yTOP = p.getDoublePosition(1);

						radiusSearch.search(new Peak(xTOP, yTOP + optimalDNALength, 0, 0, 0,
							0), yDNAEndSearchRadius, true);
						if (radiusSearch.numNeighbors() > 0) {
							Peak bottomEdge = radiusSearch.getSampler(0).get();

							double xDiff = Math.abs(bottomEdge.getDoublePosition(0) - p
								.getDoublePosition(0));
							if (xDiff < xDNAEndSearchRadius && bottomEdge.isValid()) {
								DNASegment segment = new DNASegment(xTOP, yTOP, bottomEdge
									.getDoublePosition(0), bottomEdge.getDoublePosition(1));

								calcSegmentProperties(ra, segment);

								boolean pass = true;

								// Check if the segment passes through filters
								if (varianceFilter && varianceUpperBound < segment
									.getVariance()) pass = false;

								if (medianIntensityFilter && medianIntensityLowerBound > segment
									.getMedianIntensity()) pass = false;

								if (pass) {

									if (fit && fitSecondOrder) {

										List<Peak> top = new ArrayList<Peak>();
										top.add(new Peak(segment.getX1(), segment.getY1() + 1));
										top = MarsImageUtils.fitPeaks(secondOrderImage,
											secondOrderImage, top, fitRadius, dogFilterRadius, median,
											true);

										List<Peak> bottom = new ArrayList<Peak>();
										bottom.add(new Peak(segment.getX2(), segment.getY2() - 1));
										bottom = MarsImageUtils.fitPeaks(secondOrderImage,
											secondOrderImage, bottom, fitRadius, dogFilterRadius,
											median, true);

										if (top.size() > 0 && distance(top.get(0).getX(), top.get(0)
											.getY(), segment.getX1() + 1, segment.getY1() +
												1) < fitRadius)
										{
											segment.setX1(top.get(0).getX());
											segment.setY1(top.get(0).getY());
										}

										if (bottom.size() > 0 && distance(bottom.get(0).getX(),
											bottom.get(0).getY(), segment.getX2() - 1, segment
												.getY2() - 1) < fitRadius)
										{
											segment.setX2(bottom.get(0).getX());
											segment.setY2(bottom.get(0).getY());
										}
									}

									DNASegments.add(segment);
									bottomEdge.setValid(false);
								}
							}
						}
					}
				}
			}
		}

		return DNASegments;
	}
	
	public List<DNASegment> findDNAs(RandomAccessibleInterval<T> img,
		Interval interval, int theT, int numThreads)
	{
		List<DNASegment> DNASegments = new ArrayList<DNASegment>();

		Img<DoubleType> input = opService.convert().float64(Views.iterable(img));
		Img<DoubleType> gradImage = opService.create().img(input, new DoubleType());
		int[] derivatives = { 0, 1 };
		double[] sigma = { gaussSigma, gaussSigma };

		opService.filter().derivativeGauss(gradImage, input, derivatives, sigma);

		RandomAccessibleInterval<FloatType> filteredImg = null;
		if (useDogFilter) filteredImg = MarsImageUtils.dogFilter(gradImage,
			dogFilterRadius, numThreads);

		Img<DoubleType> secondOrderImage = null;
		double median = 0;
		if (fitSecondOrder) {
			secondOrderImage = opService.create().img(input, new DoubleType());

			int[] secondDerivatives = { 0, 2 };
			double[] sigma2 = { 1, 1 };

			opService.filter().derivativeGauss(secondOrderImage, input,
				secondDerivatives, sigma2);

			median = opService.stats().median(secondOrderImage).getRealDouble();

			// Remove positive peaks in preparation for fitting negative peaks.
			Cursor<DoubleType> cursor2 = secondOrderImage.cursor();
			while (cursor2.hasNext()) {
				cursor2.fwd();
				if (cursor2.get().getRealDouble() > median) cursor2.get().set(median);
			}
		}

		List<Peak> topPeaks = new ArrayList<Peak>();
		List<Peak> bottomPeaks = new ArrayList<Peak>();

		if (useDogFilter) {
			topPeaks = MarsImageUtils.findPeaks(filteredImg, interval, theT, threshold, minimumDistance,
				false);
			bottomPeaks = MarsImageUtils.findPeaks(filteredImg, interval, theT, threshold, minimumDistance, true);
		}
		else {
			topPeaks = MarsImageUtils.findPeaks(gradImage, interval, theT, threshold, minimumDistance, false);
			bottomPeaks = MarsImageUtils.findPeaks(gradImage, interval, theT, threshold, minimumDistance, true);
		}

		if (!topPeaks.isEmpty() && !bottomPeaks.isEmpty()) {

			if (fit) {
				topPeaks = MarsImageUtils.fitPeaks(gradImage, gradImage, topPeaks,
					fitRadius, dogFilterRadius, false, 0);

				bottomPeaks = MarsImageUtils.fitPeaks(gradImage, gradImage,
					bottomPeaks, fitRadius, dogFilterRadius, true, 0);
			}

			if (!topPeaks.isEmpty() && !bottomPeaks.isEmpty()) {
				// make sure they are all valid
				// then we can remove them as we go.
				for (int i = 0; i < bottomPeaks.size(); i++)
					bottomPeaks.get(i).setValid(true);

				KDTree<Peak> bottomPeakTree = new KDTree<Peak>(bottomPeaks,
					bottomPeaks);
				RadiusNeighborSearchOnKDTree<Peak> radiusSearch =
					new RadiusNeighborSearchOnKDTree<Peak>(bottomPeakTree);

				// RandomAccessibleInterval<T> view = Views.interval(img, interval);
				RandomAccess<T> ra = Views.extendMirrorSingle(img).randomAccess();

				for (Peak p : topPeaks) {
					double xTOP = p.getDoublePosition(0);
					double yTOP = p.getDoublePosition(1);

					radiusSearch.search(new Peak(xTOP, yTOP + optimalDNALength, 0, 0, 0,
						0), yDNAEndSearchRadius, true);
					if (radiusSearch.numNeighbors() > 0) {
						Peak bottomEdge = radiusSearch.getSampler(0).get();

						double xDiff = Math.abs(bottomEdge.getDoublePosition(0) - p
							.getDoublePosition(0));
						if (xDiff < xDNAEndSearchRadius && bottomEdge.isValid()) {
							DNASegment segment = new DNASegment(xTOP, yTOP, bottomEdge
								.getDoublePosition(0), bottomEdge.getDoublePosition(1));

							calcSegmentProperties(ra, segment);

							boolean pass = true;

							// Check if the segment passes through filters
							if (varianceFilter && varianceUpperBound < segment
								.getVariance()) pass = false;

							if (medianIntensityFilter && medianIntensityLowerBound > segment
								.getMedianIntensity()) pass = false;

							if (pass) {

								if (fit && fitSecondOrder) {

									List<Peak> top = new ArrayList<Peak>();
									top.add(new Peak(segment.getX1(), segment.getY1() + 1));
									top = MarsImageUtils.fitPeaks(secondOrderImage,
										secondOrderImage, top, fitRadius, dogFilterRadius, median,
										true);

									List<Peak> bottom = new ArrayList<Peak>();
									bottom.add(new Peak(segment.getX2(), segment.getY2() - 1));
									bottom = MarsImageUtils.fitPeaks(secondOrderImage,
										secondOrderImage, bottom, fitRadius, dogFilterRadius,
										median, true);

									if (top.size() > 0 && distance(top.get(0).getX(), top.get(0)
										.getY(), segment.getX1() + 1, segment.getY1() +
											1) < fitRadius)
									{
										segment.setX1(top.get(0).getX());
										segment.setY1(top.get(0).getY());
									}

									if (bottom.size() > 0 && distance(bottom.get(0).getX(),
										bottom.get(0).getY(), segment.getX2() - 1, segment
											.getY2() - 1) < fitRadius)
									{
										segment.setX2(bottom.get(0).getX());
										segment.setY2(bottom.get(0).getY());
									}
								}

								DNASegments.add(segment);
								bottomEdge.setValid(false);
							}
						}
					}
				}
			}
		}

		return DNASegments;
	}

	private static double distance(double x1, double y1, double x2, double y2) {
		return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
	}

	private void calcSegmentProperties(RandomAccess<T> ra, DNASegment segment) {
		int x1 = (int) segment.getX1();
		int y1 = (int) segment.getY1();

		int x2 = (int) segment.getX2();
		int y2 = (int) segment.getY2();

		SimpleRegression linearFit = new SimpleRegression(true);
		linearFit.addData(x1, y1);
		linearFit.addData(x2, y2);

		// y = A + Bx
		double A = linearFit.getIntercept();
		double B = linearFit.getSlope();

		DoubleColumn col = new DoubleColumn("col");
		for (int y = y1; y <= y2; y++) {
			int x = 0;
			// intercept doesn't exist.
			if (x1 == x2) x = x1;
			else x = (int) ((y - A) / B);

			col.add(ra.setPositionAndGet(x, y).getRealDouble());
		}

		MarsTable table = new MarsTable();
		table.add(col);

		segment.setVariance(table.variance("col"));
		segment.setMedianIntensity((int) table.median("col"));
	}

	public void setGaussianSigma(double gaussSigma) {
		this.gaussSigma = gaussSigma;
	}

	public double getGaussianSigma() {
		return gaussSigma;
	}

	public void setUseDogFiler(boolean useDogFilter) {
		this.useDogFilter = useDogFilter;
	}

	public void setDogFilterRadius(double dogFilterRadius) {
		this.dogFilterRadius = dogFilterRadius;
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	public double getThreshold() {
		return threshold;
	}

	public void setMinimumDistance(int minimumDistance) {
		this.minimumDistance = minimumDistance;
	}

	public int getMinimumDistance() {
		return minimumDistance;
	}

	public void setOptimalDNALength(int optimalDNALength) {
		this.optimalDNALength = optimalDNALength;
	}

	public int getOptimalDNALength() {
		return optimalDNALength;
	}

	public void setYDNAEndSearchRadius(int yDNAEndSearchRadius) {
		this.yDNAEndSearchRadius = yDNAEndSearchRadius;
	}

	public int getYDNAEndSearchRadius() {
		return yDNAEndSearchRadius;
	}

	public void setXDNAEndSearchRadius(int xDNAEndSearchRadius) {
		this.xDNAEndSearchRadius = xDNAEndSearchRadius;
	}

	public int getXDNAEndSearchRadius() {
		return xDNAEndSearchRadius;
	}

	public void setFilterByVariance(boolean varianceFilter) {
		this.varianceFilter = varianceFilter;
	}

	public boolean getFilterByVariance() {
		return this.varianceFilter;
	}

	public int getVarianceUpperBound() {
		return varianceUpperBound;
	}

	public void setVarianceUpperBound(int varianceUpperBound) {
		this.varianceUpperBound = varianceUpperBound;
	}

	public void setFilterByMedianIntensity(boolean medianIntensityFilter) {
		this.medianIntensityFilter = medianIntensityFilter;
	}

	public boolean getFilterByMedianIntensity() {
		return medianIntensityFilter;
	}

	public void setMedianIntensityLowerBound(int medianIntensityLowerBound) {
		this.medianIntensityLowerBound = medianIntensityLowerBound;
	}

	public int getMedianIntensityLowerBound() {
		return medianIntensityLowerBound;
	}

	public void setFit(boolean fit) {
		this.fit = fit;
	}

	public boolean getFit() {
		return fit;
	}

	public void setFitSecondOrder(boolean fitSecondOrder) {
		this.fitSecondOrder = fitSecondOrder;
	}

	public boolean getFitSecondOrder() {
		return fitSecondOrder;
	}

	public void setFitRadius(int fitRadius) {
		this.fitRadius = fitRadius;
	}

	public int getFitRadius() {
		return fitRadius;
	}
}
