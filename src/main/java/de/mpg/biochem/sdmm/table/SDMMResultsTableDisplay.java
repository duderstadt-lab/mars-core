package de.mpg.biochem.sdmm.table;

import org.scijava.display.AbstractDisplay;
import org.scijava.display.Display;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.text.TextWindow;

/**
 * Display for {@link SDMMResultsTable}. This ensures that uiService.show() for a SDMMResultsTable will automatically be detected and 
 * call the view method in SDMMResultsTableView to make our custom window with custom menus.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = Display.class)
public class SDMMResultsTableDisplay extends AbstractDisplay<SDMMResultsTable> implements Display<SDMMResultsTable> {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public SDMMResultsTableDisplay() {
		super((Class) SDMMResultsTable.class);
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
