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

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import net.imagej.ops.Initializable;

@Plugin(type = Command.class, label = "Results Group Filter", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Table Utils", weight = 10,
			mnemonic = 't'),
		@Menu(label = "Results Group Filter", weight = 40, mnemonic = 'g')})
public class ResultsGroupFilterCommand extends DynamicCommand implements Initializable {
	
	@Parameter
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter(label="Table")
    private MARSResultsTable table;
	
	@Parameter(label="Group Column", choices = {"a", "b", "c"})
	private String group;
	
	@Parameter
	int min = 0;
	
	@Parameter
	int max = 100;
	
	// -- Initializable methods --

	@Override
	public void initialize() {		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput("group", String.class);
		columnItems.setChoices(resultsTableService.getColumnNames());
	}
	
	// -- Runnable methods --
	
	@Override
	public void run() {
		// sort on group column
		ResultsTableSorterCommand.sort(table, true, group);
		
		ResultsTableList rtl = new ResultsTableList(table);
		
		int to = table.getRowCount();
		double currentGroup = table.getValue(group, to - 1);
		int count = 1;
		
		for (int row = table.getRowCount() - 2; row >= 0; row--) {
			
			if (table.getValue(group, row) == currentGroup) {
				count++;
			} else {
				if (count < min || count > max)
					rtl.removeRange(row + 1, to);
				
				to = row + 1;
				currentGroup = table.getValue(group, row);
				count = 1;
			}
			
		}
		
		if (count < min || count > max)
			rtl.removeRange(0, to);
		
		uiService.show(table.getName(), table);
	}
}
