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
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import org.scijava.module.DefaultMutableModuleItem;
import org.scijava.module.MutableModuleItem;
import org.decimal4j.util.DoubleRounder;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.image.DogPeakFinder;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.image.PeakFitter;
import de.mpg.biochem.mars.image.PeakTracker;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.Gaussian2D;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.Metadata;
import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.filters.AbstractMetadataWrapper;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import io.scif.img.SCIFIOImgPlus;
import io.scif.ome.OMEMetadata;
import io.scif.services.FormatService;
import io.scif.services.TranslatorService;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.display.OverlayService;
import net.imglib2.Cursor;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;

import org.scijava.table.DoubleColumn;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

import io.scif.img.IO;

import java.awt.image.ColorModel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
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

@Plugin(type = Command.class, label = "Peak Tracker", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Peak Tracker", weight = 10, mnemonic = 'p')})
public class PeakTrackerCommand<T extends RealType< T >> extends DynamicCommand implements Command, Initializable {
	//GENERAL SERVICES NEEDED
		@Parameter(required=false)
		private RoiManager roiManager;
		
		@Parameter
		private LogService logService;
		
	    @Parameter
	    private StatusService statusService;
	    
	    @Parameter
	    private TranslatorService translatorService;
	    
	    @Parameter
	    private FormatService formatService;
	    
		@Parameter
	    private MarsTableService resultsTableService;
		
		@Parameter
		private ConvertService convertService;
		
	    @Parameter
	    private OpService opService;
		
		@Parameter
	    private MoleculeArchiveService moleculeArchiveService;
		
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
		private String channel = "1";
		
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
		private String framePeakCount = "count: 0";
		
		@Parameter(label = "Preview frame", min = "1", style = NumberWidget.SCROLL_BAR_STYLE)
		private int previewFrame;
		
		@Parameter(label="Find negative peaks")
		private boolean findNegativePeaks = false;
		
		//PEAK FITTER
		@Parameter(visibility = ItemVisibility.MESSAGE)
		private final String fitterTitle =
			"Peak fitter settings:";
		
		@Parameter(label="Fit radius")
		private int fitRadius = 4;
		
		@Parameter(label = "Minimum R-squared",
				style = NumberWidget.SLIDER_STYLE, min = "0.00", max = "1.00",
				stepSize = "0.01")
		private double RsquaredMin = 0;
		
		@Parameter(label="Verbose output")
		private boolean verbose = false;
		
		//PEAK TRACKER
		@Parameter(visibility = ItemVisibility.MESSAGE)
		private final String trackerTitle =
			"Peak tracker settings:";
		
		@Parameter(label="Max difference x")
		private double PeakTracker_maxDifferenceX = 1;
		
		@Parameter(label="Max difference y")
		private double PeakTracker_maxDifferenceY = 1;
		
		@Parameter(label="Max difference frame")
		private int PeakTracker_maxDifferenceFrame = 1;

		@Parameter(label="Minimum track length")
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
		
		//@Parameter(label="Format", choices = { "None", "MicroManager", "NorPix"})
		//private String imageFormat;
		
		@Parameter
		private UIService uiService;
		
		//OUTPUT PARAMETERS
		@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
		private SingleMoleculeArchive archive;
		
		//instance of a PeakFitter to use for all the peak fitting operations by passing an image and pixel index list and getting back subpixel fits..
		private PeakFitter fitter;
		
		//instance of a PeakTracker used for linking all the peaks between detected in each frame.
		private PeakTracker tracker;
		
		//A map with peak lists for each frame for an image stack
		private ConcurrentMap<Integer, ArrayList<Peak>> PeakStack;
		
		//box region for analysis added to the image.
		private Rectangle rect;
		private Roi startingRoi;
		//private String[] filenames;
		
		//For the progress thread
		private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
		
		//array for max error margins.
		private double[] maxDifference;
		private boolean[] ckMaxDifference;
		
		//For peak integration
		private ArrayList<int[]> innerOffsets;
		private ArrayList<int[]> outerOffsets;
		
		private Dataset dataset;
		private ImagePlus image;
		
		//private MutableModuleItem<String> positionSelection;
		//private MutableModuleItem<String> channelSelection = 
		//		new DefaultMutableModuleItem<String>(this, "Channel", String.class);
		
		@Override
		public void initialize() {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
			
			ImgPlus<?> imp = dataset.getImgPlus();
			
			if (!(imp instanceof SCIFIOImgPlus)) {
				uiService.showDialog("This image has not been opened with SCIFIO.", DialogPrompt.MessageType.ERROR_MESSAGE);
				return;
			}
			
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
				channels.add(String.valueOf(ch));
			channelItems.setChoices(channels);
			channelItems.setValue(this, String.valueOf(image.getChannel()));
		
			//Add positions
			/*
			positionSelection = new DefaultMutableModuleItem<String>(this, "Position", String.class);
			long PositionCount = dataset.getP
			if (channelCount > 1) {
				ArrayList<String> channels = new ArrayList<String>();
				for (int ch=0; ch<channelCount; ch++)
					channels.add(String.valueOf(ch));
				channelSelection.setChoices(channels);
			}
			getInfo().addInput(channelSelection);
			*/
			
			final MutableModuleItem<Integer> imgX0 = getInfo().getMutableInput("x0", Integer.class);
			imgX0.setValue(this, rect.x);
			
			final MutableModuleItem<Integer> imgY0 = getInfo().getMutableInput("y0", Integer.class);
			imgY0.setValue(this, rect.y);
			
			final MutableModuleItem<Integer> imgWidth = getInfo().getMutableInput("width", Integer.class);
			imgWidth.setValue(this, rect.width);
			
			final MutableModuleItem<Integer> imgHeight = getInfo().getMutableInput("height", Integer.class);
			imgHeight.setValue(this, rect.height);
			
			final MutableModuleItem<Integer> preFrame = getInfo().getMutableInput("previewFrame", Integer.class);
			preFrame.setValue(this, image.getCurrentSlice());
			preFrame.setMaximumValue(image.getNFrames());
		}
		@Override
		public void run() {
			if (useROI) {
				rect = new Rectangle(x0,y0,width - 1,height - 1);
			} else {
				rect = new Rectangle(0,0,image.getWidth()-1,image.getHeight()-1);
			}
			
			image.deleteRoi();
			
			//Check that imageFormat setting is correct...
			String metaDataLogMessage = "";
			
			Metadata metadata = (Metadata)dataset.getProperties().get("scifio.metadata.global");
			
			// unpack the metadata to allow for processing...
			while ((metadata instanceof AbstractMetadataWrapper)) {
				metadata = ((AbstractMetadataWrapper) metadata).unwrap();
			}
			
	        OMEMetadata omeMeta = new OMEMetadata(getContext());
	        translatorService.translate(metadata, omeMeta, true);
			
			boolean[] vary = new boolean[5];
			vary[0] = true;
			vary[1] = true;
			vary[2] = true;
			vary[3] = true;
			vary[4] = true;
			
			fitter = new PeakFitter(vary);
			
			BuildOffsets();
			
			//Build log
			LogBuilder builder = new LogBuilder();
			
			String log = LogBuilder.buildTitleBlock("Peak Tracker");
			
			addInputParameterLog(builder);
			log += builder.buildParameterList();
			if (!metaDataLogMessage.equals("")) {
				log += metaDataLogMessage + "\n";
			}
			
			PeakStack = new ConcurrentHashMap<>(image.getStackSize());
			
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
		        //peaks with the frame number as a key in the map for each list...
		        forkJoinPool.submit(() -> IntStream.rangeClosed(1, image.getStackSize()).parallel().forEach(i -> { 
		        	ArrayList<Peak> peaks = findPeaksInFrame(Integer.valueOf(channel), i);
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
		    
		    //Now we have filled the PeakStack with all the peaks found and fitted for each frame and we need to connect them using the peak tracker
		    maxDifference = new double[6]; 
		    maxDifference[0] = Double.NaN;
		    maxDifference[1] = Double.NaN;
		    maxDifference[2] = PeakTracker_maxDifferenceX;
			maxDifference[3] = PeakTracker_maxDifferenceY;
			maxDifference[4] = Double.NaN;
			maxDifference[5] = PeakTracker_maxDifferenceFrame;
			
			ckMaxDifference = new boolean[3];
			ckMaxDifference[0] = false;
			ckMaxDifference[1] = false;
			ckMaxDifference[2] = false;
		    
		    tracker = new PeakTracker(maxDifference, ckMaxDifference, minimumDistance, PeakTracker_minTrajectoryLength, integrate, verbose, logService);
		    
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
		    
		    //SHOULD the Metadata UID be unique for each dataset?? Use dumpXML() but that would be too slow
		    //MarsMath.getFNV1aBase58(str) ...
		    
			archive.putMetadata(new MarsOMEMetadata(MarsMath.getUUID58().substring(0, 10), omeMeta.getRoot()));
		    
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
				archive.logln(log);
				archive.logln("   ");	
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
		
		private ArrayList<Peak> findPeaksInFrame(int channel, int frame) {
			ImageStack stack = image.getImageStack();
			int index = image.getStackIndex(channel, 1, frame);
			ImageProcessor processor = stack.getProcessor(index);

			//Now we do the peak search and find all peaks and fit them for the current frame and return the result
			//which will be put in the concurrentHashMap PeakStack above with the frame as the key.
			ArrayList<Peak> peaks = fitPeaks(processor, findPeaks(new ImagePlus("frame " + frame, processor.duplicate()), frame));
			
			//After fitting some peaks may have moved within the mininmum distance
			//So we remove these always favoring the ones having lower fit error in x and y
			peaks = removeNearestNeighbors(peaks);
			
			return peaks;
		}

		public ArrayList<Peak> findPeaks(ImagePlus imp, int frame) {
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
			    	peaks = finder.findPeaks(dog, Intervals.createMinMax(x0, y0, x0 + width - 1, y0 + height - 1), frame);
				} else {
					peaks = finder.findPeaks(dog, frame);
				}
			} else {
				if (useROI) {
			    	peaks = finder.findPeaks((Img< T >)ImagePlusAdapter.wrap( imp ), Intervals.createMinMax(x0, y0, x0 + width - 1, y0 + height - 1), frame);
				} else {
					peaks = finder.findPeaks((Img< T >)ImagePlusAdapter.wrap( imp ), frame);
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
						//I think they need to be shifted for just the integration step. Otherwise, leave them.
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
				image.setPosition(Integer.valueOf(channel), 1, previewFrame);
				image.deleteRoi();
				ImagePlus selectedImage = new ImagePlus("current frame", image.getImageStack().getProcessor(image.getCurrentSlice()));
				ArrayList<Peak> peaks = findPeaks(selectedImage, previewFrame);
				
				final MutableModuleItem<String> preFrameCount = getInfo().getMutableInput("framePeakCount", String.class);
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
			if (image !=  null)
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
			builder.addParameter("ROI w", String.valueOf(width));
			builder.addParameter("ROI h", String.valueOf(height));
			builder.addParameter("Channel", channel);
			builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
			builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
			builder.addParameter("Threshold", String.valueOf(threshold));
			builder.addParameter("Minimum Distance", String.valueOf(minimumDistance));
			builder.addParameter("Find negative peaks", String.valueOf(findNegativePeaks));
			builder.addParameter("Fit radius", String.valueOf(fitRadius));
			builder.addParameter("Minimum R-squared", String.valueOf(RsquaredMin));
			builder.addParameter("Verbose output", String.valueOf(verbose));
			builder.addParameter("Max difference x", String.valueOf(PeakTracker_maxDifferenceX));
			builder.addParameter("Max difference y", String.valueOf(PeakTracker_maxDifferenceY));
			builder.addParameter("Max difference frame", String.valueOf(PeakTracker_maxDifferenceFrame));
			builder.addParameter("Minimum track length", String.valueOf(PeakTracker_minTrajectoryLength));
			builder.addParameter("Integrate", String.valueOf(integrate));
			builder.addParameter("Integration inner radius", String.valueOf(integrationInnerRadius));
			builder.addParameter("Integration outer radius", String.valueOf(integrationOuterRadius));
			builder.addParameter("Microscope", microscope);
			//builder.addParameter("Format", imageFormat);
		}
		
		//Getters and Setters
	    public MoleculeArchive<?,?,?> getArchive() {
	    	return archive;
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
		
		public void setMinimumRsquared(double Rsquared) {
			this.RsquaredMin = Rsquared;
		}
		
		public double getMinimumRsquared() {
			return RsquaredMin;
		}
		
		public void setVerboseOutput(boolean verbose) {
			this.verbose = verbose;
		}
		
		public boolean getVerboseOutput() {
			return verbose;
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
		
		public void setMaxDifferenceFrame(int PeakTracker_maxDifferenceFrame) {
			this.PeakTracker_maxDifferenceFrame = PeakTracker_maxDifferenceFrame;
		}
		
		public int getMaxDifferenceFrame() {
			return PeakTracker_maxDifferenceFrame;
		}
		
		public void setMinimumTrackLength(int PeakTracker_minTrajectoryLength) {
			this.PeakTracker_minTrajectoryLength = PeakTracker_minTrajectoryLength;
		}

		public int getMinimumTrackLength() {
			return PeakTracker_minTrajectoryLength;
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
		
		public void setMicroscope(String microscope) {
			this.microscope = microscope;
		}
		
		public String getMicroscope() {
			return microscope;
		}
}
