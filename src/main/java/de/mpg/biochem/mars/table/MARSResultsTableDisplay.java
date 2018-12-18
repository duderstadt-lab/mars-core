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

import org.scijava.display.AbstractDisplay;
import org.scijava.display.Display;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.text.TextWindow;

/**
 * Display for {@link MARSResultsTable}. This ensures that uiService.show() for a SDMMResultsTable will automatically be detected and 
 * call the view method in SDMMResultsTableView to make our custom window with custom menus.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = Display.class)
public class MARSResultsTableDisplay extends AbstractDisplay<MARSResultsTable> implements Display<MARSResultsTable> {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public MARSResultsTableDisplay() {
		super((Class) MARSResultsTable.class);
	}

	// -- Display methods --
/*
	@Override
	public boolean canDisplay(final Class<?> c) {
		if (c.equals(SDMMResultsTable.class)) {
			return true;
		} else { 
			return super.canDisplay(c);
		}
	}
	*/
}
