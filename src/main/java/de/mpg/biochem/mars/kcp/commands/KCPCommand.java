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

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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

import de.mpg.biochem.mars.kcp.KCP;
import de.mpg.biochem.mars.kcp.Segment;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.MarsRecord;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;
import net.imagej.ops.Initializable;
import org.scijava.table.*;

@Plugin(type = Command.class, headless = true, label = "Change Point Finder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "KCP", weight = 40,
			mnemonic = 'k'),
		@Menu(label = "Change Point Finder", weight = 1, mnemonic = 'c')})
public class KCPCommand extends DynamicCommand implements Command, Initializable {
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
    
    @Parameter(label="X Column", choices = {"a", "b", "c"})
	private String Xcolumn;
    
    @Parameter(label="Y Column", choices = {"a", "b", "c"})
	private String Ycolumn;
    
    @Parameter(label="Confidence value")
	private double confidenceLevel = 0.99;
    
    @Parameter(label="Global sigma")
	private double global_sigma = 1;
    
    @Parameter(label = "Region source:",
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Molecules",
					"Metadata" })
	private String regionSource;
    
    @Parameter(label="Calculate from background")
    private boolean calcBackgroundSigma = true;
    
    @Parameter(label="Background region", required=false)
	private String backgroundRegion;
    
    @Parameter(label="Analyze region")
    private boolean region = true;
    
    @Parameter(label="Region", required=false)
	private String regionName;
    
    @Parameter(label="Fit steps (zero slope)")
	private boolean step_analysis = false;
    
	@Parameter(label = "Include:",
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "All",
					"Tagged with", "Untagged" })
	private String include;
	
	@Parameter(label="Tags (comma separated list)")
	private String tags = "";
    
    //Global variables
    //For the progress thread
  	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
  	private final AtomicInteger numFinished = new AtomicInteger(0);
	
    @Override
	public void initialize() {
		final MutableModuleItem<String> XcolumnItems = getInfo().getMutableInput("Xcolumn", String.class);
		XcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
		
		final MutableModuleItem<String> YcolumnItems = getInfo().getMutableInput("Ycolumn", String.class);
		YcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
	}
	@Override
	public void run() {		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.getWindow().lock();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("Change Point Finder");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		archive.logln(log);
		
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
		
		//Let's build a thread pool and in a multithreaded manner perform changepoint analysis on all molecules
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		//Output first part of log message...
		logService.info(log);
		
		double starttime = System.currentTimeMillis();
		logService.info("Finding Change Points...");
		archive.getWindow().updateLockMessage("Finding Change Points...");
	    try {
	    	//Start a thread to keep track of the progress of the number of frames that have been processed.
	    	//Waiting call back to update the progress bar!!
	    	Thread progressThread = new Thread() {
	            public synchronized void run() {
                    try {
        		        while(progressUpdating.get()) {
        		        	Thread.sleep(100);
        		        	statusService.showStatus(numFinished.intValue(), UIDs.size(), "Finding Change Points for " + archive.getName());
        		        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
	            }
	        };

	        progressThread.start();
	    	
	        //This will spawn a bunch of threads that will analyze molecules individually in parallel 
	        //and put the changepoint tables back into the same molecule record
	        
	        forkJoinPool.submit(() -> UIDs.parallelStream().forEach(i -> {
	        		Molecule molecule = archive.get(i);
	        		
	        		if (molecule.getDataTable().hasColumn(Xcolumn) && molecule.getDataTable().hasColumn(Ycolumn)) {
	        			findChangePoints(molecule);
	        			archive.put(molecule);
	        		}
	        })).get();
	        
	        progressUpdating.set(false);
	        
	        statusService.showStatus(1, 1, "Change point search for archive " + archive.getName() + " - Done!");
	        
	    } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	logService.error(e.getMessage());
	    	e.printStackTrace();
			logService.info(LogBuilder.endBlock(false));
			forkJoinPool.shutdown();
			return;
	    } finally {
	        forkJoinPool.shutdown();
	    }
		
	    logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.logln(LogBuilder.endBlock(true));
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
	    	archive.unlock();

	}
	
	private void findChangePoints(Molecule molecule) {
		MarsTable datatable = molecule.getDataTable();
		
		MarsRecord regionRecord = null;
		if (region || calcBackgroundSigma) {
			if (regionSource.equals("Molecules")) {
				regionRecord = molecule;
			} else {
				regionRecord = archive.getMetadata(molecule.getMetadataUID());
			}
		}
		
		//START NaN FIX
		ArrayList<Double> xDataSafe = new ArrayList<Double>();
		ArrayList<Double> yDataSafe = new ArrayList<Double>();
		for (int i=0;i<datatable.getRowCount();i++) {
			if (!Double.isNaN(datatable.getValue(Xcolumn, i)) && !Double.isNaN(datatable.getValue(Ycolumn, i))) {
				xDataSafe.add(datatable.getValue(Xcolumn, i));
				yDataSafe.add(datatable.getValue(Ycolumn, i));
			}
		}
		
		int rowCount = xDataSafe.size();
		
		int offset = 0;
		int length = rowCount;
		
		int SigXstart = 0;
		int SigXend = rowCount;
		
		double[] xData = new double[rowCount];
		double[] yData = new double[rowCount];
		for (int i=0;i<rowCount;i++) {
			xData[i] = xDataSafe.get(i);
			yData[i] = yDataSafe.get(i);
		}
		//END FIX
		
		for (int j=0;j<rowCount;j++) {
			if (region) {
				if (regionRecord.hasRegion(regionName) && xData[j] <= regionRecord.getRegion(regionName).getStart()) {
					offset = j;
				} else if (regionRecord.hasRegion(regionName) && xData[j] <= regionRecord.getRegion(regionName).getEnd()) {
					length = j - offset + 1;
				}
			}
				
			if (calcBackgroundSigma) {
				if (regionRecord.hasRegion(backgroundRegion) && xData[j] <= regionRecord.getRegion(backgroundRegion).getStart()) {
					SigXstart = j;
				} else if (regionRecord.hasRegion(backgroundRegion) && xData[j] <= regionRecord.getRegion(backgroundRegion).getEnd()) {
					SigXend = j;
				}
			}
		}
		
		if (length == 0) {
			//This means the region probably doesn't exist...
			//So we just add a single dummy row with All NaN values...
			//Then we return...
			ArrayList<Segment> segs = new ArrayList<Segment>();
			Segment segment = new Segment(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
			segs.add(segment);
			molecule.putSegmentsTable(Xcolumn, Ycolumn, buildSegmentTable(segs));
			numFinished.incrementAndGet();
			return;
		}
		
		//Use global sigma or use local sigma or calculate sigma (in this order of priority)
		double sigma = global_sigma;
		if (molecule.hasParameter(Ycolumn + "_sigma")) {
			sigma = molecule.getParameter(Ycolumn + "_sigma");
		} else if (molecule.hasRegion(backgroundRegion)) {
			if (calcBackgroundSigma)
				sigma = KCP.calc_sigma(yData, SigXstart, SigXend);
		}
		
		double[] xRegion = Arrays.copyOfRange(xData, offset, offset + length);
		double[] yRegion = Arrays.copyOfRange(yData, offset, offset + length);
		
		KCP change = new KCP(sigma, confidenceLevel, xRegion, yRegion, step_analysis);
		try {
			MarsTable segmentsTable = buildSegmentTable(change.generate_segments());
			if (region)
				molecule.putSegmentsTable(Xcolumn, Ycolumn, regionName, segmentsTable);
			else
				molecule.putSegmentsTable(Xcolumn, Ycolumn, segmentsTable);
		} catch (ArrayIndexOutOfBoundsException e) {
			logService.error("Out of Bounds Exception");
			logService.error("UID " + molecule.getUID() + " gave an error ");
			logService.error("sigma " + sigma);
			logService.error("confidenceLevel " + confidenceLevel);
			logService.error("offset " + offset);
			logService.error("length " + length);
			e.printStackTrace();
		}
		numFinished.incrementAndGet();
	}
	
	private MarsTable buildSegmentTable(ArrayList<Segment> segments) {
		MarsTable output = new MarsTable();
		
		//Do i need to add these columns first? I can't remember...
		output.add(new DoubleColumn("x1"));
		output.add(new DoubleColumn("y1"));
		output.add(new DoubleColumn("x2"));
		output.add(new DoubleColumn("y2"));
		output.add(new DoubleColumn("A"));
		output.add(new DoubleColumn("sigma_A"));
		output.add(new DoubleColumn("B"));
		output.add(new DoubleColumn("sigma_B"));
		
		int row = 0;
		for (Segment seg : segments) {
			output.appendRow();
			output.setValue("x1", row, seg.x1);
			output.setValue("y1", row, seg.y1);
			output.setValue("x2", row, seg.x2);
			output.setValue("y2", row, seg.y2);
			output.setValue("A", row, seg.A);
			output.setValue("sigma_A", row, seg.A_sigma);
			output.setValue("B", row, seg.B);
			output.setValue("sigma_B", row, seg.B_sigma);
			row++;
		}
		return output;
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", Xcolumn);
		builder.addParameter("Y Column", Ycolumn);
		builder.addParameter("Confidence value", String.valueOf(confidenceLevel));
		builder.addParameter("Global sigma", String.valueOf(global_sigma));
		builder.addParameter("Region Source", regionSource);
		builder.addParameter("Calculate Sigma from background", String.valueOf(calcBackgroundSigma));
		builder.addParameter("Background Region", backgroundRegion);
		builder.addParameter("Analyze region", String.valueOf(region));
		builder.addParameter("Region", regionName);
		builder.addParameter("Include tags", include);
		builder.addParameter("Tags", tags);
		builder.addParameter("Fit steps (zero slope)", String.valueOf(step_analysis));
	}

	public void setArchive(MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> archive) {
		this.archive = archive;
	}
	
	public MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties> getArchive() {
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
	
	public void setConfidenceLevel(double confidenceLevel) {
		this.confidenceLevel = confidenceLevel;
	}
	
	public double getConfidenceLevel() {
		return confidenceLevel;
	}
	    
	public void setGlobalSigma(double global_sigma) {
		this.global_sigma = global_sigma;
	}
	
	public double getGlobalSigma() {
		return global_sigma;
	}
	
	public void setRegionSource(String regionSource) {
		this.regionSource = regionSource;
	}
	
	public String getRegionSource() {
		return this.regionSource;
	}
	
	public void setCalculateBackgroundSigma(boolean calcBackgroundSigma) {
		this.calcBackgroundSigma = calcBackgroundSigma;
	}
	
	public boolean getCalculateBackgroundSigma() {
		return calcBackgroundSigma;
	}
	
	public void setBackgroundRegion(String backgroundRegion) {
		this.backgroundRegion = backgroundRegion;
	}
	
	public String getBackgroundRegion() {
		return backgroundRegion;
	}
	
	public void setAnalyzeRegion(boolean region) {
		this.region = region;
	}
	
	public void setRegion(String regionName) {
		this.regionName = regionName;
	}
	
	public void setIncludeTags(String include) {
		this.include = include;
	}
	
	public String getIncludeTags() {
		return include;
	}
	
	public void setTags(String tags) {
		this.tags = tags;
	}
	
	public String getTags() {
		return tags;
	}
	
	public void setFitSteps(boolean step_analysis) {
		this.step_analysis = step_analysis;
	}
	    
	public boolean getFitSteps() {
		return step_analysis;
	}
}
