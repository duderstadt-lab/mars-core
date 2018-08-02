package de.mpg.biochem.sdmm.molecule;

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
