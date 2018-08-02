package de.mpg.biochem.sdmm.molecule;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import com.fasterxml.jackson.core.JsonParseException;

import de.mpg.biochem.sdmm.table.ResultsTableService;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.SDMMPluginsService;
import net.imagej.ops.Initializable;

import javax.swing.filechooser.FileSystemView;

@Plugin(type = Command.class, menuPath = "Plugins>SDMM Plugins>Molecule Utils>Build archive from table")
public class BuildArchiveFromTable extends DynamicCommand implements Initializable {
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
	private ResultsTableService resultsTableService;
	
	@Parameter
    private SDMMPluginsService sdmmPluginsService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private LogService logService;
    
    @Parameter(label="Table with molecule column", choices = {"a", "b", "c"})
	private String tableName;
    
    @Parameter(label="build in virtual memory")
    private boolean virtual = true;
    
    @Override
	public void initialize() {
        final MutableModuleItem<String> tableItems = getInfo().getMutableInput("tableName", String.class);
		tableItems.setChoices(resultsTableService.getTableNames());
	}
    
    @Override
	public void run() {				
		if (tableName == null)
			return;
		
		String name = tableName + ".yama";
		
		if (moleculeArchiveService.contains(name)) {
			uiService.showDialog("The MoleculeArchive " + name + " has already been created and is open.", MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
			return;
		}
		
		String log = "***************** Building MoleculeArchive from Table *****************\n";
		log += "Time             : " + new java.util.Date() + "\n";
		log += "Version          : " + sdmmPluginsService.getVersion() + "\n";
		log += "Git Build        : " + sdmmPluginsService.getBuild() + "\n";
		log += "From Table       : " + tableName + "\n";
		log += "New Archive Name : " + name;
		
		logService.info("***************** Building MoleculeArchive from Table *****************");
		logService.info("Time             : " + new java.util.Date());
		logService.info("Version          : " + sdmmPluginsService.getVersion());
		logService.info("Git Build        : " + sdmmPluginsService.getBuild());
		logService.info("From Table       : " + tableName);
		logService.info("New Archive Name : " + name);
		
		MoleculeArchive archive = new MoleculeArchive(name, resultsTableService.getResultsTable(tableName), resultsTableService, moleculeArchiveService, virtual);
		
		archive.addLogMessage(log, false);
		
		archive.addLogMessage("Molecules addeded  : " + archive.getNumberOfMolecules());
		
        moleculeArchiveService.addArchive(archive);
        archive.addLogMessage("Added to MoleculeArchiveService");
        
        archive.addLogMessage("********************************* Success *********************************");
        archive.addLogMessage(" ");
        
        moleculeArchiveService.show(name, archive);
	}
}
