/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2021 Karl Duderstadt
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;

import org.junit.jupiter.api.Test;

import de.mpg.biochem.mars.util.Gaussian2D;

public class MarsImageUtilsTest {

	/**
	 * Pixel unit tolerance above which the peak fitter test will fail.
	 */
	private static final double TOLERANCE = 0.01;

	@Test
	void findPeaks() {
		Img<UnsignedShortType> img = simulateImage();

		List<Peak> peaks = MarsImageUtils.findPeaks(img, Intervals.createMinMax(0,
			0, img.dimension(0) - 1, img.dimension(1) - 1), 0, 100, 4, false);

		// 10, 10
		assertEquals(peaks.get(0).getX(), 10);
		assertEquals(peaks.get(0).getY(), 10);

		// 44, 27
		assertEquals(peaks.get(1).getX(), 44);
		assertEquals(peaks.get(1).getY(), 27);

		// 33, 40
		assertEquals(peaks.get(2).getX(), 33);
		assertEquals(peaks.get(2).getY(), 40);
	}

	@Test
	void fitPeaks() {
		Img<UnsignedShortType> img = simulateImage();

		List<Peak> peaks = MarsImageUtils.findPeaks(img, Intervals.createMinMax(0,
			0, img.dimension(0) - 1, img.dimension(1) - 1), 0, 100, 4, false);
		MarsImageUtils.fitPeaks(img, Intervals.createMinMax(0, 0, img.dimension(0) -
			1, img.dimension(1) - 1), peaks, 4, 2, false, 0);

		assertTrue(Math.abs(10 - peaks.get(0).getX()) < TOLERANCE,
			"Peak position is off by more than the tolerance. Should be 10 was " +
				peaks.get(0).getX());
		assertTrue(Math.abs(10 - peaks.get(0).getY()) < TOLERANCE,
			"Peak position is off by more than the tolerance. Should be 10 was " +
				peaks.get(0).getY());

		assertTrue(Math.abs(43.7 - peaks.get(1).getX()) < TOLERANCE,
			"Peak position is off by more than the tolerance. Should be 43.7 was " +
				peaks.get(1).getX());
		assertTrue(Math.abs(26.7 - peaks.get(1).getY()) < TOLERANCE,
			"Peak position is off by more than the tolerance. Should be 26.7 was " +
				peaks.get(1).getY());

		assertTrue(Math.abs(32.5 - peaks.get(2).getX()) < TOLERANCE,
			"Peak position is off by more than the tolerance. Should be 32.5 was " +
				peaks.get(2).getX());
		assertTrue(Math.abs(40 - peaks.get(2).getY()) < TOLERANCE,
			"Peak position is off by more than the tolerance. Should be 40 was " +
				peaks.get(2).getY());
	}

	@Test
	void removeNearestNeighbors() {
		List<Peak> peaks = new ArrayList<Peak>();

		Peak peak1 = new Peak("1", 10, 10);
		peak1.setRsquared(0.99);
		peaks.add(peak1);

		Peak peak2 = new Peak("2", 11, 11);
		peak2.setRsquared(0.5);
		peaks.add(peak2);

		peaks = MarsImageUtils.removeNearestNeighbors(peaks, 4);

		assertTrue(peaks.size() == 1,
			"Nearest neighbor peak was not removed by MarsImageUtils.removeNearestNeighbors.");
		assertTrue(peaks.get(0).getX() == 10 & peaks.get(0).getY() == 10,
			"Peak with higher R-squared was removed by MarsImageUtils.removeNearestNeighbors.");
	}

	@Test
	void integratePeaks() {
		Img<UnsignedShortType> img = simulateImage();

		List<Peak> peaks = MarsImageUtils.findPeaks(img, Intervals.createMinMax(0,
			0, img.dimension(0) - 1, img.dimension(1) - 1), 0, 100, 4, false);
		MarsImageUtils.fitPeaks(img, Intervals.createMinMax(0, 0, img.dimension(0) -
			1, img.dimension(1) - 1), peaks, 4, 2, false, 0);
		MarsImageUtils.integratePeaks(img, Intervals.createMinMax(0, 0, img
			.dimension(0) - 1, img.dimension(1) - 1), peaks, 2, 4);

		assertEquals(peaks.get(0).getIntensity(), 24223.5);
		assertEquals(peaks.get(0).getMedianBackground(), 73972.5);

		assertEquals(peaks.get(1).getIntensity(), 23817.5);
		assertEquals(peaks.get(1).getMedianBackground(), 73993.5);

		assertEquals(peaks.get(2).getIntensity(), 23849.0);
		assertEquals(peaks.get(2).getMedianBackground(), 73815.0);
	}

	@Test
	void integratePeakNearEdge() {
		Img<UnsignedShortType> img = simulateImage();

		List<Peak> peaks = MarsImageUtils.findPeaks(img, Intervals.createMinMax(6,
			6, 14, 14), 0, 100, 4, false);
		MarsImageUtils.fitPeaks(img, Intervals.createMinMax(6, 6, 14, 14), peaks, 4,
			2, false, 0);
		MarsImageUtils.integratePeaks(img, Intervals.createMinMax(6, 6, 14, 14),
			peaks, 2, 10);

		assertEquals(peaks.get(0).getIntensity(), 22743.0);
		assertEquals(peaks.get(0).getMedianBackground(), 75453.0);
	}

	public Img<UnsignedShortType> simulateImage() {
		long[] dim = { 50, 50 };

		final ImgFactory<UnsignedShortType> imgFactory = new ArrayImgFactory<>(
			new UnsignedShortType());

		final Img<UnsignedShortType> img = imgFactory.create(dim);

		for (int x = 0; x < dim[0]; x++)
			for (int y = 0; y < dim[1]; y++) {
				Gaussian2D peak1 = new Gaussian2D(1000d, 3000d, 10d, 10d, 1.2d);
				Gaussian2D peak2 = new Gaussian2D(1000d, 3000d, 32.5d, 40d, 1.2d);
				Gaussian2D peak3 = new Gaussian2D(1000d, 3000d, 43.7d, 26.7d, 1.2d);

				img.randomAccess().setPositionAndGet(x, y).setReal(500 + peak1.getValue(
					x, y) + peak2.getValue(x, y) + peak3.getValue(x, y));
			}

		return img;
	}
}
