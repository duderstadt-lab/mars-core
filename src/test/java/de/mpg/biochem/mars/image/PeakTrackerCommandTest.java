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

import de.mpg.biochem.mars.image.commands.PeakTrackerCommand;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.Gaussian2D;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import io.scif.services.TranslatorService;

public class PeakTrackerCommandTest {

	private static final double TOLERANCE = 0.01;

	@Parameter
	protected Context context;

	@Parameter
	protected DatasetService datasetService;

	@Parameter
	protected LogService logService;

	protected Context createContext() {
		return new Context(DatasetService.class, StatusService.class,
			OpService.class, MoleculeArchiveService.class, TranslatorService.class,
			OMEXMLService.class, FormatService.class, MarsTableService.class,
			DatasetIOService.class);
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
		final PeakTrackerCommand peakTracker = new PeakTrackerCommand();

		peakTracker.setContext(context);

		// Run the Command
		peakTracker.setDataset(simulateDataset());
		peakTracker.setUseROI(false);
		peakTracker.setX0(0);
		peakTracker.setY0(0);
		peakTracker.setWidth(50);
		peakTracker.setHeight(50);
		peakTracker.setChannel(0);
		peakTracker.setUseDogFiler(true);
		peakTracker.setDogFilterRadius(1.8d);
		peakTracker.setThreshold(50);
		peakTracker.setMinimumDistance(5);
		peakTracker.setFindNegativePeaks(false);
		peakTracker.setFitRadius(4);
		peakTracker.setMinimumRsquared(0.0d);
		peakTracker.setVerboseOutput(false);
		peakTracker.setMaxDifferenceX(5);
		peakTracker.setMaxDifferenceY(5);
		peakTracker.setMaxDifferenceT(5);
		peakTracker.setMinimumTrackLength(10);
		peakTracker.setIntegrate(true);
		peakTracker.setIntegrationInnerRadius(2);
		peakTracker.setIntegrationOuterRadius(4);
		peakTracker.setMicroscope("Microscope");
		peakTracker.setPixelLength(1);
		peakTracker.setPixelUnits("pixel");
		//peakTracker.setNorpixFormat(false);

		// Run the Command
		peakTracker.run();

		// Retrieve output from the command
		SingleMoleculeArchive archive = peakTracker.getArchive();

		SingleMolecule molecule1 = archive.molecules().filter(m -> m.getTable()
			.getValue("x", 0) < 11).findFirst().get();
		for (int t = 0; t < 50; t++) {
			assertTrue(Math.abs(10d - molecule1.getTable().getValue("x",
				t)) < TOLERANCE,
				"Peak x position is off by more than the tolerance. Should be 10 was " +
					molecule1.getTable().getValue("x", t));
			assertTrue(Math.abs(10d + t / 4 - molecule1.getTable().getValue("y",
				t)) < TOLERANCE,
				"Peak y position is off by more than the tolerance. Should be " + (10d +
					t / 4) + " was " + molecule1.getTable().getValue("y", t));
		}

		SingleMolecule molecule2 = archive.molecules().filter(m -> m.getTable()
			.getValue("x", 0) < 34 && m.getTable().getValue("x", 0) > 30).findFirst()
			.get();
		for (int t = 0; t < 50; t++) {
			assertTrue(Math.abs(32.5d - molecule2.getTable().getValue("x",
				t)) < TOLERANCE,
				"Peak x position is off by more than the tolerance. Should be 32.5 was " +
					molecule2.getTable().getValue("x", t));
			assertTrue(Math.abs(20d + t / 4 - molecule2.getTable().getValue("y",
				t)) < TOLERANCE,
				"Peak y position is off by more than the tolerance. Should be " + (40d +
					t / 4) + " was " + molecule2.getTable().getValue("y", t));
		}

		SingleMolecule molecule3 = archive.molecules().filter(m -> m.getTable()
			.getValue("x", 0) < 45 && m.getTable().getValue("x", 0) > 40).findFirst()
			.get();
		for (int t = 0; t < 50; t++) {
			assertTrue(Math.abs(43.7d - molecule3.getTable().getValue("x",
				t)) < TOLERANCE,
				"Peak x position is off by more than the tolerance. Should be 43.7 was " +
					molecule3.getTable().getValue("x", t));
			assertTrue(Math.abs(26.7d + t / 4 - molecule3.getTable().getValue("y",
				t)) < TOLERANCE,
				"Peak y position is off by more than the tolerance. Should be " +
					(26.7d + t / 4) + " was " + molecule3.getTable().getValue("y", t));
		}
	}

	public Dataset simulateDataset() {
		long[] dim = { 50, 50, 50 };
		AxisType[] axes = { Axes.X, Axes.Y, Axes.TIME };
		Dataset dataset = datasetService.create(dim, "simulated image", axes, 16,
			false, false);

		for (int t = 0; t < dim[2]; t++)
			for (int x = 0; x < dim[0]; x++)
				for (int y = 0; y < dim[1]; y++) {
					Gaussian2D peak1 = new Gaussian2D(1000d, 3000d, 10d, 10d + t / 4,
						1.2d);
					Gaussian2D peak2 = new Gaussian2D(1000d, 3000d, 32.5d, 20d + t / 4,
						1.2d);
					Gaussian2D peak3 = new Gaussian2D(1000d, 3000d, 43.7d, 26.7d + t / 4,
						1.2d);

					dataset.getImgPlus().randomAccess().setPositionAndGet(x, y, t)
						.setReal(500 + peak1.getValue(x, y) + peak2.getValue(x, y) + peak3
							.getValue(x, y));
				}
		return dataset;
	}
}
