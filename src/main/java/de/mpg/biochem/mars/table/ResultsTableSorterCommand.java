/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.mars.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import net.imagej.ops.Initializable;

@Plugin(type = Command.class, label = "Results Sorter", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Table Utils", weight = 10,
			mnemonic = 't'),
		@Menu(label = "Results Sorter", weight = 20, mnemonic = 's')})
public class ResultsTableSorterCommand extends DynamicCommand implements Initializable {
	
	@Parameter
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
	
    @Parameter(label="Table")
    private MARSResultsTable table;
    
    @Parameter(label="Column", choices = {"a", "b", "c"})
	private String column;
    
	@Parameter(label="Group Column", choices = {"no group"})
	private String group;

	@Parameter(label="ascending")
	private boolean ascending;
	
	// -- Initializable methods --

	@Override
	public void initialize() {
		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput("column", String.class);
		columnItems.setChoices(resultsTableService.getColumnNames());
		
		final MutableModuleItem<String> groupItems = getInfo().getMutableInput("group", String.class);
		
		ArrayList<String> colNames = resultsTableService.getColumnNames();
		colNames.add(0, "no group");
		groupItems.setChoices(colNames);
	}
	
	// -- Runnable methods --
	
	@Override
	public void run() {
		if (group.equals("no group")) {
			table.sort(ascending, column);
		} else {
			table.sort(ascending, group, column);
		}
		
		if (table.getWindow() != null)
			table.getWindow().update();
	}
	
	public void setTable(MARSResultsTable table) {
		this.table = table;
	}
	
	public MARSResultsTable getTable() {
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
