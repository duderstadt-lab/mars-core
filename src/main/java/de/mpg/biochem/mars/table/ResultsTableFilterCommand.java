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
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 'm'),
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
    private MARSResultsTable table;
	
	@Parameter(label="Column", callback = "updateMinMax", choices = {"a", "b", "c"})
	private String columnName;
	
	@Parameter(label="Type", choices = { "Min Max" , "Standard Deviation" , "Filter Table" },
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	private String FilterType = "Min Max";
	
	@Parameter(label="Selection", choices = { "inside" , "outside" },
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	private String selectionType = "include";
	
	@Parameter(label="Min")
	private double min = 0;
	
	@Parameter(label="Max")
	private double max = 1;
	
	@Parameter(label="Mean +/- (N * STD)")
	private double N_STD = 2;
	
	@Parameter(label="Filter Table", choices = {"a", "b", "c"})
	private MARSResultsTable filterTable;
	
	private boolean TableFilter = false;
	private boolean STDFilter = false;
	private boolean includeSelection = true;
	
	// -- Callback methods --

	private void updateMinMax() {
		if (table.get(columnName) != null) {
			min = max = table.getDoubleColumn(columnName).get(0);
			
			for (double value: table.getDoubleColumn(columnName)) {
				
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
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput("columnName", String.class);
		columnItems.setChoices(resultsTableService.getColumnNames());
	}
	
	// -- Runnable methods --
	
	@Override
	public void run() {
		if (FilterType.equals("Filter Table")) {
			TableFilter = true;
			STDFilter = false;
		} else if (FilterType.equals("Standard Deviation")) {
			STDFilter = true;
			TableFilter = false;
		} else {
			STDFilter = false;
			TableFilter = false;
		}
		
		if (selectionType.equals("inside")) {
			includeSelection = true;
		} else {
			includeSelection = false;
		}
		
		double[] filterList = new double[0];
		if (TableFilter) {
			filterList = filterTable.getDoubleColumn(columnName).getArray();
		}
		
		//If STDFilter was selected we have to calculate mean and STD before filtering
		double STD = table.std(columnName);
		double mean = table.mean(columnName);
		
		//There is many better ways to do this....
		//Another option is to take care of includeSelection only at the end.
		ArrayList<Integer> deleteList = new ArrayList<Integer>(); 
		for (int row = 0; row < table.getRowCount() ; row++) {
			double value = table.getValue(columnName, row);
			
			if (Double.isNaN(value)) {
                //Lets just remove all of the null values... They can't be filtered correctly
				deleteList.add(row);
			} else if (TableFilter) {
				if (includeSelection) {
					deleteList.add(row);
					for (int q=0; q<filterList.length; q++) {
						if (value == filterList[q]) {
							deleteList.remove(deleteList.size() - 1);
							break;
						}
					}
				} else {
					for (int q=0; q<filterList.length; q++) {
						if (value == filterList[q]) {
							deleteList.add(row);
							break;
						}
					}
				}
			} else if (STDFilter) {
				if (value < (mean - N_STD*STD) || value > (mean + N_STD*STD) ) {
					if (includeSelection)
						deleteList.add(row);
				} else {
					if (!includeSelection)
						deleteList.add(row);
				}
			} else if (value < min || value > max) {
				if (includeSelection)
					deleteList.add(row);
			} else if (value >= min && value <= max) {
				if (!includeSelection)
					deleteList.add(row);
			}
		
		}
		
		int[] delList = new int[deleteList.size()];
		for (int i=0 ; i < deleteList.size(); i++) {
			delList[i] = deleteList.get(i);
		}

		resultsTableService.deleteRows(table, delList);
		
		if (table.getWindow() != null)
			table.getWindow().update();
	}
	
    public void setTable(MARSResultsTable table) {
    	this.table = table;
    }
    
    public MARSResultsTable getTable() {
    	return table;
    }
    
    public void setColumn(String columnName) {
    	this.columnName = columnName;
    }
    
    public String getColumn() {
    	return columnName;
    }
    
    public void setType(String FilterType) {
    	this.FilterType = FilterType;
    }
    
    public String getType() {
    	return FilterType;
    }
    
    public void setSelection(String selectionType) {
    	this.selectionType = selectionType;
    }

	public void setMin(double min) {
		this.min = min;
	}
	
	public double getMin() {
		return min;
	}
	
	public void setMax(double max) {
		this.max = max;
	}
	
	public double getMax() {
		return max;
	}
	
	public void setNSTD(double N_STD) {
		this.N_STD = N_STD;
	}
	
	public double getNSTD() {
		return N_STD;
	}
	
	public void setFilterTable(MARSResultsTable filterTable) {
		this.filterTable = filterTable;
	}
}
