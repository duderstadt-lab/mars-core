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

import de.mpg.biochem.mars.table.ResultsTableSorterCommand;
import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Interpolate Missing Points (x, y)", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
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
			MARSResultsTable datatable = molecule.getDataTable();
			
			int rows = datatable.getRowCount();
			
			for (int i=1;i<rows;i++) {
				//Check whether there is a gap in the slice number...
				int previous_slice = (int)datatable.getValue("slice", i-1);
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
			datatable.sort(true, "slice");
			
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
	
	public void setArchive(MoleculeArchive archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive getArchive() {
		return archive;
	}
	
	public void setMaxGap(int Maxgap) {
		this.Maxgap = Maxgap;
	}
	
	public int getMaxGap() {
		return Maxgap;
	}
}


