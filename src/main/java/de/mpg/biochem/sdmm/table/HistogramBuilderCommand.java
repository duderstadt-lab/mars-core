package de.mpg.biochem.sdmm.table;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import net.imagej.ops.Initializable;

@Plugin(type = Command.class, label = "Histogram Builder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Table Utils", weight = 10,
			mnemonic = 't'),
		@Menu(label = "Histogram Builder", weight = 50, mnemonic = 's')})
public class HistogramBuilderCommand extends DynamicCommand implements Initializable {

	@Parameter
    private ResultsTableService resultsTableService;
	
    @Parameter
    private UIService uiService;
	
    @Parameter(label="Table")
    private SDMMResultsTable table;
    
    @Parameter(label="Column", choices = {"a", "b", "c"})
	private String column;
    
    @Parameter(label="bins")
    private int bins = 100;
    
    @Parameter(label="Table", type = ItemIO.OUTPUT)
    private SDMMResultsTable outputHist;
    
	// -- Initializable methods --

	@Override
	public void initialize() {
		final MutableModuleItem<String> columnItems = getInfo().getMutableInput("column", String.class);
		columnItems.setChoices(resultsTableService.getColumnNames());
	}
	
	@Override
	public void run() {
		double max = 0;
		double min = 0;
		
		for (int i=0;i<table.getRowCount();i++) {
			if (table.getValue(column, i) > max)
				max = table.getValue(column, i);
			if (table.getValue(column, i) < min)
				min = table.getValue(column, i);
		}
		
		outputHist = new SDMMResultsTable(2, bins);
		outputHist.setColumnHeader(0, column);
		outputHist.setColumnHeader(1, "Events");

		double binSize = Math.abs(max - min)/bins;		
		for (int q=0; q<bins; q++) {
			int count = 0;
			
			for (int i=0; i<table.getRowCount();i++) {
				double x = table.getValue(column, i);
				if ((min + q*binSize) < x && x <= ((min + (q+1)*binSize))) {
					count++;
				}
			}
			
			outputHist.setValue(column, q, min + (q + 0.5)*binSize);
			outputHist.setValue("Events", q, count);
		}
	}
}