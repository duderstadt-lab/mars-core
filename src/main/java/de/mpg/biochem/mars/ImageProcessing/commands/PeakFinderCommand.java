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

import de.mpg.biochem.mars.ImageProcessing.DogPeakFinder;
import de.mpg.biochem.mars.ImageProcessing.Peak;
import de.mpg.biochem.mars.ImageProcessing.PeakFinder;
import de.mpg.biochem.mars.ImageProcessing.PeakFitter;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.Gaussian2D;
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

import org.scijava.widget.NumberWidget;

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
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;

import net.imagej.ops.OpService;

@Plugin(type = Command.class, label = "Dog Peak Finder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image Processing", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Peak Finder", weight = 1, mnemonic = 'p')})
public class PeakFinderCommand<T extends RealType< T >> extends DynamicCommand implements Command, Initializable, Previewable {
	
	//GENERAL SERVICES NEEDED
	@Parameter(required=false)
	private RoiManager roiManager;
	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
    
	@Parameter
    private MarsTableService resultsTableService;
	
    @Parameter
    private OpService opService;
	
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

	@Parameter(label="Use DoG filter")
	private boolean useDogFilter = true;
	
	@Parameter(label="DoG filter radius")
	private double dogFilterRadius = 2;
	
	@Parameter(label="Detection threshold")
	private double threshold = 50;
	
	@Parameter(label="Minimum distance between peaks (in pixels)")
	private int minimumDistance = 4;
	
	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false, callback = "previewChanged")
	private boolean preview = false;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String slicePeakCount = "count: 0";
	
	@Parameter(label = "Preview slice", min = "1", style = NumberWidget.SCROLL_BAR_STYLE)
	private int previewSlice;
	
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
	
	//PEAK FITTER
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String PeakFitterMessage =
		"Peak fitter settings:";
	
	@Parameter(label="Fit peaks")
	private boolean fitPeaks = false;
	
	@Parameter(label="Fit Radius")
	private int fitRadius = 4;
	
	@Parameter(label="Minimum R2")
	private double RsquaredMin = 0;
	
	//Which columns to write in peak table
	@Parameter(label="Verbose table fit output")
	private boolean PeakFitter_writeEverything = true;
	
	//OUTPUT PARAMETERS
	@Parameter(label="Peak Count", type = ItemIO.OUTPUT)
	private MarsTable peakCount;
	
	@Parameter(label="Peaks", type = ItemIO.OUTPUT)
	private MarsTable peakTable;
	
	//instance of a PeakFitter to use for all the peak fitting operations by passing an image and pixel index list and getting back subpixel fits..
	private PeakFitter fitter;
	
	//A map with peak lists for each slice for an image stack
	private ConcurrentMap<Integer, ArrayList<Peak>> PeakStack;
	
	//box region for analysis added to the image.
	private Rectangle rect;
	private Roi startingRoi;
	
	//For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);

	public static final String[] TABLE_HEADERS_VERBOSE = {"baseline", "height", "x", "y", "sigma", "R2"};
	
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
		
		final MutableModuleItem<Integer> preSlice = getInfo().getMutableInput("previewSlice", Integer.class);
		preSlice.setValue(this, image.getCurrentSlice());
		preSlice.setMaximumValue(image.getStackSize());
	}
	
	@Override
	public void run() {				
		image.deleteRoi();
		
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("Peak Finder");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Used to store peak list for single frame operations
		ArrayList<Peak> peaks = new ArrayList<Peak>();
		
		if (fitPeaks) {
			boolean[] vary = new boolean[5];
			vary[0] = true;
			vary[1] = true;
			vary[2] = true;
			vary[3] = true;
			vary[4] = true;
			
			fitter = new PeakFitter(vary);
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
					peakNumber = AddToManager(PeakStack.get(i),i, peakNumber);
				}
			} else {
				AddToManager(peaks,0);
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
	
	private void AddToManager(ArrayList<Peak> peaks, int slice) {
		AddToManager(peaks, slice, 0);
	}
	
	private int AddToManager(ArrayList<Peak> peaks, int slice, int startingPeakNum) {
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
		
		DogPeakFinder finder = new DogPeakFinder(threshold, minimumDistance, findNegativePeaks);
		
		if (useDogFilter) {
			// Convert image to FloatType for better numeric precision
	        Img<FloatType> converted = opService.convert().float32((Img< T >)ImagePlusAdapter.wrap( imp ));

	        // Create the filtering result
	        Img<FloatType> dog = opService.create().img(converted);

	        final double sigma1 = dogFilterRadius / Math.sqrt( 2 ) * 0.9;
			final double sigma2 = dogFilterRadius / Math.sqrt( 2 ) * 1.1;

	        // Do the DoG filtering using ImageJ Ops
	        opService.filter().dog(dog, converted, sigma2, sigma1);
	        
			//if (findNegativePeaks) {
			//	Img<FloatType> inverted = opService.create().img(dog);
			//	opService.image().invert(inverted, dog);
			//	dog = inverted;
			//}

	        if (useROI) {
		    	peaks = finder.findPeaks(dog, Intervals.createMinMax(x0, y0, x0 + width - 1, y0 + height - 1));
			} else {
				peaks = finder.findPeaks(dog);
			}
		} else {
			if (useROI) {
		    	peaks = finder.findPeaks((Img< T >)ImagePlusAdapter.wrap( imp ), Intervals.createMinMax(x0, y0, x0 + width - 1, y0 + height - 1));
			} else {
				peaks = finder.findPeaks((Img< T >)ImagePlusAdapter.wrap( imp ));
			}
		}
		
		if (peaks == null)
			peaks = new ArrayList<Peak>();
		
		return peaks;
	}
	
	public ArrayList<Peak> fitPeaks(ImageProcessor imp, ArrayList<Peak> positionList) {
		
		ArrayList<Peak> newList = new ArrayList<Peak>();
		
		int fitWidth = fitRadius * 2 + 1;
		
		for (Peak peak: positionList) {
			
			imp.setRoi(new Roi(peak.getX() - fitRadius, peak.getY() - fitRadius, fitWidth, fitWidth));
			
			double[] p = new double[5];
			p[0] = Double.NaN;
			p[1] = Double.NaN;
			p[2] = peak.getX();
			p[3] = peak.getY();
			p[4] = dogFilterRadius/2;
			double[] e = new double[5];
			
			fitter.fitPeak(imp, p, e, findNegativePeaks);
			
			// First we reset valid since it was set to false for all peaks
			// during the finding step to avoid finding the same peak twice.
			peak.setValid();
			
			for (int i = 0; i < p.length && peak.isValid(); i++) {
				if (Double.isNaN(p[i]))
					peak.setNotValid();
			}

			//If the x, y, sigma values are negative reject the peak
			//but we can have negative height p[0] or baseline p[1]
			if (p[2] < 0 || p[3] < 0 || p[4] < 0) {
				peak.setNotValid();
			}
			
			double Rsquared = -1;
			if (peak.isValid() && RsquaredMin > 0) {
				Gaussian2D gauss = new Gaussian2D(p);
				Rsquared = calcR2(gauss, imp);
				if (Rsquared <= RsquaredMin)
					peak.setNotValid();
			}
			
			if (peak.isValid()) {
				peak.setValues(p);
				peak.setRsquared(Rsquared);
				newList.add(peak);
			}
		}
		return newList;
	}
	
	private double calcR2(Gaussian2D gauss, ImageProcessor imp) {
		double SSres = 0;
		double SStot = 0;
		double mean = 0;
		double count = 0;
		
		Rectangle roi = imp.getRoi();
		
		//First determine mean
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				mean += (double)getPixelValue(imp, x, y, rect);
				count++;
			}
		}
		
		mean = mean/count;
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				double value = (double)getPixelValue(imp, x, y, rect);
				SStot += (value - mean)*(value - mean);
				
				double prediction = gauss.getValue(x, y);
				SSres += (value - prediction)*(value - prediction);
			}
		}
		
		return 1 - SSres / SStot;
	}
	
	private static float getPixelValue(ImageProcessor proc, int x, int y, Rectangle subregion) {
		//First for x if needed
		if (x < subregion.x) {
			int before = subregion.x - x;
			if (before > subregion.width)
				before = subregion.width - before % subregion.width;
			x = subregion.x + before - 1;
		} else if (x > subregion.x + subregion.width - 1) {
			int beyond = x - (subregion.x + subregion.width - 1);
			if (beyond > subregion.width)
				beyond = subregion.width - beyond % subregion.width;
			x = subregion.x + subregion.width - beyond; 
		}
			
		//Then for y
		if (y < subregion.y) {
			int before = subregion.y - y;
			if (before > subregion.height)
				before = subregion.height - before % subregion.height;
			y = subregion.y + before - 1;
		} else if (y > subregion.y + subregion.height - 1) {
			int beyond = y - (subregion.y + subregion.height - 1);
			if (beyond > subregion.height)
				beyond = subregion.height - beyond % subregion.height;
			y = subregion.y + subregion.height - beyond;  
		}
		
		return proc.getf(x, y);
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
			image.setSlice(previewSlice);
			image.deleteRoi();
			ImagePlus selectedImage = new ImagePlus("current slice", image.getImageStack().getProcessor(image.getCurrentSlice()));
			ArrayList<Peak> peaks = findPeaks(selectedImage);
			
			final MutableModuleItem<String> preSliceCount = getInfo().getMutableInput("slicePeakCount", String.class);
			if (!peaks.isEmpty()) {
				Polygon poly = new Polygon();
				
				for (Peak p: peaks) {
					int x = (int)p.getDoublePosition(0);
					int y = (int)p.getDoublePosition(1);
					poly.addPoint(x, y);
				}
				
				PointRoi peakRoi = new PointRoi(poly);
				image.setRoi(peakRoi);
				
				
				preSliceCount.setValue(this, "count: " + peaks.size());
			} else {
				preSliceCount.setValue(this, "count: 0");
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
		builder.addParameter("Use Dog Filter", String.valueOf(useDogFilter));
		builder.addParameter("Dog Filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum Distance", String.valueOf(minimumDistance));
		builder.addParameter("Find Negative Peaks", String.valueOf(findNegativePeaks));
		builder.addParameter("Generate peak count table", String.valueOf(generatePeakCountTable));
		builder.addParameter("Generate peak table", String.valueOf(generatePeakTable));
		builder.addParameter("Add to RoiManger", String.valueOf(addToRoiManger));
		builder.addParameter("Process all slices", String.valueOf(allSlices));
		builder.addParameter("Fit peaks", String.valueOf(fitPeaks));
		builder.addParameter("Fit Radius", String.valueOf(fitRadius));
		builder.addParameter("Minimum R2", String.valueOf(RsquaredMin));
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
	
	public void setUseDogFiler(boolean useDogFilter) {
		this.useDogFilter = useDogFilter;
	}
	
	public void setDogFilterRadius(double dogFilterRadius) {
		this.dogFilterRadius = dogFilterRadius;
	}

	public void setThreshold(double threshold) {
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
	
	public void setMinimumRsquared(double Rsquared) {
		this.RsquaredMin = Rsquared;
	}
	
	public double getMinimumRsquared() {
		return RsquaredMin;
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
	
	public void setVerboseFitOutput(boolean PeakFitter_writeEverything) {
		this.PeakFitter_writeEverything = PeakFitter_writeEverything;
	}
	
	public boolean getVerboseFitOutput() {
		return PeakFitter_writeEverything;
	}
}
