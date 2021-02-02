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

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import de.mpg.biochem.mars.image.commands.DNAFinderCommand;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.Gaussian2D;

public class DNAFinderCommandTest {

	private static final double TOLERANCE = 0.02;

	@Parameter
	protected Context context;

	@Parameter
	protected DatasetService datasetService;

	@Parameter
	protected LogService logService;

	protected Context createContext() {
		return new Context(DatasetService.class, StatusService.class,
			OpService.class, MarsTableService.class);
	}

	@BeforeEach
	public void setUp() {
		createContext().inject(this);
	}

	@AfterEach
	public synchronized void cleanUp() {
		if (context != null) {
			context.dispose();
			context = null;
			datasetService = null;
			logService = null;
		}
	}

	@Test
	void findPeaksCommand() {
		final DNAFinderCommand peakFinder = new DNAFinderCommand();

		peakFinder.setContext(context);

		peakFinder.setDataset(simulateDataset());
		peakFinder.setUseROI(false);
		peakFinder.setChannel(0);
		peakFinder.setGaussianSigma(1);
		peakFinder.setT(0);
		peakFinder.setUseDogFiler(true);
		peakFinder.setDogFilterRadius(1.8d);
		peakFinder.setThreshold(100);
		peakFinder.setMinimumDistance(4);
		peakFinder.setOptimalDNALength(20);
		peakFinder.setXDNAEndSearchRadius(2);
		peakFinder.setYDNAEndSearchRadius(5);
		peakFinder.setFilterByMedianIntensity(true);
		peakFinder.setMedianIntensityLowerBound(1000);
		peakFinder.setFilterByVariance(true);
		peakFinder.setVarianceUpperBound(50_000_000);
		peakFinder.setFit(true);
		peakFinder.setFitSecondOrder(true);
		peakFinder.setFitRadius(4);
		peakFinder.setAddToRoiManager(false);
		peakFinder.setGenerateDNATable(true);
		peakFinder.setProcessAllFrames(true);

		// Run the Command
		peakFinder.run();

		// Retrieve output from the command
		// peakCountTable = peakFinder.getPeakCountTable()

		MarsTable dnaTable = peakFinder.getDNATable();

		assertEquals(dnaTable.getValue("T", 0), 0);
		assertTrue(Math.abs(32 - dnaTable.getValue("x1", 0)) < TOLERANCE,
			"x1 is off by more than the tolerance. Should be 32 was " + dnaTable
				.getValue("x1", 0));
		assertTrue(Math.abs(15 - dnaTable.getValue("y1", 0)) < TOLERANCE,
			"y1 is off by more than the tolerance. Should be 15 was " + dnaTable
				.getValue("y1", 0));
		assertTrue(Math.abs(32 - dnaTable.getValue("x2", 0)) < TOLERANCE,
			"x2 is off by more than the tolerance. Should be 32 was " + dnaTable
				.getValue("x2", 0));
		assertTrue(Math.abs(35 - dnaTable.getValue("y2", 0)) < TOLERANCE,
			"y2 is off by more than the tolerance. Should be 35 was " + dnaTable
				.getValue("y2", 0));
		assertTrue(Math.abs(20 - dnaTable.getValue("length", 0)) < TOLERANCE,
			"length is off by more than the tolerance. Should be 20 was " + dnaTable
				.getValue("length", 0));

		assertEquals(dnaTable.getValue("T", 1), 0);
		assertTrue(Math.abs(10 - dnaTable.getValue("x1", 1)) < TOLERANCE,
			"x1 is off by more than the tolerance. Should be 10 was " + dnaTable
				.getValue("x1", 1));
		assertTrue(Math.abs(10 - dnaTable.getValue("y1", 1)) < TOLERANCE,
			"y1 is off by more than the tolerance. Should be 10 was " + dnaTable
				.getValue("y1", 1));
		assertTrue(Math.abs(10 - dnaTable.getValue("x2", 1)) < TOLERANCE,
			"x2 is off by more than the tolerance. Should be 10 was " + dnaTable
				.getValue("x2", 1));
		assertTrue(Math.abs(30 - dnaTable.getValue("y2", 1)) < TOLERANCE,
			"y2 is off by more than the tolerance. Should be 30 was " + dnaTable
				.getValue("y2", 1));
		assertTrue(Math.abs(20 - dnaTable.getValue("length", 1)) < TOLERANCE,
			"length is off by more than the tolerance. Should be 20 was " + dnaTable
				.getValue("length", 1));

		assertEquals(dnaTable.getValue("T", 2), 0);
		assertTrue(Math.abs(43 - dnaTable.getValue("x1", 2)) < TOLERANCE,
			"x1 is off by more than the tolerance. Should be 43 was " + dnaTable
				.getValue("x1", 2));
		assertTrue(Math.abs(20 - dnaTable.getValue("y1", 2)) < TOLERANCE,
			"y1 is off by more than the tolerance. Should be 20 was " + dnaTable
				.getValue("y1", 2));
		assertTrue(Math.abs(43 - dnaTable.getValue("x2", 2)) < TOLERANCE,
			"x2 is off by more than the tolerance. Should be 43 was " + dnaTable
				.getValue("x2", 2));
		assertTrue(Math.abs(40 - dnaTable.getValue("y2", 2)) < TOLERANCE,
			"y2 is off by more than the tolerance. Should be 40 was " + dnaTable
				.getValue("y2", 2));
		assertTrue(Math.abs(20 - dnaTable.getValue("length", 2)) < TOLERANCE,
			"length is off by more than the tolerance. Should be 20 was " + dnaTable
				.getValue("length", 2));

		assertEquals(dnaTable.getValue("T", 3), 1);
		assertTrue(Math.abs(32 - dnaTable.getValue("x1", 3)) < TOLERANCE,
			"x1 is off by more than the tolerance. Should be 32 was " + dnaTable
				.getValue("x1", 3));
		assertTrue(Math.abs(15 - dnaTable.getValue("y1", 3)) < TOLERANCE,
			"y1 is off by more than the tolerance. Should be 15 was " + dnaTable
				.getValue("y1", 3));
		assertTrue(Math.abs(32 - dnaTable.getValue("x2", 3)) < TOLERANCE,
			"x2 is off by more than the tolerance. Should be 32 was " + dnaTable
				.getValue("x2", 3));
		assertTrue(Math.abs(35 - dnaTable.getValue("y2", 3)) < TOLERANCE,
			"y2 is off by more than the tolerance. Should be 35 was " + dnaTable
				.getValue("y2", 3));
		assertTrue(Math.abs(20 - dnaTable.getValue("length", 3)) < TOLERANCE,
			"length is off by more than the tolerance. Should be 20 was " + dnaTable
				.getValue("length", 3));

		assertEquals(dnaTable.getValue("T", 4), 1);
		assertTrue(Math.abs(10 - dnaTable.getValue("x1", 4)) < TOLERANCE,
			"x1 is off by more than the tolerance. Should be 10 was " + dnaTable
				.getValue("x1", 4));
		assertTrue(Math.abs(10 - dnaTable.getValue("y1", 4)) < TOLERANCE,
			"y1 is off by more than the tolerance. Should be 10 was " + dnaTable
				.getValue("y1", 4));
		assertTrue(Math.abs(10 - dnaTable.getValue("x2", 4)) < TOLERANCE,
			"x2 is off by more than the tolerance. Should be 10 was " + dnaTable
				.getValue("x2", 4));
		assertTrue(Math.abs(30 - dnaTable.getValue("y2", 4)) < TOLERANCE,
			"y2 is off by more than the tolerance. Should be 30 was " + dnaTable
				.getValue("y2", 4));
		assertTrue(Math.abs(20 - dnaTable.getValue("length", 4)) < TOLERANCE,
			"length is off by more than the tolerance. Should be 20 was " + dnaTable
				.getValue("length", 4));

		assertEquals(dnaTable.getValue("T", 5), 1);
		assertTrue(Math.abs(43 - dnaTable.getValue("x1", 5)) < TOLERANCE,
			"x1 is off by more than the tolerance. Should be 43 was " + dnaTable
				.getValue("x1", 5));
		assertTrue(Math.abs(20 - dnaTable.getValue("y1", 5)) < TOLERANCE,
			"y1 is off by more than the tolerance. Should be 20 was " + dnaTable
				.getValue("y1", 5));
		assertTrue(Math.abs(43 - dnaTable.getValue("x2", 5)) < TOLERANCE,
			"x2 is off by more than the tolerance. Should be 43 was " + dnaTable
				.getValue("x2", 5));
		assertTrue(Math.abs(40 - dnaTable.getValue("y2", 5)) < TOLERANCE,
			"y2 is off by more than the tolerance. Should be 40 was " + dnaTable
				.getValue("y2", 5));
		assertTrue(Math.abs(20 - dnaTable.getValue("length", 5)) < TOLERANCE,
			"length is off by more than the tolerance. Should be 20 was " + dnaTable
				.getValue("length", 5));
	}

	public Dataset simulateDataset() {
		long[] dim = { 50, 50, 2 };
		AxisType[] axes = { Axes.X, Axes.Y, Axes.TIME };
		Dataset dataset = datasetService.create(dim, "Simulated image with DNAs",
			axes, 16, false, false);

		for (int t = 0; t < dim[2]; t++)
			for (int x = 0; x < dim[0]; x++)
				for (int y = 0; y < dim[1]; y++) {
					dataset.getImgPlus().randomAccess().setPositionAndGet(x, y, t)
						.setReal(500 + dnaIntensity(10d, 10d, 30d, x, y) + dnaIntensity(32d,
							15d, 35d, x, y) + dnaIntensity(43d, 20d, 40d, x, y));
				}

		return dataset;
	}

	private double dnaIntensity(double x1x2, double y1, double y2, int x, int y) {
		if (y <= y1) {
			Gaussian2D top = new Gaussian2D(1000d, 3000d, x1x2, y1, 1.2d);
			return top.getValue(x, y);
		}
		else if (y < y2) {
			Gaussian2D middle = new Gaussian2D(1000d, 3000d, x1x2, y, 1.2d);
			return middle.getValue(x, y);
		}
		else {
			Gaussian2D bottom = new Gaussian2D(1000d, 3000d, x1x2, y2, 1.2d);
			return bottom.getValue(x, y);
		}
	}
}
