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
package de.mpg.biochem.sdmm.molecule;

import java.util.HashMap;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import de.mpg.biochem.sdmm.table.*;
import de.mpg.biochem.sdmm.util.LogBuilder;

@Plugin(type = Command.class, label = "Drift Corrector", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Drift Corrector", weight = 60, mnemonic = 'd')})
public class DriftCorrectorCommand extends DynamicCommand implements Command {
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
    
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String header =
		"Region for background alignment:";
    
    @Parameter(label="from slice")
    private int from = 1;
    
    @Parameter(label="to slice")
	private int to = 100;
			
    @Parameter(label="MetaData Background X (x_drift)")
    private String meta_x = "x_drift";
    
    @Parameter(label="MetaData Background Y (y_drift)")
    private String meta_y = "y_drift";
    
    @Parameter(label="Input X (x)")
    private String input_x = "x";
    
    @Parameter(label="Input Y (y)")
    private String input_y = "y";
    
    @Parameter(label="Output X (x_drift_corr)")
    private String output_x = "x_drift_corr";
    
    @Parameter(label="Output Y (y_drift_corr)")
    private String output_y = "y_drift_corr";
    
	@Override
	public void run() {		
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Drift Corrector");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		//Build maps from slice to x and slice to y for each metadataset
		HashMap<String, HashMap<Double, Double>> metaToMapX = new HashMap<String, HashMap<Double, Double>>();
		HashMap<String, HashMap<Double, Double>> metaToMapY = new HashMap<String, HashMap<Double, Double>>();
		
		for (String metaUID : archive.getImageMetaDataUIDs()) {
			ImageMetaData meta = archive.getImageMetaData(metaUID);
			if (meta.getDataTable().get(meta_x) != null && meta.getDataTable().get(meta_y) != null) {
				metaToMapX.put(meta.getUID(), getSliceToColumnMap(meta, meta_x));
				metaToMapY.put(meta.getUID(), getSliceToColumnMap(meta, meta_y));
			} else {
				logService.error("ImageMetaData " + meta.getUID() + " is missing " +  meta_x + " or " + meta_y + " column. Aborting");
				logService.error(builder.endBlock(false));
				archive.addLogMessage("ImageMetaData " + meta.getUID() + " is missing " +  meta_x + " or " + meta_y + " column. Aborting");
				archive.addLogMessage(builder.endBlock(false));
				
				//Unlock the window so it can be changed
			    if (!uiService.isHeadless())
			    	archive.unlock();
				return;
			}
		}

		//Loop through each molecule and calculate drift corrected traces...
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			
			if (molecule == null) {
				logService.error("No record found for molecule with UID " + UID + ". Could be due to data corruption. Continuing with the rest.");
				archive.addLogMessage("No record found for molecule with UID " + UID + ". Could be due to data corruption. Continuing with the rest.");
				return;
			}
			
			HashMap<Double, Double> sliceToXMap = metaToMapX.get(molecule.getImageMetaDataUID());
			HashMap<Double, Double> sliceToYMap = metaToMapY.get(molecule.getImageMetaDataUID());
			
			SDMMResultsTable datatable = molecule.getDataTable();
			
			//If the column already exists we don't need to add it
			//instead we will just be overwriting the values below..
			if (!datatable.hasColumn(output_x))
				molecule.getDataTable().appendColumn(output_x);
			
			if (!datatable.hasColumn(output_y))
				molecule.getDataTable().appendColumn(output_y);
			
			double meanX = datatable.mean(input_x,"slice",from, to);
			double meanY = datatable.mean(input_y,"slice",from, to);
			
			//We use the mappings because many molecules are missing slices.
			//by always using the maps we ensure the correct background slice is 
			//taken that matches the molecules slice at the given index.
			for (int i=0;i<datatable.getRowCount();i++) {
				double slice = datatable.getValue("slice", i);
				
				//First calculate corrected value for current slice x
				double molX = datatable.getValue(input_x, i) - meanX;
				double backgroundX = sliceToXMap.get(slice);
				
				double x_drift_corr_value = molX - backgroundX;
				datatable.set(output_x, i, x_drift_corr_value);
		
				//Then calculate corrected value for current slice y
				double molY = datatable.getValue(input_y, i) - meanY;
				double backgroundY = sliceToYMap.get(slice);
				
				double y_drift_corr_value = molY - backgroundY;
				datatable.set(output_y, i, y_drift_corr_value);
			}
			
			archive.set(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("  ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();	
	}
	
	private HashMap<Double, Double> getSliceToColumnMap(ImageMetaData meta, String columnName) {
		HashMap<Double, Double> sliceToColumn = new HashMap<Double, Double>();
		
		SDMMResultsTable metaTable = meta.getDataTable();
		
		double meanBG = metaTable.mean(columnName, "slice", from, to);
		
		for (int i=0;i<metaTable.getRowCount();i++) {
			sliceToColumn.put(metaTable.getValue("slice", i), metaTable.getValue(columnName, i) - meanBG);
		}
		return sliceToColumn;
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("from slice", String.valueOf(from));
		builder.addParameter("to slice", String.valueOf(to));
		builder.addParameter("MetaData Background X", meta_x);
		builder.addParameter("MetaData Background Y", meta_y);
		builder.addParameter("Input X", input_x);
		builder.addParameter("Input Y", input_y);
		builder.addParameter("Output X", output_x);
		builder.addParameter("Output Y", output_y);
	}
	
	public void setArchive(MoleculeArchive archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive getArchive() {
		return archive;
	}
	
	public void setFromSlice(int from) {
		this.from = from;
	}
	
	public int getFromSlice() {
		return from;
	}
	
	public void setToSlice(int to) {
		this.to = to;
	}
	
	public int getToSlice() {
		return to;
	}
	
	public void setMetaX(String meta_x) {
		this.meta_x = meta_x;
	}
    
	public String getMetaX() {
		return meta_x;
	}
	
	public void setMetaY(String meta_y) {
		this.meta_y = meta_y;
	}
    
	public String getMetaY() {
		return meta_y;
	}
    
	public void setInputX(String input_x) {
		this.input_x = input_x;
	}
    
	public String getInputX() {
		return input_x;
	}
	
	public void setInputY(String input_y) {
		this.input_y = input_y;
	}
    
	public String getInputY() {
		return input_y;
	}
	
	public void setOutputX(String output_x) {
		this.output_x = output_x;
	}
    
	public String getOutputX() {
		return output_x;
	}
	
	public void setOutputY(String output_y) {
		this.output_y = output_y;
	}
    
	public String getOutputY() {
		return output_y;
	}
}

