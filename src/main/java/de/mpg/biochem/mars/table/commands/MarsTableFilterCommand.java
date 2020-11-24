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

package de.mpg.biochem.mars.table.commands;

import java.util.ArrayList;

import net.imagej.ops.Initializable;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;

@Plugin(type = Command.class, label = "Filter", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Table", weight = 10, mnemonic = 't'), @Menu(label = "Filter",
				weight = 30, mnemonic = 'f') })
public class MarsTableFilterCommand extends DynamicCommand implements
	Initializable
{

	@Parameter
	private MarsTableService marsTableService;

	@Parameter
	private UIService uiService;

	@Parameter
	private LogService logService;

	@Parameter(label = "Table", callback = "tableSelectionChanged", choices = { "a", "b",
		"c" })
	private MarsTable table;

	@Parameter(label = "Column", callback = "updateMinMax", choices = { "a", "b",
		"c" })
	private String columnName;

	@Parameter(label = "Type", choices = { "Min Max", "Standard Deviation",
		"Filter Table" }, style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	private String FilterType = "Min Max";

	@Parameter(label = "Selection", choices = { "inside", "outside" },
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	private String selectionType = "include";

	@Parameter(label = "Min")
	private double min = 0;

	@Parameter(label = "Max")
	private double max = 1;

	@Parameter(label = "Mean +/- (N * STD)")
	private double N_STD = 2;

	@Parameter(label = "Filter Table", choices = { "a", "b", "c" })
	private MarsTable filterTable;

	private boolean TableFilter = false;
	private boolean STDFilter = false;
	private boolean includeSelection = true;

	// -- Callback methods --
	
	private void tableSelectionChanged() {
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(table.getColumnHeadingList());
		columns.sort(String::compareToIgnoreCase);

		final MutableModuleItem<String> columnItems = getInfo().getMutableInput(
			"columnName", String.class);
		columnItems.setChoices(columns);
		
		updateMinMax();
	}

	private void updateMinMax() {
		if (table != null && table.hasColumn(columnName)) {
			min = max = table.getValue(columnName, 0);

			for (double value : (DoubleColumn) table.get(columnName)) {

				if (min > value) min = value;

				if (max < value) max = value;
			}
		}
	}

	// -- Initializable methods --

	@Override
	public void initialize() {
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(marsTableService.getTables().get(0).getColumnHeadingList());
		columns.sort(String::compareToIgnoreCase);
		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput(
			"columnName", String.class);
		columnItems.setChoices(columns);
		
		updateMinMax();
	}

	// -- Runnable methods --

	@Override
	public void run() {
		if (FilterType.equals("Filter Table")) {
			TableFilter = true;
			STDFilter = false;
		}
		else if (FilterType.equals("Standard Deviation")) {
			STDFilter = true;
			TableFilter = false;
		}
		else {
			STDFilter = false;
			TableFilter = false;
		}

		if (selectionType.equals("inside")) {
			includeSelection = true;
		}
		else {
			includeSelection = false;
		}

		double[] filterList = new double[0];
		if (TableFilter) {
			filterList = filterTable.getColumnAsDoubles(columnName);
		}

		// If STDFilter was selected we have to calculate mean and STD before
		// filtering
		double STD = table.std(columnName);
		double mean = table.mean(columnName);

		// There is many better ways to do this....
		// Another option is to take care of includeSelection only at the end.
		ArrayList<Integer> deleteList = new ArrayList<Integer>();
		for (int row = 0; row < table.getRowCount(); row++) {
			double value = table.getValue(columnName, row);

			if (Double.isNaN(value)) {
				// Lets just remove all of the null values... They can't be filtered
				// correctly
				deleteList.add(row);
			}
			else if (TableFilter) {
				if (includeSelection) {
					deleteList.add(row);
					for (int q = 0; q < filterList.length; q++) {
						if (value == filterList[q]) {
							deleteList.remove(deleteList.size() - 1);
							break;
						}
					}
				}
				else {
					for (int q = 0; q < filterList.length; q++) {
						if (value == filterList[q]) {
							deleteList.add(row);
							break;
						}
					}
				}
			}
			else if (STDFilter) {
				if (value < (mean - N_STD * STD) || value > (mean + N_STD * STD)) {
					if (includeSelection) deleteList.add(row);
				}
				else {
					if (!includeSelection) deleteList.add(row);
				}
			}
			else if (value < min || value > max) {
				if (includeSelection) deleteList.add(row);
			}
			else if (value >= min && value <= max) {
				if (!includeSelection) deleteList.add(row);
			}

		}

		int[] delList = new int[deleteList.size()];
		for (int i = 0; i < deleteList.size(); i++) {
			delList[i] = deleteList.get(i);
		}

		table.deleteRows(delList);

		if (table.getWindow() != null) table.getWindow().update();
	}

	public void setTable(MarsTable table) {
		this.table = table;
	}

	public MarsTable getTable() {
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

	public void setFilterTable(MarsTable filterTable) {
		this.filterTable = filterTable;
	}
}
