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
package de.mpg.biochem.mars.ImageProcessing.commands;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.gui.PointRoi;

import org.scijava.module.MutableModuleItem;
import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.RealRect;

import de.mpg.biochem.mars.ImageProcessing.Peak;
import de.mpg.biochem.mars.ImageProcessing.PeakFinder;
import de.mpg.biochem.mars.ImageProcessing.PeakFitter;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import io.scif.img.SCIFIOImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.display.OverlayService;
import net.imglib2.Cursor;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import net.imagej.ops.Initializable;
import org.scijava.table.DoubleColumn;
import io.scif.img.IO;
import io.scif.img.ImgIOException;

import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import net.imglib2.img.Img;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.imglib2.img.ImagePlusAdapter;

@Plugin(type = Command.class)
public class DSPeakFinderCommand<T extends RealType< T >> extends DynamicCommand implements Command, Initializable, Previewable {
	
	//GENERAL SERVICES NEEDED
	@Parameter(required=false)
	private RoiManager roiManager;
	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
    
	@Parameter
    private MarsTableService resultsTableService;
	
	//INPUT IMAGE
	@Parameter(label = "Image to search for Peaks")
	private ImagePlus image; 
	
	//ROI SETTINGS
	@Parameter(label="use ROI", persist=false)
	private boolean useROI = true;
	
	@Parameter(label="ROI x0", persist=false)
	private int x0;
	
	@Parameter(label="ROI y0", persist=false)
	private int y0;
	
	@Parameter(label="ROI width", persist=false)
	private int width;
	
	@Parameter(label="ROI height", persist=false)
	private int height;
	
	//PEAK FINDER SETTINGS
	@Parameter(label="Use Discoidal Averaging Filter")
	private boolean useDiscoidalAveragingFilter;
	
	@Parameter(label="Inner radius")
	private int DS_innerRadius;
	
	@Parameter(label="Outer radius")
	private int DS_outerRadius;
	
	@Parameter(label="Detection threshold (mean + N * STD)")
	private double threshold;
	
	@Parameter(label="Minimum distance between peaks (in pixels)")
	private int minimumDistance;
	
	@Parameter(label="Find Negative Peaks")
	private boolean findNegativePeaks = false;
	
	@Parameter(label="Generate peak count table")
	private boolean generatePeakCountTable;
	
	@Parameter(label="Generate peak table")
	private boolean generatePeakTable;
	
	@Parameter(label="Add to RoiManger")
	private boolean addToRoiManger;
	
	@Parameter(label="Molecule Names in Manager")
	private boolean moleculeNames;
	
	@Parameter(label="Process all slices")
	private boolean allSlices;

	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false, callback = "previewChanged")
	private boolean preview = false;
	
	//PEAK FITTER
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String PeakFitterMessage =
		"Peak fitter settings:";
	
	@Parameter(label="Fit peaks")
	private boolean fitPeaks = false;
	
	@Parameter(label="Fit Radius")
	private int fitRadius = 2;
	
	//Initial guesses for fitting
	@Parameter(label="Initial Baseline")
	private double PeakFitter_initialBaseline = Double.NaN;
	
	@Parameter(label="Initial Height")
	private double PeakFitter_initialHeight = Double.NaN;
	
	@Parameter(label="Initial Sigma")
	private double PeakFitter_initialSigma = Double.NaN;
	
	//parameters to vary during fit
	
	@Parameter(label="Vary Baseline")
	private boolean PeakFitter_varyBaseline = true;
	
	@Parameter(label="Vary Height")
	private boolean PeakFitter_varyHeight = true;
	
	@Parameter(label="Vary Sigma")
	private boolean PeakFitter_varySigma = true;
	
	//Check the maximal allowed error for the fitting process.
	@Parameter(label="Filter by Max Error")
	private boolean PeakFitter_maxErrorFilter = true;
	
	@Parameter(label="Max Error Baseline")
	private double PeakFitter_maxErrorBaseline = 5000;
	
	@Parameter(label="Max Error Height")
	private double PeakFitter_maxErrorHeight = 5000;
	
	@Parameter(label="Max Error X")
	private double PeakFitter_maxErrorX = 1;
	
	@Parameter(label="Max Error Y")
	private double PeakFitter_maxErrorY = 1;
	
	@Parameter(label="Max Error Sigma")
	private double PeakFitter_maxErrorSigma = 1;
	
	//Which columns to write in peak table
	@Parameter(label="Verbose table fit output")
	private boolean PeakFitter_writeEverything = true;
	
	//OUTPUT PARAMETERS
	@Parameter(label="Peak Count", type = ItemIO.OUTPUT)
	private MarsTable peakCount;
	
	@Parameter(label="Peaks", type = ItemIO.OUTPUT)
	private MarsTable peakTable;
	
	//instance of a PeakFinder to use for all the peak finding operations by passing an image and getting back a peak list.
	private PeakFinder finder;
	
	//instance of a PeakFitter to use for all the peak fitting operations by passing an image and pixel index list and getting back subpixel fits..
	private PeakFitter fitter;
	
	//A map with peak lists for each slice for an image stack
	private ConcurrentMap<Integer, ArrayList<Peak>> PeakStack;
	
	//box region for analysis added to the image.
	private Rectangle rect;
	private Roi startingRoi;
	
	//For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	
	//array for max error margins.
	private double[] maxError;
	private boolean[] vary;
			
	//public static final String[] TABLE_HEADERS = {"baseline", "height", "x", "y", "sigma"};
	public static final String[] TABLE_HEADERS_VERBOSE = {"baseline", "error_baseline", "height", "error_height", "x", "error_x", "y", "error_y", "sigma", "error_sigma"};
	
	@Override
	public void initialize() {
		if (image.getRoi() == null) {
			rect = new Rectangle(0,0,image.getWidth()-1,image.getHeight()-1);
			final MutableModuleItem<Boolean> useRoifield = getInfo().getMutableInput("useROI", Boolean.class);
			useRoifield.setValue(this, false);
		} else {
			rect = image.getRoi().getBounds();
			startingRoi = image.getRoi();
		}
		
		final MutableModuleItem<Integer> imgX0 = getInfo().getMutableInput("x0", Integer.class);
		imgX0.setValue(this, rect.x);
		
		final MutableModuleItem<Integer> imgY0 = getInfo().getMutableInput("y0", Integer.class);
		imgY0.setValue(this, rect.y);
		
		final MutableModuleItem<Integer> imgWidth = getInfo().getMutableInput("width", Integer.class);
		imgWidth.setValue(this, rect.width);
		
		final MutableModuleItem<Integer> imgHeight = getInfo().getMutableInput("height", Integer.class);
		imgHeight.setValue(this, rect.height);

	}
	@Override
	public void run() {				
		image.deleteRoi();
		
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Peak Finder");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Used to store peak list for single frame operations
		ArrayList<Peak> peaks = new ArrayList<Peak>();
		
		if (fitPeaks) {
			vary = new boolean[5];
			vary[0] = PeakFitter_varyBaseline;
			vary[1] = PeakFitter_varyHeight;
			vary[2] = true;
			vary[3] = true;
			vary[4] = PeakFitter_varySigma;
			
			fitter = new PeakFitter(vary);
			
			maxError = new double[5];
			maxError[0] = PeakFitter_maxErrorBaseline;
			maxError[1] = PeakFitter_maxErrorHeight;
			maxError[2] = PeakFitter_maxErrorX;
			maxError[3] = PeakFitter_maxErrorY;
			maxError[4] = PeakFitter_maxErrorSigma;

		}
		
		double starttime = System.currentTimeMillis();
		logService.info("Finding Peaks...");
		if (allSlices) {
			PeakStack = new ConcurrentHashMap<>();
			
			//Need to determine the number of threads
			final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
			
			ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		    try {
		    	//Start a thread to keep track of the progress of the number of frames that have been processed.
		    	//Waiting call back to update the progress bar!!
		    	Thread progressThread = new Thread() {
		            public synchronized void run() {
	                    try {
	        		        while(progressUpdating.get()) {
	        		        	Thread.sleep(100);
	        		        	statusService.showStatus(PeakStack.size(), image.getStackSize(), "Finding Peaks for " + image.getTitle());
	        		        }
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                    }
		            }
		        };

		        progressThread.start();
		        
		        //This will spawn a bunch of threads that will analyze frames individually in parallel and put the results into the PeakStack map as lists of
		        //peaks with the slice number as a key in the map for each list...
		        forkJoinPool.submit(() -> IntStream.rangeClosed(1, image.getStackSize()).parallel().forEach(i -> PeakStack.put(i, findPeaksInSlice(i)))).get();
		        	
		        progressUpdating.set(false);
		        
		        statusService.showProgress(100, 100);
		        statusService.showStatus("Peak search for " + image.getTitle() + " - Done!");
		        
		 } catch (InterruptedException | ExecutionException e) {
		        //handle exceptions
		    	e.printStackTrace();
				logService.info(LogBuilder.endBlock(false));
		 } finally {
		       forkJoinPool.shutdown();
		 }
			
		} else {
			ImagePlus selectedImage = new ImagePlus("current slice", image.getImageStack().getProcessor(image.getCurrentSlice()));
			peaks = findPeaks(selectedImage);
			if (fitPeaks) {
				peaks = fitPeaks(selectedImage.getProcessor(), peaks);
				peaks = removeNearestNeighbors(peaks);
			}
		}
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		
		if (generatePeakCountTable) {
			logService.info("Generating peak count table..");
			peakCount = new MarsTable("Peak Count - " + image.getTitle());
			DoubleColumn sliceColumn = new DoubleColumn("slice");
			DoubleColumn countColumn = new DoubleColumn("peaks");
			
			if (allSlices) {
				for (int i=1;i <= PeakStack.size() ; i++) {
					sliceColumn.addValue(i);
					countColumn.addValue(PeakStack.get(i).size());
				}
			} else {
				sliceColumn.addValue(1);
				countColumn.addValue(peaks.size());
			}
			peakCount.add(countColumn);
			peakCount.add(sliceColumn);
			
			//Make sure the output table has the correct name
			getInfo().getMutableOutput("peakCount", MarsTable.class).setLabel(peakCount.getName());
		}
		
		if (generatePeakTable) {
			logService.info("Generating peak table..");
			//build a table with all peaks
			peakTable = new MarsTable("Peaks - " + image.getTitle());
			
			ArrayList<DoubleColumn> columns = new ArrayList<DoubleColumn>();
			
			if (PeakFitter_writeEverything) {
				for (int i=0;i<TABLE_HEADERS_VERBOSE.length;i++)
					columns.add(new DoubleColumn(TABLE_HEADERS_VERBOSE[i]));
			} else {
				columns.add(new DoubleColumn("x"));
				columns.add(new DoubleColumn("y"));
			}
			
			columns.add(new DoubleColumn("slice"));
			
			if (allSlices) {
				for (int i=1;i<=PeakStack.size() ; i++) {
					ArrayList<Peak> slicePeaks = PeakStack.get(i);
					for (int j=0;j<slicePeaks.size();j++) {
						if (PeakFitter_writeEverything)
							slicePeaks.get(j).addToColumnsVerbose(columns);
						else 
							slicePeaks.get(j).addToColumnsXY(columns);
						
						columns.get(columns.size() - 1).add((double)i);
					}
				}
			} else {
				for (int j=0;j<peaks.size();j++) {
					if (PeakFitter_writeEverything)
						peaks.get(j).addToColumnsVerbose(columns);
					else 
						peaks.get(j).addToColumnsXY(columns);
					
					columns.get(columns.size() - 1).add((double)image.getCurrentSlice());
				}
			}
			
			for(DoubleColumn column : columns)
				peakTable.add(column);	
			
			//Make sure the output table has the correct name
			getInfo().getMutableOutput("peakTable", MarsTable.class).setLabel(peakTable.getName());
		}
		
		if (addToRoiManger) {
			logService.info("Adding Peaks to the RoiManger. This might take a while...");
			if (allSlices) {
				//loop through map and slices and add to Manager
				//This is slow probably because of the continuous GUI updating, but I am not sure a solution
				//There is only one add method for the RoiManager and you can only add one Roi at a time.
				int peakNumber = 1;
				for (int i=1;i <= PeakStack.size() ; i++) {
					peakNumber = AddToManger(PeakStack.get(i),i, peakNumber);
				}
			} else {
				AddToManger(peaks,0);
			}
			statusService.showStatus("Done adding ROIs to Manger");
		}
		image.setRoi(startingRoi);
		
		logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
	}
	
	private ArrayList<Peak> findPeaksInSlice(int slice) {
		ArrayList<Peak> peaks = findPeaks(new ImagePlus("slice " + slice, image.getImageStack().getProcessor(slice)));
		if (peaks.size() == 0)
			return peaks;
		if (fitPeaks) {
			peaks = fitPeaks(image.getImageStack().getProcessor(slice), peaks);
			peaks = removeNearestNeighbors(peaks);
		}
		return peaks;
	}
	
	private void AddToManger(ArrayList<Peak> peaks, int slice) {
		AddToManger(peaks, slice, 0);
	}
	
	private int AddToManger(ArrayList<Peak> peaks, int slice, int startingPeakNum) {
		if (roiManager == null)
			roiManager = new RoiManager();
		int pCount = startingPeakNum;
		if (!peaks.isEmpty()) {
			for (Peak peak : peaks) {
				PointRoi peakRoi = new PointRoi(peak.getDoublePosition(0) + 0.5, peak.getDoublePosition(1) + 0.5);
				if (slice == 0) {
					if (moleculeNames)
						peakRoi.setName("Molecule"+pCount);
					else
						peakRoi.setName(MarsMath.getUUID58());
					
				} else {
					if (moleculeNames)
						peakRoi.setName("Molecule"+pCount);
					else
						peakRoi.setName(MarsMath.getUUID58());
				}
				peakRoi.setPosition(slice);
				roiManager.addRoi(peakRoi);
				pCount++;
			}
		}
		return pCount;
	}
	
	public ArrayList<Peak> findPeaks(ImagePlus imp) {
		ArrayList<Peak> peaks;
		
		if (useDiscoidalAveragingFilter) {
	    	finder = new PeakFinder< T >(threshold, minimumDistance, DS_innerRadius, DS_outerRadius, findNegativePeaks);
	    } else {
	    	finder = new PeakFinder< T >(threshold, minimumDistance, findNegativePeaks);
	    }
		
		if (useROI) {
	    	peaks = finder.findPeaks(imp, new Roi(x0, y0, width, height));
		} else {
			peaks = finder.findPeaks(imp);
		}
		
		if (peaks == null)
			peaks = new ArrayList<Peak>();
		
		return peaks;
	}
	
	public ArrayList<Peak> fitPeaks(ImageProcessor imp, ArrayList<Peak> positionList) {
		
		ArrayList<Peak> newList = new ArrayList<Peak>();
		
		int fitWidth = fitRadius * 2 + 1;
		
		//Need to read in initial guess values
		//Also, need to read out fit results back into peak
		
		for (Peak peak: positionList) {
			
			imp.setRoi(new Roi(peak.getX() - fitRadius, peak.getY() - fitRadius, fitWidth, fitWidth));
			
			double[] p = new double[5];
			p[0] = PeakFitter_initialBaseline;
			p[1] = PeakFitter_initialHeight;
			p[2] = peak.getX();
			p[3] = peak.getY();
			p[4] = PeakFitter_initialSigma;
			double[] e = new double[5];
			
			fitter.fitPeak(imp, p, e, findNegativePeaks);
			
			// First we reset valid since it was set to false for all peaks
			// during the finding step to avoid finding the same peak twice.
			peak.setValid();
			
			for (int i = 0; i < p.length && peak.isValid(); i++) {
				if (Double.isNaN(p[i]) || Double.isNaN(e[i]) || Math.abs(e[i]) > maxError[i])
					peak.setNotValid();
			}
			
			//If the x, y, sigma values are negative reject the peak
			//but we can have negative height p[0] or baseline p[1]
			if (p[2] < 0 || p[3] < 0 || p[4] < 0) {
				peak.setNotValid();
			}
			
			//Force all peaks to be valid...
			if (!PeakFitter_maxErrorFilter)
				peak.setValid();
			
			if (peak.isValid()) {
				peak.setValues(p);
				peak.setErrorValues(e);
				newList.add(peak);
			}
		}
		return newList;
	}
	
	public ArrayList<Peak> removeNearestNeighbors(ArrayList<Peak> peakList) {
		//Sort the list from lowest to highest XYErrors
		Collections.sort(peakList, new Comparator<Peak>(){
			@Override
			public int compare(Peak o1, Peak o2) {
				return Double.compare(o1.getXError() + o1.getYError(), o2.getXError() + o2.getYError());		
			}
		});
		
		if (peakList.size() == 0)
			return peakList;
		
		//We have to make a copy to pass to the KDTREE because it will change the order and we have already sorted from lowest to highest to pick center of peaks in for loop below.
		//This is a shallow copy, which means it contains exactly the same elements as the first list, but the order can be completely different...
		ArrayList<Peak> KDTreePossiblePeaks = new ArrayList<>(peakList);

		//Allows for fast search of nearest peaks...
		KDTree<Peak> possiblePeakTree = new KDTree<Peak>(KDTreePossiblePeaks, KDTreePossiblePeaks);
		
		RadiusNeighborSearchOnKDTree< Peak > radiusSearch = new RadiusNeighborSearchOnKDTree< Peak >( possiblePeakTree );
		
		//As we loop through all possible peaks and remove those that are too close
		//we will add all the selected peaks to a new array 
		//that will serve as the finalList of actual peaks
		//This whole process is to remove pixels near the center peak pixel that are also above the detection threshold but all part of the same peak...
		ArrayList<Peak> finalPeaks = new ArrayList<Peak>();
			
		//Reset all to valid for new search
		for (int i=peakList.size()-1;i>=0;i--) {
			peakList.get(i).setValid();
		}
		
		//It is really important to remember here that possiblePeaks and KDTreePossiblePeaks are different lists but point to the same elements
		//That means if we setNotValid in one it is changing the same object in another that is required for the stuff below to work.
		for (int i=0;i<peakList.size();i++) {
			Peak peak = peakList.get(i);
			if (peak.isValid()) {
				finalPeaks.add(peak);
				
				//Then we remove all possible peaks within the minimumDistance...
				//This will include the peak we just added to the peaks list...
				radiusSearch.search(peak, minimumDistance, false);
				
				for (int j = 0 ; j < radiusSearch.numNeighbors() ; j++ ) {
					radiusSearch.getSampler(j).get().setNotValid();
				}
			}
		}
		return finalPeaks;
	}
	
	@Override
	public void preview() {
		if (preview) {
			image.deleteRoi();
			ImagePlus selectedImage = new ImagePlus("current slice", image.getImageStack().getProcessor(image.getCurrentSlice()));
			ArrayList<Peak> peaks = findPeaks(selectedImage);
			
			if (!peaks.isEmpty()) {
				Polygon poly = new Polygon();
				
				for (Peak p: peaks) {
					int x = (int)p.getDoublePosition(0);
					int y = (int)p.getDoublePosition(1);
					poly.addPoint(x, y);
				}
				
				PointRoi peakRoi = new PointRoi(poly);
				image.setRoi(peakRoi);
			}
		}
	}
	
	@Override
	public void cancel() {
		image.setRoi(startingRoi);
	}
	
	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
		// When preview box is unchecked, reset the Roi back to how it was before...
		if (!preview) cancel();
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Image Title", image.getTitle());
		if (image.getOriginalFileInfo() != null && image.getOriginalFileInfo().directory != null) {
			builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
		}
		builder.addParameter("useROI", String.valueOf(useROI));
		builder.addParameter("ROI x0", String.valueOf(x0));
		builder.addParameter("ROI y0", String.valueOf(y0));
		builder.addParameter("ROI width", String.valueOf(width));
		builder.addParameter("ROI height", String.valueOf(height));
		builder.addParameter("useDiscoidalAveragingFilter", String.valueOf(useDiscoidalAveragingFilter));
		builder.addParameter("DS_innerRadius", String.valueOf(DS_innerRadius));
		builder.addParameter("DS_outerRadius", String.valueOf(DS_outerRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum Distance", String.valueOf(minimumDistance));
		builder.addParameter("Find Negative Peaks", String.valueOf(findNegativePeaks));
		builder.addParameter("Generate peak count table", String.valueOf(generatePeakCountTable));
		builder.addParameter("Generate peak table", String.valueOf(generatePeakTable));
		builder.addParameter("Add to RoiManger", String.valueOf(addToRoiManger));
		builder.addParameter("Process all slices", String.valueOf(allSlices));
		builder.addParameter("Fit peaks", String.valueOf(fitPeaks));
		builder.addParameter("Fit Radius", String.valueOf(fitRadius));
		builder.addParameter("Initial Baseline", String.valueOf(PeakFitter_initialBaseline));
		builder.addParameter("Initial Height", String.valueOf(PeakFitter_initialHeight));
		builder.addParameter("Initial Sigma", String.valueOf(PeakFitter_initialSigma));
		builder.addParameter("Vary Baseline", String.valueOf(PeakFitter_varyBaseline));
		builder.addParameter("Vary Height", String.valueOf(PeakFitter_varyHeight));
		builder.addParameter("Vary Sigma", String.valueOf(PeakFitter_varySigma));
		builder.addParameter("Filter by Max Error", String.valueOf(this.PeakFitter_maxErrorFilter));
		builder.addParameter("Max Error Baseline", String.valueOf(PeakFitter_maxErrorBaseline));
		builder.addParameter("Max Error Height", String.valueOf(PeakFitter_maxErrorHeight));
		builder.addParameter("Max Error X", String.valueOf(PeakFitter_maxErrorX));
		builder.addParameter("Max Error Y", String.valueOf(PeakFitter_maxErrorY));
		builder.addParameter("Max Error Sigma", String.valueOf(PeakFitter_maxErrorSigma));
		builder.addParameter("Verbose fit output", String.valueOf(PeakFitter_writeEverything));
	}
	
	public MarsTable getPeakCountTable() {
		return peakCount;
	}
	
	public MarsTable getPeakTable() {
		return peakTable;
	}
	
	public void setImage(ImagePlus image) {
		this.image = image;
	}
	
	public ImagePlus getImage() {
		return image;
	}
	
	public void setUseROI(boolean useROI) {
		this.useROI = useROI;
	}
	
	public boolean getUseROI() {
		return useROI;
	}
	
	public void setX0(int x0) {
		this.x0 = x0;
	}
	
	public int getX0() {
		return x0;
	}
	
	public void setY0(int y0) {
		this.y0 = y0;
	}
	
	public int getY0() {
		return y0;
	}
	
	public void setWidth(int width) {
		this.width = width;
	}
	
	public int getWidth() {
		return width;
	}
	
	public void setHeight(int height) {
		this.height = height;
	}
	
	public int getHeight() {
		return height;
	}
	
	public void setUseDiscoidalAveragingFilter(boolean useDiscoidalAveragingFilter) {
		this.useDiscoidalAveragingFilter = useDiscoidalAveragingFilter;
	}
	
	public boolean getUseDiscoidalAveragingFilter() {
		return useDiscoidalAveragingFilter;
	}
	
	public void setInnerRadius(int DS_innerRadius) {
		this.DS_innerRadius = DS_innerRadius;
	}
	
	public int getInnerRadius() {
		return DS_innerRadius;
	}
	
	public void setOuterRadius(int DS_outerRadius) {
		this.DS_outerRadius = DS_outerRadius;
	}
	
	public int getOuterRadius() {
		return DS_outerRadius;
	}
	
	public void setThreshold(int threshold) {
		this.threshold = threshold;
	}
	
	public double getThreshold() {
		return threshold;
	}
	
	public void setMinimumDistance(int minimumDistance) {
		this.minimumDistance = minimumDistance;
	}
	
	public int getMinimumDistance() {
		return minimumDistance;
	}
	
	public void setFindNegativePeaks(boolean findNegativePeaks) {
		this.findNegativePeaks = findNegativePeaks;
	}
	
	public boolean getFindNegativePeaks() {
		return findNegativePeaks;
	}
	
	public void setGeneratePeakCountTable(boolean generatePeakCountTable) {
		this.generatePeakCountTable = generatePeakCountTable;
	}
	
	public boolean getGeneratePeakCountTable() {
		return generatePeakCountTable;
	}
	
	public void setGeneratePeakTable(boolean generatePeakTable) {
		this.generatePeakTable = generatePeakTable;
	}
	
	public boolean getGeneratePeakTable() {
		return generatePeakTable;
	}
	
	public void setAddToRoiManager(boolean addToRoiManger) {
		this.addToRoiManger = addToRoiManger;
	}
	
	public boolean getAddToRoiManager() {
		return addToRoiManger;
	}

	public void setProcessAllSlices(boolean allSlices) {
		this.allSlices = allSlices;
	}
	
	public boolean getProcessAllSlices() {
		return allSlices;
	}
	
	public void setFitPeaks(boolean fitPeaks) {
		this.fitPeaks = fitPeaks;
	}
	
	public boolean getFitPeaks() {
		return fitPeaks;
	}
	
	public void setFitRadius(int fitRadius) {
		this.fitRadius = fitRadius;
	}
	
	public int getFitRadius() {
		return fitRadius;
	}
	
	public void setInitialBaseline(double PeakFitter_initialBaseline) {
		this.PeakFitter_initialBaseline = PeakFitter_initialBaseline;
	}
	
	public double getInitialBaseline() {
		return PeakFitter_initialBaseline;
	}
	
	public void setInitialHeight(double PeakFitter_initialHeight) {
		this.PeakFitter_initialHeight = PeakFitter_initialHeight;
	}
	
	public double getInitialHeight() {
		return PeakFitter_initialHeight;
	}
	
	public void setInitialSigma(double PeakFitter_initialSigma) {
		this.PeakFitter_initialSigma = PeakFitter_initialSigma;
	}
	
	public double getInitialSigma() {
		return PeakFitter_initialSigma;
	}
	
	public void setVaryBaseline(boolean PeakFitter_varyBaseline) {
		this.PeakFitter_varyBaseline = PeakFitter_varyBaseline;
	}
	
	public boolean getVaryBaseline() {
		return PeakFitter_varyBaseline;
	}
	
	public void setVaryHeight(boolean PeakFitter_varyHeight) {
		this.PeakFitter_varyHeight = PeakFitter_varyHeight;
	}
	
	public boolean getVaryHeight() {
		return PeakFitter_varyHeight;
	}
	
	public void setVarySigma(boolean PeakFitter_varySigma) {
		this.PeakFitter_varySigma = PeakFitter_varySigma;
	}
	
	public boolean getVarySigma() {
		return PeakFitter_varySigma;
	}
	
	public void setMaxErrorFilter(boolean PeakFitter_maxErrorFilter) {
		this.PeakFitter_maxErrorFilter = PeakFitter_maxErrorFilter;
	}
	
	public boolean getMaxErrorFilter() {
		return PeakFitter_maxErrorFilter;
	}
	
	public void setMaxErrorBaseline(double PeakFitter_maxErrorBaseline) {
		this.PeakFitter_maxErrorBaseline = PeakFitter_maxErrorBaseline;
	}
	
	public double getMaxErrorBaseline() {
		return PeakFitter_maxErrorBaseline;
	}
	
	public void setMaxErrorHeight(double PeakFitter_maxErrorHeight) {
		this.PeakFitter_maxErrorHeight = PeakFitter_maxErrorHeight;
	}
	
	public double getMaxErrorHeight() {
		return PeakFitter_maxErrorHeight;
	}
	
	public void setMaxErrorX(double PeakFitter_maxErrorX) {
		this.PeakFitter_maxErrorX = PeakFitter_maxErrorX;
	}
	
	public double getMaxErrorX() {
		return PeakFitter_maxErrorX;
	}

	public void setMaxErrorY(double PeakFitter_maxErrorY) {
		this.PeakFitter_maxErrorY = PeakFitter_maxErrorY;
	}
	
	public double getMaxErrorY() {
		return PeakFitter_maxErrorY;
	}
	
	public void setMaxErrorSigma(double PeakFitter_maxErrorSigma) {
		this.PeakFitter_maxErrorSigma = PeakFitter_maxErrorSigma;
	}
	
	public double getMaxErrorSigma() {
		return PeakFitter_maxErrorSigma;
	}
	
	public void setVerboseFitOutput(boolean PeakFitter_writeEverything) {
		this.PeakFitter_writeEverything = PeakFitter_writeEverything;
	}
	
	public boolean getVerboseFitOutput() {
		return PeakFitter_writeEverything;
	}
}
