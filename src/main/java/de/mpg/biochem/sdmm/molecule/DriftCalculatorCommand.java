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

import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.LogBuilder;
import org.scijava.table.DoubleColumn;

@Plugin(type = Command.class, label = "Drift Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
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
			ImageMetaData meta = archive.getImageMetaData(metaUID);
			//Let's find the last slice
			SDMMResultsTable metaDataTable = meta.getDataTable();
			
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
					.filter(UID -> archive.get(UID).getImageMetaDataUID().equals(meta.getUID()))
					.filter(UID -> archive.get(UID).hasTag(backgroundTag))
					.forEach(UID -> {
						SDMMResultsTable datatable = archive.get(UID).getDataTable();
						double x_mean = datatable.mean(input_x);
						double y_mean = datatable.mean(input_y);
						
						for (int row = 0; row < datatable.getRowCount(); row++) {
							x_avg_background[(int)datatable.getValue("slice", row) - 1] += datatable.getValue(input_x, row) - x_mean;
							y_avg_background[(int)datatable.getValue("slice", row) - 1] += datatable.getValue(input_y, row) - y_mean;
							observations[(int)datatable.getValue("slice", row) - 1]++;
						}
				});
			} else {
				long num_full_traj = archive.getMoleculeUIDs().stream()
						.filter(UID -> archive.get(UID).getImageMetaDataUID().equals(meta.getUID()))
						.filter(UID -> archive.get(UID).hasTag(backgroundTag))
						.filter(UID -> archive.get(UID).getDataTable().getRowCount() == slices).count();
				
				if (num_full_traj == 0) {
				    archive.addLogMessage("Aborting. No complete molecules with all slices found for dataset " + meta.getUID() + "!");
				    logService.info("Aborting. No complete molecules with all slices found for dataset " + meta.getUID() + "!");
				    //archive.addLogMessage(builder.endBlock(false));
				    //archive.addLogMessage("  ");
					//uiService.showDialog("Aborting. No complete molecules with all slices found for dataset " + meta.getUID() + "!");
					continue;
				}
				
				//For all molecules in this dataset that are marked with the background tag and have all slices
				archive.getMoleculeUIDs().stream()
					.filter(UID -> archive.get(UID).getImageMetaDataUID().equals(meta.getUID()))
					.filter(UID -> archive.get(UID).hasTag(backgroundTag))
					.filter(UID -> archive.get(UID).getDataTable().getRowCount() == slices)
					.forEach(UID -> {
						SDMMResultsTable datatable = archive.get(UID).getDataTable();
						double x_mean = datatable.mean(input_x);
						double y_mean = datatable.mean(input_y);
						
						for (int row = 0; row < datatable.getRowCount(); row++) {
							x_avg_background[(int)datatable.getValue("slice", row) - 1] += datatable.getValue(input_x, row) - x_mean;
							y_avg_background[(int)datatable.getValue("slice", row) - 1] += datatable.getValue(input_y, row) - y_mean;
							observations[(int)datatable.getValue("slice", row) - 1]++;
						}
				});
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
			
			archive.setImageMetaData(meta);
		}
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
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
