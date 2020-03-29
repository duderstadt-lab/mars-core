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
package de.mpg.biochem.mars.kcp.commands;

import java.awt.AWTEvent;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import net.imagej.ops.Initializable;

import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
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

import de.mpg.biochem.mars.kcp.SegmentDistributionBuilder;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.MarsMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;

import javax.swing.JCheckBox;

import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;

import static java.util.stream.Collectors.toList;

@Plugin(type = Command.class, headless = true, label = "Segment Distribution Builder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "KCP", weight = 30,
			mnemonic = 'k'),
		@Menu(label = "Segment Distribution Builder", weight = 10, mnemonic = 's')})
public class SegmentDistributionBuilderCommand extends DynamicCommand implements Command, Initializable {
	
	//GENERAL SERVICES NEEDED	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
    private UIService uiService;
	
    @Parameter(label="MoleculeArchive")
  	private MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive;
    
    @Parameter(label="Segments", choices = {"a", "b", "c"})
	private String segmentsTableName;
	
    @Parameter(label="Start")
	private double start = 0;
    
    @Parameter(label="End")
	private double end = 0;
    
    @Parameter(label="Bins")
	private int bins;
	
	@Parameter(label = "Distribution type:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { "Rate (Gaussian)",
					"Rate (Histogram)", "Duration", "Processivity (molecule)", "Processivity (region)" })
	private String distType;
	
	@Parameter(label="Only regions with rates")
	private boolean filter = false;
	
	@Parameter(label="from")
	private double filterRegionStart = 0;
	
	@Parameter(label="to")
	private double filterRegionEnd = 100;
	
	@Parameter(label="Bootstrapping:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { "None",
					"Segments", "Molecules" })
	private String bootstrappingType;
	
	@Parameter(label="Number of cycles")
	private int bootstrapCycles = 100;
	
	@Parameter(label = "Include:",
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "All",
					"Tagged with", "Untagged" })
	private String include;
	
	@Parameter(label="Tags (comma separated list)")
	private String tags = "";
	
    @Parameter(label="Distribution", type = ItemIO.OUTPUT)
    private MarsTable results;
    
    private Map<String, ArrayList<String>> segmentTableNameToColumns;

	@Override
	public void initialize() {
		final MutableModuleItem<String> SegmentTableNames = getInfo().getMutableInput("segmentsTableName", String.class);
		Set<ArrayList<String>> segTableNames = moleculeArchiveService.getSegmentTableNames();
		segmentTableNameToColumns = new HashMap<>();
		
		ArrayList<String> names = new ArrayList<String>();
		
		for (ArrayList<String> segmentTableName : segTableNames) {
			String tabName;
			if (segmentTableName.get(2).equals(""))
				tabName = segmentTableName.get(1) + " vs " + segmentTableName.get(0);
			else 
				tabName = segmentTableName.get(1) + " vs " + segmentTableName.get(0) + " - " + segmentTableName.get(2);
			names.add(tabName);
			
			segmentTableNameToColumns.put(tabName, segmentTableName);
		}
		SegmentTableNames.setChoices(names);
	}
	
	@Override
	public void run() {
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lock();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Segment Distribution Builder");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		//archive.addLogMessage(log);
		
		//Output first part of log message...
		logService.info(log);
		
		double starttime = System.currentTimeMillis();
		logService.info("Building Distribution...");
		
		//Build Collection of UIDs based on tags if they exist...
        ArrayList<String> UIDs;
		if (include.equals("Tagged with")) {
			//First we parse tags to make a list...
	        String[] tagList = tags.split(",");
	        for (int i=0; i<tagList.length; i++) {
	        	tagList[i] = tagList[i].trim();
	        }
			
			UIDs = (ArrayList<String>)archive.getMoleculeUIDs().stream().filter(UID -> {
				boolean hasTags = true;
				for (int i=0; i<tagList.length; i++) {
					if (!archive.moleculeHasTag(UID, tagList[i])) {
						hasTags = false;
						break;
					}
				}
				return hasTags;
			}).collect(toList());
		} else if (include.equals("Untagged")) {
			UIDs = (ArrayList<String>)archive.getMoleculeUIDs().stream().filter(UID -> archive.get(UID).hasNoTags()).collect(toList());
		} else {
			//  we include All molecules...
			UIDs = archive.getMoleculeUIDs();
		}
		
		ArrayList<String> name = segmentTableNameToColumns.get(segmentsTableName);
		
		SegmentDistributionBuilder distBuilder = new SegmentDistributionBuilder(archive, UIDs, name.get(0), name.get(1), start, end, bins, logService, statusService);
		
		//Configure segment builder
		if (filter) {
			distBuilder.setFilter(filterRegionStart, filterRegionEnd);
		}
		
		if (bootstrappingType.equals("Segments")) {
			distBuilder.bootstrapSegments(bootstrapCycles);
		} else if (bootstrappingType.equals("Molecules")) {
			distBuilder.bootstrapMolecules(bootstrapCycles);
		}
		
		//Build desired distribution
		if (distType.equals("Rate (Gaussian)")) {
			results = distBuilder.buildRateGaussian();
			results.setName("Rate (Gaussian)");
		} else if (distType.equals("Rate (Histogram)")) {
			results = distBuilder.buildRateHistogram();
			results.setName("Rate (Histogram)");
		} else if (distType.equals("Duration")) {
			results = distBuilder.buildDurationHistogram();
			results.setName("Duration Distribution");
		} else if (distType.equals("Processivity (molecule)")) {
			results = distBuilder.buildProcessivityByMoleculeHistogram();
			results.setName("Processivity Distribution (molecule)");
		} else if (distType.equals("Processivity (region)")) {
			results = distBuilder.buildProcessivityByRegionHistogram();
			results.setName("Processivity Distribution (region)");
		}
		
		getInfo().getOutput("results", MarsTable.class).setLabel(results.getName());
		
	    logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
		
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlock();
	}
	
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Segments table name", segmentsTableName);
		builder.addParameter("Start", String.valueOf(start));
		builder.addParameter("End", String.valueOf(end));
		builder.addParameter("DistType", distType);
		builder.addParameter("Filter", String.valueOf(filter));
		builder.addParameter("Filter region start", String.valueOf(filterRegionStart));
		builder.addParameter("Filter region end", String.valueOf(filterRegionEnd));
		builder.addParameter("Bootstrapping type", bootstrappingType);
		builder.addParameter("Bootstrap cycles", String.valueOf(bootstrapCycles));
		builder.addParameter("Include", include);
		builder.addParameter("Tags", tags);
	}
	
	public void setArchive(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> getArchive() {
		return archive;
	}
	
	public void setSegmentsTableName(String segmentsTableName) {
		this.segmentsTableName = segmentsTableName;
	}
	
	public String getSegmentTableName() {
		return segmentsTableName;
	}

	public void setStart(double start) {
		this.start = start;
	}
	
	public double getStart() {
		return start;
	}
	
	public void setEnd(double end) {
		this.end = end;
	}
	
	public double getEnd() {
		return end;
	}
	
	public void setDistType(String distType) {
		this.distType = distType;
	}
	
	public String getDistType() {
		return distType;
	}
	
	public void setFilter(boolean filter) {
		this.filter = filter;
	}
	
	public boolean getFilter() {
		return filter;
	}
	
	public void setFilterRegionStart(double filterRegionStart) {	
		this.filterRegionStart = filterRegionStart;
	}	

	public double getFilterRegionStart() {
		return filterRegionStart;
	}
	
	public void setFilterRegionEnd(double filterRegionEnd) {
		this.filterRegionEnd = filterRegionEnd;
	}
	
	public double getFilterRegionEnd() {
		return filterRegionEnd;
	}
	
	public void setBootstrappingType(String bootstrappingType) {
		this.bootstrappingType = bootstrappingType;
	}
	
	public String getBootstrappingType() {
		return bootstrappingType;
	}
	
	public void setBootstrapCycles(int bootstrapCycles) {
		this.bootstrapCycles = bootstrapCycles;
	}
	
	public int getBootstrapCycles() {
		return bootstrapCycles;
	}
	
	public void setInclude(String include) {
		this.include = include;
	}
	
	public String getInclude() {
		return include;
	}
	
	public void setTags(String tags) {
		this.tags = tags;
	}
	
	public String getTags() {
		return tags;
	}
}
