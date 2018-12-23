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
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private LogService logService;
    
    @Parameter(label="MARSResultsTable (csv or tab) ")
    private File file;
    
    @Parameter(label="MARSResultsTable", type = ItemIO.OUTPUT)
    private MARSResultsTable results;

	@Override
	public void run() {				
		if (file == null)
			return;
		
		results = open(file.getAbsolutePath());
		results.setName(file.getName());
		
		getInfo().getOutput("results", MARSResultsTable.class).setLabel(results.getName());
	}
	
	public ResultsTableOpenerCommand() {}
	
	public MARSResultsTable open(String absolutePath) {
		File file = new File(absolutePath);
		double size_in_bytes = file.length();
		double readPosition = 0;
		final String lineSeparator =  "\n";
		int currentPercentDone = 0;
		int currentPercent = 0;
		
        MARSResultsTable rt = new MARSResultsTable(file.getName());
				
        Path path = Paths.get(absolutePath);
        boolean csv = absolutePath.endsWith(".csv") || absolutePath.endsWith(".CSV");
        String cellSeparator =  csv?",":"\t";
        
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        	    String header = br.readLine();
        	    readPosition += header.getBytes().length + lineSeparator.getBytes().length;
        	    String[] headings = header.split(cellSeparator);
        	    
        	    int firstColumn = headings.length>0&&headings[0].equals(" ")?1:0;

            for (int i=firstColumn; i<headings.length; i++) {
                headings[i] = headings[i].trim();
            }
            
            boolean[] stringColumn = new boolean[headings.length];

            int row = 0;
            for(String line = null; (line = br.readLine()) != null;) {
        	    String[] items = line.split(cellSeparator);
        	    
        	    //During the first cycle we need to build the table with columns that are either 
                //DoubleColumns or GenericColumns for numbers or strings
            	//We need to detect this by what is in the first row...
        	    if (row == 0) {
        	    	for (int i=firstColumn; i<headings.length;i++) {
        	    		if(items[i].equals("NaN") || items[i].equals("-Infinity") || items[i].equals("Infinity")) {
        	    			//This should be a DoubleColumn
        	    			rt.add(new DoubleColumn(headings[i]));
        	    			stringColumn[i] = false;
        	    		} else {
        	    			double value = Double.NaN;
        	    			try {
         	    				value = Double.parseDouble(items[i]);
         	    			} catch (NumberFormatException e) {}
        	    			
        	    			if (Double.isNaN(value)) {
        	    				rt.add(new GenericColumn(headings[i]));
        	    				stringColumn[i] = true;
        	    			} else {
        	    				rt.add(new DoubleColumn(headings[i]));
        	    				stringColumn[i] = false;
        	    			}
        	    		}
        	    	}
        	    }
        	    
        	    rt.appendRow();
        	    for (int i=firstColumn; i<headings.length;i++) {
        	    	if (stringColumn[i]) {
		    		   rt.setStringValue(i - firstColumn, row, items[i].trim());
        	    	} else {
        	    		double value = Double.NaN;
		    			try {
		    				value = Double.parseDouble(items[i]);
		    			} catch (NumberFormatException e) {}
		    			
		    			rt.setValue(i - firstColumn, row, value);
        	    	}
        	    }
        	    readPosition += line.getBytes().length + lineSeparator.getBytes().length;
        	    currentPercent = (int)Math.round(readPosition*1000/size_in_bytes);
        	    if (currentPercent > currentPercentDone) {
    	    		currentPercentDone = currentPercent;
    	    		statusService.showStatus(currentPercent, 1000, "Opening file " + file.getName());
        	    }
        	    row++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        statusService.showProgress(100, 100);
        statusService.showStatus("Opening file " + file.getName() + " - Done!");
        
        return rt;
	}
	
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
	
	public void setTableService(ResultsTableService resultsTableService) {
		this.resultsTableService = resultsTableService;
	}
	
	public void setUIService(UIService uiService) {
		this.uiService = uiService;
	}
	
	public void setStatusService(StatusService statusService) {
		this.statusService = statusService;
	}
	
	public void setLogService(LogService logService) {
		this.logService = logService;
	}

}
