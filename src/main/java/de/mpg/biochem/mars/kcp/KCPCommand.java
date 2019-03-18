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
package de.mpg.biochem.mars.kcp;

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

import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
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
		@Menu(label = "Change Point Finder", weight = 1, mnemonic = 'f')})
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
  	private MoleculeArchive archive;
    
    @Parameter(label="X Column", choices = {"a", "b", "c"})
	private String Xcolumn;
    
    @Parameter(label="Y Column", choices = {"a", "b", "c"})
	private String Ycolumn;
    
    @Parameter(label="Confidence value")
	private double confidenceLevel = 0.99;
    
    @Parameter(label="Global sigma")
	private double global_sigma = 1;
    
    @Parameter(label="Calculate from background")
    private boolean calcBackgroundSigma = true;
    
    @Parameter(label="Background start")
	private String bg_start_name = "bg_start";
    
    @Parameter(label="Background end")
	private String bg_end_name = "bg_end";
    
    @Parameter(label="region")
    private boolean region = true;
    
    @Parameter(label="Region start")
	private String start_name = "start";
    
    @Parameter(label="Region end")
	private String end_name = "end";
    
    @Parameter(label="Fit steps (zero slope)")
	private boolean step_analysis = false;
    
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
			archive.getWindow().lockArchive();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Change Point Finder");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		archive.addLogMessage(log);
		
		//Let's build a thread pool and in a multithreaded manner perform changepoint analysis on all molecules
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		//Output first part of log message...
		logService.info(log);
		
		double starttime = System.currentTimeMillis();
		logService.info("Finding Change Points...");
	    try {
	    	//Start a thread to keep track of the progress of the number of frames that have been processed.
	    	//Waiting call back to update the progress bar!!
	    	Thread progressThread = new Thread() {
	            public synchronized void run() {
                    try {
        		        while(progressUpdating.get()) {
        		        	Thread.sleep(100);
        		        	statusService.showStatus(numFinished.intValue(), archive.getNumberOfMolecules(), "Finding Change Points for " + archive.getName());
        		        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
	            }
	        };

	        progressThread.start();
	        
	        //This will spawn a bunch of threads that will analyze molecules individually in parallel 
	        //and put the changepoint tables back into the same molecule record
	        
	        forkJoinPool.submit(() -> IntStream.range(0, archive.getNumberOfMolecules()).parallel().forEach(i -> {
	        		Molecule molecule = archive.get(i);
	        		
	        		findChangePoints(molecule);
	        		
	        		archive.put(molecule);
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
	    archive.addLogMessage(LogBuilder.endBlock(true));
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
	    	archive.unlock();

	}
	
	private void findChangePoints(Molecule molecule) {
		MARSResultsTable datatable = molecule.getDataTable();
		int offset = 0;
		int length = datatable.getRowCount();
		
		int SigXstart = 0;
		int SigXend = datatable.getRowCount();
		
		double[] xData = datatable.getColumnAsDoubles(Xcolumn);
		double[] yData = datatable.getColumnAsDoubles(Ycolumn);
		
		for (int j=0;j<datatable.getRowCount();j++) {
			if (region) {
				if (molecule.hasParameter(start_name) && xData[j] <= molecule.getParameter(start_name)) {
					offset = j;
				} else if (molecule.hasParameter(end_name) && xData[j] <= molecule.getParameter(end_name)) {
					length = j - offset;
				}
			}
				
			if (calcBackgroundSigma) {
				if (molecule.hasParameter(bg_start_name) && xData[j] <= molecule.getParameter(bg_start_name)) {
					SigXstart = j;
				} else if (molecule.hasParameter(bg_end_name) && xData[j] <= molecule.getParameter(bg_end_name)) {
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
			molecule.putSegmentsTable(Ycolumn, Xcolumn, buildSegmentTable(segs));
			numFinished.incrementAndGet();
			return;
		}
		
		//Use global sigma or use local sigma or calculate sigma (in this order of priority)
		double sigma = global_sigma;
		if (molecule.hasParameter(Ycolumn + "_sigma")) {
			sigma = molecule.getParameter(Ycolumn + "_sigma");
		} else if (molecule.hasParameter(bg_start_name) || molecule.hasParameter(bg_end_name)) {
			if (calcBackgroundSigma)
				sigma = KCP.calc_sigma(yData, SigXstart, SigXend);
		}
		
		double[] xRegion = Arrays.copyOfRange(xData, offset, offset + length);
		double[] yRegion = Arrays.copyOfRange(yData, offset, offset + length);
		KCP change = new KCP(sigma, confidenceLevel, xRegion, yRegion, step_analysis);
		try {
			molecule.putSegmentsTable(Ycolumn, Xcolumn, buildSegmentTable(change.generate_segments()));
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
	
	private MARSResultsTable buildSegmentTable(ArrayList<Segment> segments) {
		MARSResultsTable output = new MARSResultsTable();
		
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
		builder.addParameter("Fit steps (zero slope)", String.valueOf(step_analysis));
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
	
	public void setFitSteps(boolean step_analysis) {
		this.step_analysis = step_analysis;
	}
	    
	public boolean getFitSteps() {
		return step_analysis;
	}
}
