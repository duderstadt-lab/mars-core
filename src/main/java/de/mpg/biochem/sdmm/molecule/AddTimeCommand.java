package de.mpg.biochem.sdmm.molecule;

import org.decimal4j.util.DoubleRounder;

import org.scijava.ItemIO;
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

import java.util.HashMap;

import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.LogBuilder;
import org.scijava.table.DoubleColumn;

@Plugin(type = Command.class, label = "Add Time", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Add Time", weight = 40, mnemonic = 'a')})
public class AddTimeCommand extends DynamicCommand implements Command {
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
	
	@Override
	public void run() {		
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Add Time (s)");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		//First let's generate lookup maps for slice to time for all metadata items in the archive
		//Could just use a list of maps, but I guess this is simplier below...
		HashMap<String, HashMap<Double, Double>> metaToMap = new HashMap<String, HashMap<Double, Double>>();
		for (String metaUID : archive.getImageMetaDataUIDs()) {
			ImageMetaData meta = archive.getImageMetaData(metaUID);
			if (meta.getDataTable().get("Time (s)") != null && meta.getDataTable().get("slice") != null) {
				metaToMap.put(meta.getUID(), getSliceToTimeMap(meta.getUID()));
			} else {
				logService.error("ImageMetaData " + meta.getUID() + " is missing a Time (s) or slice column. Aborting");
				logService.error(builder.endBlock(false));
				archive.addLogMessage("ImageMetaData " + meta.getUID() + " is missing a Time (s) or slice column. Aborting");
				archive.addLogMessage(builder.endBlock(false));
				
				//Unlock the window so it can be changed
			    if (!uiService.isHeadless()) {
			    	archive.getWindow().updateAll();
					archive.getWindow().unlockArchive();
				}
				return;
			}
		}
		
		//Loop through each molecule and add a Time (s) column using the metadata information...
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			
			HashMap<Double, Double> sliceToTimeMap = metaToMap.get(molecule.getImageMetaDataUID());
			SDMMResultsTable datatable = molecule.getDataTable();
			
			//If the column already exists we don't need to add it
			//instead we will just be overwriting the values below..
			if (!datatable.hasColumn("Time (s)"))
				molecule.getDataTable().appendColumn("Time (s)");
			
			for (int i=0;i<datatable.getRowCount();i++) {
				molecule.getDataTable().set("Time (s)", i, sliceToTimeMap.get(datatable.get("slice", i)));
			}
			
			archive.set(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("  ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();	
	}
	
	private HashMap<Double, Double> getSliceToTimeMap(String metaUID) {
		HashMap<Double, Double> sliceToTime = new HashMap<Double, Double>();
		
		//First we retrieve columns from image metadata
		DoubleColumn metaSlice = (DoubleColumn) archive.getImageMetaData(metaUID).getDataTable().get("slice"); 
		DoubleColumn metaTime = (DoubleColumn) archive.getImageMetaData(metaUID).getDataTable().get("Time (s)"); 
		
		for (int i=0;i<metaSlice.size();i++) {
			sliceToTime.put(metaSlice.get(i), metaTime.get(i));
		}
		return sliceToTime;
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
	}
	
	public void setArchive(MoleculeArchive archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive getArchive() {
		return archive;
	}
}
