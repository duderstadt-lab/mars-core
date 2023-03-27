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

package de.mpg.biochem.mars.table.commands;

import java.util.ArrayList;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import org.scijava.Initializable;

@Plugin(type = Command.class, label = "Sort", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
			label = "Table", weight = 3, mnemonic = 't'), @Menu(label = "Sort",
				weight = 2, mnemonic = 's') })
public class MarsTableSorterCommand extends DynamicCommand implements
	Initializable
{

	@Parameter
	private MarsTableService marsTableService;

	@Parameter
	private UIService uiService;

	@Parameter(callback = "tableSelectionChanged", label = "Table")
	private MarsTable table;

	@Parameter(label = "Column", choices = { "a", "b", "c" })
	private String column;

	@Parameter(label = "Group Column", choices = { "no group" })
	private String group;

	@Parameter(label = "ascending")
	private boolean ascending;

	// -- Callback methods --

	private void tableSelectionChanged() {
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(table.getColumnHeadingList());
		columns.sort(String::compareToIgnoreCase);

		final MutableModuleItem<String> columnItems = getInfo().getMutableInput(
			"column", String.class);
		columnItems.setChoices(columns);

		final MutableModuleItem<String> groupItems = getInfo().getMutableInput(
			"group", String.class);

		final ArrayList<String> colNames = columns;
		colNames.add(0, "no group");
		groupItems.setChoices(colNames);
	}

	// -- Initializable methods --

	@Override
	public void initialize() {
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(marsTableService.getTables().get(0).getColumnHeadingList());
		columns.sort(String::compareToIgnoreCase);

		final MutableModuleItem<String> columnItems = getInfo().getMutableInput(
			"column", String.class);
		columnItems.setChoices(columns);

		final MutableModuleItem<String> groupItems = getInfo().getMutableInput(
			"group", String.class);

		final ArrayList<String> colNames = columns;
		colNames.add(0, "no group");
		groupItems.setChoices(colNames);
	}

	// -- Runnable methods --

	@Override
	public void run() {
		if (group.equals("no group")) {
			table.sort(ascending, column);
		}
		else {
			table.sort(ascending, group, column);
		}

		if (table.getWindow() != null) table.getWindow().update();
	}

	public void setTable(MarsTable table) {
		this.table = table;
	}

	public MarsTable getTable() {
		return table;
	}

	public void setColumn(String column) {
		this.column = column;
	}

	public String getColumn() {
		return column;
	}

	public void setGroupColumn(String group) {
		this.group = group;
	}

	public String getGroupColumn() {
		return group;
	}

	public void setAscending(boolean ascending) {
		this.ascending = ascending;
	}

	public boolean getAscending() {
		return ascending;
	}
}
