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
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import com.fasterxml.jackson.core.JsonParseException;

import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.SDMMPluginsService;
import net.imagej.ops.Initializable;

import javax.swing.filechooser.FileSystemView;

@Plugin(type = Command.class, menuPath = "Plugins>SDMM Plugins>Molecule Utils>Open MoleculeArchive")
public class MoleculeArchiveOpener extends DynamicCommand {
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
    private SDMMPluginsService sdmmPluginsService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private LogService logService;
    
    @Parameter(label="MoleculeArchive (.yama) ")
    private File file;
    
    @Parameter(label="Use virtual memory")
    private boolean virtual = true;
    
    @Override
	public void run() {				
		if (file == null)
			return;
		
		String name = file.getName();
		
		if (moleculeArchiveService.contains(name)) {
			uiService.showDialog("The MoleculeArchive " + name + " is already open.", MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
			return;
		}
		
		String log = "************************* Opening MoleculeArchive *************************\n";
		log += "Time             : " + new java.util.Date() + "\n";
		log += "Version          : " + sdmmPluginsService.getVersion() + "\n";
		log += "Git Build        : " + sdmmPluginsService.getBuild() + "\n";
		log += "Loading File     : " + file.getAbsolutePath() + "\n";
		log += "Archive Name     : " + name;
		
		logService.info("************************* Opening MoleculeArchive *************************");
		logService.info("Time             : " + new java.util.Date());
		logService.info("Version          : " + sdmmPluginsService.getVersion());
		logService.info("Git Build        : " + sdmmPluginsService.getBuild());
		logService.info("Loading File     : " + file.getAbsolutePath());
		logService.info("Archive Name     : " + name);
		
		try {
			MoleculeArchive archive = new MoleculeArchive(name,file,moleculeArchiveService,virtual);
			
			archive.addLogMessage(log, false);
			
			archive.addLogMessage("Molecules Loaded : " + archive.getNumberOfMolecules());
			
	        moleculeArchiveService.addArchive(archive);
	        archive.addLogMessage("Added to MoleculeArchiveService");
	        
	        archive.addLogMessage("********************************* Success *********************************");
	        archive.addLogMessage(" ");
	        
	        moleculeArchiveService.show(name, archive);
	        
		} catch (JsonParseException e) {
			e.printStackTrace();
			logService.error("JsonParseExcpetion - are you sure this is a properly formatted yama file?");
			logService.error("********************************* Failure *********************************");
		} catch (IOException e) {
			e.printStackTrace();
			logService.error("IOException - does the yama file exist?");
			logService.error("********************************* Failure *********************************");
		}
	}
}
