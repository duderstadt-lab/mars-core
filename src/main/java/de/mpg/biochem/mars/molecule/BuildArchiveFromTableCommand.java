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
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import com.fasterxml.jackson.core.JsonParseException;

import de.mpg.biochem.mars.table.ResultsTableService;
import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import net.imagej.ops.Initializable;

import javax.swing.filechooser.FileSystemView;

@Plugin(type = Command.class, label = "Build archive from table", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Build archive from table", weight = 10, mnemonic = 'b')})
public class BuildArchiveFromTableCommand extends DynamicCommand {
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
	private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private LogService logService;
    
    @Parameter(label="Table with molecule column")
	private MARSResultsTable table;
    
    @Parameter(label="build in virtual memory")
    private boolean virtual = true;
    
    //OUTPUT PARAMETERS
	@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
	private MoleculeArchive archive;
    
    @Override
	public void run() {				
		
		String name = table.getName() + ".yama";
		
		if (moleculeArchiveService.contains(name)) {
			uiService.showDialog("The MoleculeArchive " + name + " has already been created and is open.", MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
			return;
		}
		
		if (table.get("molecule") == null) {
			uiService.showDialog("The table given doesn't have a molecule column. It must have a molecule column in order to generate the Molecule Archive.", MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
			return;
		}
		
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Building MoleculeArchive from Table");

		builder.addParameter("From Table", table.getName());
		builder.addParameter("Virtual", String.valueOf(virtual));
		builder.addParameter("Ouput Archive Name", name);
		
		archive = new MoleculeArchive(name, table, moleculeArchiveService, virtual);

		builder.addParameter("Molecules addeded", String.valueOf(archive.getNumberOfMolecules()));
		log += builder.buildParameterList();
		log += builder.endBlock(true);
		
		//Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", MoleculeArchive.class).setLabel(archive.getName());
        
        archive.addLogMessage(log);
        
        logService.info(log);
	}
    
    public MoleculeArchive getArchive() {
    	return archive;
    }
    
    public void setTable(MARSResultsTable table) {
    	this.table = table;
    }
    
    public MARSResultsTable getTable() {
    	return table;
    }
    
    public void setVirtual(boolean virtual) {
    	this.virtual = virtual;
    }
    
    public boolean getVirtual() {
    	return virtual;
    }
}
