package de.mpg.biochem.sdmm.table;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;
import org.scijava.log.*;

import net.imagej.table.DoubleColumn;

@Plugin(type = Command.class, menuPath = "Plugins>SDMM Plugins>Table Utils>Open ResultsTable")
public class ResultsTableOpener implements Command {
	
	@Parameter
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private LogService logService;

	@Override
	public void run() {		
		// ask the user for a file to open
		final File file = uiService.chooseFile(null, FileWidget.OPEN_STYLE);
		
		if (file.getAbsolutePath() == null)
			return;
		
		SDMMResultsTable results = open(file.getAbsolutePath());
		results.setName(file.getName());
		
		resultsTableService.addTable(results);
		uiService.show(results.getName(), results);
	}
	
	public ResultsTableOpener() {}
	
	public SDMMResultsTable open(String absolutePath) {
		File file = new File(absolutePath);
		double size_in_bytes = file.length();
		double readPosition = 0;
		final String lineSeparator =  "\n";
		int currentPercentDone = 0;
		int currentPercent = 0;
		
        SDMMResultsTable rt = new SDMMResultsTable(file.getName());
				
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

            for (int i=firstColumn; i<headings.length;i++) {
            		rt.add(new DoubleColumn(headings[i]));
            }
            
            int row = 0;
            for(String line = null; (line = br.readLine()) != null;) {
               	rt.appendRow();
            	    String[] items = line.split(cellSeparator);
            	    for (int i=firstColumn; i<headings.length;i++) {
            	    		   double value = Double.NaN;
            	    	       if (items[i]==null)
            	    	    	   	    continue;
            	    			try {
            	    				value = Double.parseDouble(items[i]);
            	    			} catch (NumberFormatException e) {}
            	    			
            	    			rt.set(i - firstColumn, row, value);
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
