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

package de.mpg.biochem.mars.kcp.commands;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.imagej.ops.Initializable;

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
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsRegion;

@Plugin(type = Command.class, headless = true, label = "Sigma Calculator",
	menu = { @Menu(label = MenuConstants.PLUGINS_LABEL,
		weight = MenuConstants.PLUGINS_WEIGHT,
		mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
			weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
				label = "KCP", weight = 30, mnemonic = 'k'), @Menu(
					label = "Sigma Calculator", weight = 20, mnemonic = 's') })
public class SigmaCalculatorCommand extends DynamicCommand implements Command,
	Initializable
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

	@Parameter(label = "Region type:",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { "All",
			"Defined below", "Defined in Molecules", "Defined in Metadata" })
	private String regionType;

	@Parameter(label = "from")
	private double from = 1;

	@Parameter(label = "to")
	private double to = 500;

	@Parameter(label = "Region")
	private String regionName;

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

		String log = LogBuilder.buildTitleBlock("Sigma Calculator");

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		// Output first part of log message...
		logService.info(log);

		// Lock the window so it can't be changed while processing
		if (!uiService.isHeadless()) archive.lock();

		archive.logln(log);

		final String paramName = yColumn + "_sigma";

		ConcurrentMap<String, MarsRegion> regionMap =
			new ConcurrentHashMap<String, MarsRegion>();

		if (regionType.equals("Defined in Metadata")) {
			archive.getMetadataUIDs().parallelStream().forEach(metaUID -> {
				MarsMetadata metadata = archive.getMetadata(metaUID);
				if (metadata.hasRegion(regionName)) regionMap.put(metaUID, metadata
					.getRegion(regionName));
			});
		}

		// Loop through each molecule and calculate sigma, add it as a parameter
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			MarsTable datatable = molecule.getTable();

			if (regionType.equals("Defined below")) {
				molecule.setParameter(paramName, datatable.std(yColumn, xColumn, from,
					to));
			}
			else if (regionType.equals("Defined in Molecules") && molecule.hasRegion(
				regionName))
			{
				MarsRegion regionOfInterest = molecule.getRegion(regionName);
				molecule.setParameter(paramName, datatable.std(yColumn, xColumn,
					regionOfInterest.getStart(), regionOfInterest.getEnd()));
			}
			else if (regionType.equals("Defined in Metadata") && regionMap
				.containsKey(molecule.getMetadataUID()))
			{
				MarsRegion regionOfInterest = regionMap.get(molecule.getMetadataUID());
				molecule.setParameter(paramName, datatable.std(yColumn, xColumn,
					regionOfInterest.getStart(), regionOfInterest.getEnd()));
			}
			else {
				// WE assume this mean sigma for whole trace.
				molecule.setParameter(paramName, datatable.std(yColumn));
			}

			archive.put(molecule);
		});

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
		archive.logln(LogBuilder.endBlock(true));
		archive.logln("   ");

		// Unlock the window so it can be changed
		if (!uiService.isHeadless()) archive.unlock();
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", xColumn);
		builder.addParameter("Y Column", yColumn);
		builder.addParameter("Region type", regionType);
		builder.addParameter("from", String.valueOf(from));
		builder.addParameter("to", String.valueOf(to));
		builder.addParameter("Region", regionName);
		builder.addParameter("New parameter added", yColumn + "_sigma");
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

	public void setxColumn(String xColumn) {
		this.xColumn = xColumn;
	}

	public String getxColumn() {
		return xColumn;
	}

	public void setyColumn(String yColumn) {
		this.yColumn = yColumn;
	}

	public String getyColumn() {
		return yColumn;
	}

	public void setRegionType(String regionType) {
		this.regionType = regionType;
	}

	public String getRegionType() {
		return this.regionType;
	}

	public void setRegionName(String regionName) {
		this.regionName = regionName;
	}

	public String getRegionName() {
		return regionName;
	}

	public void setFrom(double from) {
		this.from = from;
	}

	public double getFrom() {
		return from;
	}

	public void setTo(double to) {
		this.to = to;
	}

	public double getTo() {
		return to;
	}
}
