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

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.FileWidget;

import net.imagej.ops.Initializable;

@Plugin(type = Command.class, label = "Results Filter", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Table Utils", weight = 10,
			mnemonic = 't'),
		@Menu(label = "Results Filter", weight = 30, mnemonic = 'f')})
public class ResultsTableFilterCommand extends DynamicCommand implements Initializable {
	
	@Parameter
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private LogService logService;
    
	@Parameter(label="Table", callback = "updateMinMax", choices = {"a", "b", "c"})
	private String tableName;
	
	@Parameter(label="Column", callback = "updateMinMax", choices = {"a", "b", "c"})
	private String columnName;
	
	@Parameter(label="Filter Type", choices = { "Min_Max" , "Standard_Deviation" , "Table" },
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	private String FilterType = "Min_Max";
	
	@Parameter(label="Filter Selection", choices = { "include" , "exclude" },
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	private String selectionType = "include";
	
	@Parameter(label="Min")
	private double min = 0;
	
	@Parameter(label="Max")
	private double max = 1;
	
	@Parameter(label="Mean +/- (N * STD)")
	private double N_STD = 2;
	
	@Parameter(label="Filter Table", choices = {"a", "b", "c"})
	private String filterTableName;
	
	private MARSResultsTable table, filterTable;
	
	private boolean TableFilter = false;
	private boolean STDFilter = false;
	private boolean includeSelection = true;
	
	// -- Callback methods --

	private void updateMinMax() {
		table = resultsTableService.getResultsTable(tableName);
		if (table.get(columnName) != null) {
			min = max = table.getDoubleColumn(columnName).get(0);
			
			//int index = 0;
			for (double value: table.getDoubleColumn(columnName)) {
				//logService.info("index " + index + " " + value);
				//index++;
				
				if (min > value)
					min = value;
				
				if (max < value)
					max = value;
			}
		}
	}
	
	// -- Initializable methods --

	@Override
	public void initialize() {
        final MutableModuleItem<String> tableItems = getInfo().getMutableInput("tableName", String.class);
		tableItems.setChoices(resultsTableService.getTableNames());
		
		final MutableModuleItem<String> filterTableItems = getInfo().getMutableInput("filterTableName", String.class);
		//ArrayList<String> tableNames = resultsTableService.getColumnNames();
		//tableNames.add(0, "None");
		filterTableItems.setChoices(resultsTableService.getTableNames());
		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput("columnName", String.class);
		columnItems.setChoices(resultsTableService.getColumnNames());
	}
	
	// -- Runnable methods --
	
	@Override
	public void run() {
		table = resultsTableService.getResultsTable(tableName);
		
		if (FilterType.equals("Table")) {
			TableFilter = true;
			STDFilter = false;
		} else if (FilterType.equals("Standard_Deviation")) {
			STDFilter = true;
			TableFilter = false;
		} else {
			STDFilter = false;
			TableFilter = false;
		}
		
		if (selectionType.equals("include")) {
			includeSelection = true;
		} else {
			includeSelection = false;
		}
		
		double[] filterList = new double[0];
		if (TableFilter) {
			filterList = filterTable.getDoubleColumn(columnName).getArray();
		}
		
		//If STDFilter was selected we have to calculate mean and STD before filtering
		double STD = 0;
		double mean = 0;
		if (STDFilter) {
			for (int i = 0; i < table.getRowCount() ; i++) {
				mean += table.getValue(columnName, i);
			}
			mean /= table.getRowCount();
			
			double diffSquares = 0;
			for (int i = 0; i < table.getRowCount() ; i++) {
				diffSquares += (mean - table.getValue(columnName, i))*(mean - table.getValue(columnName, i));
			}
			
			STD = Math.sqrt(diffSquares/(table.getRowCount()-1));		
		}
		
		//There is many better ways to do this....
		//Another option is to take care of includeSelection only at the end.
		ArrayList<Integer> deleteList = new ArrayList<Integer>(); 
		for (int i = 0; i < table.getRowCount() ; i++) {
			double value = table.getValue(columnName, i);
			
			if (Double.isNaN(value)) {
                //Lets just remove all of the null values... They can't be filtered correctly
				deleteList.add(i);
			} else if (TableFilter) {
				if (includeSelection) {
					deleteList.add(i);
					for (int q=0; q<filterList.length; q++) {
						if (value == filterList[q]) {
							deleteList.remove(deleteList.size() - 1);
							break;
						}
					}
				} else {
					for (int q=0; q<filterList.length; q++) {
						if (value == filterList[q]) {
							deleteList.add(i);
							break;
						}
					}
				}
			} else if (STDFilter) {
				if (value < (mean - N_STD*STD) || value > (mean + N_STD*STD) ) {
					if (includeSelection)
						deleteList.add(i);
				} else {
					if (!includeSelection)
						deleteList.add(i);
				}
			} else if (value < min || value > max) {
				if (includeSelection)
					deleteList.add(i);
			} else if (value >= min && value <= max) {
				if (!includeSelection)
					deleteList.add(i);
			}
		
		}
		
		//I guess I put this since the delete method in ResultsTableUtil doesn't work with ArrayLists
		int[] delList = new int[deleteList.size()];
		for (int i=0 ; i < deleteList.size(); i++) {
			delList[i] = deleteList.get(i);
		}

		resultsTableService.deleteRows(table, delList);
		uiService.show(tableName, table);
	}
}
