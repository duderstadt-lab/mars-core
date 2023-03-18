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

package de.mpg.biochem.mars.molecule.commands;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsRegion;
import net.imagej.ops.Initializable;

@Plugin(type = Command.class, label = "Region Difference Calculator", menu = {
	@Menu(label = MenuConstants.PLUGINS_LABEL,
		weight = MenuConstants.PLUGINS_WEIGHT,
		mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
			weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
				label = "Molecule", weight = 2, mnemonic = 'm'), @Menu(label = "Util",
					weight = 7, mnemonic = 'u'), @Menu(
						label = "Region Difference Calculator", weight = 10,
						mnemonic = 'o') })
public class RegionDifferenceCalculatorCommand extends DynamicCommand implements
	Command, Initializable
{

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

	@Parameter(label = "X Column", choices = { "a", "b", "c" })
	private String xColumn;

	@Parameter(label = "Y Column", choices = { "a", "b", "c" })
	private String yColumn;

	@Parameter(label = "Region source:",
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Molecules",
			"Metadata" })
	private String regionSource;

	@Parameter(label = "Region 1 name")
	private String regionOneName;

	@Parameter(label = "Region 2 name")
	private String regionTwoName;

	@Parameter(label = "Parameter Name")
	private String ParameterName;

	// -- Callback methods --
	private void archiveSelectionChanged() {
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(archive.properties().getColumnSet());
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
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(moleculeArchiveService.getArchives().get(0).properties()
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
		// Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();

		// Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Region Difference Calculator");

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		// Output first part of log message...
		logService.info(log);

		archive.getWindow().updateLockMessage("Calculating Region Differences...");

		archive.logln(log);

		if (regionSource.equals("Molecules")) {
			// Loop through each molecule and add reversal difference value to
			// parameters for each molecule
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				Molecule molecule = archive.get(UID);

				if (!molecule.hasRegion(regionOneName) || !molecule.hasRegion(
					regionTwoName)) return;

				MarsTable datatable = molecule.getTable();

				double region1_mean = datatable.mean(yColumn, xColumn, molecule
					.getRegion(regionOneName).getStart(), molecule.getRegion(
						regionOneName).getEnd());
				double region2_mean = datatable.mean(yColumn, xColumn, molecule
					.getRegion(regionTwoName).getStart(), molecule.getRegion(
						regionTwoName).getEnd());

				molecule.setParameter(ParameterName, region1_mean - region2_mean);

				archive.put(molecule);
			});
		}
		else {
			// Before we start we should build a Map of region information from the
			// image metadata records
			// then we can use the map as we go through the molecules.
			// This will be most efficient.
			ConcurrentMap<String, MarsRegion> metadataRegionOneMap =
				new ConcurrentHashMap<String, MarsRegion>();
			ConcurrentMap<String, MarsRegion> metadataRegionTwoMap =
				new ConcurrentHashMap<String, MarsRegion>();

			archive.getMetadataUIDs().parallelStream().forEach(metaUID -> {
				MarsMetadata metadata = archive.getMetadata(metaUID);
				if (metadata.hasRegion(regionOneName)) metadataRegionOneMap.put(metaUID,
					metadata.getRegion(regionOneName));

				if (metadata.hasRegion(regionTwoName)) metadataRegionTwoMap.put(metaUID,
					metadata.getRegion(regionTwoName));
			});

			// Loop through each molecule and add reversal difference value to
			// parameters for each molecule
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				String metaUID = archive.getMetadataUIDforMolecule(UID);
				if (!metadataRegionOneMap.containsKey(metaUID) && !metadataRegionTwoMap
					.containsKey(metaUID)) return;

				MarsRegion regionOne = metadataRegionOneMap.get(metaUID);
				MarsRegion regionTwo = metadataRegionTwoMap.get(metaUID);

				Molecule molecule = archive.get(UID);
				MarsTable datatable = molecule.getTable();

				double region1_mean = datatable.mean(yColumn, xColumn, regionOne
					.getStart(), regionOne.getEnd());
				double region2_mean = datatable.mean(yColumn, xColumn, regionTwo
					.getStart(), regionTwo.getEnd());

				molecule.setParameter(ParameterName, region1_mean - region2_mean);

				archive.put(molecule);
			});
		}

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
		archive.logln("\n" + LogBuilder.endBlock(true));
		archive.logln("   ");
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", xColumn);
		builder.addParameter("Y Column", yColumn);
		builder.addParameter("Region source", regionSource);
		builder.addParameter("Region 1 name", regionOneName);
		builder.addParameter("Region 2 name", regionTwoName);
		builder.addParameter("Parameter Name", ParameterName);
	}

	public static double calcRegionDifference(Molecule molecule, String xColumn,
		String yColumn, MarsRegion regionOne, MarsRegion regionTwo,
		String parameterName)
	{
		MarsTable datatable = molecule.getTable();

		double region1_mean = datatable.mean(yColumn, xColumn, regionOne.getStart(),
			regionOne.getEnd());
		double region2_mean = datatable.mean(yColumn, xColumn, regionTwo.getStart(),
			regionTwo.getEnd());

		double parameterValue = region1_mean - region2_mean;

		molecule.setParameter(parameterName, parameterValue);

		return parameterValue;
	}

	// Getters and Setters
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

	public void setRegionSource(String regionSource) {
		this.regionSource = regionSource;
	}

	public String getRegionSource() {
		return this.regionSource;
	}

	public void setRegionOne(String regionOneName) {
		this.regionOneName = regionOneName;
	}

	public String getRegionOne() {
		return this.regionOneName;
	}

	public void setRegionTwo(String regionTwoName) {
		this.regionTwoName = regionTwoName;
	}

	public String getRegionTwo() {
		return this.regionTwoName;
	}

	public void setParameterName(String ParameterName) {
		this.ParameterName = ParameterName;
	}

	public String getParameterName() {
		return ParameterName;
	}
}
