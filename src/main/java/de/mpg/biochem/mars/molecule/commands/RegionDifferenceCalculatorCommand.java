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
package de.mpg.biochem.mars.molecule.commands;

import org.decimal4j.util.DoubleRounder;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;

import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.MarsImageMetadata;
import de.mpg.biochem.mars.molecule.MarsRecord;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import net.imagej.ops.Initializable;
import org.scijava.table.DoubleColumn;

import java.util.concurrent.ConcurrentHashMap;
import de.mpg.biochem.mars.util.*;

@Plugin(type = Command.class, label = "Region Difference Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Region Difference Calculator", weight = 20, mnemonic = 'o')})
public class RegionDifferenceCalculatorCommand extends DynamicCommand implements Command, Initializable {
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
    
    @Parameter(label="X Column", choices = {"a", "b", "c"})
	private String Xcolumn;
    
    @Parameter(label="Y Column", choices = {"a", "b", "c"})
	private String Ycolumn;
    
    @Parameter(label = "Region source:",
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Molecules",
					"Metadata" })
	private String regionSource;
	
    @Parameter(label="Region 1 name")
   	private String regionOneName;
    
    @Parameter(label="Region 2 name")
   	private String regionTwoName;
    
    @Parameter(label="Parameter Name")
    private String ParameterName;
    
	@Override
	public void initialize() {
		final MutableModuleItem<String> XcolumnItems = getInfo().getMutableInput("Xcolumn", String.class);
		XcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
		
		final MutableModuleItem<String> YcolumnItems = getInfo().getMutableInput("Ycolumn", String.class);
		YcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
	}
    
	@Override
	public void run() {		
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Region Difference Calculator");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		if (regionSource.equals("Molecules")) {
			//Loop through each molecule and add reversal difference value to parameters for each molecule
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				Molecule molecule = archive.get(UID);
				
				if (!molecule.hasRegion(regionOneName) && !molecule.hasRegion(regionTwoName))
					return;
				
				MarsTable datatable = molecule.getDataTable();
				
				double region1_mean = datatable.mean(Ycolumn, Xcolumn, molecule.getRegion(regionOneName).getStart(), molecule.getRegion(regionOneName).getEnd());
				double region2_mean = datatable.mean(Ycolumn, Xcolumn, molecule.getRegion(regionTwoName).getStart(), molecule.getRegion(regionTwoName).getEnd());
				
				molecule.setParameter(ParameterName, region1_mean - region2_mean);
				
				archive.put(molecule);
			});
		} else {
			//Before we start we should build a Map of region information from the image metadata records
			//then we can use the map as we go through the molecules.
			//This will be most efficient.
			ConcurrentMap<String, RegionOfInterest> metadataRegionOneMap = new ConcurrentHashMap<String, RegionOfInterest>();
			ConcurrentMap<String, RegionOfInterest> metadataRegionTwoMap = new ConcurrentHashMap<String, RegionOfInterest>();
			
			archive.getImageMetadataUIDs().parallelStream().forEach(metaUID -> {
				MarsImageMetadata metadata = archive.getImageMetadata(metaUID);
				if (metadata.hasRegion(regionOneName))
					metadataRegionOneMap.put(metaUID, metadata.getRegion(regionOneName));
				
				if (metadata.hasRegion(regionTwoName))
					metadataRegionTwoMap.put(metaUID, metadata.getRegion(regionTwoName));
			});
			
			//Loop through each molecule and add reversal difference value to parameters for each molecule
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				String metaUID = archive.getImageMetadataUIDforMolecule(UID);
				if (!metadataRegionOneMap.containsKey(metaUID) && !metadataRegionTwoMap.containsKey(metaUID))
					return;
				
				RegionOfInterest regionOne = metadataRegionOneMap.get(metaUID);
				RegionOfInterest regionTwo = metadataRegionTwoMap.get(metaUID);
				
				Molecule molecule = archive.get(UID);
				MarsTable datatable = molecule.getDataTable();
				
				double region1_mean = datatable.mean(Ycolumn, Xcolumn, regionOne.getStart(), regionOne.getEnd());
				double region2_mean = datatable.mean(Ycolumn, Xcolumn, regionTwo.getStart(), regionTwo.getEnd());
				
				molecule.setParameter(ParameterName, region1_mean - region2_mean);
				
				archive.put(molecule);
			});
		}
		
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.addLogMessage("\n" + LogBuilder.endBlock(true));
	    archive.addLogMessage("   ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless()) 
			archive.unlock();
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", Xcolumn);
		builder.addParameter("Y Column", Ycolumn);
		builder.addParameter("Region 1 name", regionOneName);
		builder.addParameter("Region 2 name", regionTwoName);
		builder.addParameter("Parameter Name", ParameterName);
	}
	
	//Getters and Setters
	public void setArchive(MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> getArchive() {
		return archive;
	}
    
    public void setXcolumn(String Xcolumn) {
    	this.Xcolumn = Xcolumn;
    }
    
    public String getXcolumn() {
    	return Xcolumn;
    }
    
	public void setYcolumn(String Ycolumn) {
		this.Ycolumn = Ycolumn;
	}
	
	public String getYcolumn() {
		return Ycolumn;
	}
	
	public void setRegionOne(String regionOneName) {
		this.regionOneName = regionOneName;
	}
	
	public String getRegionOne() {
		return this.regionOneName;
	}
	
	public void setRegionTwo(String regionTwoName) {
		this.regionTwoName = regionTwoName;
	}
	
	public String getRegionTwo() {
		return this.regionTwoName;
	}
	
    public void setParameterName(String ParameterName) {
    	this.ParameterName = ParameterName;
    }
    
    public String getParameterName() {
    	return ParameterName;
    }
}

