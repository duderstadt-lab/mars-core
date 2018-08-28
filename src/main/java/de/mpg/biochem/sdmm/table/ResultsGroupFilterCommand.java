package de.mpg.biochem.sdmm.table;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import net.imagej.ops.Initializable;

@Plugin(type = Command.class, label = "Results Group Filter", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Table Utils", weight = 10,
			mnemonic = 't'),
		@Menu(label = "Results Group Filter", weight = 40, mnemonic = 'g')})
public class ResultsGroupFilterCommand extends DynamicCommand implements Initializable {
	
	@Parameter
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter(label="Table")
    private SDMMResultsTable table;
	
	@Parameter(label="Group Column", choices = {"a", "b", "c"})
	private String group;
	
	@Parameter
	int min = 0;
	
	@Parameter
	int max = 100;
	
	// -- Initializable methods --

	@Override
	public void initialize() {		
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput("group", String.class);
		columnItems.setChoices(resultsTableService.getColumnNames());
	}
	
	// -- Runnable methods --
	
	@Override
	public void run() {
		// sort on group column
		ResultsTableSorterCommand.sort(table, true, group);
		
		ResultsTableList rtl = new ResultsTableList(table);
		
		int to = table.getRowCount();
		double currentGroup = table.get(group, to - 1);
		int count = 1;
		
		for (int row = table.getRowCount() - 2; row >= 0; row--) {
			
			if (table.get(group, row) == currentGroup) {
				count++;
			} else {
				if (count < min || count > max)
					rtl.removeRange(row + 1, to);
				
				to = row + 1;
				currentGroup = table.get(group, row);
				count = 1;
			}
			
		}
		
		if (count < min || count > max)
			rtl.removeRange(0, to);
		
		uiService.show(table.getName(), table);
	}
}
