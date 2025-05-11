/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.*;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.img.ImgFactory;
import net.imglib2.parallel.Parallelization;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Masks;
import net.imglib2.roi.RealMask;
import net.imglib2.roi.RealMaskRealInterval;
import net.imglib2.roi.Regions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;

import de.mpg.biochem.mars.util.Gaussian2D;

/**
 * Common utility functions for DoG filtering, peak finding, peak fitting and
 * related operations.
 * 
 * @author Karl Duderstadt
 */
public class MarsImageUtils {

	/**
	 * Global innerOffset integration position cache. Maps innerRadius to lists of
	 * x, y offsets.
	 */
	private static Map<Integer, List<int[]>> innerOffsetCache;

	/**
	 * Global outerOffset integration position cache. Maps innerRadius,
	 * outerRadius to list of x, y offsets.
	 */
	private static Map<Integer[], List<int[]>> outerOffsetCache;

	/**
	 * This method creates a list of integer x, y offsets from a central point
	 * based on the provided radius. The offset list can be used to efficiently
	 * visit the neighborhood of x, y positions around a central integration
	 * position.
	 * 
	 * @param innerRadius Inner integration radius.
	 * @return The list of x, y offsets for innerRadius.
	 */
	public synchronized static List<int[]> innerOffsets(int innerRadius) {
		if (innerOffsetCache == null) innerOffsetCache =
				new HashMap<>();

		if (!innerOffsetCache.containsKey(innerRadius)) {
			ArrayList<int[]> innerOffsets = new ArrayList<>();

			for (int y = -innerRadius; y <= innerRadius; y++) {
				for (int x = -innerRadius; x <= innerRadius; x++) {
					double d = Math.round(Math.sqrt(x * x + y * y));

					if (d <= innerRadius) {
						int[] pos = new int[2];
						pos[0] = x;
						pos[1] = y;
						innerOffsets.add(pos);
					}
				}
			}
			innerOffsetCache.put(innerRadius, innerOffsets);
		}

		return innerOffsetCache.get(innerRadius);
	}

	/**
	 * This method creates a list of integer x, y offsets between the provided
	 * inner radius and outer radius. The offset list can be used to efficiently
	 * visit the background neighborhood of x, y positions forming an outside ring
	 * around a central integration position.
	 * 
	 * @param innerRadius Inner integration radius.
	 * @param outerRadius Outer integration radius.
	 * @return The list of x, y offsets for between innerRadius and outerRadius.
	 */
	public synchronized static List<int[]> outerOffsets(int innerRadius,
														int outerRadius)
	{
		if (outerOffsetCache == null) outerOffsetCache =
				new HashMap<>();

		Integer[] radii = new Integer[2];
		radii[0] = innerRadius;
		radii[1] = outerRadius;

		if (!outerOffsetCache.containsKey(radii)) {
			ArrayList<int[]> outerOffsets = new ArrayList<>();

			for (int y = -outerRadius; y <= outerRadius; y++) {
				for (int x = -outerRadius; x <= outerRadius; x++) {
					double d = Math.round(Math.sqrt(x * x + y * y));

					if (d > innerRadius && d <= outerRadius) {
						int[] pos = new int[2];
						pos[0] = x;
						pos[1] = y;
						outerOffsets.add(pos);
					}
				}
			}
			outerOffsetCache.put(radii, outerOffsets);
		}

		return outerOffsetCache.get(radii);
	}

	/**
	 * This method converts from a RealMaskRealInterval to an IterableRegion
	 * 
	 * @param roi A RealMaskRealInterval represent the region of interest to
	 *          convert.
	 * @return An IterableRegion.
	 */
	@SuppressWarnings("unused")
	public static IterableRegion<BoolType> toIterableRegion(
		RealMaskRealInterval roi)
	{
		RealRandomAccessible<BoolType> rra = Masks.toRealRandomAccessible(roi);
		RandomAccessibleOnRealRandomAccessible<BoolType> ra = Views.raster(rra);
		Interval interval = Intervals.largestContainedInterval(roi);
		IntervalView<BoolType> rai = Views.interval(ra, interval);
		return Regions.iterable(rai);
	}

	/**
	 * This method converts from a RealMask to an IterableRegion
	 * 
	 * @param mask The RealMaks representing the roi.
	 * @param image The Interval of the image.
	 * @return An IterableRegion.
	 */
	public static IterableRegion<BoolType> toIterableRegion(RealMask mask,
		Interval image)
	{
		final RandomAccessible<BoolType> discreteROI = Views.raster(Masks
			.toRealRandomAccessible(mask));
		final IntervalView<BoolType> boundedDiscreteROI = Views.interval(
			discreteROI, image);
		return Regions.iterable(boundedDiscreteROI);
	}

	/**
	 * This method returned a list of peaks in the 2D image within the interval
	 * and iterable interval specified that are above the pixel value threshold
	 * specified. The local maximum within the minimum distance is always chosen.
	 * The point in time provided is set for all peaks returned. Negative peaks
	 * can be located if desired. The peak search will only be performed in the
	 * iterable interval provided.
	 * 
	 * @param <T> Image type.
	 * @param iterableInterval The IterableInterval to search for peaks in the
	 *          image.
	 * @param t The T position being searched for peaks.
	 * @param threshold The pixel value threshold for peak detection.
	 * @param minimumDistance The minimum allowed distance between peaks.
	 * @param findNegativePeaks Whether to search for negative peaks.
	 * @return The list of peaks found.
	 */
	public static <T extends RealType<T> & NativeType<T>> List<Peak> findPeaks(
		IterableInterval<T> iterableInterval, int t,
		double threshold, int minimumDistance, boolean findNegativePeaks)
	{
		Cursor<T> cursor = iterableInterval.cursor();
		return MarsImageUtils.findPeaks(cursor, t, threshold, minimumDistance,
			findNegativePeaks);
	}

	/**
	 * This method returned a list of peaks in the 2D image within the interval
	 * specified that are above the pixel value threshold specified. The local
	 * maximum within the minimum distance is always chosen. The point in time
	 * provided is set for all peaks returned. Negative peaks can be located if
	 * desired.
	 * 
	 * @param <T> Image type.
	 * @param img 2D image containing peaks.
	 * @param interval The interval to search for peaks in the image.
	 * @param t The T position being searched for peaks.
	 * @param threshold The pixel value threshold for peak detection.
	 * @param minimumDistance The minimum allowed distance between peaks.
	 * @param findNegativePeaks Whether to search for negative peaks.
	 * @return The list of peaks found.
	 */
	public static <T extends RealType<T> & NativeType<T>> List<Peak> findPeaks(
		RandomAccessible<T> img, Interval interval, int t, double threshold,
		int minimumDistance, boolean findNegativePeaks)
	{
		Cursor<T> cursor = Views.interval(img, interval).cursor();
		return MarsImageUtils.findPeaks(cursor, t, threshold, minimumDistance,
			findNegativePeaks);
	}

	/**
	 * This method returned a list of peaks in the 2D image using the cursor
	 * specified that are above the pixel value threshold specified. The local
	 * maximum within the minimum distance is always chosen. The point in time
	 * provided is set for all peaks returned. Negative peaks can be located if
	 * desired.
	 * 
	 * @param <T> Image type.
	 * @param cursor The cursor used to iterate through the regions of interest.
	 * @param t The T position being searched for peaks.
	 * @param threshold The pixel value threshold for peak detection.
	 * @param minimumDistance The minimum allowed distance between peaks.
	 * @param findNegativePeaks Whether to search for negative peaks.
	 * @return The list of peaks found.
	 */
	public static <T extends RealType<T> & NativeType<T>> List<Peak> findPeaks(
		Cursor<T> cursor, int t, double threshold,
		int minimumDistance, boolean findNegativePeaks)
	{
		List<PeakPixel> possiblePeaks = new ArrayList<>();

		if (!findNegativePeaks) {
			while (cursor.hasNext() && !Thread.currentThread().isInterrupted()) {
				double pixel = cursor.next().getRealDouble();

				if (pixel > threshold) {
					possiblePeaks.add(new PeakPixel(cursor.getIntPosition(0), cursor
						.getIntPosition(1), pixel));
				}
			}

			if (possiblePeaks.isEmpty()) return new ArrayList<>();

			// Sort the list from lowest to highest pixel value...
			possiblePeaks.sort(Comparator.comparingDouble(o -> o.pixelValue));
		}
		else {
			while (cursor.hasNext() && !Thread.currentThread().isInterrupted()) {
				double pixel = cursor.next().getRealDouble();

				if (pixel < threshold * (-1)) {
					possiblePeaks.add(new PeakPixel(cursor.getIntPosition(0), cursor
						.getIntPosition(1), pixel));
				}
			}

			if (possiblePeaks.isEmpty()) return new ArrayList<>();

			// Sort the list from highest to lowest pixel value...
			possiblePeaks.sort((o1, o2) -> Double.compare(o2.pixelValue, o1.pixelValue));
		}

		// We have to make a copy to pass to the KDTREE because it will change the
		// order, and we have already sorted from lowest to highest to pick center of
		// peaks in the loop below.
		// This is a shallow copy, which means it contains exactly the same elements
		// as the first list, but the order can be completely different...
		List<PeakPixel> KDTreePossiblePeaks = new ArrayList<>(possiblePeaks);

		// Allows for fast searching of the nearest peaks.
		KDTree<PeakPixel> possiblePeakTree = new KDTree<>(
				KDTreePossiblePeaks, KDTreePossiblePeaks);

		RadiusNeighborSearchOnKDTree<PeakPixel> radiusSearch =
				new RadiusNeighborSearchOnKDTree<>(possiblePeakTree);

		// As we loop through all possible peaks and remove those that are too close
		// we will add all the selected peaks to a new array
		// that will serve as the finalList of actual peaks
		// This whole process is to remove pixels near the center peak pixel that
		// are also above the detection threshold but all part of the same peak...
		List<Peak> finalPeaks = new ArrayList<>();

		// It is really important to remember here that possiblePeaks and
		// KDTreePossiblePeaks are different lists but point to the same elements
		// That means if we setNotValid in one it is changing the same object in
		// another that is required for the stuff below to work.
		for (int i = possiblePeaks.size() - 1; i >= 0; i--) {
			if (Thread.currentThread().isInterrupted()) break;

			PeakPixel peakPixel = possiblePeaks.get(i);
			if (peakPixel.valid) {
				finalPeaks.add(new Peak(peakPixel.x, peakPixel.y, t));

				// Then we remove all possible peaks within the minimumDistance...
				// This will include the peak we just added to the peaks list...
				radiusSearch.search(peakPixel, minimumDistance, false);

				for (int j = 0; j < radiusSearch.numNeighbors(); j++) {
					radiusSearch.getSampler(j).get().valid = false;
				}
			}
		}

		possiblePeaks.clear();
		KDTreePossiblePeaks.clear();

		return finalPeaks;
	}
	
	/**
	 * This method uses the OpService to apply a Difference of Gaussian (DoG)
	 * filter on a 2D image provided. The dog filter op requires two sigmas that
	 * are used to generate two images and the difference of these images is
	 * returned. The relationship between dogFilterRadius and the two sigmas is
	 * the following: sigma1 = dogFilterRadius / sqrt(2) * 1.1 sigma2 =
	 * dogFilterRadius / sqrt(2) * 0.9
	 * 
	 * @param <T> Image type.
	 * @param img 2D image that will be dog filtered.
	 * @param dogFilterRadius Radius to use for dog filtering.
	 * @return The dog filtered image.
	 */
	@SuppressWarnings("unused")
	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType>
		dogFilter(RandomAccessibleInterval<T> img, double dogFilterRadius)
	{
		return dogFilter(img, dogFilterRadius, 1);
	}

	/**
	 * This method uses the OpService to apply a Difference of Gaussian (DoG)
	 * filter on a 2D image provided. The dog filter op requires two sigmas that
	 * are used to generate two images and the difference of these images is
	 * returned. The relationship between dogFilterRadius and the two sigmas is
	 * the following: sigma1 = dogFilterRadius / sqrt(2) * 1.1 sigma2 =
	 * dogFilterRadius / sqrt(2) * 0.9
	 * 
	 * @param <T> Image type.
	 * @param img 2D image that will be dog filtered.
	 * @param dogFilterRadius Radius to use for dog filtering.
	 * @param numThreads The number of threads to use.
	 * @return The dog filtered image.
	 */
	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType>
		dogFilter(RandomAccessibleInterval<T> img, double dogFilterRadius,
			int numThreads)
	{
		final RandomAccessible< T > extended = Views.extendMirrorSingle( img );
		final FloatType type = new FloatType();
		
		//Make sure to translate the dog images to the interval matching img. 
		//This ensures the dog filtered image returned represent the same interval as the one provided.
		final RandomAccessibleInterval< FloatType > dog =  Views.translate(Util.getArrayOrCellImgFactory( img, type ).create( img ), img.min(0), img.min(1));
		final RandomAccessibleInterval< FloatType > dog2 = Views.translate(Util.getArrayOrCellImgFactory( img, type ).create( img ), img.min(0), img.min(1));

		final double sigma1 = dogFilterRadius / Math.sqrt(2) * 1.1;
		final double sigma2 = dogFilterRadius / Math.sqrt(2) * 0.9;

		try
		{
			Parallelization.runWithNumThreads( numThreads,
					() -> Gauss3.gauss( new double[] { sigma1, sigma1 }, extended, dog2)
			);
			Parallelization.runWithNumThreads( numThreads,
					() -> Gauss3.gauss( new double[] { sigma2, sigma2 }, extended, dog)
			);
		}
		catch ( final IncompatibleTypeException e )
		{
			e.printStackTrace();
		}

		final IterableInterval< FloatType > dogIterable = Views.iterable( dog );
		final IterableInterval< FloatType > tmpIterable = Views.iterable( dog2 );
		final Cursor< FloatType > dogCursor = dogIterable.cursor();
		final Cursor< FloatType > tmpCursor = tmpIterable.cursor();
		while ( dogCursor.hasNext() )
			dogCursor.next().sub( tmpCursor.next() );

		return dog;
	}

	/**
	 * Given a 2D image and a list of peak positions in pixels, this function
	 * performs subpixel 2D gaussian fitting and updates the peak positions
	 * accordingly. If the R-squared from fitting is below the threshold provided,
	 * the whole pixel position is left unchanged. The image is mirrored for pixel
	 * values beyond the interval provided. The starting guess for sigma is half
	 * initialSize.
	 * 
	 * @param <T> Image type.
	 * @param img 2D image containing peaks.
	 * @param interval The interval to use for peak fitting.
	 * @param peaks The list of Peaks to fit with subpixel accuracy.
	 * @param radius The radius of the square region of pixels to use for fitting.
	 * @param initialSize A starting guess for the peak size.
	 * @param findNegativePeaks Whether negative peaks are being fit.
	 * @param RsquaredMin The minimum allowed R-squared value below which fits
	 *          are rejected.
	 * @return The list of peaks after fitting with those having rejected fits
	 *         removed.
	 */
	public static <T extends RealType<T> & NativeType<T>> List<Peak> fitPeaks(
		RandomAccessible<T> img, Interval interval, List<Peak> peaks, int radius,
		double initialSize, boolean findNegativePeaks, double RsquaredMin)
	{

		List<Peak> newList = new ArrayList<>();

		int fitDiameter = radius * 2 + 1;

		PeakFitter<T> fitter = new PeakFitter<>();

		RandomAccessible<T> rae = Views.extendMirrorSingle(Views.interval(img,
			interval));
		RandomAccess<T> ra = rae.randomAccess();

		for (Peak peak : peaks) {

			if (Thread.currentThread().isInterrupted()) break;

			Rectangle subregion = new Rectangle((int) (peak.getX() - radius),
				(int) (peak.getY() - radius), fitDiameter, fitDiameter);

			double[] p = new double[5];
			p[0] = Double.NaN;
			p[1] = Double.NaN;
			p[2] = peak.getX();
			p[3] = peak.getY();
			p[4] = initialSize / 2;
			double[] e = new double[5];

			fitter.fitPeak(rae, p, e, subregion, findNegativePeaks);

			peak.setValid(true);

			for (int i = 0; i < p.length && peak.isValid(); i++) {
				if (Double.isNaN(p[i])) peak.setValid(false);
			}

			if (p[2] < 0 || p[3] < 0 || p[4] < 0) {
				peak.setValid(false);
			}

			double Rsquared = 0;
			if (peak.isValid()) {
				Gaussian2D gauss = new Gaussian2D(p);
				Rsquared = calcR2(ra, radius, gauss);
				if (Rsquared <= RsquaredMin) peak.setValid(false);
			}

			if (peak.isValid()) {
				peak.setValues(p);
				peak.setRsquared(Rsquared);
				newList.add(peak);
			}
		}
		return newList;
	}

	/**
	 * Given a 2D image and a list of peak positions in pixels, this function
	 * performs subpixel 2D gaussian fitting and updates the peak positions
	 * accordingly. If the R-squared from fitting is below the threshold provided,
	 * the whole pixel position is left unchanged. The image is mirrored for pixel
	 * values beyond the interval provided. The starting guess for sigma is half
	 * initialSize.
	 * 
	 * @param <T> Image type.
	 * @param img 2D image containing peaks.
	 * @param interval The interval to use for peak fitting.
	 * @param peaks The list of Peaks to fit with subpixel accuracy.
	 * @param radius The radius of the square region of pixels to use for fitting.
	 * @param initialSize A starting guess for the peak size.
	 * @param fitRegionThreshold The threshold pixel value for the region to fit.
	 * @param findNegativePeaks Whether negative peaks are being fit.
	 * @return The list of peaks after fitting with those having rejected fits
	 *         removed.
	 */
	public static <T extends RealType<T> & NativeType<T>> List<Peak> fitPeaks(
		RandomAccessible<T> img, Interval interval, List<Peak> peaks, int radius,
		double initialSize, double fitRegionThreshold, boolean findNegativePeaks)
	{

		List<Peak> newList = new ArrayList<>();

		int fitDiameter = radius * 2 + 1;

		PeakFitter<T> fitter = new PeakFitter<>();

		RandomAccessible<T> rae = Views.extendMirrorSingle(Views.interval(img,
			interval));

		for (Peak peak : peaks) {

			if (Thread.currentThread().isInterrupted()) break;

			Rectangle subregion = new Rectangle((int) (peak.getX() - radius),
				(int) (peak.getY() - radius), fitDiameter, fitDiameter);

			double[] p = new double[5];
			p[0] = Double.NaN;
			p[1] = Double.NaN;
			p[2] = peak.getX();
			p[3] = peak.getY();
			p[4] = initialSize / 2;
			double[] e = new double[5];

			fitter.fitPeak(rae, p, e, subregion, fitRegionThreshold,
				findNegativePeaks);

			peak.setValid(true);

			for (int i = 0; i < p.length && peak.isValid(); i++) {
				if (Double.isNaN(p[i])) peak.setValid(false);
			}

			if (p[2] < 0 || p[3] < 0 || p[4] < 0) {
				peak.setValid(false);
			}

			if (peak.isValid()) {
				peak.setValues(p);
				newList.add(peak);
			}
		}
		return newList;
	}

	/**
	 * This method calculates the R-squared for the 2D gaussian provided within
	 * the radius specified for the image given as a RandomAccess.
	 * 
	 * @param <T> Image type.
	 * @param ra 2D image containing peak used for the R-squared calculation.
	 * @param radius The radius of the square region used for the R-squared
	 *          calculation.
	 * @param gauss The 2D gaussian from fitting to use for the R-squared
	 *          calculation.
	 * @return The R-squared value.
	 */
	public static <T extends RealType<T> & NativeType<T>> double calcR2(
		RandomAccess<T> ra, int radius, Gaussian2D gauss)
	{
		double ssr = 0;
		double sst = 0;
		double mean = 0;
		double count = 0;

		int x1 = (int) (gauss.x - radius);
		int x2 = (int) (gauss.x + radius);
		int y1 = (int) (gauss.y - radius);
		int y2 = (int) (gauss.y + radius);

		// Very, very bad fits can go to the max integer value in which case we
		// return 0.
		if (x1 == Integer.MAX_VALUE || x2 == Integer.MAX_VALUE ||
			y1 == Integer.MAX_VALUE || y2 == Integer.MAX_VALUE) return 0;

		for (int y = y1; y <= y2; y++) {
			for (int x = x1; x <= x2; x++) {
				mean += ra.setPositionAndGet(x, y).getRealDouble();
				count++;
			}
		}

		mean = mean / count;

		for (int y = y1; y <= y2; y++) {
			for (int x = x1; x <= x2; x++) {
				double value = ra.setPositionAndGet(x, y).getRealDouble();
				sst += (value - mean) * (value - mean);

				double prediction = gauss.getValue(x, y);
				ssr += (value - prediction) * (value - prediction);
			}
		}

		return 1 - ssr / sst;
	}

	/**
	 * This method removes peaks from the provided list that are closer than the
	 * minimum distance given. If two peaks are closer than the minimum distance,
	 * the peak with the lower R-squared value, indicating a poorer fit, will be
	 * removed.
	 * 
	 * @param peaks The list of peaks that nearest neighbors should be removed
	 *          from.
	 * @param minimumDistance The smallest allowed distance between peaks in
	 *          pixels.
	 * @return A new peak list with nearest neighbors removed.
	 */
	public static List<Peak> removeNearestNeighbors(List<Peak> peaks,
		int minimumDistance)
	{
		if (peaks.size() < 2) return peaks;

		peaks.sort((o1, o2) -> Double.compare(o2.getRSquared(), o1.getRSquared()));

		// We have to make a copy to pass to the KDTREE because it will change the
		// order, and we have already sorted from lowest to highest to pick center of
		// peaks in for loop below.
		// This is a shallow copy, which means it contains exactly the same elements
		// as the first list, but the order can be completely different...
		ArrayList<Peak> KDTreePossiblePeaks = new ArrayList<>(peaks);

		KDTree<Peak> possiblePeakTree = new KDTree<>(KDTreePossiblePeaks,
				KDTreePossiblePeaks);

		RadiusNeighborSearchOnKDTree<Peak> radiusSearch =
				new RadiusNeighborSearchOnKDTree<>(possiblePeakTree);

		// As we loop through all possible peaks and remove those that are too close
		// we will add all the selected peaks to a new array
		// that will serve as the finalList of actual peaks
		// This whole process is to remove pixels near the center peak pixel that
		// are also above the detection threshold but all part of the same peak...
		ArrayList<Peak> finalPeaks = new ArrayList<>();

		// Reset all to valid for new search
		for (int i = peaks.size() - 1; i >= 0; i--) {
			peaks.get(i).setValid(true);
		}

		// It is really important to remember here that possiblePeaks and
		// KDTreePossiblePeaks are different lists but point to the same elements
		// That means if we setNotValid in one it is changing the same object in
		// another that is required for the stuff below to work.
		for (Peak value : peaks) {
			if (Thread.currentThread().isInterrupted()) break;
			if (value.isValid()) {
				finalPeaks.add(value);

				// Then we remove all possible peaks within the minimumDistance...
				// This will include the peak we just added to the peaks list...
				radiusSearch.search(value, minimumDistance, false);

				for (int j = 0; j < radiusSearch.numNeighbors(); j++) {
					radiusSearch.getSampler(j).get().setValid(false);
				}
			}
		}
		return finalPeaks;
	}

	/**
	 * This method integrates the intensity of peaks in the 2D image provided. The
	 * interval given is mirrored at the edges for pixel values that lie outside
	 * the boundaries. The values of all pixels within the region defined by the
	 * innerRadius are summed. The median value of the pixels between innerRadius
	 * and outerRadius is subtracted from each pixel in the sum to yield the
	 * background corrected total intensity.
	 * 
	 * @param <T> Image type.
	 * @param img 2D image containing peaks.
	 * @param interval The interval to mirror at the edges during integration.
	 * @param peaks The Peaks to integrate.
	 * @param innerRadius The region to integrate.
	 * @param outerRadius The outer radius of the region used to calculate the
	 *          background.
	 */
	public static <T extends RealType<T> & NativeType<T>> void integratePeaks(
		RandomAccessible<T> img, Interval interval, List<Peak> peaks,
		int innerRadius, int outerRadius)
	{
		integratePeaks(img, interval, peaks, innerRadius, outerRadius, false);
	}

	/**
	 * This method integrates the intensity of peaks in the 2D image provided. The
	 * interval given is mirrored at the edges for pixel values that lie outside
	 * the boundaries. The values of all pixels within the region defined by the
	 * innerRadius are summed. The median value of the pixels between innerRadius
	 * and outerRadius is subtracted from each pixel in the sum to yield the
	 * background corrected total intensity.
	 * 
	 * @param <T> Image type.
	 * @param img 2D image containing peaks.
	 * @param interval The interval to mirror at the edges during integration.
	 * @param peaks The Peaks to integrate.
	 * @param innerRadius The region to integrate.
	 * @param outerRadius The outer radius of the region used to calculate the
	 *          background.
	 * @param verbose Whether to include all values.
	 */
	public static <T extends RealType<T> & NativeType<T>> void integratePeaks(
		RandomAccessible<T> img, Interval interval, List<Peak> peaks,
		int innerRadius, int outerRadius, boolean verbose)
	{
		RandomAccessibleInterval<T> view = Views.interval(img, interval);
		RandomAccess<T> ra = Views.extendMirrorSingle(view).randomAccess();

		List<int[]> innerOffsets = innerOffsets(innerRadius);
		List<int[]> outerOffsets = outerOffsets(innerRadius, outerRadius);

		for (Peak peak : peaks) {
			if (Thread.currentThread().isInterrupted()) break;

			// Type casting from double to int rounds down always, so we have to add
			// 0.5 offset to be correct.
			int x = (int) (peak.getX() + 0.5);
			int y = (int) (peak.getY() + 0.5);

			if (Double.isNaN(peak.getX()) || Double.isNaN(peak.getY())) {
				peak.setIntensity(Double.NaN);
				peak.setMedianBackground(Double.NaN);
			}
			else {
				double intensity = 0;
				List<Double> outerPixelValues = new ArrayList<>();

				for (int[] circleOffset : innerOffsets) {
					intensity += ra.setPositionAndGet(x + circleOffset[0], y +
							circleOffset[1]).getRealDouble();
				}

				for (int[] circleOffset : outerOffsets) {
					outerPixelValues.add(ra.setPositionAndGet(x + circleOffset[0], y +
							circleOffset[1]).getRealDouble());
				}

				Collections.sort(outerPixelValues);
				double outerMedian;
				if (outerPixelValues.size() % 2 == 0) outerMedian =
					(outerPixelValues.get(outerPixelValues.size() / 2) +
							outerPixelValues.get(outerPixelValues.size() / 2 - 1)) / 2;
				else outerMedian = outerPixelValues.get(outerPixelValues
					.size() / 2);

				if (verbose) {
					peak.setProperty(Peak.UNCORRECTED_INTENSITY, intensity);
					peak.setProperty(Peak.MEAN_BACKGROUND, outerPixelValues.stream()
						.mapToDouble(Double::doubleValue).average().orElse(Double.NaN) *
						innerOffsets.size());
				}

				double medianBackground = outerMedian * innerOffsets.size();

				intensity -= medianBackground;

				peak.setIntensity(intensity);
				peak.setMedianBackground(medianBackground);
			}
		}
	}

	/**
	 * Convenience method to retrieve a 2D view from an ImgPlus for a given z, c,
	 * and t. Axis positions are based on a zero index. This method re-slices along
	 * c, t, and z. Therefore, for higher dimensional images, the output may be
	 * more than 2D.
	 * 
	 * @param <T> Image type.
	 * @param img ImgPlus provide a view from.
	 * @param z The Z axis position.
	 * @param c The C axis position.
	 * @param t The T axis position.
	 * @return The slice of the image specified.
	 */
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

	public static boolean intervalContains(final Interval interval,
		double... location)
	{
		for (int d = 0; d < location.length; d++)
			if (location[d] < interval.min(d) && location[d] > interval.max(d))
				return false;

		return true;
	}

}
