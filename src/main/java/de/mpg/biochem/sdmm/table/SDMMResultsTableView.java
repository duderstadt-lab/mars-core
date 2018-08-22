package de.mpg.biochem.sdmm.table;

import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import org.scijava.display.Display;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.ui.UserInterface;
import org.scijava.ui.viewer.AbstractDisplayViewer;
import org.scijava.ui.viewer.DisplayViewer;

@Plugin(type = DisplayViewer.class)
public class SDMMResultsTableView extends AbstractDisplayViewer<SDMMResultsTable> implements DisplayViewer<SDMMResultsTable> {
	
	@Parameter
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
	
	//This method is called to create and display a window
	//here we override it to make sure that calls like uiService.show( .. for SDMMResultsTable 
	//will use this method automatically..
	@Override
	public void view(final UserInterface ui, final Display<?> d) {
		SDMMResultsTable results = (SDMMResultsTable)d.get(0);
		results.setName(d.getName());

		//We add it to the ResultsTableService if it hasn't been added already
		if (resultsTableService.getResultsTable(results.getName()) == null) {
			resultsTableService.addTable(results);
			
			//We also create a new window since we assume it is a new table...
			new SDMMResultsTableWindow(results.getName(), results, resultsTableService);
		} else {
			//We update the table if it is already open
			//This is really ugly at the moment and in the future should be implemented through display
			resultsTableService.getResultsTable(results.getName()).getWindow().update();
		}	
	}

	@Override
	public boolean canView(final Display<?> d) {
		if (d instanceof SDMMResultsTableDisplay) {
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public SDMMResultsTableDisplay getDisplay() {
		return (SDMMResultsTableDisplay) super.getDisplay();
	}

	@Override
	public boolean isCompatible(UserInterface arg0) {
		//Needs to be updated if all contexts are to be enabled beyond ImageJ
		return true;
	}
}
