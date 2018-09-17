package de.mpg.biochem.sdmm.molecule;

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

import java.util.HashMap;

import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.LogBuilder;
import net.imagej.ops.Initializable;
import net.imagej.table.DoubleColumn;

@Plugin(type = Command.class, label = "Region Difference Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
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
    private MoleculeArchive archive;
    
    @Parameter(label="X Column", choices = {"a", "b", "c"})
	private String Xcolumn;
    
    @Parameter(label="Y Column", choices = {"a", "b", "c"})
	private String Ycolumn;
	
    @Parameter(label="Region 1 start")
	private int r1_start = 0;
    
    @Parameter(label="Region 1 end")
	private int r1_end = 100;
    
    @Parameter(label="Region 2 start")
	private int r2_start = 150;
    
    @Parameter(label="Region 2 end")
	private int r2_end = 250;
    
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
		
		//Loop through each molecule and add reversal difference value to parameters for each molecule
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			SDMMResultsTable datatable = molecule.getDataTable();
			
			double region1_mean = datatable.mean(Ycolumn, Xcolumn, r1_start, r1_end);
			double region2_mean = datatable.mean(Ycolumn, Xcolumn, r2_start, r2_end);
			
			molecule.setParameter(ParameterName, region1_mean - region2_mean);
			
			archive.set(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("   ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless()) 
			archive.unlock();
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", Xcolumn);
		builder.addParameter("Y Column", Ycolumn);
		builder.addParameter("Region 1 start", String.valueOf(r1_start));
		builder.addParameter("Region 1 end", String.valueOf(r1_end));
		builder.addParameter("Region 2 start", String.valueOf(r2_start));
		builder.addParameter("Region 2 end", String.valueOf(r2_end));
		builder.addParameter("Parameter Name", ParameterName);
	}
	
	//Getters and Setters
	public void setArchive(MoleculeArchive archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive getArchive() {
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
	
    public void setRegion1Start(int r1_start) {
    	this.r1_start = r1_start;
    }
    
    public int getRegion1Start() {
    	return r1_start;
    }
    
    public void setRegion1End(int r1_end) {
    	this.r1_end = r1_end;
    }
    
    public int getRegion1End() {
    	return r1_end;
    }
    
    public void setRegion2Start(int r2_start) {
    	this.r2_start = r2_start;
    }
    
    public int getRegion2Start() {
    	return r2_start;
    }
    
    public void setRegion2End(int r2_end) {
    	this.r2_end = r2_end;
    }
    
    public int getRegion2End() {
    	return r2_end;
    }
    
    public void setParameterName(String ParameterName) {
    	this.ParameterName = ParameterName;
    }
    
    public String getParameterName() {
    	return ParameterName;
    }
}

