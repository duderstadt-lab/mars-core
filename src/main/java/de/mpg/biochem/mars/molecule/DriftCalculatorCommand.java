/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
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

import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.util.LogBuilder;

import org.scijava.table.DoubleColumn;

@Plugin(type = Command.class, label = "Drift Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Drift Calculator", weight = 50, mnemonic = 'd')})
public class DriftCalculatorCommand extends DynamicCommand implements Command {
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
    
    @Parameter(label="Background Tag")
    private String backgroundTag = "background";
    
    @Parameter(label="Input X (x)")
    private String input_x = "x";
    
    @Parameter(label="Input Y (y)")
    private String input_y = "y";
    
    @Parameter(label="Output X (x_drift)")
    private String output_x = "x_drift";
    
    @Parameter(label="Output Y (y_drift)")
    private String output_y = "y_drift";
    
    @Parameter(label="Use incomplete traces")
    private boolean use_incomplete_traces = false;
    
	@Override
	public void run() {	
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Drift Calculator");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		//We will want to calculate the background for each dataset 
		//in the archive separately
		for (String metaUID : archive.getImageMetaDataUIDs()) {
			MARSImageMetaData meta = archive.getImageMetaData(metaUID);
			//Let's find the last slice
			MARSResultsTable metaDataTable = meta.getDataTable();
			
			int slices = (int)metaDataTable.getValue("slice", metaDataTable.getRowCount()-1);
			
			//First calculator global background
			double[] x_avg_background = new double[slices];
			double[] y_avg_background = new double[slices];
			int[] observations = new int[slices];
			for (int i=0;i<slices;i++) {
				x_avg_background[i] = 0;
				y_avg_background[i] = 0;
				observations[i] = 0;
			}
			
			if (use_incomplete_traces) {
				//For all molecules in this dataset that are marked with the background tag and have all slices
				archive.getMoleculeUIDs().stream()
					.filter(UID -> archive.getImageMetaDataUIDforMolecule(UID).equals(meta.getUID()))
					.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
					.forEach(UID -> {
						MARSResultsTable datatable = archive.get(UID).getDataTable();
						double x_mean = datatable.mean(input_x);
						double y_mean = datatable.mean(input_y);
						
						for (int row = 0; row < datatable.getRowCount(); row++) {
							x_avg_background[(int)datatable.getValue("slice", row) - 1] += datatable.getValue(input_x, row) - x_mean;
							y_avg_background[(int)datatable.getValue("slice", row) - 1] += datatable.getValue(input_y, row) - y_mean;
							observations[(int)datatable.getValue("slice", row) - 1]++;
						}
				});
			} else {
				//For all molecules in this dataset that are marked with the background tag and have all slices
				//Throws and error for a non array... So strange...
				long[] num_full_traj = new long[1];
				num_full_traj[0] = 0;
				archive.getMoleculeUIDs().stream()
					.filter(UID -> archive.getImageMetaDataUIDforMolecule(UID).equals(meta.getUID()))
					.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
					.forEach(UID -> {
						MARSResultsTable datatable = archive.get(UID).getDataTable();
						if (archive.get(UID).getDataTable().getRowCount() == slices) {
							double x_mean = datatable.mean(input_x);
							double y_mean = datatable.mean(input_y);
							
							for (int row = 0; row < datatable.getRowCount(); row++) {
								x_avg_background[(int)datatable.getValue("slice", row) - 1] += datatable.getValue(input_x, row) - x_mean;
								y_avg_background[(int)datatable.getValue("slice", row) - 1] += datatable.getValue(input_y, row) - y_mean;
								observations[(int)datatable.getValue("slice", row) - 1]++;
							}
							num_full_traj[0]++;
						}
				});
				
				if (num_full_traj[0] == 0) {
				    archive.addLogMessage("Aborting. No complete molecules with all slices found for dataset " + meta.getUID() + "!");
				    logService.info("Aborting. No complete molecules with all slices found for dataset " + meta.getUID() + "!");
				    continue;
				}
			}
			
			if (!metaDataTable.hasColumn(output_x))
				metaDataTable.appendColumn(output_x);
			
			if (!metaDataTable.hasColumn(output_y))
				metaDataTable.appendColumn(output_y);
			
			for (int row = 0; row < slices ; row++) {
				if (observations[row] == 0) {
					//No traces had an observation at this position...
					metaDataTable.setValue(output_x, row, Double.NaN);
					metaDataTable.setValue(output_y, row, Double.NaN);
				} else {
					metaDataTable.setValue(output_x, row, x_avg_background[row]/observations[row]);
					metaDataTable.setValue(output_y, row, y_avg_background[row]/observations[row]);
				}
			}
			
			archive.putImageMetaData(meta);
		}
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.addLogMessage(LogBuilder.endBlock(true));
	    archive.addLogMessage("  ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Input X", input_x);
		builder.addParameter("Input Y", input_y);
		builder.addParameter("Output X", output_x);
		builder.addParameter("Output Y", output_y);
		builder.addParameter("Use incomplete traces", String.valueOf(use_incomplete_traces));
		builder.addParameter("Background Tag", backgroundTag);
	}
	
	public void setArchive(MoleculeArchive archive) {
		this.archive = archive;
	}
	
	public void setInputX(String input_x) {
		this.input_x = input_x;
	}
	
	public void setInputY(String input_y) {
		this.input_y = input_y;
	}
	
	public void setOutputX(String output_x) {
		this.output_x = output_x;
	}
	
	public void setOutputY(String output_y) {
		this.output_y = output_y;
	}
	
	public void setUseIncompleteTraces(boolean use_incomplete_traces) {
		this.use_incomplete_traces = use_incomplete_traces;
	}
	
	public void setBackgroundTag(String backgroundTag) {
		this.backgroundTag = backgroundTag;
	}
	
	public MoleculeArchive getArchive() {
		return archive;
	}
	
	public String getInputX() {
		return input_x;
	}
	
	public String getInputY() {
		return input_y;
	}
	
	public String getOutputX() {
		return output_x;
	}
	
	public String getOutputY() {
		return output_y;
	}
	
	public boolean getUseIncompleteTraces() {
		return use_incomplete_traces;
	}
    
	public String getBackgroundTag() {
		return backgroundTag;
	}
}
