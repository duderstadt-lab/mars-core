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
package de.mpg.biochem.mars.kcp.commands;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.decimal4j.util.DoubleRounder;

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

import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsRegion;
import net.imagej.ops.Initializable;

@Plugin(type = Command.class, headless = true, label = "Sigma Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "KCP", weight = 30,
			mnemonic = 'k'),
		@Menu(label = "Sigma Calculator", weight = 20, mnemonic = 's')})
public class SigmaCalculatorCommand extends DynamicCommand implements Command, Initializable {
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
    
    @Parameter(label="X Column", choices = {"a", "b", "c"})
	private String Xcolumn;
    
    @Parameter(label="Y Column", choices = {"a", "b", "c"})
	private String Ycolumn;
    
	@Parameter(label = "Region:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { "All slices",
					"Defined below", "Defined in Molecules", "Defined in Metadata" })
	private String region;
	
	@Parameter(label="from")
	private double from = 1;
	
	@Parameter(label="to")
	private double to = 500;
	
    @Parameter(label="Region from MarsRecord")
	private String regionName;
    
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
		
		String log = builder.buildTitleBlock("Sigma Calculator");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		final String paramName = Ycolumn + "_sigma";
		
		ConcurrentMap<String, MarsRegion> regionMap = new ConcurrentHashMap<String, MarsRegion>();
		
		if (region.equals("Defined in Metadata")) {
			archive.getImageMetadataUIDs().parallelStream().forEach(metaUID -> {
				MarsMetadata metadata = archive.getImageMetadata(metaUID);
				if (metadata.hasRegion(regionName))
					regionMap.put(metaUID, metadata.getRegion(regionName));
			});
		}
		
		//Loop through each molecule and calculate sigma, add it as a parameter
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			MarsTable datatable = molecule.getDataTable();
			
			if (region.equals("Defined below")) {
				molecule.setParameter(paramName, datatable.std(Ycolumn, Xcolumn, from, to));
			} else if (region.equals("Defined in Molecules") && molecule.hasRegion(regionName)) {
				MarsRegion regionOfInterest = molecule.getRegion(regionName);
				molecule.setParameter(paramName, datatable.std(Ycolumn, Xcolumn, regionOfInterest.getStart(), regionOfInterest.getEnd()));				
			} else if (region.equals("Defined in Metadata") && regionMap.containsKey(molecule.getImageMetadataUID())) {
				MarsRegion regionOfInterest = regionMap.get(molecule.getImageMetadataUID());
				molecule.setParameter(paramName, datatable.std(Ycolumn, Xcolumn, regionOfInterest.getStart(), regionOfInterest.getEnd()));
			} else {
				//WE assume this mean sigma for whole trace.
				molecule.setParameter(paramName, datatable.std(Ycolumn));
			}
			
			archive.put(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.addLogMessage(LogBuilder.endBlock(true));
	    archive.addLogMessage("   ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless()) 
			archive.unlock();
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", Xcolumn);
		builder.addParameter("Y Column", Ycolumn);
		builder.addParameter("Region", region);
		builder.addParameter("from", String.valueOf(from));
		builder.addParameter("to", String.valueOf(to));
		builder.addParameter("Region", regionName);
		builder.addParameter("New parameter added", Ycolumn + "_sigma");
	}
}


