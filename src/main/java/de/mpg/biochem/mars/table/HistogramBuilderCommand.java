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
package de.mpg.biochem.mars.table;

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
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
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
    private MARSResultsTable table;
    
    @Parameter(label="Column", choices = {"a", "b", "c"})
	private String column;
    
    @Parameter(label="bins")
    private int bins = 100;
    
    @Parameter(label="Histogram Table", type = ItemIO.OUTPUT)
    private MARSResultsTable outputHist;
    
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
		
		outputHist = new MARSResultsTable(2, bins);
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
	
	public void setInputTable(MARSResultsTable table) {
		this.table = table;
	}
	
	public MARSResultsTable getInputTable() {
		return table;
	}
	
	public void setColumnName(String column) {
		this.column = column;
	}
	
	public String getColumnName() {
		return column;
	}
	
	public void setBins(int bins) {
		this.bins = bins;
	}
	
	public int getBins() {
		return bins;
	}
	
	public MARSResultsTable getHistogramTable() {
		return outputHist;
	}
}
