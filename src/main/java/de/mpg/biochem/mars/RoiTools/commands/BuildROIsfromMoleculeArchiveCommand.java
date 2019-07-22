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
package de.mpg.biochem.mars.RoiTools.commands;

import static java.util.stream.Collectors.toList;

import java.awt.Rectangle;
import java.util.ArrayList;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.MarsImageMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Build ROIs from MoleculeArchive", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "ROI Tools", weight = 30,
			mnemonic = 'r'),
		@Menu(label = "Build ROIs from MoleculeArchive", weight = 40, mnemonic = 'b')})
public class BuildROIsfromMoleculeArchiveCommand extends DynamicCommand implements Command {
	@Parameter
	private LogService logService;
	
	@Parameter
	private UIService uiService;
	
	@Parameter(required=false)
	private RoiManager roiManager;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String tableTypeMessage =
		"Archive molecules must contain x, y columns for particle positions.";
	
	@Parameter(label="MoleculeArchive")
	private MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> archive;
	
	@Parameter(label="Tags (comma separated list)")
	private String tags = "";
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String roiDimensionsMessage =
			"ROI dimensions - position (x0, y0) relative to mean x and y.";
	
	@Parameter(label="x0")
	private int x0 = -6;
	
	@Parameter(label="y0")
	private int y0 = -6;
	@Parameter(label="width")
	private int width = 12;
	
	@Parameter(label="height")
	private int height = 12;
	
	@Override
	public void run() {
        if (roiManager == null)
			roiManager = new RoiManager();
        
        ArrayList<String> moleculeUIDs;
        
        if (!tags.trim().equals("")) {
	        //Build tag list
	        String[] tagList = tags.split(",");
	        for (int i=0; i<tagList.length; i++) {
	        	tagList[i] = tagList[i].trim();
	        }
	 		 
	        moleculeUIDs = (ArrayList<String>)archive.getMoleculeUIDs().stream().filter(UID -> {
	 				boolean hasTag = false;
	 				for (int i=0; i<tagList.length; i++) {
	 		        	for (String tag : archive.get(UID).getTags()) {
	 		        		if (!tagList[i].equals("") && tagList[i].equals(tag)) {
	 		        			hasTag = true;
	 		        		}
	 		        	}
	 		        }
	 				return hasTag;
	 			}).collect(toList());
        } else {
        	moleculeUIDs = archive.getMoleculeUIDs();
        }
        
        //Build log message
  		LogBuilder builder = new LogBuilder();
  		
  		String log = builder.buildTitleBlock("Build ROIs from MoleculeArchive");
  		
  		addInputParameterLog(builder);
  		log += builder.buildParameterList();
  		
  		//Output first part of log message...
  		logService.info(log);
  		//archive.addLogMessage(log);
        
        //Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		//Loop through each molecule and add a Time (s) column using the metadata information...
		moleculeUIDs.stream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			MarsTable datatable = molecule.getDataTable();
			
			int x_value = (int)(datatable.mean("x") + 0.5);
			int y_value = (int)(datatable.mean("y") + 0.5);
			
			Rectangle r = new Rectangle(x_value + x0, y_value + y0, width, height);
			
			Roi roi = new Roi(r);
				
			roi.setName(UID);
			roiManager.addRoi(roi);
		});
		
	    logService.info(builder.endBlock(true));
	    //archive.addLogMessage(builder.endBlock(true));
	    //archive.addLogMessage("   ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless()) 
			archive.unlock();	
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("tags", tags);
		builder.addParameter("x0", String.valueOf(x0));
		builder.addParameter("y0", String.valueOf(y0));
		builder.addParameter("width", String.valueOf(width));
		builder.addParameter("height", String.valueOf(height));
	}
}
