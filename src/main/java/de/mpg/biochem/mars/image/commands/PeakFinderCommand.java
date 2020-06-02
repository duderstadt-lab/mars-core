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
package de.mpg.biochem.mars.image.commands;

import ij.ImagePlus;
import ij.ImageStack;
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
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.RealRect;

import de.mpg.biochem.mars.image.DogPeakFinder;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.image.PeakFitter;
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
import net.imagej.Dataset;
import net.imagej.display.ImageDisplay;
import net.imagej.display.OverlayService;
import net.imglib2.Cursor;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import java.util.Map;
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

@Plugin(type = Command.class, label = "Peak Finder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image", weight = 20,
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
    
    @Parameter
	private ConvertService convertService;
	
	//INPUT IMAGE
    @Parameter(label = "Image to search for Peaks")
	private ImageDisplay imageDisplay;
	
	//ROI SETTINGS
	@Parameter(label="Use ROI", persist=false)
	private boolean useROI = true;
	
	@Parameter(label="ROI x0", persist=false)
	private int x0;
	
	@Parameter(label="ROI y0", persist=false)
	private int y0;
	
	@Parameter(label="ROI width", persist=false)
	private int width;
	
	@Parameter(label="ROI height", persist=false)
	private int height;
	
	@Parameter(label="Channel", choices = {"a", "b", "c"})
	private String channel = "0";
	
	//PEAK FINDER SETTINGS
	@Parameter(label="Use DoG filter")
	private boolean useDogFilter = true;
	
	@Parameter(label="DoG filter radius")
	private double dogFilterRadius = 2;
	
	@Parameter(label="Detection threshold")
	private double threshold = 50;
	
	@Parameter(label="Minimum distance between peaks")
	private int minimumDistance = 4;
	
	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false, callback = "previewChanged")
	private boolean preview = false;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String tPeakCount = "count: 0";
	
	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE)
	private int previewT;
	
	@Parameter(label="Find negative peaks")
	private boolean findNegativePeaks = false;
	
	@Parameter(label="Generate peak count table")
	private boolean generatePeakCountTable;
	
	@Parameter(label="Generate peak table")
	private boolean generatePeakTable;
	
	@Parameter(label="Add to ROIManager")
	private boolean addToRoiManager;
	
	@Parameter(label="Molecule names in ROIManager")
	private boolean moleculeNames;
	
	@Parameter(label="Process all frames")
	private boolean allFrames;
	
	//PEAK FITTER
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String PeakFitterMessage =
		"Peak fitter settings:";
	
	@Parameter(label="Fit peaks")
	private boolean fitPeaks = false;
	
	@Parameter(label="Fit radius")
	private int fitRadius = 4;
	
	@Parameter(label = "Minimum R-squared",
			style = NumberWidget.SLIDER_STYLE, min = "0.00", max = "1.00",
			stepSize = "0.01")
	private double RsquaredMin = 0;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String integrationTitle =
		"Peak integration settings:";
	
	@Parameter(label="Integrate")
	private boolean integrate = false;
	
	@Parameter(label="Inner radius")
	private int integrationInnerRadius = 1;
	
	@Parameter(label="Outer radius")
	private int integrationOuterRadius = 3;
	
	//Which columns to write in peak table
	@Parameter(label="Verbose output")
	private boolean verbose = true;
	
	//OUTPUT PARAMETERS
	@Parameter(label="Peak Count", type = ItemIO.OUTPUT)
	private MarsTable peakCount;
	
	@Parameter(label="Peaks", type = ItemIO.OUTPUT)
	private MarsTable peakTable;
	
	//instance of a PeakFitter to use for all the peak fitting operations by passing an image and pixel index list and getting back subpixel fits..
	private PeakFitter fitter;
	
	//A map with peak lists for each frame for an image stack
	private ConcurrentMap<Integer, ArrayList<Peak>> PeakStack;
	
	//box region for analysis added to the image.
	private Rectangle rect;
	private Roi startingRoi;
	
	//For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);

	public static final String[] TABLE_HEADERS_VERBOSE = {"baseline", "height", "sigma", "R2"};
	
	//For peak integration
	private ArrayList<int[]> innerOffsets;
	private ArrayList<int[]> outerOffsets;
	
	private Dataset dataset;
	private ImagePlus image;
	
	@Override
	public void initialize() {
		dataset = (Dataset) imageDisplay.getActiveView().getData();
		image = convertService.convert(imageDisplay, ImagePlus.class);
		
		if (image.getRoi() == null) {
			rect = new Rectangle(0,0,image.getWidth()-1,image.getHeight()-1);
			final MutableModuleItem<Boolean> useRoifield = getInfo().getMutableInput("useROI", Boolean.class);
			useRoifield.setValue(this, false);
		} else {
			rect = image.getRoi().getBounds();
			startingRoi = image.getRoi();
		}
		
		final MutableModuleItem<String> channelItems = getInfo().getMutableInput("channel", String.class);
		long channelCount = dataset.getChannels();
		ArrayList<String> channels = new ArrayList<String>();
		for (int ch=1; ch<=channelCount; ch++)
			channels.add(String.valueOf(ch - 1));
		channelItems.setChoices(channels);
		channelItems.setValue(this, String.valueOf(image.getChannel() - 1));
		
		final MutableModuleItem<Integer> imgX0 = getInfo().getMutableInput("x0", Integer.class);
		imgX0.setValue(this, rect.x);
		
		final MutableModuleItem<Integer> imgY0 = getInfo().getMutableInput("y0", Integer.class);
		imgY0.setValue(this, rect.y);
		
		final MutableModuleItem<Integer> imgWidth = getInfo().getMutableInput("width", Integer.class);
		imgWidth.setValue(this, rect.width);
		
		final MutableModuleItem<Integer> imgHeight = getInfo().getMutableInput("height", Integer.class);
		imgHeight.setValue(this, rect.height);
		
		final MutableModuleItem<Integer> preFrame = getInfo().getMutableInput("previewT", Integer.class);
		preFrame.setValue(this, image.getFrame() - 1);
		preFrame.setMaximumValue(image.getNFrames() - 1);
	}
	
	@Override
	public void run() {	
		if (useROI) {
			rect = new Rectangle(x0,y0,width - 1,height - 1);
		} else {
			rect = new Rectangle(0,0,image.getWidth()-1,image.getHeight()-1);
		}
		
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
		
		BuildOffsets();
		
		double starttime = System.currentTimeMillis();
		logService.info("Finding Peaks...");
		if (allFrames) {
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
	        		        	statusService.showStatus(PeakStack.size(), image.getNFrames(), "Finding Peaks for " + image.getTitle());
	        		        }
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                    }
		            }
		        };

		        progressThread.start();
		        
		      //This will spawn a bunch of threads that will analyze frames individually in parallel and put the results into the PeakStack map as lists of
		        //peaks with the frame number as a key in the map for each list...
		        forkJoinPool.submit(() -> IntStream.range(0, image.getNFrames()).parallel().forEach(i -> { 
		        	ArrayList<Peak> tpeaks = findPeaksInT(Integer.valueOf(channel), i);
		        	//Don't add to stack unless peaks were detected.
		        	if (tpeaks.size() > 0)
		        		PeakStack.put(i, tpeaks);
		        })).get();
		        	
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
			ImagePlus selectedImage = new ImagePlus("current frame", image.getImageStack().getProcessor(image.getCurrentSlice()));
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
			DoubleColumn frameColumn = new DoubleColumn("T");
			DoubleColumn countColumn = new DoubleColumn("peaks");
			
			if (allFrames) {
				for (int i=1;i <= PeakStack.size() ; i++) {
					frameColumn.addValue(i);
					countColumn.addValue(PeakStack.get(i).size());
				}
			} else {
				frameColumn.addValue(1);
				countColumn.addValue(peaks.size());
			}
			peakCount.add(frameColumn);
			peakCount.add(countColumn);
			
			//Make sure the output table has the correct name
			getInfo().getMutableOutput("peakCount", MarsTable.class).setLabel(peakCount.getName());
		}
		
		if (generatePeakTable) {
			logService.info("Generating peak table..");
			//build a table with all peaks
			peakTable = new MarsTable("Peaks - " + image.getTitle());
			
			Map<String, DoubleColumn> columns = new LinkedHashMap<String, DoubleColumn>();
			
			columns.put("T", new DoubleColumn("T"));
			columns.put("x", new DoubleColumn("x"));
			columns.put("y", new DoubleColumn("y"));
			
			if (integrate) 
				columns.put("Intensity", new DoubleColumn("Intensity"));
			
			if (verbose)
				for (int i=0;i<TABLE_HEADERS_VERBOSE.length;i++)
					columns.put(TABLE_HEADERS_VERBOSE[i], new DoubleColumn(TABLE_HEADERS_VERBOSE[i]));
			
			if (allFrames)
				for (int i=0;i<PeakStack.size() ; i++) {
					ArrayList<Peak> framePeaks = PeakStack.get(i);
					for (int j=0;j<framePeaks.size();j++)
						framePeaks.get(j).addToColumns(columns);
				}
			else
				for (int j=0;j<peaks.size();j++)
					peaks.get(j).addToColumns(columns);
			
			for(String key : columns.keySet())
				peakTable.add(columns.get(key));	
			
			//Make sure the output table has the correct name
			getInfo().getMutableOutput("peakTable", MarsTable.class).setLabel(peakTable.getName());
		}
		
		if (addToRoiManager) {
			logService.info("Adding Peaks to the RoiManger. This might take a while...");
			if (allFrames) {
				//loop through map and frames and add to Manager
				//This is slow probably because of the continuous GUI updating, but I am not sure a solution
				//There is only one add method for the RoiManager and you can only add one Roi at a time.
				int peakNumber = 1;
				for (int i=1;i <= PeakStack.size() ; i++) {
					peakNumber = AddToManager(PeakStack.get(i), Integer.valueOf(channel), i, peakNumber);
				}
			} else {
				AddToManager(peaks, Integer.valueOf(channel), 0);
			}
			statusService.showStatus("Done adding ROIs to Manger");
		}
		image.setRoi(startingRoi);
		
		logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
	}
	
	private void BuildOffsets() {
		innerOffsets = new ArrayList<int[]>();
		outerOffsets = new ArrayList<int[]>();
		
		for (int y = -integrationOuterRadius; y <= integrationOuterRadius; y++) {
			for (int x = -integrationOuterRadius; x <= integrationOuterRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));
	
				if (d <= integrationInnerRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					innerOffsets.add(pos);
				} else if (d <= integrationOuterRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					outerOffsets.add(pos);
				}
			}
		}
	}
	
	private ArrayList<Peak> findPeaksInT(int channel, int frame) {
		ImageStack stack = image.getImageStack();
		int index = image.getStackIndex(channel, 1, frame);
		ImageProcessor processor = stack.getProcessor(index);
		
		ArrayList<Peak> peaks = findPeaks(new ImagePlus("frame " + frame, processor));
		if (peaks.size() == 0)
			return peaks;
		if (fitPeaks) {
			peaks = fitPeaks(processor, peaks);
			peaks = removeNearestNeighbors(peaks);
		}
		return peaks;
	}
	
	private void AddToManager(ArrayList<Peak> peaks, int channel, int frame) {
		AddToManager(peaks, channel, frame, 0);
	}
	
	private int AddToManager(ArrayList<Peak> peaks, int channel, int frame, int startingPeakNum) {
		if (roiManager == null)
			roiManager = new RoiManager();
		int pCount = startingPeakNum;
		if (!peaks.isEmpty()) {
			for (Peak peak : peaks) {
				PointRoi peakRoi = new PointRoi(peak.getDoublePosition(0) + 0.5, peak.getDoublePosition(1) + 0.5);
				if (frame == 0) {
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
				peakRoi.setPosition(channel, 1, frame);
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
			
			double Rsquared = 0;
			if (peak.isValid()) {
				Gaussian2D gauss = new Gaussian2D(p);
				Rsquared = calcR2(gauss, imp);
				if (Rsquared <= RsquaredMin)
					peak.setNotValid();
			}
			
			if (peak.isValid()) {
				peak.setValues(p);
				peak.setRsquared(Rsquared);
				
				//Integrate intensity
				if (integrate) {
					//Type casting from double to int rounds down always, so we have to add 0.5 offset to be correct.
					//Math.round() is be an alternative option...
					double[] intensity = integratePeak(imp, (int)(peak.getX() + 0.5), (int)(peak.getY() + 0.5), rect);
					peak.setIntensity(intensity[0]);
				}
				
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
	
	private double[] integratePeak(ImageProcessor ip, int x, int y, Rectangle region) {
		if (x == Double.NaN || y == Double.NaN) {
			double[] NULLinten = new double[2];
			NULLinten[0] = Double.NaN;
			NULLinten[1] = Double.NaN;
			return NULLinten;
		}
		
		double Intensity = 0;
		int innerPixels = 0;
		ArrayList<Float> outerPixelValues = new ArrayList<Float>();
		
		for (int[] circleOffset: innerOffsets) {
			Intensity += (double)getPixelValue(ip, x + circleOffset[0], y + circleOffset[1], region);
			innerPixels++;
		}
		
		for (int[] circleOffset: outerOffsets) {
			outerPixelValues.add(getPixelValue(ip, x + circleOffset[0], y + circleOffset[1], region));
		}
		
		//Find the Median background value...
		Collections.sort(outerPixelValues);
		double outerMedian;
		if (outerPixelValues.size() % 2 == 0)
		    outerMedian = ((double)outerPixelValues.get(outerPixelValues.size()/2) + (double)outerPixelValues.get(outerPixelValues.size()/2 - 1))/2;
		else
		    outerMedian = (double) outerPixelValues.get(outerPixelValues.size()/2);
		
		Intensity -= outerMedian*innerPixels;
		
		double[] inten = new double[2];
		inten[0] = Intensity;
		inten[1] = outerMedian;

		return inten;
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
		if (peakList.size() < 2)
			return peakList;
		
		//Sort the list from highest to lowest Rsquared
		Collections.sort(peakList, new Comparator<Peak>(){
			@Override
			public int compare(Peak o1, Peak o2) {
				return Double.compare(o2.getRSquared(), o1.getRSquared());		
			}
		});
		
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
			image.setPosition(Integer.valueOf(channel) + 1, 1, previewT + 1);
			image.deleteRoi();
			ImagePlus selectedImage = new ImagePlus("current frame", image.getImageStack().getProcessor(image.getCurrentSlice()));
			ArrayList<Peak> peaks = findPeaks(selectedImage);
			
			final MutableModuleItem<String> preFrameCount = getInfo().getMutableInput("tPeakCount", String.class);
			if (!peaks.isEmpty()) {
				Polygon poly = new Polygon();
				
				for (Peak p: peaks) {
					int x = (int)p.getDoublePosition(0);
					int y = (int)p.getDoublePosition(1);
					poly.addPoint(x, y);
				}
				
				PointRoi peakRoi = new PointRoi(poly);
				image.setRoi(peakRoi);
				
				
				preFrameCount.setValue(this, "count: " + peaks.size());
			} else {
				preFrameCount.setValue(this, "count: 0");
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
		builder.addParameter("Use ROI", String.valueOf(useROI));
		builder.addParameter("ROI x0", String.valueOf(x0));
		builder.addParameter("ROI y0", String.valueOf(y0));
		builder.addParameter("ROI width", String.valueOf(width));
		builder.addParameter("ROI height", String.valueOf(height));
		builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum distance", String.valueOf(minimumDistance));
		builder.addParameter("Find negative peaks", String.valueOf(findNegativePeaks));
		builder.addParameter("Generate peak count table", String.valueOf(generatePeakCountTable));
		builder.addParameter("Generate peak table", String.valueOf(generatePeakTable));
		builder.addParameter("Add to ROIManager", String.valueOf(addToRoiManager));
		builder.addParameter("Process all frames", String.valueOf(allFrames));
		builder.addParameter("Fit peaks", String.valueOf(fitPeaks));
		builder.addParameter("Fit Radius", String.valueOf(fitRadius));
		builder.addParameter("Minimum R-squared", String.valueOf(RsquaredMin));
		builder.addParameter("Integrate", String.valueOf(integrate));
		builder.addParameter("Integration inner radius", String.valueOf(integrationInnerRadius));
		builder.addParameter("Integration outer radius", String.valueOf(integrationOuterRadius));
		builder.addParameter("Verbose output", String.valueOf(verbose));
	}
	
	public MarsTable getPeakCountTable() {
		return peakCount;
	}
	
	public MarsTable getPeakTable() {
		return peakTable;
	}
	
	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}
	
	public Dataset getDataset() {
		return dataset;
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
	
	public void setChannel(int channel) {
		this.channel = String.valueOf(channel);
	}
	
	public int getChannel() {
		return Integer.valueOf(channel);
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
	
	public void setAddToRoiManager(boolean addToRoiManager) {
		this.addToRoiManager = addToRoiManager;
	}
	
	public boolean getAddToRoiManager() {
		return addToRoiManager;
	}

	public void setProcessAllFrames(boolean allFrames) {
		this.allFrames = allFrames;
	}
	
	public boolean getProcessAllFrames() {
		return allFrames;
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
	
	public void setIntegrate(boolean integrate) {
		this.integrate = integrate;
	}
	
	public boolean getIntegrate() {
		return integrate;
	}
	
	public void setIntegrationInnerRadius(int integrationInnerRadius) {
		this.integrationInnerRadius = integrationInnerRadius;
	}
	
	public int getIntegrationInnerRadius() {
		return integrationInnerRadius;
	}
	
	public void setIntegrationOuterRadius(int integrationOuterRadius) {
		this.integrationOuterRadius = integrationOuterRadius;
	}
	
	public int getIntegrationOuterRadius() {
		return integrationOuterRadius;
	}
	
	public void setVerboseOutput(boolean verbose) {
		this.verbose = verbose;
	}
	
	public boolean getVerboseOutput() {
		return verbose;
	}
}
