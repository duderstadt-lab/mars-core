package de.mpg.biochem.sdmm.molecule;

import org.decimal4j.util.DoubleRounder;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import de.mpg.biochem.sdmm.table.ResultsTableSorterCommand;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.LogBuilder;

@Plugin(type = Command.class, label = "Interpolate Missing Points (x, y)", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Interpolate Missing Points (x, y)", weight = 80, mnemonic = 'm')})
public class InterpolateMissingPointsCommand extends DynamicCommand implements Command {
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
    private UIService uiService;
	
    @Parameter(label="MoleculeArchive")
    private MoleculeArchive archive;
    
    @Parameter(label="Max gap size (in slices)")
	private int Maxgap = 30;
    
	@Override
	public void run() {		
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Interpolate Missing Points");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		//Loop through each molecule and add MSD parameter for each
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			SDMMResultsTable datatable = molecule.getDataTable();
			
			int previous_slice = (int)datatable.getValue("slice", 0);
			
			int rows = datatable.getRowCount();
			
			for (int i=1;i<rows;i++) {
				//Check whether there is a gap in the slice number...
				int current_slice = (int)datatable.getValue("slice", i);
				if (previous_slice != current_slice - 1) {
					if (current_slice - previous_slice < Maxgap) {
						for (int w=1; w < current_slice - previous_slice ; w++) {
							datatable.appendRow();
							
							datatable.setValue("slice", datatable.getRowCount() - 1, previous_slice + w);
							datatable.setValue("x", datatable.getRowCount() - 1, datatable.getValue("x", i-1) + w*(datatable.getValue("x", i) - datatable.getValue("x", i-1))/(current_slice - previous_slice));
							datatable.setValue("y", datatable.getRowCount() - 1, datatable.getValue("y", i-1) + w*(datatable.getValue("y", i) - datatable.getValue("y", i-1))/(current_slice - previous_slice));
						}
					}
				}
			}
			
			//now that we have added all the new rows we need to resort the table by slice.
			ResultsTableSorterCommand.sort(datatable, true, "slice");
			
			archive.set(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("   ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless()) 
			archive.unlock();
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Max gap size (in slices)", String.valueOf(Maxgap));
	}
}


