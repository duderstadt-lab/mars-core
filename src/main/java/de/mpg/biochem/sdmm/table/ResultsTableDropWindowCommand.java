package de.mpg.biochem.sdmm.table;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

@Plugin(type = Command.class, label = "Drag & Drop Window", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Table Utils", weight = 10,
			mnemonic = 't'),
		@Menu(label = "Drag & Drop Window", weight = 10, mnemonic = 'd')})
public class ResultsTableDropWindowCommand implements Command {
	
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
