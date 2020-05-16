/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule.commands;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.scijava.Context;
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

import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import net.imagej.ops.Initializable;

import javax.swing.filechooser.FileSystemView;

@Plugin(type = Command.class, label = "Build archive from table", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Build archive from table", weight = 10, mnemonic = 'b')})
public class BuildArchiveFromTableCommand extends DynamicCommand {
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
	private Context context;
	
	@Parameter
	private MarsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private LogService logService;
    
    @Parameter(label="Table with molecule column")
	private MarsTable table;
    
    //OUTPUT PARAMETERS
	@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;
    
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
		
		String log = LogBuilder.buildTitleBlock("Building MoleculeArchive from Table");

		builder.addParameter("From Table", table.getName());
		builder.addParameter("Ouput Archive Name", name);
		
		archive = new SingleMoleculeArchive(name, table);

		builder.addParameter("Molecules addeded", String.valueOf(archive.getNumberOfMolecules()));
		log += builder.buildParameterList();
		
		//Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", AbstractMoleculeArchive.class).setLabel(archive.getName());
        logService.info(log);
        logService.info(LogBuilder.endBlock(true));
        
        log += "\n" + LogBuilder.endBlock(true);
        archive.logln(log);
	}
    
    public AbstractMoleculeArchive getArchive() {
    	return archive;
    }
    
    public void setTable(MarsTable table) {
    	this.table = table;
    }
    
    public MarsTable getTable() {
    	return table;
    }
}
