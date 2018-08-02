package de.mpg.biochem.sdmm.table;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

@Plugin(type = Command.class, headless = false, menuPath = "Plugins>SDMM Plugins>Table Utils>Drag & Drop Window")
public class ResultsTableDropWindow implements Command {
	
	@Parameter
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private LogService logService;

	Thread instance;
	
	@Override
	public void run() {
		TableDropRunner runner = new TableDropRunner();
		runner.setStatusService(statusService);
		runner.setTableService(resultsTableService);
		runner.setUIService(uiService);
		runner.setLogService(logService);
		instance = new Thread(runner, "DragDrop");
        instance.start();
	}

}
