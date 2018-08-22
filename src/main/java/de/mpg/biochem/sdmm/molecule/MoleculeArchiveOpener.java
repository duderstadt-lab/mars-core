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
import de.mpg.biochem.sdmm.util.LogBuilder;
import net.imagej.ops.Initializable;

import javax.swing.filechooser.FileSystemView;

@Plugin(type = Command.class, menuPath = "Plugins>SDMM Plugins>Molecule Utils>Open MoleculeArchive")
public class MoleculeArchiveOpener extends DynamicCommand {
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
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
		
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Opening MoleculeArchive");
		builder.addParameter("Loading File", file.getAbsolutePath());
		builder.addParameter("Archive Name", name);
		
		log += builder.buildParameterList();
		
		logService.info(log);
		
		try {
			MoleculeArchive archive = new MoleculeArchive(name,file,moleculeArchiveService,virtual);
	        moleculeArchiveService.addArchive(archive);
	        moleculeArchiveService.show(name, archive);
		} catch (JsonParseException e) {
			e.printStackTrace();
			logService.error("JsonParseExcpetion - are you sure this is a properly formatted yama file?");
			logService.error(builder.endBlock(false));
		} catch (IOException e) {
			e.printStackTrace();
			logService.error("IOException - does the yama file exist?");
			logService.error(builder.endBlock(false));
		}
		logService.info(builder.endBlock(true));
	}
}
