package de.mpg.biochem.sdmm.RoiTools;

import java.awt.Rectangle;

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
	
	@Parameter
	private RoiManager roiManager;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String tableTypeMessage =
		"Archive molecules must contain x, y columns for particle positions.";
	
	@Parameter(label="MoleculeArchive")
	private MoleculeArchive archive;
	
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
			archive.lockArchive();
		
		//Loop through each molecule and add a Time (s) column using the metadata information...
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			SDMMResultsTable datatable = molecule.getDataTable();
			
			int x_value = (int)datatable.getValue("x", 0);
			int y_value = (int)datatable.getValue("y", 0);
			
			Rectangle r = new Rectangle(x_value + x0, y_value + y0, width, height);
			
			Roi roi = new Roi(r);
				
			roi.setName(UID);
			roiManager.addRoi(roi);
			
			archive.set(molecule);
		});
		
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("   ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless()) 
			archive.unlockArchive();	
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("x0", String.valueOf(x0));
		builder.addParameter("y0", String.valueOf(y0));
		builder.addParameter("width", String.valueOf(width));
		builder.addParameter("height", String.valueOf(height));
	}
}
