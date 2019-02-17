/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;

import com.fasterxml.jackson.core.JsonParseException;

import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import net.imagej.ops.Initializable;

import javax.swing.filechooser.FileSystemView;

@Plugin(type = Command.class, label = "Open MoleculeArchive", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Open MoleculeArchive", weight = 1, mnemonic = 'o')})
public class OpenMoleculeArchiveCommand extends DynamicCommand {
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private LogService logService;
    
    @Parameter(label="MoleculeArchive (.yama file or .yama.store directory)", style="both")
    private File file;
    
    @Parameter(label="Use virtual memory")
    private boolean virtual = true;
    
	@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
	private MoleculeArchive archive;
    
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
			archive = new MoleculeArchive(name,file,moleculeArchiveService,virtual);
			
			getInfo().getOutput("archive", MoleculeArchive.class).setLabel(name);
			
		} catch (JsonParseException e) {
			e.printStackTrace();
			logService.error("JsonParseExcpetion - are you sure this is a properly formatted yama file?");
			logService.error(builder.endBlock(false));
			return;
		} catch (IOException e) {
			e.printStackTrace();
			logService.error("IOException - does the yama file exist?");
			logService.error(builder.endBlock(false));
			return;
		}
		logService.info(builder.endBlock(true));
	}
    
    //Getters and Setters
    public MoleculeArchive getArchive() {
    	return archive;
    }
    
    public void setFile(File file) {
    	this.file = file;
    }
    
    public File getFile() {
    	return file;
    }
    
    public void setVirtual(boolean virtual) {
    	this.virtual = virtual;
    }
    
    public boolean getVirtual() {
    	return virtual;
    }
}
