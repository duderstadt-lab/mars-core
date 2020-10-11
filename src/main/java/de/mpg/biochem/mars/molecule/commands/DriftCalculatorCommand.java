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

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEPlane;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;

import org.scijava.table.*;

@Plugin(type = Command.class, label = "Drift Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
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
    private MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive;
    
    @Parameter(label="Background Tag")
    private String backgroundTag = "background";
    
    @Parameter(label="Input X (x)")
    private String input_x = "x";
    
    @Parameter(label="Input Y (y)")
    private String input_y = "y";
    
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
		
		archive.logln(log);
		
		//We will want to calculate the background for each dataset 
		//in the archive separately
		for (String metaUID : archive.getMetadataUIDs()) {
			MarsMetadata meta = archive.getMetadata(metaUID);
			//Let's find the last T
			//MarsTable metaDataTable = meta.getDataTable();
			
			int sizeT = meta.getImage(0).getSizeT();
			
			HashMap<Integer, DoubleColumn> xValuesColumns = new HashMap<Integer, DoubleColumn>();
			HashMap<Integer, DoubleColumn> yValuesColumns = new HashMap<Integer, DoubleColumn>();
			
			for (int t=0;t<=sizeT;t++) {
				xValuesColumns.put(t, new DoubleColumn("X " + t));
				yValuesColumns.put(t, new DoubleColumn("Y " + t));
			}
			
			if (use_incomplete_traces) {
				//For all molecules in this dataset that are marked with the background tag and have all Ts
				archive.getMoleculeUIDs().stream()
					.filter(UID -> archive.getMetadataUIDforMolecule(UID).equals(meta.getUID()))
					.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
					.forEach(UID -> {
						MarsTable datatable = archive.get(UID).getTable();
						double x_mean = datatable.mean(input_x);
						double y_mean = datatable.mean(input_y);
						
						for (int row = 0; row < datatable.getRowCount(); row++) {
							int t = (int)datatable.getValue("T", row);
							xValuesColumns.get(t).add(datatable.getValue(input_x, row) - x_mean);
							yValuesColumns.get(t).add(datatable.getValue(input_y, row) - y_mean);
						}
				});
			} else {
				//For all molecules in this dataset that are marked with the background tag and have all Ts
				long[] num_full_traj = new long[1];
				num_full_traj[0] = 0;
				archive.getMoleculeUIDs().stream()
					.filter(UID -> archive.getMetadataUIDforMolecule(UID).equals(meta.getUID()))
					.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
					.forEach(UID -> {
						MarsTable datatable = archive.get(UID).getTable();
						if (archive.get(UID).getTable().getRowCount() == sizeT) {
							double x_mean = datatable.mean(input_x);
							double y_mean = datatable.mean(input_y);
							
							for (int row = 0; row < datatable.getRowCount(); row++) {
								int t = (int)datatable.getValue("T", row);
								xValuesColumns.get(t).add(datatable.getValue(input_x, row) - x_mean);
								yValuesColumns.get(t).add(datatable.getValue(input_y, row) - y_mean);
							}
							num_full_traj[0]++;
						}
				});
				
				if (num_full_traj[0] == 0) {
				    archive.logln("Aborting. No complete molecules with all Ts found for dataset " + meta.getUID() + "!");
				    logService.info("Aborting. No complete molecules with all Ts found for dataset " + meta.getUID() + "!");
				    continue;
				}
			}
			
			MarsTable driftTable = new MarsTable();
			driftTable.appendColumn("T");
			driftTable.appendColumn("x");
			driftTable.appendColumn("y");
			
			int gRow = 0;
			for (int t = 0; t <= sizeT ; t++) {
				if (xValuesColumns.get(t).size() == 0 || yValuesColumns.get(t).size() == 0)
					continue;
				
				double xTFinalValue = Double.NaN;
				double yTFinalValue = Double.NaN;
					
				MarsTable xTempTable = new MarsTable();
				xTempTable.add(xValuesColumns.get(t));
				
				MarsTable yTempTable = new MarsTable();
				yTempTable.add(yValuesColumns.get(t));
				
				if (mode.equals("mean")) {
					xTFinalValue = xTempTable.mean("X " + t);
					yTFinalValue = yTempTable.mean("Y " + t);
				} else {
					xTFinalValue = xTempTable.median("X " + t);
					yTFinalValue = yTempTable.median("Y " + t);
				}
				
				driftTable.appendRow();
				driftTable.setValue("T", gRow, t);
				driftTable.setValue("x", gRow, xTFinalValue);
				driftTable.setValue("y", gRow, yTFinalValue);
				gRow++;
			}
			
			if (driftTable.getRowCount() != sizeT)	
				linearInterpolateGaps(driftTable, sizeT);
			
			//Build Maps
			HashMap<Integer, Double> TtoXMap = new HashMap<Integer, Double>();
			HashMap<Integer, Double> TtoYMap = new HashMap<Integer, Double>();
			
			driftTable.rows().forEach(row -> {
				TtoXMap.put((int)row.getValue("T"), row.getValue("x"));
				TtoYMap.put((int)row.getValue("T"), row.getValue("y"));
			});
			
			meta.getImage(0).planes().forEach(plane -> {
				plane.setXDrift(TtoXMap.get(plane.getT()));
				plane.setYDrift(TtoYMap.get(plane.getT()));
			});
			
			double xZeroPoint = 0;
			double yZeroPoint = 0;
			
			if (zeroPoint.equals("beginning")) {
				xZeroPoint = meta.getPlane(0, 0, 0, 0).getXDrift();
				yZeroPoint = meta.getPlane(0, 0, 0, 0).getYDrift();
			} else if (zeroPoint.equals("end")) {
				xZeroPoint = meta.getPlane(0, 0, 0, meta.getImage(0).getSizeT() - 1).getXDrift();
				yZeroPoint = meta.getPlane(0, 0, 0, meta.getImage(0).getSizeT() - 1).getYDrift();
			}
			
			final double xZeroPointFinal = xZeroPoint;
			final double yZeroPointFinal = yZeroPoint;
			meta.getImage(0).planes().forEach(plane -> {
				plane.setXDrift(plane.getXDrift() - xZeroPointFinal);
				plane.setYDrift(plane.getYDrift() - yZeroPointFinal);
			});
			
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
	
	private static void linearInterpolateGaps(MarsTable table, int sizeT) {
		int rows = table.getRowCount();

		for (int i=1;i<rows;i++) {
			//Check whether there is a gap in the T index...
			int previous_T = (int)table.getValue("T", i-1);
			int current_T = (int)table.getValue("T", i);
			if (previous_T != current_T - 1) {
				for (int w=1; w < current_T - previous_T ; w++) {
					table.appendRow();
					table.setValue("T", table.getRowCount() - 1, previous_T + w);
					table.setValue("x", table.getRowCount() - 1, table.getValue("x", i-1) + w*(table.getValue("x", i) - table.getValue("x", i-1))/(current_T - previous_T));
					table.setValue("y", table.getRowCount() - 1, table.getValue("y", i-1) + w*(table.getValue("y", i) - table.getValue("y", i-1))/(current_T - previous_T));
				}
			}
		}
		
		//fill ends if points are missing there...
		if (table.getValue("T", 0) > 1) {
			for (int t=0;t < table.getValue("T", 0); t++) {
				table.appendRow();
				table.setValue("T", table.getRowCount() - 1, t);
				table.setValue("x", table.getRowCount() - 1, table.getValue("x", 0));
				table.setValue("y", table.getRowCount() - 1, table.getValue("y", 0));
			}
		}
		
		if (table.getValue("T", rows - 1) != sizeT) {
			for (int t = (int)table.getValue("T", rows - 1) + 1;t < sizeT; sizeT++) {
				table.appendRow();
				table.setValue("T", table.getRowCount() - 1, t);
				table.setValue("x", table.getRowCount() - 1, table.getValue("x", rows - 1));
				table.setValue("y", table.getRowCount() - 1, table.getValue("y", rows - 1));
			}
		}
		
		//now that we have added all the new rows we need to resort the table by T.
		table.sort(true, "T");
	}
	
	//Fix me...
	public static void calcDrift(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive, String backgroundTag, String input_x, String input_y, 
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
			
			archive.logln(log);
			
			//We will want to calculate the background for each dataset 
			//in the archive separately
			for (String metaUID : archive.getMetadataUIDs()) {
				MarsMetadata meta = archive.getMetadata(metaUID);
				//Let's find the last T
				//MarsTable metaDataTable = meta.getDataTable();
				
				int sizeT = meta.getImage(0).getSizeT();
				
				HashMap<Integer, DoubleColumn> xValuesColumns = new HashMap<Integer, DoubleColumn>();
				HashMap<Integer, DoubleColumn> yValuesColumns = new HashMap<Integer, DoubleColumn>();
				
				for (int t=0;t<=sizeT;t++) {
					xValuesColumns.put(t, new DoubleColumn("X " + t));
					yValuesColumns.put(t, new DoubleColumn("Y " + t));
				}
				
				if (use_incomplete_traces) {
					//For all molecules in this dataset that are marked with the background tag and have all Ts
					archive.getMoleculeUIDs().stream()
						.filter(UID -> archive.getMetadataUIDforMolecule(UID).equals(meta.getUID()))
						.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
						.forEach(UID -> {
							MarsTable datatable = archive.get(UID).getTable();
							double x_mean = datatable.mean(input_x);
							double y_mean = datatable.mean(input_y);
							
							for (int row = 0; row < datatable.getRowCount(); row++) {
								int t = (int)datatable.getValue("T", row);
								xValuesColumns.get(t).add(datatable.getValue(input_x, row) - x_mean);
								yValuesColumns.get(t).add(datatable.getValue(input_y, row) - y_mean);
							}
					});
				} else {
					//For all molecules in this dataset that are marked with the background tag and have all Ts
					long[] num_full_traj = new long[1];
					num_full_traj[0] = 0;
					archive.getMoleculeUIDs().stream()
						.filter(UID -> archive.getMetadataUIDforMolecule(UID).equals(meta.getUID()))
						.filter(UID -> archive.moleculeHasTag(UID, backgroundTag))
						.forEach(UID -> {
							MarsTable datatable = archive.get(UID).getTable();
							if (archive.get(UID).getTable().getRowCount() == sizeT) {
								double x_mean = datatable.mean(input_x);
								double y_mean = datatable.mean(input_y);
								
								for (int row = 0; row < datatable.getRowCount(); row++) {
									int t = (int)datatable.getValue("T", row);
									xValuesColumns.get(t).add(datatable.getValue(input_x, row) - x_mean);
									yValuesColumns.get(t).add(datatable.getValue(input_y, row) - y_mean);
								}
								num_full_traj[0]++;
							}
					});
					
					if (num_full_traj[0] == 0) {
					    archive.logln("Aborting. No complete molecules with all Ts found for dataset " + meta.getUID() + "!");
					    continue;
					}
				}
				
				MarsTable driftTable = new MarsTable();
				driftTable.appendColumn("T");
				driftTable.appendColumn("x");
				driftTable.appendColumn("y");
				
				int gRow = 0;
				for (int t = 0; t <= sizeT ; t++) {
					if (xValuesColumns.get(t).size() == 0 || yValuesColumns.get(t).size() == 0)
						continue;
					
					double xTFinalValue = Double.NaN;
					double yTFinalValue = Double.NaN;
						
					MarsTable xTempTable = new MarsTable();
					xTempTable.add(xValuesColumns.get(t));
					
					MarsTable yTempTable = new MarsTable();
					yTempTable.add(yValuesColumns.get(t));
					
					if (mode.equals("mean")) {
						xTFinalValue = xTempTable.mean("X " + t);
						yTFinalValue = yTempTable.mean("Y " + t);
					} else {
						xTFinalValue = xTempTable.median("X " + t);
						yTFinalValue = yTempTable.median("Y " + t);
					}
					
					driftTable.appendRow();
					driftTable.setValue("T", gRow, t);
					driftTable.setValue("x", gRow, xTFinalValue);
					driftTable.setValue("y", gRow, yTFinalValue);
					gRow++;
				}
				
				if (driftTable.getRowCount() != sizeT)	
					linearInterpolateGaps(driftTable, sizeT);
				
				//Build Maps
				HashMap<Integer, Double> TtoXMap = new HashMap<Integer, Double>();
				HashMap<Integer, Double> TtoYMap = new HashMap<Integer, Double>();
				
				driftTable.rows().forEach(row -> {
					TtoXMap.put((int)row.getValue("T"), row.getValue("x"));
					TtoYMap.put((int)row.getValue("T"), row.getValue("y"));
				});
				
				meta.getImage(0).planes().forEach(plane -> {
					plane.setXDrift(TtoXMap.get(plane.getT()));
					plane.setYDrift(TtoYMap.get(plane.getT()));
				});
				
				double xZeroPoint = 0;
				double yZeroPoint = 0;
				
				if (zeroPoint.equals("beginning")) {
					xZeroPoint = meta.getPlane(0, 0, 0, 0).getXDrift();
					yZeroPoint = meta.getPlane(0, 0, 0, 0).getYDrift();
				} else if (zeroPoint.equals("end")) {
					xZeroPoint = meta.getPlane(0, 0, 0, meta.getImage(0).getSizeT() - 1).getXDrift();
					yZeroPoint = meta.getPlane(0, 0, 0, meta.getImage(0).getSizeT() - 1).getYDrift();
				}
				
				final double xZeroPointFinal = xZeroPoint;
				final double yZeroPointFinal = yZeroPoint;
				meta.getImage(0).planes().forEach(plane -> {
					plane.setXDrift(plane.getXDrift() - xZeroPointFinal);
					plane.setYDrift(plane.getYDrift() - yZeroPointFinal);
				});
				
				archive.putMetadata(meta);
			}
			
		    archive.logln(LogBuilder.endBlock(true));
		    archive.logln("  ");
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Input X", input_x);
		builder.addParameter("Input Y", input_y);
		builder.addParameter("Use incomplete traces", String.valueOf(use_incomplete_traces));
		builder.addParameter("Zero Point", zeroPoint);
		builder.addParameter("Background Tag", backgroundTag);
		builder.addParameter("mode", mode);
	}
	
	public void setArchive(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive) {
		this.archive = archive;
	}
	
	public void setInputX(String input_x) {
		this.input_x = input_x;
	}
	
	public void setInputY(String input_y) {
		this.input_y = input_y;
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
	
	public MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> getArchive() {
		return archive;
	}
	
	public String getInputX() {
		return input_x;
	}
	
	public String getInputY() {
		return input_y;
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
