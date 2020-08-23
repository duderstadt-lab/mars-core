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

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Drift Corrector", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule", weight = 1,
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
    private MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive;
    
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String header =
		"Region for background alignment:";
    
    @Parameter(label="start T")
    private int start = 0;
    
    @Parameter(label="end T")
	private int end = 100;
    
    @Parameter(label="Input X (x)")
    private String input_x = "x";
    
    @Parameter(label="Input Y (y)")
    private String input_y = "y";
    
    @Parameter(label="Output X (x_drift_corr)")
    private String output_x = "x_drift_corr";
    
    @Parameter(label="Output Y (y_drift_corr)")
    private String output_y = "y_drift_corr";
    
    @Parameter(label="correct original coordinates")
    private boolean retainCoordinates = false;
    
	@Override
	public void run() {		
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("Drift Corrector");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.logln(log);
		
		//Build maps from slice to x and slice to y for each metadataset
		HashMap<String, HashMap<Double, Double>> metaToMapX = new HashMap<String, HashMap<Double, Double>>();
		HashMap<String, HashMap<Double, Double>> metaToMapY = new HashMap<String, HashMap<Double, Double>>();
		
		for (String metaUID : archive.getMetadataUIDs()) {
			MarsMetadata meta = archive.getMetadata(metaUID);
			if (!retainCoordinates) {
				metaToMapX.put(meta.getUID(), getToXDriftMap(meta, start, end));
				metaToMapY.put(meta.getUID(), getToYDriftMap(meta, start, end));
			} else {
				metaToMapX.put(meta.getUID(), getToXDriftMap(meta));
				metaToMapY.put(meta.getUID(), getToYDriftMap(meta));
			}
		}

		//Loop through each molecule and calculate drift corrected traces...
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			
			if (molecule == null) {
				logService.error("No record found for molecule with UID " + UID + ". Could be due to data corruption. Continuing with the rest.");
				archive.logln("No record found for molecule with UID " + UID + ". Could be due to data corruption. Continuing with the rest.");
				return;
			}
			
			HashMap<Double, Double> TtoXMap = metaToMapX.get(molecule.getMetadataUID());
			HashMap<Double, Double> TtoYMap = metaToMapY.get(molecule.getMetadataUID());
			
			MarsTable datatable = molecule.getTable();
			
			//If the column already exists we don't need to add it
			//instead we will just be overwriting the values below..
			if (!datatable.hasColumn(output_x))
				molecule.getTable().appendColumn(output_x);
			
			if (!datatable.hasColumn(output_y))
				molecule.getTable().appendColumn(output_y);
			
			//If we want to retain the original coordinates then 
			//we don't subtract anything except the drift.
			double meanX = 0;
			double meanY = 0;
			
			if (!retainCoordinates) {
				meanX = datatable.mean(input_x,"T",start, end);
				meanY = datatable.mean(input_y,"T",start, end);
			}
			
			final double meanXFinal = meanX;
			final double meanYFinal = meanY;
			datatable.rows().forEach(row -> {
				double T = row.getValue("T");
				
				double molX = row.getValue(input_x) - meanXFinal;
				double backgroundX = Double.NaN;
				
				if (TtoXMap.containsKey(T))
					backgroundX = TtoXMap.get(T);
				
				double x_drift_corr_value = molX - backgroundX;
				row.setValue(output_x, x_drift_corr_value);
		
				double molY = row.getValue(input_y) - meanYFinal;
				double backgroundY = Double.NaN;
				
				if (TtoYMap.containsKey(T))
					backgroundY = TtoYMap.get(T);
				
				double y_drift_corr_value = molY - backgroundY;
				row.setValue(output_y, y_drift_corr_value);
			});
			
			archive.put(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.logln("\n" + LogBuilder.endBlock(true));
	    archive.logln("  ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();	
	}
	
	//Add channel input?
	private static HashMap<Double, Double> getToXDriftMap(MarsMetadata meta, int from, int to) {
		HashMap<Double, Double> TtoColumn = new HashMap<Double, Double>();
		
		double meanXbg = 0;
		int count = 0;
		for (int t=from; t<=to; t++) {
			meanXbg += meta.getPlane(0, 0, 0, t).getXDrift();
			count++;
		}
		meanXbg = meanXbg/count; 
		
		for (int t=0; t<meta.getImage(0).getSizeT(); t++) {
			TtoColumn.put(meta.getPlane(0, 0, 0, t).getXDrift(), meta.getPlane(0, 0, 0, t).getXDrift() - meanXbg);
		}
		return TtoColumn;
	}
	
	private static HashMap<Double, Double> getToXDriftMap(MarsMetadata meta) {
		HashMap<Double, Double> TtoColumn = new HashMap<Double, Double>();
		
		for (int t=0; t<meta.getImage(0).getSizeT(); t++) {
			TtoColumn.put(meta.getPlane(0, 0, 0, t).getXDrift(), meta.getPlane(0, 0, 0, t).getXDrift());
		}
		return TtoColumn;
	}
	
	private static HashMap<Double, Double> getToYDriftMap(MarsMetadata meta, int from, int to) {
		HashMap<Double, Double> TtoColumn = new HashMap<Double, Double>();
		
		double meanYbg = 0;
		int count = 0;
		for (int t=from; t<=to; t++) {
			meanYbg += meta.getPlane(0, 0, 0, t).getYDrift();
			count++;
		}
		meanYbg = meanYbg/count; 
		
		for (int t=0; t<meta.getImage(0).getSizeT(); t++) {
			TtoColumn.put(meta.getPlane(0, 0, 0, t).getYDrift(), meta.getPlane(0, 0, 0, t).getYDrift() - meanYbg);
		}
		return TtoColumn;
	}
	
	private static HashMap<Double, Double> getToYDriftMap(MarsMetadata meta) {
		HashMap<Double, Double> TtoColumn = new HashMap<Double, Double>(); 
		
		for (int t=0; t<meta.getImage(0).getSizeT(); t++) {
			TtoColumn.put(meta.getPlane(0, 0, 0, t).getYDrift(), meta.getPlane(0, 0, 0, t).getYDrift());
		}
		return TtoColumn;
	}
	
	public static void correctDrift(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive, int from, int to, String meta_x, 
			String meta_y, String input_x, String input_y, String output_x, String output_y, boolean retainCoordinates) {
			//Build log message
			LogBuilder builder = new LogBuilder();
			
			String log = LogBuilder.buildTitleBlock("Drift Corrector");
			
			builder.addParameter("MoleculeArchive", archive.getName());
			builder.addParameter("from slice", String.valueOf(from));
			builder.addParameter("to slice", String.valueOf(to));
			builder.addParameter("Metadata Background X", meta_x);
			builder.addParameter("Metadata Background Y", meta_y);
			builder.addParameter("Input X", input_x);
			builder.addParameter("Input Y", input_y);
			builder.addParameter("Output X", output_x);
			builder.addParameter("Output Y", output_y);
			builder.addParameter("correct original coordinates", String.valueOf(retainCoordinates));
			log += builder.buildParameterList();
			
			archive.logln(log);
			
			//Build maps from slice to x and slice to y for each metadataset
			HashMap<String, HashMap<Double, Double>> metaToMapX = new HashMap<String, HashMap<Double, Double>>();
			HashMap<String, HashMap<Double, Double>> metaToMapY = new HashMap<String, HashMap<Double, Double>>();
			
			for (String metaUID : archive.getMetadataUIDs()) {
				MarsMetadata meta = archive.getMetadata(metaUID);
				if (!retainCoordinates) {
					metaToMapX.put(meta.getUID(), getToXDriftMap(meta, from, to));
					metaToMapY.put(meta.getUID(), getToYDriftMap(meta, from, to));
				} else {
					metaToMapX.put(meta.getUID(), getToXDriftMap(meta));
					metaToMapY.put(meta.getUID(), getToYDriftMap(meta));
				}
			}

			//Loop through each molecule and calculate drift corrected traces...
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				Molecule molecule = archive.get(UID);
				
				if (molecule == null) {
					archive.logln("No record found for molecule with UID " + UID + ". Could be due to data corruption. Continuing with the rest.");
					return;
				}
				
				HashMap<Double, Double> TtoXMap = metaToMapX.get(molecule.getMetadataUID());
				HashMap<Double, Double> TtoYMap = metaToMapY.get(molecule.getMetadataUID());
				
				MarsTable datatable = molecule.getTable();
				
				//If the column already exists we don't need to add it
				//instead we will just be overwriting the values below..
				if (!datatable.hasColumn(output_x))
					molecule.getTable().appendColumn(output_x);
				
				if (!datatable.hasColumn(output_y))
					molecule.getTable().appendColumn(output_y);
				
				//If we want to retain the original coordinates then 
				//we don't subtract anything except the drift.
				double meanX = 0;
				double meanY = 0;
				
				if (!retainCoordinates) {
					meanX = datatable.mean(input_x,"T",from, to);
					meanY = datatable.mean(input_y,"T",from, to);
				}
				
				final double meanXFinal = meanX;
				final double meanYFinal = meanY;
				datatable.rows().forEach(row -> {
					double T = row.getValue("T");
					
					double molX = row.getValue(input_x) - meanXFinal;
					double backgroundX = Double.NaN;
					
					if (TtoXMap.containsKey(T))
						backgroundX = TtoXMap.get(T);
					
					double x_drift_corr_value = molX - backgroundX;
					row.setValue(output_x, x_drift_corr_value);
			
					double molY = row.getValue(input_y) - meanYFinal;
					double backgroundY = Double.NaN;
					
					if (TtoYMap.containsKey(T))
						backgroundY = TtoYMap.get(T);
					
					double y_drift_corr_value = molY - backgroundY;
					row.setValue(output_y, y_drift_corr_value);
				});
				
				archive.put(molecule);
			});
			
		    archive.logln(LogBuilder.endBlock(true));
		    archive.logln("  ");
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("from T", String.valueOf(start));
		builder.addParameter("to T", String.valueOf(end));
		builder.addParameter("Input X", input_x);
		builder.addParameter("Input Y", input_y);
		builder.addParameter("Output X", output_x);
		builder.addParameter("Output Y", output_y);
		builder.addParameter("correct original coordinates", String.valueOf(retainCoordinates));
	}
	
	public void setArchive(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> getArchive() {
		return archive;
	}
	
	public void setStartT(int start) {
		this.start = start;
	}
	
	public int getStartT() {
		return start;
	}
	
	public void setEndT(int end) {
		this.end = end;
	}
	
	public int getEndT() {
		return end;
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
	
	public void setCorrectOriginalCoordinates(boolean retainCoordinates) {
		this.retainCoordinates = retainCoordinates;
	}
}

