package de.mpg.biochem.sdmm.molecule;

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

import de.mpg.biochem.sdmm.table.*;
import de.mpg.biochem.sdmm.util.LogBuilder;

@Plugin(type = Command.class, label = "Drift Corrector", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
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
    private MoleculeArchive archive;
    
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String header =
		"Region for background alignment:";
    
    @Parameter(label="from slice")
    private int from = 1;
    
    @Parameter(label="to slice")
	private int to = 100;
			
	@Override
	public void run() {		
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Drift Corrector");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lockArchive();
		
		archive.addLogMessage(log);
		
		//Build maps from slice to x and slice to y for each metadataset
		HashMap<String, HashMap<Double, Double>> metaToMapX = new HashMap<String, HashMap<Double, Double>>();
		HashMap<String, HashMap<Double, Double>> metaToMapY = new HashMap<String, HashMap<Double, Double>>();
		
		for (ImageMetaData meta : archive.getImageMetaData()) {
			if (meta.getDataTable().get("x_drift") != null && meta.getDataTable().get("y_drift") != null) {
				metaToMapX.put(meta.getUID(), getSliceToColumnMap(meta,"x_drift"));
				metaToMapY.put(meta.getUID(), getSliceToColumnMap(meta,"y_drift"));
			} else {
				logService.error("ImageMetaData " + meta.getUID() + " is missing x_drift or y_drift column. Aborting");
				logService.error(builder.endBlock(false));
				archive.addLogMessage("ImageMetaData " + meta.getUID() + " is missing x_drift or y_drift column. Aborting");
				archive.addLogMessage(builder.endBlock(false));
				
				//Unlock the window so it can be changed
			    if (!uiService.isHeadless()) {
			    	archive.getWindow().updateAll();
					archive.getWindow().unlockArchive();
				}
				return;
			}
		}
		
		//Loop through each molecule and calculate drift corrected traces...
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			
			HashMap<Double, Double> sliceToXMap = metaToMapX.get(molecule.getImageMetaDataUID());
			HashMap<Double, Double> sliceToYMap = metaToMapY.get(molecule.getImageMetaDataUID());
			
			SDMMResultsTable datatable = molecule.getDataTable();
			
			//If the column already exists we don't need to add it
			//instead we will just be overwriting the values below..
			if (!datatable.hasColumn("x_drift_corr"))
				molecule.getDataTable().appendColumn("x_drift_corr");
			
			if (!datatable.hasColumn("y_drift_corr"))
				molecule.getDataTable().appendColumn("y_drift_corr");
			
			double meanX = datatable.mean("x","slice",from, to);
			double meanY = datatable.mean("y","slice",from, to);
			
			//We use the mappings because many molecules are missing slices.
			//by always using the maps we ensure the correct background slice is 
			//taken that matches the molecules slice at the given index.
			for (int i=0;i<datatable.getRowCount();i++) {
				double slice = datatable.getValue("slice", i);
				
				//First calculate corrected value for current slice x
				double molX = datatable.getValue("x", i) - meanX;
				double backgroundX = sliceToXMap.get(slice);
				
				double x_drift_corr_value = molX - backgroundX;
				datatable.set("x_drift_corr", i, x_drift_corr_value);
		
				//Then calculate corrected value for current slice y
				double molY = datatable.getValue("y", i) - meanY;
				double backgroundY = sliceToYMap.get(slice);
				
				double y_drift_corr_value = molY - backgroundY;
				datatable.set("x_drift_corr", i, y_drift_corr_value);
			}
			
			archive.set(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("  ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlockArchive();	
	}
	
	private HashMap<Double, Double> getSliceToColumnMap(ImageMetaData meta, String columnName) {
		HashMap<Double, Double> sliceToColumn = new HashMap<Double, Double>();
		
		SDMMResultsTable metaTable = meta.getDataTable();
		
		double meanBG = metaTable.mean(columnName,"slice",from, to);
		
		for (int i=0;i<meta.getDataTable().getRowCount();i++) {
			sliceToColumn.put(metaTable.getValue("slice", i), metaTable.getValue(columnName, i) - meanBG);
		}
		return sliceToColumn;
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("from slice", String.valueOf(from));
		builder.addParameter("to slice", String.valueOf(to));
	}
}

