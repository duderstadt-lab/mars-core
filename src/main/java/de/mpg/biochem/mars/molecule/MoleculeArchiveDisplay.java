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
package de.mpg.biochem.mars.molecule;

import org.scijava.display.AbstractDisplay;
import org.scijava.display.Display;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Display for {@link MoleculeArchive}. This ensures that uiService.show() for a MoleculeArchive will automatically be detected and 
 * call the view method in MoleculeArchiveView to make our custom window with custom menus.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = Display.class)
public class MoleculeArchiveDisplay extends AbstractDisplay<MoleculeArchive> implements Display<MoleculeArchive> {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public MoleculeArchiveDisplay() {
		super((Class) MoleculeArchive.class);
	}

	// -- Display methods --

	@Override
	public boolean canDisplay(final Class<?> c) {
		if (c.equals(MoleculeArchive.class)) {
			return true;
		} else { 
			return super.canDisplay(c);
		}
	}
}
