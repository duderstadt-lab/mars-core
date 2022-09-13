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

package de.mpg.biochem.mars.molecule.commands;

import java.util.ArrayList;

import net.imagej.ops.Initializable;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Build Archive from Table", menu = {
	@Menu(label = MenuConstants.PLUGINS_LABEL,
		weight = MenuConstants.PLUGINS_WEIGHT,
		mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
			weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
				label = "Molecule", weight = 2, mnemonic = 'm'), @Menu(
					label = "Build Archive from Table", weight = 3, mnemonic = 'b') })
public class BuildArchiveFromTableCommand extends DynamicCommand implements
  Initializable
{

	@Parameter
	private MarsTableService marsTableService;
	
	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	@Parameter
	private MarsTableService resultsTableService;

	@Parameter
	private UIService uiService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private LogService logService;
	
	@Parameter(label = "Table", callback = "tableSelectionChanged", choices = {
		"a", "b", "c" })
	private MarsTable table;

	@Parameter(label = "Molecule id column", choices = { "a", "b",
		"c" })
	private String columnName;

	// OUTPUT PARAMETERS
	@Parameter(label = "Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;
	
  //-- Callback methods --

	private void tableSelectionChanged() {
		ArrayList<String> columns = new ArrayList<>();
		columns.addAll(table.getColumnHeadingList());
		columns.sort(String::compareToIgnoreCase);
		columns.add(0, "None");
		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput(
			"columnName", String.class);
		columnItems.setChoices(columns);
	}

	// -- Initializable methods --

	@Override
	public void initialize() {
		ArrayList<String> columns = new ArrayList<>();
		columns.addAll(marsTableService.getTables().get(0).getColumnHeadingList());
		columns.sort(String::compareToIgnoreCase);
		columns.add(0, "None");
		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput(
			"columnName", String.class);
		columnItems.setChoices(columns);
	}

	@Override
	public void run() {

		String name = table.getName() + ".yama";

		if (moleculeArchiveService.contains(name)) {
			uiService.showDialog("The MoleculeArchive " + name +
				" has already been created and is open.", MessageType.ERROR_MESSAGE,
				OptionType.DEFAULT_OPTION);
			return;
		}

		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock(
			"Building SingleMoleculeArchive from Table");

		builder.addParameter("Table", table.getName());
		builder.addParameter("Ouput archive name", name);
		builder.addParameter("Molecule id column", columnName);

		archive = (!columnName.equals("None")) ? new SingleMoleculeArchive(name, table, columnName) : new SingleMoleculeArchive(name, table);

		builder.addParameter("Molecules added", String.valueOf(archive
			.getNumberOfMolecules()));
		log += builder.buildParameterList();

		// Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", SingleMoleculeArchive.class).setLabel(
			archive.getName());
		logService.info(log);
		logService.info(LogBuilder.endBlock(true));

		log += "\n" + LogBuilder.endBlock(true);
		archive.logln(log);
	}

	public SingleMoleculeArchive getArchive() {
		return archive;
	}

	public void setTable(MarsTable table) {
		this.table = table;
	}

	public MarsTable getTable() {
		return table;
	}
	
	public void setMoleculeIdColumn(String columnName) {
		this.columnName = columnName;
	}

	public String getMoleculeIdColumn() {
		return columnName;
	}
	
}
