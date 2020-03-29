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
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import org.scijava.module.MutableModuleItem;
import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.ImageProcessing.Peak;
import de.mpg.biochem.mars.ImageProcessing.PeakFinder;
import de.mpg.biochem.mars.ImageProcessing.PeakFitter;
import de.mpg.biochem.mars.ImageProcessing.PeakTracker;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import net.imagej.ops.Initializable;
import org.scijava.table.DoubleColumn;
import io.scif.img.IO;
import io.scif.img.ImgIOException;

import java.awt.image.ColorModel;
import java.awt.Color;
import java.awt.Font;
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

@Plugin(type = Command.class, label = "Peak Tracker", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image Processing", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Peak Tracker", weight = 10, mnemonic = 'd')})
public class FinderFitterTrackerCommand<T extends RealType< T >> extends DynamicCommand implements Command, Initializable {
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
	    private MoleculeArchiveService moleculeArchiveService;
		
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
		
		@Parameter(label="Find negative peaks")
		private boolean findNegativePeaks = false;
		
		//PEAK FITTER
		@Parameter(visibility = ItemVisibility.MESSAGE)
		private final String fitterTitle =
			"Peak fitter settings:";
		
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
		
		//Maximum allow error for the fitting process
		@Parameter(label="Max Error Baseline")
		private double PeakFitter_maxErrorBaseline = 50;
		
		@Parameter(label="Max Error Height")
		private double PeakFitter_maxErrorHeight = 50;
		
		@Parameter(label="Max Error X")
		private double PeakFitter_maxErrorX = 0.2;
		
		@Parameter(label="Max Error Y")
		private double PeakFitter_maxErrorY = 0.2;
		
		@Parameter(label="Max Error Sigma")
		private double PeakFitter_maxErrorSigma = 0.2;
		
		@Parameter(label="Verbose fit output")
		private boolean PeakFitter_writeEverything = true;
		
		//PEAK TRACKER
		@Parameter(visibility = ItemVisibility.MESSAGE)
		private final String trackerTitle =
			"Peak tracker settings:";
		
		//@Parameter(label="Check Max Difference Baseline")
		private boolean PeakTracker_ckMaxDifferenceBaseline = false;
		
		//@Parameter(label="Max Difference Baseline")
		private double PeakTracker_maxDifferenceBaseline = 5000;
		
		//@Parameter(label="Check Max Difference Height")
		private boolean PeakTracker_ckMaxDifferenceHeight = false;
		
		//@Parameter(label="Max Difference Height")
		private double PeakTracker_maxDifferenceHeight = 5000;
		
		@Parameter(label="Max Difference X")
		private double PeakTracker_maxDifferenceX = 1;
		
		@Parameter(label="Max Difference Y")
		private double PeakTracker_maxDifferenceY = 1;
		
		//@Parameter(label="Check Max Difference Sigma")
		private boolean PeakTracker_ckMaxDifferenceSigma = false;
		
		//@Parameter(label="Max Difference Sigma")
		private double PeakTracker_maxDifferenceSigma = 1;
		
		@Parameter(label="Max Difference Slice")
		private int PeakTracker_maxDifferenceSlice = 1;

		@Parameter(label="Min trajectory length")
		private int PeakTracker_minTrajectoryLength = 100;
		
		@Parameter(visibility = ItemVisibility.MESSAGE)
		private final String integrationTitle =
			"Peak integration settings:";
		
		@Parameter(label="Integrate")
		private boolean integrate = false;
		
		@Parameter(label="Inner radius")
		private int integrationInnerRadius = 1;
		
		@Parameter(label="Outer radius")
		private int integrationOuterRadius = 3;
		
		@Parameter(label="Microscope")
		private String microscope;
		
		@Parameter(label="Format", choices = { "None", "MicroManager", "NorPix"})
		private String imageFormat;
		
		//OUTPUT PARAMETERS
		@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
		private SingleMoleculeArchive archive;
		
		//instance of a PeakFinder to use for all the peak finding operations by passing an image and getting back a peak list.
		private PeakFinder finder;
		
		//instance of a PeakFitter to use for all the peak fitting operations by passing an image and pixel index list and getting back subpixel fits..
		private PeakFitter fitter;
		
		//instance of a PeakTracker used for linking all the peaks between detected in each frame.
		private PeakTracker tracker;
		
		//A map with peak lists for each slice for an image stack
		private ConcurrentMap<Integer, ArrayList<Peak>> PeakStack;
		
		//A map that will hold all the metadata from individual frames as they are processed
		//this will contain the 'label' information from each image header
		private ConcurrentMap<Integer, String> metaDataStack;
		
		//box region for analysis added to the image.
		private Rectangle rect;
		private Roi startingRoi;
		//private String[] filenames;
		
		//For the progress thread
		private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
		
		//array for max error margins.
		private double[] maxDifference;
		private double[] maxError;
		private boolean[] ckMaxDifference;
		private boolean[] vary;
		
		//For peak integration
		private ArrayList<int[]> innerOffsets;
		private ArrayList<int[]> outerOffsets;
		
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
			
			//Check that imageFormat setting is correct...
			String metaDataLogMessage = "";
			String label = image.getStack().getSliceLabel(1);
			if (label == null) {
				metaDataLogMessage = "No ImageMetadata was found in image header so the format was switched to None.";
    			imageFormat = "None";
    		} else if (imageFormat.equals("MicroManager") && label.indexOf("{") == -1) {
    			metaDataLogMessage = "Images did not appear to have a MicroManager image format so the format was switched to None.";
    			imageFormat = "None";
			} else if (imageFormat.equals("NorPix") && label.indexOf("DateTime: ") == -1) {
				metaDataLogMessage = "Images did not appear to have a NorPix image format so the format was switched to None.";
    			imageFormat = "None";
			}
			
			if (useDiscoidalAveragingFilter) {
		    	finder = new PeakFinder< T >(threshold, minimumDistance, DS_innerRadius, DS_outerRadius, findNegativePeaks);
		    } else {
		    	finder = new PeakFinder< T >(threshold, minimumDistance, findNegativePeaks);
		    }
			
			vary = new boolean[5];
			vary[0] = PeakFitter_varyBaseline;
			vary[1] = PeakFitter_varyHeight;
			vary[2] = true;
			vary[3] = true;
			vary[4] = PeakFitter_varySigma;
			
			fitter = new PeakFitter(vary);
			
			if (integrate)
				BuildOffsets();
			
			maxError = new double[5];
			maxError[0] = PeakFitter_maxErrorBaseline;
			maxError[1] = PeakFitter_maxErrorHeight;
			maxError[2] = PeakFitter_maxErrorX;
			maxError[3] = PeakFitter_maxErrorY;
			maxError[4] = PeakFitter_maxErrorSigma;
			
			//Build log
			LogBuilder builder = new LogBuilder();
			
			String log = LogBuilder.buildTitleBlock("Peak Tracker");
			
			addInputParameterLog(builder);
			log += builder.buildParameterList();
			if (!metaDataLogMessage.equals("")) {
				log += metaDataLogMessage + "\n";
			}
			
			PeakStack = new ConcurrentHashMap<>(image.getStackSize());
			
			metaDataStack = new ConcurrentHashMap<>(image.getStackSize());
			
			//Need to determine the number of threads
			final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
			
			ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
			
			//Output first part of log message...
			logService.info(log);
			
			double starttime = System.currentTimeMillis();
			logService.info("Finding and Fitting Peaks...");
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
		        forkJoinPool.submit(() -> IntStream.rangeClosed(1, image.getStackSize()).parallel().forEach(i -> { 
		        	ArrayList<Peak> peaks = findPeaksInSlice(i);
		        	//Don't add to stack unless peaks were detected.
		        	if (peaks.size() > 0)
		        		PeakStack.put(i, peaks);
		        })).get();
		        
		        progressUpdating.set(false);
		        
		        statusService.showStatus(1, 1, "Peak search for " + image.getTitle() + " - Done!");
		        
		   } catch (InterruptedException | ExecutionException e) {
		        // handle exceptions
		    	e.printStackTrace();
				logService.info(LogBuilder.endBlock(false));
		   } finally {
		      forkJoinPool.shutdown();
		   }
		    
		    logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		    
		    //Now we have filled the PeakStack with all the peaks found and fitted for each slice and we need to connect them using the peak tracker
		    maxDifference = new double[6]; 
		    maxDifference[0] = PeakTracker_maxDifferenceBaseline;
		    maxDifference[1] = PeakTracker_maxDifferenceHeight;
		    maxDifference[2] = PeakTracker_maxDifferenceX;
			maxDifference[3] = PeakTracker_maxDifferenceY;
			maxDifference[4] = PeakTracker_maxDifferenceSigma;
			maxDifference[5] = PeakTracker_maxDifferenceSlice;
			
			ckMaxDifference = new boolean[3];
			ckMaxDifference[0] = PeakTracker_ckMaxDifferenceBaseline;
			ckMaxDifference[1] = PeakTracker_ckMaxDifferenceHeight;
			ckMaxDifference[2] = PeakTracker_ckMaxDifferenceSigma;
		    
		    tracker = new PeakTracker(maxDifference, ckMaxDifference, minimumDistance, PeakTracker_minTrajectoryLength, integrate, PeakFitter_writeEverything, logService);
		    
		    //Let's make sure we create a unique archive name...
		    //I guess this is already taken care of in the service now...
		    //but it doesn't hurt to leave it for the moment.
		    String newName = "archive";
		    int num = 1;
		    while (moleculeArchiveService.getArchive(newName + ".yama") != null) {
		    	newName = "archive" + num;
		    	num++;
		    }
		    
		    archive = new SingleMoleculeArchive(newName + ".yama");
		    
		    SdmmImageMetadata metaData = new SdmmImageMetadata(image, microscope, imageFormat, metaDataStack);
			archive.putMetadata(metaData);
		    
		    tracker.track(PeakStack, archive);
		    
		    //Reorder Molecules to Natural Order, so there is a common order
		    //if we have to recover..
		    archive.naturalOrderSortMoleculeIndex();
		    
		    //Make sure the output archive has the correct name
			getInfo().getMutableOutput("archive", SingleMoleculeArchive.class).setLabel(archive.getName());
		    
			image.setRoi(startingRoi);
	        
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			statusService.showProgress(1, 1);
			
			logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
			if (archive.getNumberOfMolecules() == 0) {
				logService.info("No molecules found. Maybe there is a problem with your settings");
				archive = null;
				logService.info(LogBuilder.endBlock(false));
			} else {
				logService.info(LogBuilder.endBlock(true));
	
				log += "\n" + LogBuilder.endBlock(true);
				archive.addLogMessage(log);
				archive.addLogMessage("   ");	
			}
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
		
		private ArrayList<Peak> findPeaksInSlice(int slice) {
			ImageStack stack = image.getImageStack();
			ImageProcessor processor = stack.getProcessor(slice);
			
			//IT IS REALLY IMPORTANT THAT THE LABEL IS RETRIEVED 
			//RIGHT AFTER RUNNING GET PROCESSOR ON THE SAME SLICE
			//The get processor method will load in all the info
			//Then the getSliceLabel will retreive it
			//If you are working in virtual memtory the slice label won't be set properly until
			//the get Processor method has been run...
			
			//First we add the header information to the metadata map 
			//to generate the metadata table later in the molecule archive.
			if (!imageFormat.equals("None")) {
				String label = stack.getSliceLabel(slice);
				metaDataStack.put(slice, label);
			}
			
			//Now we do the peak search and find all peaks and fit them for the current slice and return the result
			//which will be put in the concurrentHashMap PeakStack above with the slice as the key.
			ArrayList<Peak> peaks = fitPeaks(processor, findPeaks(new ImagePlus("slice " + slice, processor), slice));
			
			//After fitting some peaks may have moved within the mininmum distance
			//So we remove these always favoring the ones having lower fit error in x and y
			peaks = removeNearestNeighbors(peaks);
			
			return peaks;
		}

		public ArrayList<Peak> findPeaks(ImagePlus imp, int slice) {
			ArrayList<Peak> peaks;
			
			if (useROI) {
		    	peaks = finder.findPeaks(imp, new Roi(x0, y0, width, height), slice);
			} else {
				peaks = finder.findPeaks(imp, slice);
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
				
				fitter.fitPeak(imp, p, e);
				
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
				
				//Force all peaks to be valid... If we are not checking error
				if (!PeakFitter_maxErrorFilter)
					peak.setValid();
				
				if (peak.isValid()) {
					peak.setValues(p);
					peak.setErrorValues(e);
					
					//Integrate intensity
					if (integrate) {
						//I think they need to be shifted for just the integration step. Otherwise, leave them.
						double[] intensity = integratePeak(imp, (int)(peak.getX() + 0.5), (int)(peak.getY() + 0.5), rect);
						peak.setIntensity(intensity[0]);
					}
					
					newList.add(peak);
				}
			}
			return newList;
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
		
		//Infinite Mirror images to prevent out of bounds issues
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
			
			//Sort the list from lowest to highest XYErrors
			Collections.sort(peakList, new Comparator<Peak>(){
				@Override
				public int compare(Peak o1, Peak o2) {
					return Double.compare(o1.getXError() + o1.getYError(), o2.getXError() + o2.getYError());		
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
		
		private void addInputParameterLog(LogBuilder builder) {
			builder.addParameter("Image Title", image.getTitle());
			if (image.getOriginalFileInfo() != null && image.getOriginalFileInfo().directory != null) {
				builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
			}
			builder.addParameter("useROI", String.valueOf(useROI));
			builder.addParameter("ROI x0", String.valueOf(x0));
			builder.addParameter("ROI y0", String.valueOf(y0));
			builder.addParameter("ROI w", String.valueOf(width));
			builder.addParameter("ROI h", String.valueOf(height));
			builder.addParameter("useDiscoidalAveragingFilter", String.valueOf(useDiscoidalAveragingFilter));
			builder.addParameter("DS_innerRadius", String.valueOf(DS_innerRadius));
			builder.addParameter("DS_outerRadius", String.valueOf(DS_outerRadius));
			builder.addParameter("Threshold", String.valueOf(threshold));
			builder.addParameter("Minimum Distance", String.valueOf(minimumDistance));
			builder.addParameter("Find Negative Peaks", String.valueOf(findNegativePeaks));
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
			builder.addParameter("Check Max Difference Baseline", String.valueOf(PeakTracker_ckMaxDifferenceBaseline));
			builder.addParameter("Max Difference Baseline", String.valueOf(PeakTracker_maxDifferenceBaseline));
			builder.addParameter("Check Max Difference Height", String.valueOf(PeakTracker_ckMaxDifferenceHeight));
			builder.addParameter("Max Difference Height", String.valueOf(PeakTracker_maxDifferenceHeight));
			builder.addParameter("Max Difference X", String.valueOf(PeakTracker_maxDifferenceX));
			builder.addParameter("Max Difference Y", String.valueOf(PeakTracker_maxDifferenceY));
			builder.addParameter("Check Max Difference Sigma", String.valueOf(PeakTracker_ckMaxDifferenceSigma));
			builder.addParameter("Max Difference Sigma", String.valueOf(PeakTracker_maxDifferenceSigma));
			builder.addParameter("Max Difference Slice", String.valueOf(PeakTracker_maxDifferenceSlice));
			builder.addParameter("Min trajectory length", String.valueOf(PeakTracker_minTrajectoryLength));
			builder.addParameter("Microscope", microscope);
			builder.addParameter("Format", imageFormat);
		}
		
		//Getters and Setters
	    public MoleculeArchive<?,?,?> getArchive() {
	    	return archive;
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
		
		public void setCheckMaxDifferenceBaseline(boolean PeakTracker_ckMaxDifferenceBaseline) {
			this.PeakTracker_ckMaxDifferenceBaseline = PeakTracker_ckMaxDifferenceBaseline; 
		}
		
		public boolean getCheckMaxDifferenceBaseline() {
			return PeakTracker_ckMaxDifferenceBaseline; 
		}
		
		public void setMaxDifferenceBaseline(double PeakTracker_maxDifferenceBaseline) {
			this.PeakTracker_maxDifferenceBaseline = PeakTracker_maxDifferenceBaseline; 
		}
		
		public double getMaxDifferenceBaseline() {
			return PeakTracker_maxDifferenceBaseline; 
		}
		
		public void setCheckMaxDifferenceHeight(boolean PeakTracker_ckMaxDifferenceHeight) {
			this.PeakTracker_ckMaxDifferenceHeight = PeakTracker_ckMaxDifferenceHeight; 
		}
		
		public boolean getCheckMaxDifferenceHeight() {
			return PeakTracker_ckMaxDifferenceHeight; 
		}
		
		public void setMaxDifferenceHeight(double PeakTracker_maxDifferenceHeight) {
			this.PeakTracker_maxDifferenceHeight = PeakTracker_maxDifferenceHeight; 
		}
		
		public double getMaxDifferenceHeight() {
			return PeakTracker_maxDifferenceHeight; 
		}
		
		public void setMaxDifferenceX(double PeakTracker_maxDifferenceX) {
			this.PeakTracker_maxDifferenceX = PeakTracker_maxDifferenceX;
		}
		
		public double getMaxDifferenceX() {
			return PeakTracker_maxDifferenceX;
		}
		
		public void setMaxDifferenceY(double PeakTracker_maxDifferenceY) {
			this.PeakTracker_maxDifferenceY = PeakTracker_maxDifferenceY;
		}
		
		public double getMaxDifferenceY() {
			return PeakTracker_maxDifferenceY;
		}
		
		public void setCheckMaxDifferenceSigma(boolean PeakTracker_ckMaxDifferenceSigma) {
			this.PeakTracker_ckMaxDifferenceSigma = PeakTracker_ckMaxDifferenceSigma;
		}

		public boolean getCheckMaxDifferenceSigma() {
			return PeakTracker_ckMaxDifferenceSigma;
		}
		
		public void setMaxDifferenceSigma(double PeakTracker_maxDifferenceSigma) {
			this.PeakTracker_maxDifferenceSigma = PeakTracker_maxDifferenceSigma;
		}
		
		public double getMaxDifferenceSigma() {
			return PeakTracker_maxDifferenceSigma;
		}
		
		public void setMaxDifferenceSlice(int PeakTracker_maxDifferenceSlice) {
			this.PeakTracker_maxDifferenceSlice = PeakTracker_maxDifferenceSlice;
		}
		
		public int getMaxDifferenceSlice() {
			return PeakTracker_maxDifferenceSlice;
		}
		
		public void setMinTrajectoryLength(int PeakTracker_minTrajectoryLength) {
			this.PeakTracker_minTrajectoryLength = PeakTracker_minTrajectoryLength;
		}

		public int getMinTrajectoryLength() {
			return PeakTracker_minTrajectoryLength;
		}
		
		public void setMicroscope(String microscope) {
			this.microscope = microscope;
		}
		
		public String getMicroscope() {
			return microscope;
		}
		
		public void setImageFormat(String imageFormat) {
			this.imageFormat = imageFormat;
		}
		
		public String getImageFormat() {
			return imageFormat;
		}
}
