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
package de.mpg.biochem.mars.molecule;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemVisibility;
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

import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import net.imagej.ops.Initializable;
import org.scijava.table.DoubleColumn;

import javax.swing.JLabel;

@Plugin(type = Command.class, label = "Generate bps", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Generate bps", weight = 30, mnemonic = 'g')})
public class GenerateBPSCommand extends DynamicCommand implements Command, Initializable {
	
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
	
	@Parameter(label="um per pixel")
	private double um_per_pixel = 1.56;
	
	@Parameter(label="global bps per um")
	private double global_bps_per_um = 3000;
	
	@Parameter(label = "Use", style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Reversal", "Region"})
	private String conversionType = "Reversal";
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String ReversalRegionsMessage =
		"If using reversal regions:";
	
	@Parameter(label="Reverse flow start")
	private int rf_start = 150;
	
	@Parameter(label="Reverse flow end")
	private int rf_end = 250;
	
	@Parameter(label="Forward flow start")
	private int ff_start = 0;
	
	@Parameter(label="Forward flow end")
	private int ff_end = 100;

	@Parameter(label="Flow Start")
	private int f_start = 300;
	
	@Parameter(label="Flow End")
	private int f_end = 400;
	
	@Parameter(label="Bead radius (um)")
	private double bead_radius = 0.5;
	
	@Parameter(label = "Conversion type", style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Global", "By molecule"})
	private String globalorMol = "global";
	
	@Parameter(label="DNA_length_in_bps")
	private double DNA_length_bps = 20666;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String backgroundRegionsMessage = "If using background region:";

	@Parameter(label="Start")
	private int bg_start = 1;
	
	@Parameter(label="End")
	private int bg_end = 500;
	
	@Parameter(label="Output Column")
	private String distance_column_name = "bps";
	
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
		
		String log = builder.buildTitleBlock("Generate bps");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		archive.addLogMessage(log);
		
		if (conversionType.equals("Reversal")) {
			//Loop through each molecule and extract the start and end regions for reversal
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				Molecule molecule = archive.get(UID);
				
				double ff_mean = molecule.getDataTable().mean(Ycolumn, Xcolumn, ff_start, ff_end);
				double rf_mean = molecule.getDataTable().mean(Ycolumn, Xcolumn, rf_start, rf_end);
				double f_mean = molecule.getDataTable().mean(Ycolumn, Xcolumn, f_start, f_end);
				
				//Let's switch rf and ff if the camera orientation is opposite to make sure the math still works out...
				boolean cameraFlipped = false;
				if (ff_mean > rf_mean) {
					cameraFlipped = true;
					double tmp_ff_mean = ff_mean;
					ff_mean = rf_mean;
					rf_mean = tmp_ff_mean;
				}
				//This is the attachment_Position in raw yColumn values
				double attachment_Position = ff_mean + (rf_mean - ff_mean)/2;
				double mol_bps_per_um;
				if (!globalorMol.equals("Global"))
					mol_bps_per_um = DNA_length_bps / (Math.abs(attachment_Position - f_mean)*um_per_pixel - bead_radius);
				else 
					mol_bps_per_um = global_bps_per_um;
				
				MARSResultsTable table = molecule.getDataTable();
				
				if (!table.hasColumn(distance_column_name))
					table.appendColumn(distance_column_name);
				
				for (int j=0; j< table.getRowCount(); j++) {
					double output = (table.getValue(Ycolumn, j) - attachment_Position)*um_per_pixel;
					if (output > 0)
						output = (output - bead_radius)*mol_bps_per_um;
					else if (output < 0)
						output = (output + bead_radius)*mol_bps_per_um;
					else 
						output = output*mol_bps_per_um;
						
					if (!cameraFlipped)
						output *= -1;
					
					table.setValue(distance_column_name, j, output);
				}

				archive.put(molecule);
			});
		} else if (conversionType.equals("Region")) {
			
			//We loop through all molecules, find mean in background region, subtract it
			//and transform to correct units...
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				Molecule molecule = archive.get(UID);
				
				//First we set to global start and end for the region
				//Then if the molecule has parameters those override the global values
				int tab_bg_start = bg_start;
				int tab_bg_end = bg_end;
				if (molecule.hasParameter("bg_start") && molecule.hasParameter("bg_end")) {
					tab_bg_start = (int)molecule.getParameter("bg_start");
					tab_bg_end = (int)molecule.getParameter("bg_end");
				}
				
				MARSResultsTable table = molecule.getDataTable();
				
				double mean_background = table.mean(Ycolumn, Xcolumn, tab_bg_start, tab_bg_end);
				
				if (!table.hasColumn(distance_column_name))
					table.appendColumn(distance_column_name);
				
				for (int j = 0; j < table.getRowCount(); j++) {
					double bps = (table.getValue(Ycolumn, j) - mean_background)*um_per_pixel*global_bps_per_um;
					table.setValue(distance_column_name, j, bps);
				}
				
				archive.put(molecule);
			});
		}
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.addLogMessage(LogBuilder.endBlock(true));
	    
	    //Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();	
	}	
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", Xcolumn);
		builder.addParameter("Y Column", Ycolumn);
		builder.addParameter("um per pixel", String.valueOf(um_per_pixel));
		builder.addParameter("global bps per um", String.valueOf(global_bps_per_um));
		builder.addParameter("Use", conversionType);
		builder.addParameter("Reverse flow start", String.valueOf(rf_start));
		builder.addParameter("Reverse flow end", String.valueOf(rf_end));	
		builder.addParameter("Forward flow start", String.valueOf(ff_start));
		builder.addParameter("Forward flow end", String.valueOf(ff_end));
		builder.addParameter("FlowStart", String.valueOf(f_start));
		builder.addParameter("FlowEnd", String.valueOf(f_end));
		builder.addParameter("Bead radius (um)", String.valueOf(bead_radius));
		builder.addParameter("Conversion type", globalorMol);
		builder.addParameter("DNA_length_in_bps", String.valueOf(DNA_length_bps));
		builder.addParameter("Start", String.valueOf(bg_start));
		builder.addParameter("End", String.valueOf(bg_end));
		builder.addParameter("Output Column", distance_column_name);
	}
	
	public void setArchive(MoleculeArchive archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive getArchive() {
		return archive;
	}
	
	public void setXcolumn(String Xcolumn) {
		this.Xcolumn = Xcolumn;
	}
	
	public String getXcolumn() {
		return Xcolumn;
	}
    
	public void setYcolumn(String Ycolumn) {
		this.Ycolumn = Ycolumn;
	}
	
	public String getYcolumn() {
		return Ycolumn;
	}
	
	public void setUmPerPixel(double um_per_pixel) {
		this.um_per_pixel = um_per_pixel;
	}
	
	public double getUmPerPixel() {
		return um_per_pixel;
	}
	
	public void setGlobalBpsPerUm(double global_bps_per_um) {
		this.global_bps_per_um = global_bps_per_um;
	}
	
	public double getGlobalBpsPerUm() {
		return global_bps_per_um;
	}
	
	public void setUse(String conversionType) {
		this.conversionType = conversionType;
	}
	
	public String getUse() {
		return conversionType;
	}
	
	public void setReverseFlowStart(int rf_start) {
		this.rf_start = rf_start;
	}
	
	public int getReverseFlowStart() {
		return rf_start;
	}
	
	public void setReverseFlowEnd(int rf_end) {
		this.rf_end = rf_end;
	}
	
	public int getReverseFlowEnd() {
		return rf_end;
	}
	
	public void setForwardFlowStart(int ff_start) {
		this.ff_start = ff_start;
	}
	
	public int getForwardFlowStart() {
		return ff_start;
	}
	
	public void setForwardFlowEnd(int ff_end) {
		this.ff_end = ff_end;
	}
	
	public int getForwardFlowEnd() {
		return ff_end;
	}
	
	public void setFlowStart(int f_start) {
		this.f_start = f_start;
	}
	
	public int getFlowStart() {
		return f_start;
	}

	public void setFlowEnd(int f_end) {
		this.f_start = f_end;
	}
	
	public int getFlowEnd() {
		return f_end;
	}
	
	public void setBeadRadius(double bead_radius) {
		this.bead_radius = bead_radius;
	}
	
	public double getBeadRadius() {
		return bead_radius;
	}
	
	public void setConversionType(String globalorMol) {
		this.globalorMol = globalorMol;
	}
	
	public String getConversionType() {
		return globalorMol;
	}
	
	public void setDNALengthBps(double DNA_length_bps) {
		this.DNA_length_bps = DNA_length_bps;
	}
	
	public double getDNALengthBps() {
		return DNA_length_bps;
	}
	
	public void setStart(int bg_start) {
		this.bg_start = bg_start;
	}
	
	public int getStart() {
		return bg_start;
	}

	public void setEnd(int bg_end) {
		this.bg_end = bg_end;
	}
	
	public int getEnd() {
		return bg_end;
	}
	
	public void setOutputColumn(String distance_column_name) {
		this.distance_column_name = distance_column_name;
	}
	
	public String getOutputColumn() {
		return distance_column_name;
	}
}
