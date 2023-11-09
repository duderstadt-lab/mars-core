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

package de.mpg.biochem.mars.kcp.commands;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.scijava.Initializable;

import org.decimal4j.util.DoubleRounder;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.kcp.KCP;
import de.mpg.biochem.mars.kcp.KCPSegment;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.MarsRecord;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, headless = true, label = "Change Point Finder",
	menu = { @Menu(label = MenuConstants.PLUGINS_LABEL,
		weight = MenuConstants.PLUGINS_WEIGHT,
		mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
			weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
				label = "KCP", weight = 4, mnemonic = 'k'), @Menu(
					label = "Change Point Finder", weight = 1, mnemonic = 'c') })
public class KCPCommand extends DynamicCommand implements Command,
	Initializable
{

	// GENERAL SERVICES NEEDED
	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	@Parameter
	private UIService uiService;

	@Parameter(callback = "archiveSelectionChanged", label = "MoleculeArchive")
	private MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive;

	@Parameter(label = "X column", choices = { "a", "b", "c" })
	private String xColumn;

	@Parameter(label = "Y column", choices = { "a", "b", "c" })
	private String yColumn;

	@Parameter(label = "Confidence value")
	private double confidenceLevel = 0.99;

	@Parameter(label = "Global sigma", style="format:0.000")
	private double global_sigma = 1;

	@Parameter(label = "Region source:",
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Molecules",
			"Metadata" })
	private String regionSource;

	@Parameter(label = "Calculate sigma from background")
	private boolean calcBackgroundSigma = true;

	@Parameter(label = "Background region", required = false)
	private String backgroundRegion;

	@Parameter(label = "Analyze region")
	private boolean region = true;

	@Parameter(label = "Region", required = false)
	private String regionName;

	@Parameter(label = "Fit steps (zero slope)")
	private boolean step_analysis = false;

	@Parameter(label = "Include:",
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "All",
			"Tagged with", "Untagged" })
	private String include;

	@Parameter(label = "Tags (comma separated list (AND))")
	private String tags = "";

	@Parameter(label = "Thread count", required = false, min = "1", max = "120")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	// Global variables
	// For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	private final AtomicInteger numFinished = new AtomicInteger(0);

	// -- Callback methods --
	@SuppressWarnings("unused")
	private void archiveSelectionChanged() {
		ArrayList<String> columns = new ArrayList<>(archive.properties().getColumnSet());
		columns.sort(String::compareToIgnoreCase);

		final MutableModuleItem<String> xColumnItems = getInfo().getMutableInput(
			"xColumn", String.class);
		xColumnItems.setChoices(columns);

		final MutableModuleItem<String> yColumnItems = getInfo().getMutableInput(
			"yColumn", String.class);
		yColumnItems.setChoices(columns);
	}

	@Override
	public void initialize() {
		ArrayList<String> columns = new ArrayList<>(moleculeArchiveService.getArchives().get(0).properties()
				.getColumnSet());
		columns.sort(String::compareToIgnoreCase);

		final MutableModuleItem<String> xColumnItems = getInfo().getMutableInput(
			"xColumn", String.class);
		xColumnItems.setChoices(columns);

		final MutableModuleItem<String> yColumnItems = getInfo().getMutableInput(
			"yColumn", String.class);
		yColumnItems.setChoices(columns);
	}

	@Override
	public void run() {
		// Lock the window, so it can't be changed while processing
		if (!uiService.isHeadless()) archive.getWindow().lock();

		// Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Change Point Finder");

		addInputParameterLog(builder);
		log += builder.buildParameterList();
		archive.logln(log);

		// Build Collection of UIDs based on tags if they exist...
		List<String> UIDs;
		if (include.equals("Tagged with")) {
			// First we parse tags to make a list...
			String[] tagList = tags.split(",");
			for (int i = 0; i < tagList.length; i++) {
				tagList[i] = tagList[i].trim();
			}

			UIDs = archive.getMoleculeUIDs().stream().filter(UID -> {
				boolean hasTags = true;
				for (String s : tagList) {
					if (!archive.moleculeHasTag(UID, s)) {
						hasTags = false;
						break;
					}
				}
				return hasTags;
			}).collect(toList());
		}
		else if (include.equals("Untagged")) {
			UIDs = archive.getMoleculeUIDs().stream().filter(
				UID -> archive.get(UID).hasNoTags()).collect(toList());
		}
		else {
			// we include All molecules...
			UIDs = archive.getMoleculeUIDs();
		}

		ForkJoinPool forkJoinPool = new ForkJoinPool(nThreads);

		// Output first part of log message...
		logService.info(log);

		double startTime = System.currentTimeMillis();
		logService.info("Finding Change Points...");
		archive.getWindow().updateLockMessage("Finding Change Points...");
		try {
			// Start a thread to keep track of the progress of the number of frames
			// that have been processed.
			// Waiting call back to update the progress bar!!
			Thread progressThread = new Thread() {

				public synchronized void run() {
					try {
						while (progressUpdating.get()) {
							Thread.sleep(100);
							statusService.showStatus(numFinished.intValue(), UIDs.size(),
								"Finding Change Points for " + archive.getName());
						}
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			};

			progressThread.start();

			// This will spawn a bunch of threads that will analyze molecules
			// individually in parallel and put the change point tables back
			// into the same molecule record.

			forkJoinPool.submit(() -> UIDs.parallelStream().forEach(i -> {
				Molecule molecule = archive.get(i);

				if (molecule.getTable().hasColumn(xColumn) && molecule.getTable()
					.hasColumn(yColumn))
				{
					findChangePoints(molecule);
					archive.put(molecule);
				}
			})).get();

			progressUpdating.set(false);

		}
		catch (InterruptedException | ExecutionException e) {
			// handle exceptions
			logService.error(e.getMessage());
			e.printStackTrace();
			logService.info(LogBuilder.endBlock(false));
			forkJoinPool.shutdown();
			return;
		}
		finally {
			forkJoinPool.shutdown();
		}

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			startTime) / 60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
		archive.logln(LogBuilder.endBlock(true));

		statusService.showStatus(1, 1, "Change point search for archive " + archive
			.getName() + " - Done!");
	}

	private void findChangePoints(Molecule molecule) {
		MarsTable table = molecule.getTable();
		MarsRecord regionRecord = (regionSource.equals("Molecules")) ? molecule : archive.getMetadata(molecule.getMetadataUID());

		// START NaN FIX
		ArrayList<Double> xDataSafe = new ArrayList<>();
		ArrayList<Double> yDataSafe = new ArrayList<>();
		for (int i = 0; i < table.getRowCount(); i++) {
			if (!Double.isNaN(table.getValue(xColumn, i)) && !Double.isNaN(
				table.getValue(yColumn, i)))
			{
				xDataSafe.add(table.getValue(xColumn, i));
				yDataSafe.add(table.getValue(yColumn, i));
			}
		}

		int rowCount = xDataSafe.size();

		int offset = 0;
		int length = rowCount;

		int sigXStart = 0;
		int sigXEnd = rowCount;

		double[] xData = new double[rowCount];
		double[] yData = new double[rowCount];
		for (int i = 0; i < rowCount; i++) {
			xData[i] = xDataSafe.get(i);
			yData[i] = yDataSafe.get(i);
		}
		// END FIX

		if (region && !regionRecord.hasRegion(regionName)) return;

		boolean offsetSet = false;
		boolean sigmaOffsetSet = false;
		for (int j = 0; j < rowCount; j++) {
			if (region) {
				if (xData[j] >= regionRecord.getRegion(regionName).getStart() &&
					!offsetSet)
				{
					offset = j;
					offsetSet = true;
				}
				else if (xData[j] <= regionRecord.getRegion(regionName).getEnd()) {
					length = j - offset + 1;
				}
			}

			if (calcBackgroundSigma && regionRecord.hasRegion(backgroundRegion)) {
				if (xData[j] >= regionRecord.getRegion(backgroundRegion).getStart() &&
					!sigmaOffsetSet)
				{
					sigXStart = j;
					sigmaOffsetSet = true;
				}
				else if (xData[j] <= regionRecord.getRegion(backgroundRegion)
					.getEnd())
				{
					sigXEnd = j;
				}
			}
		}

		if (length == 0) {
			// When length is zero we add a single dummy row with all NaN values.
			ArrayList<KCPSegment> segments = new ArrayList<>();
			KCPSegment segment = new KCPSegment(Double.NaN, Double.NaN, Double.NaN,
				Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
			segments.add(segment);
			molecule.putSegmentsTable(xColumn, yColumn, buildSegmentTable(segments));
			numFinished.incrementAndGet();
			return;
		}

		// Use global sigma or use local sigma or calculate sigma (in this order of
		// priority)
		double sigma = global_sigma;
		if (molecule.hasParameter(yColumn + "_sigma")) {
			sigma = molecule.getParameter(yColumn + "_sigma");
		}
		else if (molecule.hasRegion(backgroundRegion)) {
			if (calcBackgroundSigma) sigma = KCP.calc_sigma(yData, sigXStart,
					sigXEnd);
		}

		double[] xRegion = Arrays.copyOfRange(xData, offset, offset + length);
		double[] yRegion = Arrays.copyOfRange(yData, offset, offset + length);

		KCP change = new KCP(sigma, confidenceLevel, xRegion, yRegion,
			step_analysis);
		try {
			MarsTable segmentsTable = buildSegmentTable(change.generate_segments());
			if (region) molecule.putSegmentsTable(xColumn, yColumn, regionName,
				segmentsTable);
			else molecule.putSegmentsTable(xColumn, yColumn, segmentsTable);
		}
		catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
		numFinished.incrementAndGet();
	}

	private MarsTable buildSegmentTable(List<KCPSegment> segments) {
		MarsTable output = new MarsTable();
		output.add(new DoubleColumn(KCPSegment.X1));
		output.add(new DoubleColumn(KCPSegment.Y1));
		output.add(new DoubleColumn(KCPSegment.X2));
		output.add(new DoubleColumn(KCPSegment.Y2));
		output.add(new DoubleColumn(KCPSegment.A));
		output.add(new DoubleColumn(KCPSegment.SIGMA_A));
		output.add(new DoubleColumn(KCPSegment.B));
		output.add(new DoubleColumn(KCPSegment.SIGMA_B));

		int row = 0;
		for (KCPSegment seg : segments) {
			output.appendRow();
			output.setValue(KCPSegment.X1, row, seg.x1);
			output.setValue(KCPSegment.Y1, row, seg.y1);
			output.setValue(KCPSegment.X2, row, seg.x2);
			output.setValue(KCPSegment.Y2, row, seg.y2);
			output.setValue(KCPSegment.A, row, seg.a);
			output.setValue(KCPSegment.SIGMA_A, row, seg.sigma_a);
			output.setValue(KCPSegment.B, row, seg.b);
			output.setValue(KCPSegment.SIGMA_B, row, seg.sigma_b);
			row++;
		}
		return output;
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", xColumn);
		builder.addParameter("Y Column", yColumn);
		builder.addParameter("Confidence value", String.valueOf(confidenceLevel));
		builder.addParameter("Global sigma", String.valueOf(global_sigma));
		builder.addParameter("Region Source", regionSource);
		builder.addParameter("Calculate sigma from background", String.valueOf(
			calcBackgroundSigma));
		builder.addParameter("Background Region", backgroundRegion);
		builder.addParameter("Analyze region", String.valueOf(region));
		builder.addParameter("Region", regionName);
		builder.addParameter("Include tags", include);
		builder.addParameter("Tags", tags);
		builder.addParameter("Fit steps (zero slope)", String.valueOf(
			step_analysis));
		builder.addParameter("Thread count", nThreads);
	}

	public void setArchive(
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive)
	{
		this.archive = archive;
	}

	public
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>>
		getArchive()
	{
		return archive;
	}

	public void setXColumn(String xColumn) {
		this.xColumn = xColumn;
	}

	public String getXColumn() {
		return xColumn;
	}

	public void setYColumn(String yColumn) {
		this.yColumn = yColumn;
	}

	public String getYColumn() {
		return yColumn;
	}

	public void setConfidenceLevel(double confidenceLevel) {
		this.confidenceLevel = confidenceLevel;
	}

	public double getConfidenceLevel() {
		return confidenceLevel;
	}

	public void setGlobalSigma(double global_sigma) {
		this.global_sigma = global_sigma;
	}

	public double getGlobalSigma() {
		return global_sigma;
	}

	public void setRegionSource(String regionSource) {
		this.regionSource = regionSource;
	}

	public String getRegionSource() {
		return this.regionSource;
	}

	public void setCalculateBackgroundSigma(boolean calcBackgroundSigma) {
		this.calcBackgroundSigma = calcBackgroundSigma;
	}

	public boolean getCalculateBackgroundSigma() {
		return calcBackgroundSigma;
	}

	public void setBackgroundRegion(String backgroundRegion) {
		this.backgroundRegion = backgroundRegion;
	}

	public String getBackgroundRegion() {
		return backgroundRegion;
	}

	public void setAnalyzeRegion(boolean region) {
		this.region = region;
	}

	public void setRegion(String regionName) {
		this.regionName = regionName;
	}

	public void setIncludeTags(String include) {
		this.include = include;
	}

	public String getIncludeTags() {
		return include;
	}

	public void setTags(String tags) {
		this.tags = tags;
	}

	public String getTags() {
		return tags;
	}

	public void setFitSteps(boolean step_analysis) {
		this.step_analysis = step_analysis;
	}

	public boolean getFitSteps() {
		return step_analysis;
	}

	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}

	public int getThreads() {
		return this.nThreads;
	}
}
