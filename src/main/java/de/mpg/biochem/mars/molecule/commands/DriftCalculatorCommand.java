/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.molecule.commands;

import java.util.ArrayList;
import java.util.HashMap;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.MarsMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.SdmmImageMetadata;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;

import org.scijava.table.*;

@Plugin(type = Command.class, label = "Drift Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule", weight = 1,
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
    private MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive;
    
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

    @Parameter(label="mode", choices = {"mean", "median"})
	private String mode = "mean";
    
    @Parameter(label = "Zero:",
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "beginning", "end" })
	private String zeroPoint = "end";
    
	@Override
	public void run() {	
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("Drift Calculator");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.logln(log);
		
		//We will want to calculate the background for each dataset 
		//in the archive separately
		for (String metaUID : archive.getMetadataUIDs()) {
			MarsMetadata meta = archive.getMetadata(metaUID);
			//Let's find the last slice
			MarsTable metaDataTable = meta.getDataTable();
			
			int slices = (int)metaDataTable.getValue("slice", metaDataTable.getRowCount()-1);
			
			HashMap<Integer, DoubleColumn> xValuesColumns = new HashMap<Integer, DoubleColumn>();
			HashMap<Integer, DoubleColumn> yValuesColumns = new HashMap<Integer, DoubleColumn>();
			
			for (int slice=1;slice<=slices;slice++) {
				xValuesColumns.put(slice, new DoubleColumn("X " + slice));
				yValuesColumns.put(slice, new DoubleColumn("Y " + slice));
			}
			
			if (use_incomplete_traces) {
				//For all molecules in this dataset that are marked with the background tag and have all slices
				archive.getMoleculeUIDs().stream()
					.filter(UID -> archive.getMetadataUIDforMolecule(UID).equals(meta.getUID()))
					.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
					.forEach(UID -> {
						MarsTable datatable = archive.get(UID).getDataTable();
						double x_mean = datatable.mean(input_x);
						double y_mean = datatable.mean(input_y);
						
						for (int row = 0; row < datatable.getRowCount(); row++) {
							int slice = (int)datatable.getValue("slice", row);
							xValuesColumns.get(slice).add(datatable.getValue(input_x, row) - x_mean);
							yValuesColumns.get(slice).add(datatable.getValue(input_y, row) - y_mean);
						}
				});
			} else {
				//For all molecules in this dataset that are marked with the background tag and have all slices
				long[] num_full_traj = new long[1];
				num_full_traj[0] = 0;
				archive.getMoleculeUIDs().stream()
					.filter(UID -> archive.getMetadataUIDforMolecule(UID).equals(meta.getUID()))
					.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
					.forEach(UID -> {
						MarsTable datatable = archive.get(UID).getDataTable();
						if (archive.get(UID).getDataTable().getRowCount() == slices) {
							double x_mean = datatable.mean(input_x);
							double y_mean = datatable.mean(input_y);
							
							for (int row = 0; row < datatable.getRowCount(); row++) {
								int slice = (int)datatable.getValue("slice", row);
								xValuesColumns.get(slice).add(datatable.getValue(input_x, row) - x_mean);
								yValuesColumns.get(slice).add(datatable.getValue(input_y, row) - y_mean);
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
			
			MarsTable driftTable = new MarsTable();
			driftTable.appendColumn("slice");
			driftTable.appendColumn("x");
			driftTable.appendColumn("y");
			
			int gRow = 0;
			for (int slice = 1; slice <= slices ; slice++) {
				if (xValuesColumns.get(slice).size() == 0 || yValuesColumns.get(slice).size() == 0)
					continue;
				
				double xSliceFinalValue = Double.NaN;
				double ySliceFinalValue = Double.NaN;
					
				MarsTable xTempTable = new MarsTable();
				xTempTable.add(xValuesColumns.get(slice));
				
				MarsTable yTempTable = new MarsTable();
				yTempTable.add(yValuesColumns.get(slice));
				
				if (mode.equals("mean")) {
					xSliceFinalValue = xTempTable.mean("X " + slice);
					ySliceFinalValue = yTempTable.mean("Y " + slice);
				} else {
					xSliceFinalValue = xTempTable.median("X " + slice);
					ySliceFinalValue = yTempTable.median("Y " + slice);
				}
				
				driftTable.appendRow();
				driftTable.setValue("slice", gRow, slice);
				driftTable.setValue("x", gRow, xSliceFinalValue);
				driftTable.setValue("y", gRow, ySliceFinalValue);
				gRow++;
			}
			
			if (driftTable.getRowCount() != slices)	
				linearInterpolateGaps(driftTable, slices);
			
			if (!metaDataTable.hasColumn(output_x))
				metaDataTable.appendColumn(output_x);
			
			if (!metaDataTable.hasColumn(output_y))
				metaDataTable.appendColumn(output_y);
			
			for (int row = 0; row < metaDataTable.getRowCount() ; row++) {
				metaDataTable.setValue(output_x, row, driftTable.getValue("x", row));
				metaDataTable.setValue(output_y, row, driftTable.getValue("y", row));
			}
			
			double xZeroPoint = 0;
			double yZeroPoint = 0;
			
			if (zeroPoint.equals("beginning")) {
				xZeroPoint = metaDataTable.getValue(output_x, 0);
				yZeroPoint = metaDataTable.getValue(output_y, 0);
			} else if (zeroPoint.equals("end")) {
				xZeroPoint = metaDataTable.getValue(output_x, metaDataTable.getRowCount() - 1);
				yZeroPoint = metaDataTable.getValue(output_y, metaDataTable.getRowCount() - 1);
			}
			
			for (int row=0; row < metaDataTable.getRowCount(); row++) {
				metaDataTable.setValue(output_x, row, metaDataTable.getValue(output_x, row) - xZeroPoint);
				metaDataTable.setValue(output_y, row, metaDataTable.getValue(output_y, row) - yZeroPoint);
			}
			
			archive.putMetadata(meta);
		}
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.logln("\n" + LogBuilder.endBlock(true));
	    archive.logln("  ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();
	}
	
	private static void linearInterpolateGaps(MarsTable table, int slices) {
		int rows = table.getRowCount();

		for (int i=1;i<rows;i++) {
			//Check whether there is a gap in the slice number...
			int previous_slice = (int)table.getValue("slice", i-1);
			int current_slice = (int)table.getValue("slice", i);
			if (previous_slice != current_slice - 1) {
				for (int w=1; w < current_slice - previous_slice ; w++) {
					table.appendRow();
					table.setValue("slice", table.getRowCount() - 1, previous_slice + w);
					table.setValue("x", table.getRowCount() - 1, table.getValue("x", i-1) + w*(table.getValue("x", i) - table.getValue("x", i-1))/(current_slice - previous_slice));
					table.setValue("y", table.getRowCount() - 1, table.getValue("y", i-1) + w*(table.getValue("y", i) - table.getValue("y", i-1))/(current_slice - previous_slice));
				}
			}
		}
		
		//fill ends if points are missing there...
		if (table.getValue("slice", 0) > 1) {
			for (int slice=1;slice < table.getValue("slice", 0); slice++) {
				table.appendRow();
				table.setValue("slice", table.getRowCount() - 1, slice);
				table.setValue("x", table.getRowCount() - 1, table.getValue("x", 0));
				table.setValue("y", table.getRowCount() - 1, table.getValue("y", 0));
			}
		}
		
		if (table.getValue("slice", rows - 1) != slices) {
			for (int slice = (int)table.getValue("slice", rows - 1) + 1;slice <= slices; slice++) {
				table.appendRow();
				table.setValue("slice", table.getRowCount() - 1, slice);
				table.setValue("x", table.getRowCount() - 1, table.getValue("x", rows - 1));
				table.setValue("y", table.getRowCount() - 1, table.getValue("y", rows - 1));
			}
		}
		
		//now that we have added all the new rows we need to resort the table by slice.
		table.sort(true, "slice");
	}
	
	public static void calcDrift(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive, String backgroundTag, String input_x, String input_y, 
			String output_x, String output_y, boolean use_incomplete_traces, String mode, String zeroPoint) {
			//Build log message
			LogBuilder builder = new LogBuilder();
			
			String log = LogBuilder.buildTitleBlock("Drift Calculator");
			
			builder.addParameter("MoleculeArchive", archive.getName());
			builder.addParameter("Input X", input_x);
			builder.addParameter("Input Y", input_y);
			builder.addParameter("Output X", output_x);
			builder.addParameter("Output Y", output_y);
			builder.addParameter("Use incomplete traces", String.valueOf(use_incomplete_traces));
			builder.addParameter("Zero Point", zeroPoint);
			builder.addParameter("Background Tag", backgroundTag);
			builder.addParameter("mode", mode);
			log += builder.buildParameterList();
			
			archive.addLogMessage(log);
			
			//We will want to calculate the background for each dataset 
			//in the archive separately
			for (String metaUID : archive.getMetadataUIDs()) {
				MarsMetadata meta = archive.getMetadata(metaUID);
				//Let's find the last slice
				MarsTable metaDataTable = meta.getDataTable();
				
				int slices = (int)metaDataTable.getValue("slice", metaDataTable.getRowCount()-1);
				
				HashMap<Integer, DoubleColumn> xValuesColumns = new HashMap<Integer, DoubleColumn>();
				HashMap<Integer, DoubleColumn> yValuesColumns = new HashMap<Integer, DoubleColumn>();
				
				for (int slice=1;slice<=slices;slice++) {
					xValuesColumns.put(slice, new DoubleColumn("X " + slice));
					yValuesColumns.put(slice, new DoubleColumn("Y " + slice));
				}
				
				if (use_incomplete_traces) {
					//For all molecules in this dataset that are marked with the background tag and have all slices
					archive.getMoleculeUIDs().stream()
						.filter(UID -> archive.getMetadataUIDforMolecule(UID).equals(meta.getUID()))
						.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
						.forEach(UID -> {
							MarsTable datatable = archive.get(UID).getDataTable();
							double x_mean = datatable.mean(input_x);
							double y_mean = datatable.mean(input_y);
							
							for (int row = 0; row < datatable.getRowCount(); row++) {
								int slice = (int)datatable.getValue("slice", row);
								xValuesColumns.get(slice).add(datatable.getValue(input_x, row) - x_mean);
								yValuesColumns.get(slice).add(datatable.getValue(input_y, row) - y_mean);
							}
					});
				} else {
					//For all molecules in this dataset that are marked with the background tag and have all slices
					//Throws and error for a non array... So strange...
					long[] num_full_traj = new long[1];
					num_full_traj[0] = 0;
					archive.getMoleculeUIDs().stream()
						.filter(UID -> archive.getMetadataUIDforMolecule(UID).equals(meta.getUID()))
						.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
						.forEach(UID -> {
							MarsTable datatable = archive.get(UID).getDataTable();
							if (archive.get(UID).getDataTable().getRowCount() == slices) {
								double x_mean = datatable.mean(input_x);
								double y_mean = datatable.mean(input_y);
								
								for (int row = 0; row < datatable.getRowCount(); row++) {
									int slice = (int)datatable.getValue("slice", row);
									xValuesColumns.get(slice).add(datatable.getValue(input_x, row) - x_mean);
									yValuesColumns.get(slice).add(datatable.getValue(input_y, row) - y_mean);
								}
								num_full_traj[0]++;
							}
					});
					
					if (num_full_traj[0] == 0) {
					    archive.logln("Aborting. No complete molecules with all slices found for dataset " + meta.getUID() + "!");
					    continue;
					}
				}
				
				MarsTable driftTable = new MarsTable();
				driftTable.appendColumn("slice");
				driftTable.appendColumn("x");
				driftTable.appendColumn("y");
				
				int gRow = 0;
				for (int slice = 1; slice <= slices ; slice++) {
					if (xValuesColumns.get(slice).size() == 0 || yValuesColumns.get(slice).size() == 0)
						continue;
					
					double xSliceFinalValue = Double.NaN;
					double ySliceFinalValue = Double.NaN;
						
					MarsTable xTempTable = new MarsTable();
					xTempTable.add(xValuesColumns.get(slice));
					
					MarsTable yTempTable = new MarsTable();
					yTempTable.add(yValuesColumns.get(slice));
					
					if (mode.equals("mean")) {
						xSliceFinalValue = xTempTable.mean("X " + slice);
						ySliceFinalValue = yTempTable.mean("Y " + slice);
					} else {
						xSliceFinalValue = xTempTable.median("X " + slice);
						ySliceFinalValue = yTempTable.median("Y " + slice);
					}
					
					driftTable.appendRow();
					driftTable.setValue("slice", gRow, slice);
					driftTable.setValue("x", gRow, xSliceFinalValue);
					driftTable.setValue("y", gRow, ySliceFinalValue);
					gRow++;
				}
				
				if (driftTable.getRowCount() != slices)	
					linearInterpolateGaps(driftTable, slices);
				
				if (!metaDataTable.hasColumn(output_x))
					metaDataTable.appendColumn(output_x);
				
				if (!metaDataTable.hasColumn(output_y))
					metaDataTable.appendColumn(output_y);
				
				for (int row = 0; row < metaDataTable.getRowCount() ; row++) {
					metaDataTable.setValue(output_x, row, driftTable.getValue("x", row));
					metaDataTable.setValue(output_y, row, driftTable.getValue("y", row));
				}
				
				double xZeroPoint = 0;
				double yZeroPoint = 0;
				
				if (zeroPoint.equals("beginning")) {
					xZeroPoint = metaDataTable.getValue(output_x, 0);
					yZeroPoint = metaDataTable.getValue(output_y, 0);
				} else if (zeroPoint.equals("end")) {
					xZeroPoint = metaDataTable.getValue(output_x, metaDataTable.getRowCount() - 1);
					yZeroPoint = metaDataTable.getValue(output_y, metaDataTable.getRowCount() - 1);
				}
				
				for (int row=0; row < metaDataTable.getRowCount(); row++) {
					metaDataTable.setValue(output_x, row, metaDataTable.getValue(output_x, row) - xZeroPoint);
					metaDataTable.setValue(output_y, row, metaDataTable.getValue(output_y, row) - yZeroPoint);
				}
				
				archive.putMetadata(meta);
			}
			
		    archive.logln(LogBuilder.endBlock(true));
		    archive.logln("  ");
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Input X", input_x);
		builder.addParameter("Input Y", input_y);
		builder.addParameter("Output X", output_x);
		builder.addParameter("Output Y", output_y);
		builder.addParameter("Use incomplete traces", String.valueOf(use_incomplete_traces));
		builder.addParameter("Zero Point", zeroPoint);
		builder.addParameter("Background Tag", backgroundTag);
		builder.addParameter("mode", mode);
	}
	
	public void setArchive(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive) {
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
	
	public void setMode(String mode) {
		this.mode = mode;
	}
	
	public void setBackgroundTag(String backgroundTag) {
		this.backgroundTag = backgroundTag;
	}
	
	public void setZeroPoint(String zeroPoint) {
		this.zeroPoint = zeroPoint;
	}
	
	public MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> getArchive() {
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
	
	public String getMode() {
		return mode;
	}
    
	public String getBackgroundTag() {
		return backgroundTag;
	}
	
	public String getZeroPoint() {
		return zeroPoint;
	}
}
