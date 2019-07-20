/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule.commands;

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

import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.MarsImageMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.SDMMImageMetadata;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Drift Corrector", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
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
    private MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> archive;
    
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
			MarsImageMetadata meta = archive.getImageMetaData(metaUID);
			if (meta.getDataTable().get(meta_x) != null && meta.getDataTable().get(meta_y) != null) {
				metaToMapX.put(meta.getUID(), getSliceToColumnMap(meta, meta_x));
				metaToMapY.put(meta.getUID(), getSliceToColumnMap(meta, meta_y));
			} else {
				logService.error("ImageMetaData " + meta.getUID() + " is missing " +  meta_x + " or " + meta_y + " column. Aborting");
				logService.error(LogBuilder.endBlock(false));
				archive.addLogMessage("ImageMetaData " + meta.getUID() + " is missing " +  meta_x + " or " + meta_y + " column. Aborting");
				archive.addLogMessage(LogBuilder.endBlock(false));
				
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
			
			MarsResultsTable datatable = molecule.getDataTable();
			
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
				double backgroundX = Double.NaN;
				
				//If using incomplete traces for building the background
				//sometimes there are missing slices..
				if (sliceToXMap.containsKey(slice))
					backgroundX = sliceToXMap.get(slice);
				
				double x_drift_corr_value = molX - backgroundX;
				datatable.set(output_x, i, x_drift_corr_value);
		
				//Then calculate corrected value for current slice y
				double molY = datatable.getValue(input_y, i) - meanY;
				double backgroundY = Double.NaN;
				
				//If using incomplete traces for building the background
				//sometimes there are missing slices..
				if (sliceToYMap.containsKey(slice))
					backgroundY = sliceToYMap.get(slice);
				
				double y_drift_corr_value = molY - backgroundY;
				datatable.set(output_y, i, y_drift_corr_value);
			}
			
			archive.put(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.addLogMessage(LogBuilder.endBlock(true));
	    archive.addLogMessage("  ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();	
	}
	
	private HashMap<Double, Double> getSliceToColumnMap(MarsImageMetadata meta, String columnName) {
		HashMap<Double, Double> sliceToColumn = new HashMap<Double, Double>();
		
		MarsResultsTable metaTable = meta.getDataTable();
		
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
	
	public void setArchive(MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> getArchive() {
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

