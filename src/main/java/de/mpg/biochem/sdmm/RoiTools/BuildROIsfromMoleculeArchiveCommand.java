package de.mpg.biochem.sdmm.RoiTools;

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
import de.mpg.biochem.sdmm.molecule.Molecule;
import de.mpg.biochem.sdmm.molecule.MoleculeArchive;
import de.mpg.biochem.sdmm.table.*;
import de.mpg.biochem.sdmm.util.LogBuilder;

@Plugin(type = Command.class, label = "Build ROIs from MoleculeArchive", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
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
	private MoleculeArchive archive;
	
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
  		archive.addLogMessage(log);
        
        //Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		//Loop through each molecule and add a Time (s) column using the metadata information...
		moleculeUIDs.stream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			SDMMResultsTable datatable = molecule.getDataTable();
			
			int x_value = (int)(datatable.mean("x") + 0.5);
			int y_value = (int)(datatable.mean("y") + 0.5);
			
			Rectangle r = new Rectangle(x_value + x0, y_value + y0, width, height);
			
			Roi roi = new Roi(r);
				
			roi.setName(UID);
			roiManager.addRoi(roi);
		});
		
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("   ");
	    
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
