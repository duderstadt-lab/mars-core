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

package de.mpg.biochem.mars.kcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.scijava.app.StatusService;
import org.scijava.log.LogService;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;

public class SegmentDistributionBuilder {
	// Here we use a bunch of global variables for everything
	// Would be better not to do this and instead make all the
	// methods self-contained and static
	// However, this is kind of how it was previously in the old version
	// Also the advantage now is that you load the data once
	// and can then make as many distributions as you want on it

	private final Random ran;

	private final double start;
	private final double end;
	private final int bins;
	private final double binWidth;

	private boolean filter = false;
	private double filter_region_start = 0;
	private double filter_region_stop = 100;

	private int bootstrap_cycles = 100;
	private boolean bootstrap_Segments = false;
	private boolean bootstrap_Molecules = false;

	private final MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive;
	private final ArrayList<String> UIDs;
	private final String yColumnName;
	private final String xColumnName;

	// Global variables
	// For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	private final AtomicInteger numFinished = new AtomicInteger(0);

	private final LogService logService;
	private final StatusService statusService;

	private ForkJoinPool forkJoinPool;

	public SegmentDistributionBuilder(
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive,
		ArrayList<String> UIDs, String yColumnName, String xColumnName,
		double start, double end, int bins, LogService logService,
		StatusService statusService)
	{
		this.archive = archive;
		this.UIDs = UIDs;
		this.yColumnName = yColumnName;
		this.xColumnName = xColumnName;

		this.start = start;
		this.end = end;
		this.bins = bins;

		binWidth = (end - start) / bins;

		this.logService = logService;
		this.statusService = statusService;

		ran = new Random();
	}

	// METHODS FOR SETTING OR UNSETTING A RATE FILTER REGION
	public void setFilter(double filter_region_start, double filter_region_stop) {
		filter = true;
		this.filter_region_start = filter_region_start;
		this.filter_region_stop = filter_region_stop;
	}

	public void unsetFilter() {
		filter = false;
	}

	// METHODS TO ACTIVE BOOTSTRAPPING
	public void bootstrapMolecules(int bootstrap_cycles) {
		bootstrap_Segments = false;
		bootstrap_Molecules = true;
		this.bootstrap_cycles = bootstrap_cycles;
	}

	public void bootstrapSegments(int bootstrap_cycles) {
		bootstrap_Segments = true;
		bootstrap_Molecules = false;
		this.bootstrap_cycles = bootstrap_cycles;
	}

	public void noBootstrapping() {
		bootstrap_Segments = false;
		bootstrap_Molecules = false;
	}

	// METHODS FOR EACH DISTRIBUTION TYPE
	public MarsTable buildRateGaussian(final int nThreads) {
		MarsTable table;
		if (bootstrap_Segments || bootstrap_Molecules) {
			table = new MarsTable(7, bins);
		}
		else {
			table = new MarsTable(3, bins);
		}

		table.setColumnHeader(0, "Rate");
		table.setColumnHeader(1, "Probability");
		table.setColumnHeader(2, "Probability Density");

		ArrayList<Gaussian> gaussians = generateGaussians(UIDs);

		double[] distribution = generate_Gaussian_Distribution(gaussians);

		double binWidth = (end - start) / bins;

		// Now let's determine the normalization constant...
		double normalization = 0;
		for (int a = 0; a < bins; a++) {
			normalization += distribution[a];
		}
		double prob_den_norm = normalization * binWidth;

		// Now let's normalize the distribution and go ahead and generate the
		// table...
		for (int a = 0; a < bins; a++) {
			table.setValue("Rate", a, start + (a + 0.5) * binWidth);
			table.setValue("Probability", a, distribution[a] / normalization);
			table.setValue("Probability Density", a, distribution[a] / prob_den_norm);
		}

		// Now if we are bootstrapping we generate a series of resampled
		// distributions and output the mean and std.
		if (bootstrap_Segments || bootstrap_Molecules) {
			ConcurrentMap<Integer, double[]> boot_distributions =
				new ConcurrentHashMap<>(bootstrap_cycles);

			forkJoinPool = new ForkJoinPool(nThreads);

			try {
				// Start a thread to keep track of the progress of the number of frames
				// that have been processed.
				// Waiting call back to update the progress bar!!
				Thread progressThread = new Thread() {

					public synchronized void run() {
						try {
							while (progressUpdating.get()) {
								Thread.sleep(100);
								statusService.showStatus(numFinished.intValue(),
									bootstrap_cycles, "Building distribution from " + archive
										.getName());
							}
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
				};

				progressThread.start();

				// This will spawn a bunch of threads that will generate distributions
				// individually in parallel
				// and put the results into the boot_distributions map
				// keys will just be numbered from 1 to bootstrap_cycles ...
				forkJoinPool.submit(() -> IntStream.range(0, bootstrap_cycles)
					.parallel().forEach(q -> {
						double[] bootDistribution;

						if (bootstrap_Molecules) {
							ArrayList<String> resampledUIDs = new ArrayList<>();
							for (int i = 0; i < UIDs.size(); i++) {
								resampledUIDs.add(UIDs.get(ran.nextInt(UIDs.size())));
							}
							// build distribution.
							bootDistribution = generate_Gaussian_Distribution(
								generateGaussians(resampledUIDs));
						}
						else {
							// bootstrap_Segments must be true...
							ArrayList<Gaussian> resampledGaussians =
									new ArrayList<>();
							for (int i = 0; i < gaussians.size(); i++) {
								resampledGaussians.add(gaussians.get(ran.nextInt(gaussians
									.size())));
							}
							// build distribution.
							bootDistribution = generate_Gaussian_Distribution(
								resampledGaussians);
						}

						// Now let's determine the normalization constant...
						double norm = 0;
						for (int a = 0; a < bins; a++) {
							norm += bootDistribution[a];
						}

						double[] new_dist = new double[bins];
						for (int a = 0; a < bins; a++) {
							new_dist[a] = bootDistribution[a] / norm;
						}
						boot_distributions.put(q, new_dist);

						numFinished.incrementAndGet();
					})).get();

				progressUpdating.set(false);

				statusService.showStatus(1, 1, "Done building distribution from " +
					archive.getName());

			}
			catch (InterruptedException | ExecutionException e) {
				// handle exceptions
				logService.error(e.getMessage());
				e.printStackTrace();
				logService.info(LogBuilder.endBlock(false));
				forkJoinPool.shutdown();
			}
			finally {
				forkJoinPool.shutdown();
			}

			buildBootstrapRateColumns(table, boot_distributions);
		}

		return table;
	}

	public MarsTable buildRateHistogram(final int nThreads) {
		MarsTable table;
		if (bootstrap_Segments || bootstrap_Molecules) {
			table = new MarsTable(7, bins);
		}
		else {
			table = new MarsTable(3, bins);
		}

		table.setColumnHeader(0, "Rate");
		table.setColumnHeader(1, "Probability");
		table.setColumnHeader(2, "Probability Density");

		ArrayList<KCPSegment> allSegments = collectSegments(UIDs);

		double[] distribution = generate_Histogram_Distribution(allSegments);

		// Now let's determine the normalization constant...
		double normalization = 0;
		for (int a = 0; a < bins; a++) {
			normalization += distribution[a];
		}
		double prob_den_norm = normalization * binWidth;

		// Now let's normalize the distribution and go ahead and generate the
		// table...
		for (int a = 0; a < bins; a++) {
			table.setValue("Rate", a, start + (a + 0.5) * binWidth);
			table.setValue("Probability", a, distribution[a] / normalization);
			table.setValue("Probability Density", a, distribution[a] / prob_den_norm);
		}

		// Now if we are bootstrapping we generate a series of resampled
		// distributions and output the mean and std.
		if (bootstrap_Segments || bootstrap_Molecules) {
			ConcurrentMap<Integer, double[]> boot_distributions =
				new ConcurrentHashMap<>(bootstrap_cycles);

			forkJoinPool = new ForkJoinPool(nThreads);

			try {
				// Start a thread to keep track of the progress of the number of frames
				// that have been processed.
				// Waiting call back to update the progress bar!!
				Thread progressThread = new Thread() {

					public synchronized void run() {
						try {
							while (progressUpdating.get()) {
								Thread.sleep(100);
								statusService.showStatus(numFinished.intValue(),
									bootstrap_cycles, "Building histogram from " + archive
										.getName());
							}
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
				};

				progressThread.start();

				// This will spawn a bunch of threads that will generate distributions
				// individually in parallel
				// and put the results into the boot_distributions map
				// keys will just be numbered from 1 to bootstrap_cycles ...
				forkJoinPool.submit(() -> IntStream.range(0, bootstrap_cycles)
					.parallel().forEach(q -> {
						double[] bootDistribution;

						if (bootstrap_Molecules) {
							ArrayList<String> resampledUIDs = new ArrayList<>();
							for (int i = 0; i < UIDs.size(); i++) {
								resampledUIDs.add(UIDs.get(ran.nextInt(UIDs.size())));
							}
							// build distribution.
							bootDistribution = generate_Histogram_Distribution(
								collectSegments(resampledUIDs));
						}
						else {
							// bootstrap_Segments must be true...
							ArrayList<KCPSegment> resampledSegments =
									new ArrayList<>();
							for (int i = 0; i < allSegments.size(); i++) {
								resampledSegments.add(allSegments.get(ran.nextInt(allSegments
									.size())));
							}
							// build distribution.
							bootDistribution = generate_Histogram_Distribution(
								resampledSegments);
						}

						// Now let's determine the normalization constant...
						double norm = 0;
						for (int a = 0; a < bins; a++) {
							norm += bootDistribution[a];
						}

						double[] new_dist = new double[bins];
						for (int a = 0; a < bins; a++) {
							new_dist[a] = bootDistribution[a] / norm;
						}
						boot_distributions.put(q, new_dist);

						numFinished.incrementAndGet();
					})).get();

				progressUpdating.set(false);

				statusService.showStatus(1, 1, "Done building histogram from " + archive
					.getName());

			}
			catch (InterruptedException | ExecutionException e) {
				// handle exceptions
				logService.error(e.getMessage());
				e.printStackTrace();
				logService.info(LogBuilder.endBlock(false));
				forkJoinPool.shutdown();
			}
			finally {
				forkJoinPool.shutdown();
			}

			buildBootstrapRateColumns(table, boot_distributions);
		}

		return table;
	}

	public MarsTable buildDurationHistogram(final int nThreads) {
		return buildDurationHistogram(false, false, nThreads);
	}

	public MarsTable buildProcessivityByMoleculeHistogram(final int nThreads) {
		return buildDurationHistogram(false, true, nThreads);
	}

	public MarsTable buildProcessivityByRegionHistogram(final int nThreads) {
		return buildDurationHistogram(true, false, nThreads);
	}

	private MarsTable buildDurationHistogram(boolean processivityPerRegion,
		boolean processivityPerMolecule, final int nThreads)
	{
		MarsTable table;
		if (bootstrap_Segments || bootstrap_Molecules) {
			table = new MarsTable(4, bins);
		}
		else {
			table = new MarsTable(2, bins);
		}

		table.setColumnHeader(0, "Duration");
		table.setColumnHeader(1, "Occurrences");

		ArrayList<KCPSegment> allSegments = collectSegments(UIDs);

		double[] distribution = generate_Duration_Distribution(allSegments,
			processivityPerRegion, processivityPerMolecule);

		for (int a = 0; a < bins; a++) {
			table.setValue("Duration", a, start + (a + 0.5) * binWidth);
			table.setValue("Occurrences", a, distribution[a]);
		}

		// Now if we are bootstrapping we generate a series of resampled
		// distributions and output the mean and std.
		if (bootstrap_Segments || bootstrap_Molecules) {
			ConcurrentMap<Integer, double[]> boot_distributions =
				new ConcurrentHashMap<>(bootstrap_cycles);

			forkJoinPool = new ForkJoinPool(nThreads);

			try {
				// Start a thread to keep track of the progress of the number of frames
				// that have been processed.
				// Waiting call back to update the progress bar!!
				Thread progressThread = new Thread() {

					public synchronized void run() {
						try {
							while (progressUpdating.get()) {
								Thread.sleep(100);
								statusService.showStatus(numFinished.intValue(),
									bootstrap_cycles, "Building duration histogram from " +
										archive.getName());
							}
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
				};

				progressThread.start();

				// This will spawn a bunch of threads that will generate distributions
				// individually in parallel
				// and put the results into the boot_distributions map
				// keys will just be numbered from 1 to bootstrap_cycles ...
				forkJoinPool.submit(() -> IntStream.range(0, bootstrap_cycles)
					.parallel().forEach(q -> {
						double[] bootDistribution;

						if (bootstrap_Molecules) {
							ArrayList<String> resampledUIDs = new ArrayList<>();
							for (int i = 0; i < UIDs.size(); i++) {
								resampledUIDs.add(UIDs.get(ran.nextInt(UIDs.size())));
							}
							// build distribution.
							bootDistribution = generate_Duration_Distribution(collectSegments(
								resampledUIDs), processivityPerRegion, processivityPerMolecule);
						}
						else {
							// bootstrap_Segments must be true...
							ArrayList<KCPSegment> resampledSegments =
									new ArrayList<>();
							for (int i = 0; i < allSegments.size(); i++) {
								resampledSegments.add(allSegments.get(ran.nextInt(allSegments
									.size())));
							}
							// build distribution.
							bootDistribution = generate_Duration_Distribution(
								resampledSegments, processivityPerRegion,
								processivityPerMolecule);
						}

						// Now let's determine the normalization constant...
						double norm = 0;
						for (int a = 0; a < bins; a++) {
							norm += bootDistribution[a];
							logService.info("norm " + norm);
						}

						double[] new_dist = new double[bins];
						for (int a = 0; a < bins; a++) {
							new_dist[a] = bootDistribution[a] / norm;
							logService.info("new_dist" + bootDistribution[a] / norm);
						}
						boot_distributions.put(q, new_dist);

						numFinished.incrementAndGet();
					})).get();

				progressUpdating.set(false);

				statusService.showStatus(1, 1,
					"Done building duration histogram from " + archive.getName());

			}
			catch (InterruptedException | ExecutionException e) {
				// handle exceptions
				logService.error(e.getMessage());
				e.printStackTrace();
				logService.info(LogBuilder.endBlock(false));
				forkJoinPool.shutdown();
			}
			finally {
				forkJoinPool.shutdown();
			}
			buildBootstrapDurationColumns(table, boot_distributions);
		}

		return table;
	}

	// INTERNAL METHODS FOR BUILDING DISTRIBUTIONS
	private double[] generate_Gaussian_Distribution(
		ArrayList<Gaussian> Gaussians)
	{
		double integration_resolution = 100;
		double[] distribution = new double[bins];
		for (int a = 0; a < bins; a++) {
			// Here we should integrate to get the mean value in the bin instead of
			// just taking the value at the center
			for (double q = start + a * binWidth; q < (start + (a + 1) *
				binWidth); q += binWidth / integration_resolution)
			{
				for (Gaussian gaussian : Gaussians) {
					distribution[a] += gaussian.getValue(q) * gaussian
							.getDuration() * (binWidth / integration_resolution);
				}
			}
		}

		return distribution;
	}

	private double[] generate_Histogram_Distribution(
		ArrayList<KCPSegment> allSegments)
	{
		double[] distribution = new double[bins];

		for (int a = 0; a < bins; a++) {
			for (KCPSegment allSegment : allSegments) {
				if (Double.isNaN(allSegment.b) || Double.isNaN(allSegment.x1) || Double.isNaN(allSegment.x2)) continue;
				if (!filter || (allSegment.b > filter_region_start &&
						allSegment.b < filter_region_stop)) {
					// We test to see if the current slope is in the current bin, which is
					// centered at the position on the x-axis.
					if (((start + a * binWidth) < allSegment.b) && (allSegment.b <= (start + (a + 1) * binWidth))) {
						// If it is inside we add the number of observations of that slope
						// minus 1 since it takes at least two frames to find the slope.
						distribution[a] += (allSegment.x2 - allSegment.x1);
					}
				}
			}
		}
		return distribution;
	}

	private double[] generate_Duration_Distribution(
		ArrayList<KCPSegment> allSegments, boolean processivityPerRegion,
		boolean processivityPerMolecule)
	{
		double[] distribution = new double[bins];

		ArrayList<Double> durations = new ArrayList<>();
		boolean wasInsideRegion = false;
		double duration = 0;
		String curUID = allSegments.get(0).getUID();

		if (processivityPerMolecule) {
			for (KCPSegment allSegment : allSegments) {
				if (!filter || (allSegment.b > filter_region_start &&
						allSegment.b < filter_region_stop)) {
					if (allSegment.getUID().equals(curUID)) {
						durations.add(duration);
						duration = 0;
					}
					duration += allSegment.b * (allSegment.x2 -
							allSegment.x1);
					curUID = allSegment.getUID();
				}
			}
		}
		else {
			for (KCPSegment allSegment : allSegments) {
				if (!filter || (allSegment.b > filter_region_start &&
						allSegment.b < filter_region_stop)) {
					if (allSegment.getUID().equals(curUID)) {
						if (wasInsideRegion) {
							durations.add(duration);
							duration = 0;
						}
					}
					if (processivityPerRegion) duration += allSegment.b *
							(allSegment.x2 - allSegment.x1);
					else duration += allSegment.x2 - allSegment.x1;
					wasInsideRegion = true;
				} else if (wasInsideRegion) {
					durations.add(duration);
					duration = 0;
					wasInsideRegion = false;
				}
				curUID = allSegment.getUID();
			}
		}
		if (duration != 0) durations.add(duration);

		for (int a = 0; a < bins; a++) {
			for (Double aDouble : durations) {
				if ((start + a * binWidth) <= aDouble && aDouble < (start + (a + 1) * binWidth)) {
					distribution[a]++;
				}
			}
		}
		return distribution;
	}

	private ArrayList<KCPSegment> collectSegments(Collection<String> UIDSet) {
		ArrayList<KCPSegment> allSegments = new ArrayList<>();

		// Can't do this multithreaded at the moment since we are using an arraylist
		UIDSet.forEach(UID -> {
			if (archive.get(UID).getSegmentsTable(xColumnName, yColumnName) != null) {
				// Get the segments table for the current molecule
				MarsTable segments = archive.get(UID).getSegmentsTable(xColumnName,
					yColumnName);

				for (int row = 0; row < segments.getRowCount(); row++) {
					if (Double.isNaN(segments.getValue("B", row))) continue;
					if (!filter || segments.getValue("B", row) > filter_region_start &&
						segments.getValue("B", row) < filter_region_stop) allSegments.add(
							new KCPSegment(segments, row, UID));
				}
			}
		});

		return allSegments;
	}

	private ArrayList<Gaussian> generateGaussians(Collection<String> UIDSet) {
		ArrayList<Gaussian> Gaussians = new ArrayList<>();

		// Can't do this multithreaded at the moment since we are using an arraylist
		UIDSet.forEach(UID -> {
			if (archive.get(UID).getSegmentsTable(xColumnName, yColumnName) != null) {
				// Get the segments table for the current molecule
				MarsTable segments = archive.get(UID).getSegmentsTable(xColumnName,
					yColumnName);

				for (int row = 0; row < segments.getRowCount(); row++) {
					if (Double.isNaN(segments.getValue("B", row)) || Double.isNaN(segments
						.getValue("sigma_B", row)) || Double.isNaN(segments.getValue("X2",
							row)) || Double.isNaN(segments.getValue("X1", row))) continue;
					if (!filter || segments.getValue("B", row) > filter_region_start &&
						segments.getValue("B", row) < filter_region_stop)
					{
						if (segments.getValue("sigma_B", row) != 0 && segments.getValue(
							"X1", row) != 0 && segments.getValue("X2", row) != 0) Gaussians
								.add(new Gaussian(segments.getValue("B", row), segments
									.getValue("sigma_B", row), segments.getValue("X2", row) -
										segments.getValue("X1", row)));
					}
				}
			}
		});

		return Gaussians;
	}

	public void buildBootstrapRateColumns(MarsTable table,
		ConcurrentMap<Integer, double[]> boot_distributions)
	{
		// Now we build and add additional columns to the table.
		double[] mean_distribution = new double[bins];
		double[] std_distribution = new double[bins];

		for (int a = 0; a < bins; a++) {
			mean_distribution[a] = 0;
		}

		for (int a = 0; a < bins; a++) {
			for (int q = 0; q < bootstrap_cycles; q++) {
				mean_distribution[a] += boot_distributions.get(q)[a];
			}
			mean_distribution[a] /= bootstrap_cycles;
		}

		for (int a = 0; a < bins; a++) {
			double sumSquareDiffs = 0;
			for (int q = 0; q < bootstrap_cycles; q++) {
				sumSquareDiffs += (boot_distributions.get(q)[a] -
					mean_distribution[a]) * (boot_distributions.get(q)[a] -
						mean_distribution[a]);
			}
			std_distribution[a] = Math.sqrt(sumSquareDiffs / (bootstrap_cycles - 1));
		}

		table.setColumnHeader(3, "Bootstrap Probability");
		table.setColumnHeader(4, "Bootstrap Probability STD");
		table.setColumnHeader(5, "Bootstrap Probability Density");
		table.setColumnHeader(6, "Bootstrap Probability Density STD");

		for (int a = 0; a < bins; a++) {
			table.setValue("Bootstrap Probability", a, mean_distribution[a]);
			table.setValue("Bootstrap Probability STD", a, std_distribution[a]);
			table.setValue("Bootstrap Probability Density", a, mean_distribution[a] /
				binWidth);
			table.setValue("Bootstrap Probability Density STD", a,
				std_distribution[a] / binWidth);
		}
	}

	// Kind of duplicated from above...
	// should simplify
	public void buildBootstrapDurationColumns(MarsTable table,
		ConcurrentMap<Integer, double[]> boot_distributions)
	{
		// Now we build and add additional columns to the table.
		double[] mean_distribution = new double[bins];
		double[] std_distribution = new double[bins];

		for (int a = 0; a < bins; a++) {
			mean_distribution[a] = 0;
		}

		for (int a = 0; a < bins; a++) {
			for (int q = 0; q < bootstrap_cycles; q++) {
				mean_distribution[a] += boot_distributions.get(q)[a];
			}
			mean_distribution[a] /= bootstrap_cycles;
		}

		for (int a = 0; a < bins; a++) {
			double sumSquareDiffs = 0;
			for (int q = 0; q < bootstrap_cycles; q++) {
				sumSquareDiffs += (boot_distributions.get(q)[a] -
					mean_distribution[a]) * (boot_distributions.get(q)[a] -
						mean_distribution[a]);
			}
			std_distribution[a] = Math.sqrt(sumSquareDiffs / (bootstrap_cycles - 1));
		}

		table.setColumnHeader(2, "Bootstrap Probability");
		table.setColumnHeader(3, "Bootstrap Probability STD");

		for (int a = 0; a < bins; a++) {
			table.setValue("Bootstrap Probability", a, mean_distribution[a]);
			table.setValue("Bootstrap Probability STD", a, std_distribution[a]);
		}
	}

}
