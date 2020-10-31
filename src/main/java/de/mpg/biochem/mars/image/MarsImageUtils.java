/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.mpg.biochem.mars.util.Gaussian2D;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.ops.OpService;

public class MarsImageUtils {

	public static List<int[]> innerIntegrationOffsets(
		int integrationInnerRadius)
	{
		ArrayList<int[]> innerOffsets = new ArrayList<int[]>();

		for (int y = -integrationInnerRadius; y <= integrationInnerRadius; y++) {
			for (int x = -integrationInnerRadius; x <= integrationInnerRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));

				if (d <= integrationInnerRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					innerOffsets.add(pos);
				}
			}
		}
		return innerOffsets;
	}

	public static List<int[]> outerIntegrationOffsets(int integrationInnerRadius,
		int integrationOuterRadius)
	{
		ArrayList<int[]> outerOffsets = new ArrayList<int[]>();

		for (int y = -integrationOuterRadius; y <= integrationOuterRadius; y++) {
			for (int x = -integrationOuterRadius; x <= integrationOuterRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));

				if (d > integrationInnerRadius && d <= integrationOuterRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					outerOffsets.add(pos);
				}
			}
		}
		return outerOffsets;
	}

	public static <T extends RealType<T> & NativeType<T>> List<Peak> findPeaks(
		RandomAccessible<T> img, Interval interval, int t, double threshold,
		int minimumDistance, boolean findNegativePeaks)
	{

		PeakFinder<T> finder = new PeakFinder<T>(threshold, minimumDistance,
			findNegativePeaks);
		ArrayList<Peak> peaks = finder.findPeaks(img, interval, t);

		if (peaks == null) peaks = new ArrayList<Peak>();

		return peaks;
	}

	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType>
		dogFilter(RandomAccessibleInterval<T> img, double dogFilterRadius,
			OpService opService)
	{
		Img<FloatType> converted = opService.convert().float32(Views.iterable(img));
		Img<FloatType> dog = opService.create().img(converted);

		final double sigma1 = dogFilterRadius / Math.sqrt(2) * 0.9;
		final double sigma2 = dogFilterRadius / Math.sqrt(2) * 1.1;

		opService.filter().dog(dog, converted, sigma2, sigma1);

		return dog;
	}

	public static <T extends RealType<T> & NativeType<T>> List<Peak> fitPeaks(
		RandomAccessible<T> img, List<Peak> positionList, int fitRadius,
		double initialRadius, boolean findNegativePeaks, double RsquaredMin,
		Interval interval)
	{

		List<Peak> newList = new ArrayList<Peak>();

		int fitWidth = fitRadius * 2 + 1;

		PeakFitter fitter = new PeakFitter();

		RandomAccessible<T> rae = Views.extendMirrorSingle(Views.interval(img,
			interval));
		RandomAccess<T> ra = rae.randomAccess();

		for (Peak peak : positionList) {

			Rectangle subregion = new Rectangle((int) (peak.getX() - fitRadius),
				(int) (peak.getY() - fitRadius), fitWidth, fitWidth);

			double[] p = new double[5];
			p[0] = Double.NaN;
			p[1] = Double.NaN;
			p[2] = peak.getX();
			p[3] = peak.getY();
			p[4] = initialRadius / 2;
			double[] e = new double[5];

			fitter.fitPeak(rae, p, e, subregion, findNegativePeaks);

			peak.setValid();

			for (int i = 0; i < p.length && peak.isValid(); i++) {
				if (Double.isNaN(p[i])) peak.setNotValid();
			}

			if (p[2] < 0 || p[3] < 0 || p[4] < 0) {
				peak.setNotValid();
			}

			double Rsquared = 0;
			if (peak.isValid()) {
				Gaussian2D gauss = new Gaussian2D(p);
				Rsquared = calcR2(gauss, ra, subregion);
				if (Rsquared <= RsquaredMin) peak.setNotValid();
			}

			if (peak.isValid()) {
				peak.setValues(p);
				peak.setRsquared(Rsquared);
				newList.add(peak);
			}
		}
		return newList;
	}

	public static <T extends RealType<T> & NativeType<T>> double calcR2(
		Gaussian2D gauss, RandomAccess<T> ra, Rectangle roi)
	{
		double SSres = 0;
		double SStot = 0;
		double mean = 0;
		double count = 0;

		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				mean += ra.setPositionAndGet(x, y).getRealDouble();
				count++;
			}
		}

		mean = mean / count;

		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				double value = ra.setPositionAndGet(x, y).getRealDouble();
				SStot += (value - mean) * (value - mean);

				double prediction = gauss.getValue(x, y);
				SSres += (value - prediction) * (value - prediction);
			}
		}

		return 1 - SSres / SStot;
	}

	public static List<Peak> removeNearestNeighbors(List<Peak> peakList,
		int minimumDistance)
	{
		if (peakList.size() < 2) return peakList;

		// Sort the list from highest to lowest Rsquared
		Collections.sort(peakList, new Comparator<Peak>() {

			@Override
			public int compare(Peak o1, Peak o2) {
				return Double.compare(o2.getRSquared(), o1.getRSquared());
			}
		});

		// We have to make a copy to pass to the KDTREE because it will change the
		// order and we have already sorted from lowest to highest to pick center of
		// peaks in for loop below.
		// This is a shallow copy, which means it contains exactly the same elements
		// as the first list, but the order can be completely different...
		ArrayList<Peak> KDTreePossiblePeaks = new ArrayList<>(peakList);

		// Allows for fast search of nearest peaks...
		KDTree<Peak> possiblePeakTree = new KDTree<Peak>(KDTreePossiblePeaks,
			KDTreePossiblePeaks);

		RadiusNeighborSearchOnKDTree<Peak> radiusSearch =
			new RadiusNeighborSearchOnKDTree<Peak>(possiblePeakTree);

		// As we loop through all possible peaks and remove those that are too close
		// we will add all the selected peaks to a new array
		// that will serve as the finalList of actual peaks
		// This whole process is to remove pixels near the center peak pixel that
		// are also above the detection threshold but all part of the same peak...
		ArrayList<Peak> finalPeaks = new ArrayList<Peak>();

		// Reset all to valid for new search
		for (int i = peakList.size() - 1; i >= 0; i--) {
			peakList.get(i).setValid();
		}

		// It is really important to remember here that possiblePeaks and
		// KDTreePossiblePeaks are different lists but point to the same elements
		// That means if we setNotValid in one it is changing the same object in
		// another that is required for the stuff below to work.
		for (int i = 0; i < peakList.size(); i++) {
			Peak peak = peakList.get(i);
			if (peak.isValid()) {
				finalPeaks.add(peak);

				// Then we remove all possible peaks within the minimumDistance...
				// This will include the peak we just added to the peaks list...
				radiusSearch.search(peak, minimumDistance, false);

				for (int j = 0; j < radiusSearch.numNeighbors(); j++) {
					radiusSearch.getSampler(j).get().setNotValid();
				}
			}
		}
		return finalPeaks;
	}

	public static <T extends RealType<T> & NativeType<T>> void integratePeaks(
		RandomAccessible<T> img, Interval interval, List<Peak> peaks,
		List<int[]> innerOffsets, List<int[]> outerOffsets)
	{
		for (Peak peak : peaks) {
			// Type casting from double to int rounds down always, so we have to add
			// 0.5 offset to be correct.
			double[] intensity = integratePeak(img, interval, (int) (peak.getX() +
				0.5), (int) (peak.getY() + 0.5), innerOffsets, outerOffsets);
			peak.setIntensity(intensity[0]);
		}
	}

	public static <T extends RealType<T> & NativeType<T>> double[] integratePeak(
		RandomAccessible<T> img, Interval interval, int x, int y,
		List<int[]> innerOffsets, List<int[]> outerOffsets)
	{

		RandomAccessibleInterval<T> view = Views.interval(img, interval);

		if (x == Double.NaN || y == Double.NaN) {
			double[] NULLinten = new double[2];
			NULLinten[0] = Double.NaN;
			NULLinten[1] = Double.NaN;
			return NULLinten;
		}

		double intensity = 0;
		int innerPixels = 0;
		ArrayList<Double> outerPixelValues = new ArrayList<Double>();

		RandomAccess<T> ra = Views.extendMirrorSingle(view).randomAccess(interval);

		for (int[] circleOffset : innerOffsets) {
			intensity += ra.setPositionAndGet(x + circleOffset[0], y +
				circleOffset[1]).getRealDouble();
			innerPixels++;
		}

		for (int[] circleOffset : outerOffsets) {
			outerPixelValues.add(ra.setPositionAndGet(x + circleOffset[0], y +
				circleOffset[1]).getRealDouble());
		}

		Collections.sort(outerPixelValues);
		double outerMedian;
		if (outerPixelValues.size() % 2 == 0) outerMedian =
			((double) outerPixelValues.get(outerPixelValues.size() / 2) +
				(double) outerPixelValues.get(outerPixelValues.size() / 2 - 1)) / 2;
		else outerMedian = (double) outerPixelValues.get(outerPixelValues.size() /
			2);

		intensity -= outerMedian * innerPixels;

		double[] inten = new double[2];
		inten[0] = intensity;
		inten[1] = outerMedian;

		return inten;
	}

	public static <T extends RealType<T> & NativeType<T>>
		RandomAccessibleInterval<T> get2DHyperSlice(final ImgPlus<T> img,
			final int z, final int c, final int t)
	{
		RandomAccessible<T> frameImg;
		final int cDim = img.dimensionIndex(Axes.CHANNEL);
		if (cDim < 0) {
			frameImg = img;
		}
		else {
			frameImg = Views.hyperSlice(img, cDim, c);
		}

		int timeDim = img.dimensionIndex(Axes.TIME);
		if (timeDim >= 0) {
			if (cDim >= 0 && timeDim > cDim) {
				timeDim--;
			}
			frameImg = Views.hyperSlice(frameImg, timeDim, t);
		}

		int zDim = img.dimensionIndex(Axes.Z);
		if (zDim >= 0) {
			frameImg = Views.hyperSlice(frameImg, zDim, z);
		}

		return Views.interval(frameImg, Intervals.createMinSize(0, 0, img.dimension(
			0), img.dimension(1)));
	}
}
