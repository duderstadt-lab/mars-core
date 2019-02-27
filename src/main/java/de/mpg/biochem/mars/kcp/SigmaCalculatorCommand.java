/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
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
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package de.mpg.biochem.mars.kcp;

import org.decimal4j.util.DoubleRounder;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import net.imagej.ops.Initializable;

@Plugin(type = Command.class, headless = true, label = "Sigma Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "KCP", weight = 30,
			mnemonic = 'k'),
		@Menu(label = "Sigma Calculator", weight = 20, mnemonic = 's')})
public class SigmaCalculatorCommand extends DynamicCommand implements Command, Initializable {
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
    private UIService uiService;
	
    @Parameter(label="MoleculeArchive")
    private MoleculeArchive archive;
    
    @Parameter(label="X Column", choices = {"a", "b", "c"})
	private String Xcolumn;
    
    @Parameter(label="Y Column", choices = {"a", "b", "c"})
	private String Ycolumn;
    
	@Parameter(label = "Region:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { "all slices",
					"defined below", "bg_start to bg_end" })
	private String region;
	
	@Parameter(label="from")
	private double from = 1;
	
	@Parameter(label="to")
	private double to = 500;
    
    @Override
	public void initialize() {
		final MutableModuleItem<String> XcolumnItems = getInfo().getMutableInput("Xcolumn", String.class);
		XcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
		
		final MutableModuleItem<String> YcolumnItems = getInfo().getMutableInput("Ycolumn", String.class);
		YcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
	}
    
	@Override
	public void run() {		
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Sigma Calculator");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		final String paramName = Ycolumn + "_sigma";
		
		//Loop through each molecule and calculate sigma, add it as a parameter
		archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			Molecule molecule = archive.get(UID);
			MARSResultsTable datatable = molecule.getDataTable();
			
			if (region.equals("defined below")) {
				molecule.setParameter(paramName, datatable.std(Ycolumn, Xcolumn, from, to));
			} else if (region.equals("bg_start to bg_end")) {
				double start = molecule.getParameter("bg_start");
				double end = molecule.getParameter("bg_end");
				molecule.setParameter(paramName, datatable.std(Ycolumn, Xcolumn, start, end));
			} else {
				//WE assume this mean sigma for whole trace.
				molecule.setParameter(paramName, datatable.std(Ycolumn));
			}
			
			archive.put(molecule);
		});
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	    archive.addLogMessage("   ");
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless()) 
			archive.unlock();
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", Xcolumn);
		builder.addParameter("Y Column", Ycolumn);
		builder.addParameter("Region", region);
		builder.addParameter("from", String.valueOf(from));
		builder.addParameter("to", String.valueOf(to));
		builder.addParameter("New parameter added", Ycolumn + "_sigma");
	}
}


