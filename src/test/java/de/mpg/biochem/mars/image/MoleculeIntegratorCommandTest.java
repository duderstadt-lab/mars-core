/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2022 Karl Duderstadt
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

import java.util.HashMap;
import java.util.Map;

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
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;

import de.mpg.biochem.mars.image.commands.MoleculeIntegratorMultiViewCommand;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.Gaussian2D;
import de.mpg.biochem.mars.util.MarsMath;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import io.scif.services.TranslatorService;

public class MoleculeIntegratorCommandTest {

	private static final double TOLERANCE = 30;

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
			DatasetIOService.class, PlatformService.class);
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
		final MoleculeIntegratorMultiViewCommand moleculeIntegrator =
			new MoleculeIntegratorMultiViewCommand();

		moleculeIntegrator.setContext(context);

		// Run the Command
		moleculeIntegrator.setDataset(simulateDataset());

		moleculeIntegrator.setInnerRadius(2);
		moleculeIntegrator.setOuterRadius(7);
		moleculeIntegrator.setRegionBoundaries("LONG", 0, 0, 50, 25);
		moleculeIntegrator.setRegionBoundaries("SHORT", 0, 25, 50, 25);
		moleculeIntegrator.setMicroscope("simulated");

		Map<Integer, Map<String, Peak>> longIntegrationMap = new HashMap<>();
		for (int t = 0; t < 50; t++) {
			Map<String, Peak> peaks = new HashMap<>();
			peaks.put("molecule1", new Peak("molecule1", 10.0d, 10.0d));
			peaks.put("molecule2", new Peak("molecule2", 32.5d, 8d));
			peaks.put("molecule3", new Peak("molecule3", 43.7d, 16.7d));
			longIntegrationMap.put(t, peaks);
		}
		moleculeIntegrator.addIntegrationMap("FRET Red", 0, moleculeIntegrator
				.getInterval("LONG"), longIntegrationMap);

		Map<Integer, Map<String, Peak>> longIntegrationMap2 = new HashMap<>();
		for (int t = 0; t < 50; t++) {
			Map<String, Peak> peaks = new HashMap<>();
			peaks.put("molecule1", new Peak("molecule1", 10.0d, 10.0d));
			peaks.put("molecule2", new Peak("molecule2", 32.5d, 8d));
			peaks.put("molecule3", new Peak("molecule3", 43.7d, 16.7d));
			longIntegrationMap2.put(t, peaks);
		}
		moleculeIntegrator.addIntegrationMap("Red", 2, moleculeIntegrator
			.getInterval("LONG"), longIntegrationMap2);

		Map<Integer, Map<String, Peak>> shortIntegrationMap = new HashMap<>();
		for (int t = 0; t < 50; t++) {
			Map<String, Peak> peaks = new HashMap<>();
			peaks.put("molecule1", new Peak("molecule1", 10.0d, 35d));
			peaks.put("molecule2", new Peak("molecule2", 32.5d, 33d));
			peaks.put("molecule3", new Peak("molecule3", 43.7d, 41.7d));
			shortIntegrationMap.put(t, peaks);
		}
		moleculeIntegrator.addIntegrationMap("FRET Green", 0, moleculeIntegrator
			.getInterval("SHORT"), shortIntegrationMap);

		Map<Integer, Map<String, Peak>> shortIntegrationMap2 = new HashMap<>();
		for (int t = 0; t < 50; t++) {
			Map<String, Peak> peaks = new HashMap<>();
			peaks.put("molecule1", new Peak("molecule1", 10.0d, 35d));
			peaks.put("molecule2", new Peak("molecule2", 32.5d, 33d));
			peaks.put("molecule3", new Peak("molecule3", 43.7d, 41.7d));
			shortIntegrationMap2.put(t, peaks);
		}
		moleculeIntegrator.addIntegrationMap("Blue", 1, moleculeIntegrator
			.getInterval("SHORT"), shortIntegrationMap2);

		// Run the Command
		moleculeIntegrator.run();

		// Retrieve output from the command
		SingleMoleculeArchive archive = moleculeIntegrator.getArchive();

		MarsTable table1 = archive.get("molecule1").getTable();
		MarsTable table2 = archive.get("molecule2").getTable();
		MarsTable table3 = archive.get("molecule3").getTable();
		for (int t = 0; t < 50; t++) {
			// FRET Red
			Gaussian2D peak1 = new Gaussian2D(0, Math.cos(Math.PI * t / 10) * 2000d +
				2000d, 10.0d, 10.0d, 1.2d);
			Gaussian2D peak2 = new Gaussian2D(0, Math.cos(Math.PI * t / 10) * 2000d +
				2000d, 32.5d, 8d, 1.2d);
			Gaussian2D peak3 = new Gaussian2D(0, Math.cos(Math.PI * t / 10) * 2000d +
				2000d, 43.7d, 16.7d, 1.2d);

			double integratedPeak1 = integratePeak(peak1, 2);
			assertTrue(Math.abs(table1.getValue("FRET Red", t) -
				integratedPeak1) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak1 + " was " + table1.getValue("FRET Red", t));
			double integratedPeak2 = integratePeak(peak2, 2);
			assertTrue(Math.abs(table2.getValue("FRET Red", t) -
				integratedPeak2) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak2 + " was " + table2.getValue("FRET Red", t));
			double integratedPeak3 = integratePeak(peak3, 2);
			assertTrue(Math.abs(table3.getValue("FRET Red", t) -
				integratedPeak3) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak3 + " was " + table3.getValue("FRET Red", t));

			// FRET Green
			Gaussian2D peak4 = new Gaussian2D(0, Math.sin(Math.PI * t / 10) * 2000d +
				2000d, 10.0d, 35d, 1.2d);
			Gaussian2D peak5 = new Gaussian2D(0, Math.sin(Math.PI * t / 10) * 2000d +
				2000d, 32.5d, 33d, 1.2d);
			Gaussian2D peak6 = new Gaussian2D(0, Math.sin(Math.PI * t / 10) * 2000d +
				2000d, 43.7d, 41.7d, 1.2d);

			double integratedPeak4 = integratePeak(peak4, 2);
			assertTrue(Math.abs(table1.getValue("FRET Green", t) -
				integratedPeak4) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak4 + " was " + table1.getValue("FRET Green", t));
			double integratedPeak5 = integratePeak(peak5, 2);
			assertTrue(Math.abs(table2.getValue("FRET Green", t) -
				integratedPeak5) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak5 + " was " + table2.getValue("FRET Green", t));
			double integratedPeak6 = integratePeak(peak6, 2);
			assertTrue(Math.abs(table3.getValue("FRET Green", t) -
				integratedPeak6) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak6 + " was " + table3.getValue("FRET Green", t));

			// Blue
			Gaussian2D peak7 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) * 500d +
				2500d, 10.0d, 35d, 1.2d);
			Gaussian2D peak8 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) * 500d +
				2500d, 32.5d, 33d, 1.2d);
			Gaussian2D peak9 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) * 500d +
				2500d, 43.7d, 41.7d, 1.2d);

			double integratedPeak7 = integratePeak(peak7, 2);
			assertTrue(Math.abs(table1.getValue("Blue", t) -
				integratedPeak7) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak7 + " was " + table1.getValue("Blue", t));
			double integratedPeak8 = integratePeak(peak8, 2);
			assertTrue(Math.abs(table2.getValue("Blue", t) -
				integratedPeak8) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak8 + " was " + table2.getValue("Blue", t));
			double integratedPeak9 = integratePeak(peak9, 2);
			assertTrue(Math.abs(table3.getValue("Blue", t) -
				integratedPeak9) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak9 + " was " + table3.getValue("Blue", t));

			// Red
			Gaussian2D peak10 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) * 500d +
				2500d, 10.0d, 10.0d, 1.2d);
			Gaussian2D peak11 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) * 500d +
				2500d, 32.5d, 8d, 1.2d);
			Gaussian2D peak12 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) * 500d +
				2500d, 43.7d, 16.7d, 1.2d);

			double integratedPeak10 = integratePeak(peak10, 2);
			assertTrue(Math.abs(table1.getValue("Red", t) -
				integratedPeak10) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak10 + " was " + table1.getValue("Red", t));
			double integratedPeak11 = integratePeak(peak11, 2);
			assertTrue(Math.abs(table2.getValue("Red", t) -
				integratedPeak11) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak11 + " was " + table2.getValue("Red", t));
			double integratedPeak12 = integratePeak(peak12, 2);
			assertTrue(Math.abs(table3.getValue("Red", t) -
				integratedPeak12) < TOLERANCE,
				"Integrated intensity is off by more than the tolerance. Should be " +
					integratedPeak12 + " was " + table3.getValue("Red", t));
		}
	}

	private int integratePeak(Gaussian2D gaussian, int radius) {
		int sum = 0;
		for (int y = -radius; y <= radius; y++) {
			for (int x = -radius; x <= radius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));

				if (d <= radius) {
					int cx = (int) (gaussian.x + 0.5);
					int cy = (int) (gaussian.y + 0.5);
					sum += gaussian.getValue(x + cx, y + cy);
				}
			}
		}
		return sum;
	}

	public Dataset simulateDataset() {
		long[] dim = { 50, 50, 3, 50 };
		AxisType[] axes = { Axes.X, Axes.Y, Axes.CHANNEL, Axes.TIME };
		Dataset dataset = datasetService.create(dim, "Simulated Image", axes, 16,
			false, false);

		for (int t = 0; t < dim[3]; t++)
			for (int x = 0; x < dim[0]; x++)
				for (int y = 0; y < dim[1]; y++) {
					Gaussian2D peak1 = new Gaussian2D(0, Math.cos(Math.PI * t / 10) *
						2000d + 2000d, 10.0d, 10.0d, 1.2d);
					Gaussian2D peak2 = new Gaussian2D(0, Math.cos(Math.PI * t / 10) *
						2000d + 2000d, 32.5d, 8d, 1.2d);
					Gaussian2D peak3 = new Gaussian2D(0, Math.cos(Math.PI * t / 10) *
						2000d + 2000d, 43.7d, 16.7d, 1.2d);
					Gaussian2D peak4 = new Gaussian2D(0, Math.sin(Math.PI * t / 10) *
						2000d + 2000d, 10.0d, 35d, 1.2d);
					Gaussian2D peak5 = new Gaussian2D(0, Math.sin(Math.PI * t / 10) *
						2000d + 2000d, 32.5d, 33d, 1.2d);
					Gaussian2D peak6 = new Gaussian2D(0, Math.sin(Math.PI * t / 10) *
						2000d + 2000d, 43.7d, 41.7d, 1.2d);

					dataset.getImgPlus().randomAccess().setPositionAndGet(x, y, 0, t)
						.setReal(peak1.getValue(x, y) + peak2.getValue(x, y) + peak3
							.getValue(x, y) + peak4.getValue(x, y) + peak5.getValue(x, y) +
							peak6.getValue(x, y));

					Gaussian2D peak7 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) *
						500d + 2500d, 10.0d, 35d, 1.2d);
					Gaussian2D peak8 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) *
						500d + 2500d, 32.5d, 33d, 1.2d);
					Gaussian2D peak9 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) *
						500d + 2500d, 43.7d, 41.7d, 1.2d);
					dataset.getImgPlus().randomAccess().setPositionAndGet(x, y, 1, t)
						.setReal(peak7.getValue(x, y) + peak8.getValue(x, y) + peak9
							.getValue(x, y));

					Gaussian2D peak10 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) *
						500d + 2500d, 10.0d, 10.0d, 1.2d);
					Gaussian2D peak11 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) *
						500d + 2500d, 32.5d, 8d, 1.2d);
					Gaussian2D peak12 = new Gaussian2D(0, Math.sin(Math.PI * t / 5) *
						500d + 2500d, 43.7d, 16.7d, 1.2d);
					dataset.getImgPlus().randomAccess().setPositionAndGet(x, y, 2, t)
						.setReal(peak10.getValue(x, y) + peak11.getValue(x, y) + peak12
							.getValue(x, y));
				}

		return dataset;
	}
}
