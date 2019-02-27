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
package de.mpg.biochem.mars.table;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import com.fasterxml.jackson.core.JsonParseException;

import de.mpg.biochem.mars.molecule.MoleculeArchive;

import org.scijava.command.DynamicCommand;
import org.scijava.log.*;
import org.scijava.menu.MenuConstants;

import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericColumn;

@Plugin(type = Command.class, label = "Open ResultsTable", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Table Utils", weight = 10,
			mnemonic = 't'),
		@Menu(label = "Open ResultsTable", weight = 1, mnemonic = 'o')})
public class ResultsTableOpenerCommand extends DynamicCommand {
    @Parameter
    private StatusService statusService;
    
    @Parameter(label="MARSResultsTable (csv, tab or json) ")
    private File file;
    
    @Parameter(label="MARSResultsTable", type = ItemIO.OUTPUT)
    private MARSResultsTable results;

	@Override
	public void run() {				
		if (file == null)
			return;
		
		try {
			results = new MARSResultsTable(file, statusService);
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		getInfo().getOutput("results", MARSResultsTable.class).setLabel(results.getName());
	}
	
	public ResultsTableOpenerCommand() {}
	
	//Utility methods to set Parameters not initialized...
	public void setFile(File file) {
		this.file = file;
	}
	
	public File getFile() {
		return file;
	}
	
	public MARSResultsTable getTable() {
		return results;
	}
}
