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

import net.imagej.ops.Initializable;

import java.util.ArrayList;

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

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Variance Calculator", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
			label = "Molecule", weight = 1, mnemonic = 'm'), @Menu(
				label = "Variance Calculator", weight = 70, mnemonic = 'm') })
public class VarianceCalculatorCommand extends DynamicCommand implements
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

	@Parameter(label = "Column", choices = { "a", "b", "c" })
	private String column;

	@Parameter(label = "Parameter Name")
	private String ParameterName = "column_Variance";

	// -- Callback methods --
	private void archiveSelectionChanged() {
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(archive.properties().getColumnSet());
		columns.sort(String::compareToIgnoreCase);
		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput(
			"column", String.class);
		columnItems.setChoices(columns);
	}
	
	@Override
	public void initialize() {
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(moleculeArchiveService.getArchives().get(0).properties().getColumnSet());
		columns.sort(String::compareToIgnoreCase);
		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput(
			"column", String.class);
		columnItems.setChoices(columns);
	}

	@Override
	public void run() {
		// Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();

		// Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Variance Calculator");

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		// Output first part of log message...
		logService.info(log);

		// Lock the window so it can't be changed while processing
		if (!uiService.isHeadless()) archive.lock();

		archive.logln(log);

		// Loop through each molecule and add Variance parameter for each
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);

			molecule.setParameter(ParameterName, molecule.getTable().variance(
				column));

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
		builder.addParameter("Column", column);
		builder.addParameter("Parameter Name", ParameterName);
	}

	public static double calcVariance(Molecule molecule, String column,
		String parameterName)
	{
		double variance = molecule.getTable().variance(column);

		molecule.setParameter(parameterName, variance);

		return variance;
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

	public void setColumn(String column) {
		this.column = column;
	}

	public String getColumn() {
		return column;
	}

	public void setParameterName(String ParameterName) {
		this.ParameterName = ParameterName;
	}

	public String getParameterName() {
		return ParameterName;
	}
}
