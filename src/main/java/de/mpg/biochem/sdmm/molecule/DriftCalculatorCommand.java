package de.mpg.biochem.sdmm.molecule;

import org.decimal4j.util.DoubleRounder;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.LogBuilder;
import net.imagej.table.DoubleColumn;

@Plugin(type = Command.class, label = "Drift Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
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
    private MoleculeArchive archive;
    
    @Parameter(label="Background Tag")
    private String backgroundTag = "background";
    
	@Override
	public void run() {	
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Drift Calculator");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		//We will want to calculate the background for each dataset 
		//in the archive separately
		for (String metaUID : archive.getImageMetaDataUIDs()) {
			ImageMetaData meta = archive.getImageMetaData(metaUID);
			//Let's find the last slice
			SDMMResultsTable metaDataTable = meta.getDataTable();
			
			int slices = (int)metaDataTable.getValue("slice", metaDataTable.getRowCount()-1);
			
			//First calculator global background
			double[] x_avg_background = new double[slices];
			double[] y_avg_background = new double[slices];
			for (int i=0;i<slices;i++) {
				x_avg_background[i] = 0;
				y_avg_background[i] = 0;
			}
			
			long num_full_traj = archive.getMoleculeUIDs().stream()
					.filter(UID -> archive.get(UID).getImageMetaDataUID().equals(meta.getUID()))
					.filter(UID -> archive.get(UID).hasTag(backgroundTag))
					.filter(UID -> archive.get(UID).getDataTable().getRowCount() == slices).count();
			
			if (num_full_traj == 0) {
				uiService.showDialog("No complete molecules with all slices found for dataset " + meta.getUID() + "!");
				return;
			}
			
			//For all molecules in this dataset that are marked with the background tag and have all slices
			archive.getMoleculeUIDs().stream()
				.filter(UID -> archive.get(UID).getImageMetaDataUID().equals(meta.getUID()))
				.filter(UID -> archive.get(UID).hasTag(backgroundTag))
				.filter(UID -> archive.get(UID).getDataTable().getRowCount() == slices)
				.forEach(UID -> {
					SDMMResultsTable datatable = archive.get(UID).getDataTable();
					double x_mean = datatable.mean("x");
					double y_mean = datatable.mean("y");
					
					for (int row = 0; row < datatable.getRowCount(); row++) {
						x_avg_background[row] += datatable.getValue("x", row) - x_mean;
						y_avg_background[row] += datatable.getValue("y", row) - y_mean;
					}
			});
			
			DoubleColumn x_background = new DoubleColumn("x_drift");
			DoubleColumn y_background = new DoubleColumn("y_drift");
			
			for (int row = 0; row < slices ; row++) {
				x_background.add(x_avg_background[row]/num_full_traj);
				y_background.add(y_avg_background[row]/num_full_traj);
			}
			
			metaDataTable.add(x_background);
			metaDataTable.add(y_background);
			
			archive.setImageMetaData(meta);
		}
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("  ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Background Tag", backgroundTag);
	}
	
	public void setArchive(MoleculeArchive archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive getArchive() {
		return archive;
	}
	
	public void setBackgroundTag(String backgroundTag) {
		this.backgroundTag = backgroundTag;
	}
    
	public String getBackgroundTag() {
		return backgroundTag;
	}
}
