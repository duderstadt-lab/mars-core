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

package de.mpg.biochem.mars.molecule.commands;

import org.decimal4j.util.DoubleRounder;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Add Time", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
			label = "Molecule", weight = 1, mnemonic = 'm'), @Menu(label = "Add Time",
				weight = 40, mnemonic = 'a') })
public class AddTimeCommand extends DynamicCommand implements Command {

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	@Parameter
	private UIService uiService;

	@Parameter(label = "MoleculeArchive")
	private SingleMoleculeArchive archive;

	@Parameter(label = "Source:",
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "dt",
			"Time increment" })
	private String source;

	@Parameter(label = "Time increment (s)")
	private double timeIncrement = 1.00d;

	@Override
	public void run() {
		// Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();

		// Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Add Time (s)");

		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Source", source);
		if (source.equals("Time increment")) builder.addParameter(
			"Time increment (s)", timeIncrement);
		log += builder.buildParameterList();

		// Output first part of log message...
		logService.info(log);

		// Lock the window so it can't be changed while processing
		if (!uiService.isHeadless()) archive.lock();

		archive.logln(log);

		// Loop through each molecule and add a Time (s) column using the metadata
		// information...
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			SingleMolecule molecule = archive.get(UID);

			MarsMetadata metadata = archive.getMetadata(molecule.getMetadataUID());
			MarsTable datatable = molecule.getTable();

			// If the column already exists we don't need to add it
			// instead we will just be overwriting the values below..
			if (!datatable.hasColumn("Time (s)")) datatable.appendColumn("Time (s)");

			int imageIndex = 0;
			if (metadata.getImageCount() > 1) for (int index = 0; index < metadata
				.getImageCount(); index++)
			{
				if (metadata.getImage(index).getImageID() == molecule.getImage()) {
					imageIndex = index;
					break;
				}
			}

			final int finalImageIndex = imageIndex;

			if (source.equals("dt")) datatable.rows().forEach(row -> row.setValue(
				"Time (s)", metadata.getPlane(finalImageIndex, 0, (int) molecule
					.getParameter("Channel"), (int) row.getValue("T"))
					.getDeltaTinSeconds()));
			else molecule.getTable().rows().forEach(row -> row.setValue("Time (s)",
				row.getValue("T") * timeIncrement));

			archive.put(molecule);
		});

		// Set the incrementTime for all metadata to match that provided.
		if (source.equals("Time increment")) archive.metadata().forEach(
			metadata -> metadata.images().forEach(image -> image
				.setTimeIncrementInSeconds(timeIncrement)));

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));

		archive.logln(LogBuilder.endBlock(true));
		archive.logln("  ");

		// Unlock the window so it can be changed
		if (!uiService.isHeadless()) archive.unlock();
	}

	public static void addTime(SingleMoleculeArchive archive) {
		// Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Add Time (s)");

		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Source", "dt");
		log += builder.buildParameterList();

		archive.logln(log);

		// Loop through each molecule and add a Time (s) column using the metadata
		// information...
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			SingleMolecule molecule = archive.get(UID);

			MarsMetadata metadata = archive.getMetadata(molecule.getMetadataUID());
			MarsTable datatable = molecule.getTable();

			// If the column already exists we don't need to add it
			// instead we will just be overwriting the values below..
			if (!datatable.hasColumn("Time (s)")) datatable.appendColumn("Time (s)");

			datatable.rows().forEach(row -> row.setValue("Time (s)", metadata
				.getPlane(0, 0, (int) molecule.getParameter("Channel"), (int) row
					.getValue("T")).getDeltaTinSeconds()));

			archive.put(molecule);
		});

		archive.logln(LogBuilder.endBlock(true));
		archive.logln("  ");
	}

	public static void addTime(SingleMoleculeArchive archive,
		double timeIncrement)
	{
		// Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Add Time (s)");

		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Time increment (s)", timeIncrement);
		log += builder.buildParameterList();

		archive.logln(log);

		// Loop through each molecule and add a Time (s) column using the metadata
		// information...
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			SingleMolecule molecule = archive.get(UID);

			MarsTable datatable = molecule.getTable();

			// If the column already exists we don't need to add it
			// instead we will just be overwriting the values below..
			if (!datatable.hasColumn("Time (s)")) datatable.appendColumn("Time (s)");

			molecule.getTable().rows().forEach(row -> row.setValue("Time (s)", row
				.getValue("T") * timeIncrement));

			archive.put(molecule);
		});

		// Set the incrementTime for all metadata to match that provided.
		archive.metadata().forEach(metadata -> metadata.images().forEach(
			image -> image.setTimeIncrementInSeconds(timeIncrement)));

		archive.logln(LogBuilder.endBlock(true));
		archive.logln("  ");
	}

	public void setArchive(SingleMoleculeArchive archive) {
		this.archive = archive;
	}

	public SingleMoleculeArchive getArchive() {
		return archive;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getSource() {
		return this.source;
	}

	public void setTimeIncrement(double timeIncrement) {
		this.timeIncrement = timeIncrement;
	}

	public double getTimeIncrement() {
		return this.timeIncrement;
	}
}
