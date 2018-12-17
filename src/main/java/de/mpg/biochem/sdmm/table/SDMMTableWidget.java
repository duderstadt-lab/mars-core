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
package de.mpg.biochem.sdmm.table;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.*;
import org.scijava.widget.WidgetModel;

import org.scijava.ui.swing.widget.SwingInputWidget;

/**
 * Swing implementation of multiple choice selector widget for SDMMResutlsTable.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = InputWidget.class)
public class SDMMTableWidget extends SwingInputWidget<SDMMResultsTable> implements InputWidget<SDMMResultsTable, JPanel>, ActionListener {

	@Parameter
    private ResultsTableService resultsTableService;
		
	private JComboBox<Object> comboBox;

	@Override
	public void actionPerformed(final ActionEvent e) {
		updateModel();
	}

	// -- InputWidget methods --

	@Override
	public SDMMResultsTable getValue() {
		return resultsTableService.getResultsTable((String)comboBox.getSelectedItem());
	}

	// -- WrapperPlugin methods --

	@Override
	public void set(final WidgetModel model) {
		super.set(model);
		
		String[] items = new String[resultsTableService.getTableNames().size()];
		resultsTableService.getTableNames().toArray(items);

		comboBox = new JComboBox<>(items);
		setToolTip(comboBox);
		getComponent().add(comboBox);
		comboBox.addActionListener(this);
		
		updateModel();

		refreshWidget();
	}

	// -- Typed methods --

	@Override
	public boolean supports(final WidgetModel model) {
		return super.supports(model) && model.isType(SDMMResultsTable.class) && resultsTableService.getTableNames().size() > 0;
	}

	@Override
	public void doRefresh() {
		//final String value = get().getValue().toString();
		//if (value.equals(comboBox.getSelectedItem())) return; // no change
		//comboBox.setSelectedItem(value);
	}
}
