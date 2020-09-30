/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.image.commands;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.gui.Line;
import ij.gui.Overlay;

import org.scijava.module.MutableModuleItem;
import org.apache.commons.math3.stat.regression.SimpleRegression;
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
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import de.mpg.biochem.mars.image.*;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.Gaussian2D;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import net.imagej.Dataset;
import net.imagej.display.ImageDisplay;
import net.imglib2.KDTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;
import org.scijava.table.DoubleColumn;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.img.ImagePlusAdapter;

@Plugin(type = Command.class, label = "DNA Finder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 'm'),
		@Menu(label = "Image", weight = 20,
			mnemonic = 'i'),
		@Menu(label = "DNA Finder", weight = 1, mnemonic = 'd')})
public class DNAFinderCommand<T extends RealType< T >> extends DynamicCommand implements Command, Initializable, Previewable {
	
	//GENERAL SERVICES NEEDED
	@Parameter(required=false)
	private RoiManager roiManager;
	
	@Parameter
	private LogService logService;
	
	@Parameter
	private OpService opService;
	
    @Parameter
    private StatusService statusService;
    
	@Parameter
    private MarsTableService marsTableService;
	
	@Parameter
	private ConvertService convertService;
	
	//INPUT IMAGE
    @Parameter(label = "Image to search for DNAs")
	private ImageDisplay imageDisplay;
	
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
	
	@Parameter(label="Channel", choices = {"a", "b", "c"})
	private String channel = "0";
	
	//DNA FINDER SETTINGS
	
	@Parameter(label="Gaussian Smoothing Sigma")
	private double gaussSigma = 2;
	
	@Parameter(label="Use DoG filter")
	private boolean useDogFilter = true;
	
	@Parameter(label="DoG filter radius")
	private double dogFilterRadius = 2;
	
	@Parameter(label="Detection threshold")
	private double threshold = 50;
	
	@Parameter(label="Minimum distance between edges (in pixels)")
	private int minimumDistance = 6;
	
	@Parameter(label="Optimal DNA length (in pixels)")
	private int optimalDNALength = 38;
	
	@Parameter(label="DNA end search y (in pixels)")
	private int yDNAEndSearchRadius = 6;
	
	@Parameter(label="DNA end search x (in pixels)")
	private int xDNAEndSearchRadius = 5;
	
	@Parameter(label="Filter by median intensity")
	private boolean medianIntensityFilter = false;
	
	@Parameter(label="Median DNA intensity lower bound")
	private int medianIntensityLowerBound = 0;
	
	@Parameter(label="Filter by intensity variance")
	private boolean varianceFilter = false;
	
	@Parameter(label="DNA intensity variance upper bound")
	private double varianceUpperBound = 1_000_000;
	
	@Parameter(label="Fit ends (subpixel localization)")
	private boolean fitPeaks = false;
	
	@Parameter(label="Fit Radius")
	private int fitRadius = 4;
	
	@Parameter(label = "Minimum R-squared",
			style = NumberWidget.SLIDER_STYLE, min = "0.00", max = "1.00",
			stepSize = "0.01")
	private double RsquaredMin = 0;
	
	@Parameter(label = "Preview label type:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { "Median intensity",
					"Variance intensity"})
	private String previewLabelType;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String tDNACount = "count: 0";
	
	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE)
	private int previewT;
	
	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false, callback = "previewChanged")
	private boolean preview = false;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String OutputMessage =
		"Output:";
	
	@Parameter(label="Generate DNA count table")
	private boolean generateDNACountTable;
	
	@Parameter(label="Generate DNA table")
	private boolean generateDNATable;
	
	@Parameter(label="Add to RoiManger")
	private boolean addToRoiManger;
	
	@Parameter(label="Molecule Names in Manager")
	private boolean moleculeNames;
	
	@Parameter(label="Process all Frames")
	private boolean allFrames;
	
	//OUTPUT PARAMETERS
	@Parameter(label="DNA Count", type = ItemIO.OUTPUT)
	private MarsTable DNACount;
	
	@Parameter(label="DNA Table", type = ItemIO.OUTPUT)
	private MarsTable DNATable;
	
	//A map with peak lists for each slice for an image stack
	private ConcurrentMap<Integer, ArrayList<DNASegment>> DNAStack;
	
	//box region for analysis added to the image.
	private Rectangle rect;
	private Roi startingRoi;
	
	//For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	
	private Dataset dataset;
	private ImagePlus image;
	
	private boolean swapZandT = false;

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
		
		if (image.getNFrames() < 2) {
			preFrame.setValue(this, image.getSlice() - 1);
			preFrame.setMaximumValue(image.getStackSize() - 1);
			swapZandT = true;
		} else {
			preFrame.setValue(this, image.getFrame() - 1);
			preFrame.setMaximumValue(image.getNFrames() - 1);
		}
	}
	
	@Override
	public void run() {
		if (useROI) {
			rect = new Rectangle(x0,y0,width - 1,height - 1);
		} else {
			rect = new Rectangle(0,0,image.getWidth()-1,image.getHeight()-1);
		}
		
		image.deleteRoi();
		image.setOverlay(null);
		
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("DNA Finder");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();

		//Output first part of log message...
		logService.info(log);
		
		//Used to store dna list for single frame operations
		ArrayList<DNASegment> DNASegments = new ArrayList<DNASegment>();
		
		//Used to store dna list for multiframe search
		DNAStack = new ConcurrentHashMap<>();
		
		double starttime = System.currentTimeMillis();
		logService.info("Finding DNAs...");
		if (allFrames) {
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
	        		        	if (swapZandT)
	        		        		statusService.showStatus(DNAStack.size(), image.getStackSize(), "Finding DNAs for " + image.getTitle());
	        		        	else
	        		        		statusService.showStatus(DNAStack.size(), image.getNFrames(), "Finding DNAs for " + image.getTitle());
	        		        }
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                    }
		            }
		        };

		        progressThread.start();
		        
		        //This will spawn a bunch of threads that will analyze frames individually in parallel and put the results into the PeakStack map as lists of
		        //peaks with the slice number as a key in the map for each list...
		        if (swapZandT)
		        	forkJoinPool.submit(() -> IntStream.range(0, image.getStackSize()).parallel().forEach(t -> DNAStack.put(t, findDNAsInT(Integer.valueOf(channel), t)))).get();
		        else 
		        	forkJoinPool.submit(() -> IntStream.range(0, image.getNFrames()).parallel().forEach(t -> DNAStack.put(t, findDNAsInT(Integer.valueOf(channel), t)))).get();
		        
		        progressUpdating.set(false);
		        
		        statusService.showProgress(100, 100);
		        statusService.showStatus("DNA search for " + image.getTitle() + " - Done!");
		        
		 } catch (InterruptedException | ExecutionException e) {
		        //handle exceptions
		    	e.printStackTrace();
				logService.info(LogBuilder.endBlock(false));
		 } finally {
		       forkJoinPool.shutdown();
		 }
			
		} else {
			DNASegments = findDNAsInT(Integer.valueOf(channel), previewT);
		}
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		
		if (generateDNACountTable) {
			logService.info("Generating DNA count table..");
			DNACount = new MarsTable("DNA Count - " + image.getTitle());
			DoubleColumn frameColumn = new DoubleColumn("T");
			DoubleColumn countColumn = new DoubleColumn("DNAs");
			
			if (allFrames) {
				for (int i=0;i < DNAStack.size() ; i++) {
					frameColumn.addValue(i);
					countColumn.addValue(DNAStack.get(i).size());
				}
			} else {
				frameColumn.addValue(previewT);
				countColumn.addValue(DNASegments.size());
			}
			DNACount.add(frameColumn);
			DNACount.add(countColumn);
			
			//Make sure the output table has the correct name
			getInfo().getMutableOutput("DNACount", MarsTable.class).setLabel(DNACount.getName());
		}
		
		if (generateDNATable) {
			logService.info("Generating peak table..");
			//build a table with all peaks
			String title = "DNAs Table - " + image.getTitle();
			DNATable = new MarsTable(title, "T", "x1", "y1", "x2", "y2", "length");
			
			if (allFrames) {
				int row = 0;
				for (int t=0;t < DNAStack.size(); t++) {
					ArrayList<DNASegment> tDNAs = DNAStack.get(t);
					for (int j=0;j<tDNAs.size();j++) {
						DNATable.appendRow();
						DNATable.setValue("T", row, t);
						DNATable.setValue("x1", row, tDNAs.get(j).getX1());
						DNATable.setValue("y1", row, tDNAs.get(j).getY1());
						DNATable.setValue("x2", row, tDNAs.get(j).getX2());
						DNATable.setValue("y2", row, tDNAs.get(j).getY2());
						DNATable.setValue("length", row, tDNAs.get(j).getLength());
						DNATable.setValue("median intensity", row, tDNAs.get(j).getMedianIntensity());
						DNATable.setValue("intensity variance", row, tDNAs.get(j).getVariance());
						row++;
					}
				}
			} else {
				int row = 0;
				for (int j=0;j<DNASegments.size();j++) {
					DNATable.appendRow();
					DNATable.setValue("T", row, previewT);
					DNATable.setValue("x1", row, DNASegments.get(j).getX1());
					DNATable.setValue("y1", row, DNASegments.get(j).getY1());
					DNATable.setValue("x2", row, DNASegments.get(j).getX2());
					DNATable.setValue("y2", row, DNASegments.get(j).getY2());
					DNATable.setValue("length", row, DNASegments.get(j).getLength());
					DNATable.setValue("median intensity", row, DNASegments.get(j).getMedianIntensity());
					DNATable.setValue("intensity variance", row, DNASegments.get(j).getVariance());
					row++;
				}
			}
			
			//Make sure the output table has the correct name
			getInfo().getMutableOutput("DNATable", MarsTable.class).setLabel(DNATable.getName());
		}
		
		if (addToRoiManger) {
			logService.info("Adding Peaks to the RoiManger. This might take a while...");
			if (allFrames) {
				//loop through map and slices and add to Manager
				//This is slow probably because of the continuous GUI updating, but I am not sure a solution
				//There is only one add method for the RoiManager and you can only add one Roi at a time.
				int dnaNumber = 1;
				for (int i=0;i < DNAStack.size() ; i++) {
					dnaNumber = AddToManager(DNAStack.get(i), Integer.valueOf(channel), i, dnaNumber);
				}
			} else {
				AddToManager(DNASegments, Integer.valueOf(channel), 0);
			}
			statusService.showStatus("Done adding ROIs to Manger");
		}
		image.setRoi(startingRoi);
		
		logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
	}
	
	private ArrayList<DNASegment> findDNAsInT(int channel, int t) {
		ImageStack stack = image.getImageStack();
		int index = t + 1;
		if (!swapZandT)
			index = image.getStackIndex(channel + 1, 1, t + 1);
		
		ImagePlus tImage = new ImagePlus("T " + t, stack.getProcessor(index));
		return findDNAs(tImage, t);
	}
	
	private ArrayList<DNASegment> findDNAs(ImagePlus tImage, int t) {
		ArrayList<DNASegment> DNASegments = new ArrayList<DNASegment>();
		
		//Bit of ugliness here going from IJ1 to IJ2 and back...
		//Should make an IJ2 peak detector.
		final Img<DoubleType> input = ImagePlusAdapter.wrap( tImage );
		Img<DoubleType> output = opService.create().img(input, new DoubleType());
		int[] derivatives = {0, 1};
		double[] sigma = {gaussSigma, gaussSigma};
		
		opService.filter().derivativeGauss(output, input, derivatives, sigma);
		tImage = ImageJFunctions.wrap(output, "guass filtered");
		
		ArrayList<Peak> positivePeaks = findPeaks(tImage, false);
		ArrayList<Peak> negativePeaks = findPeaks(tImage, true);
		
		if (!positivePeaks.isEmpty() || !negativePeaks.isEmpty()) {
		
			if (fitPeaks) {
				positivePeaks = fitPeaks(tImage.getProcessor(), positivePeaks, false);
				positivePeaks = removeNearestNeighbors(positivePeaks);
				
				negativePeaks = fitPeaks(tImage.getProcessor(), negativePeaks, true);
				negativePeaks = removeNearestNeighbors(negativePeaks);
			}
			
			//make sure they are all valid
			//then we can remove them as we go.
			for (int i=0; i< negativePeaks.size();i++)
					negativePeaks.get(i).setValid();
		
		
			KDTree<Peak> negativePeakTree = new KDTree<Peak>(negativePeaks, negativePeaks);
			RadiusNeighborSearchOnKDTree< Peak > radiusSearch = new RadiusNeighborSearchOnKDTree< Peak >( negativePeakTree );
			
			for (Peak p: positivePeaks) {
				double xTOP = p.getDoublePosition(0);
				double yTOP = p.getDoublePosition(1);
				
				radiusSearch.search(new Peak(xTOP, yTOP + optimalDNALength,0,0,0,0), yDNAEndSearchRadius, true);
				if(radiusSearch.numNeighbors() > 0) {
					Peak bottomEdge = radiusSearch.getSampler(0).get();
					
					double xDiff = Math.abs(bottomEdge.getDoublePosition(0) - p.getDoublePosition(0));
					if (xDiff < xDNAEndSearchRadius && bottomEdge.isValid()) {
						DNASegment segment = new DNASegment(xTOP, yTOP, bottomEdge.getDoublePosition(0), bottomEdge.getDoublePosition(1));
						
						calcSegmentProperties(tImage.getProcessor(), segment);
						
						boolean pass = true;
						
						//Check if the segment passes through filters
						if (varianceFilter && varianceUpperBound < segment.getVariance())
							pass = false;

						if (medianIntensityFilter && medianIntensityLowerBound > segment.getMedianIntensity())
							pass = false;
							
					    if (pass) {	
					    	DNASegments.add(segment);
							bottomEdge.setNotValid();
						}
					}
				}	
			}
		}
		
		return DNASegments;
	}
	
	private void calcSegmentProperties(ImageProcessor currentImage, DNASegment segment) {
		int x1 = (int)segment.getX1();
		int y1 = (int)segment.getY1();
		
		int x2 = (int)segment.getX2();
		int y2 = (int)segment.getY2();
		
		SimpleRegression linearFit = new SimpleRegression(true);
		linearFit.addData(x1, y1);
		linearFit.addData(x2, y2);
		
		//y = A + Bx
		double A = linearFit.getIntercept();
		double B = linearFit.getSlope();
		
		DoubleColumn col = new DoubleColumn("col");
		for (int y=y1; y<=y2; y++) {
			int x = 0;
			//intercept doesn't exist.
			if (x1 == x2)
				x = x1;
			else
				x = (int)((y - A)/B);
			
			if (y < 0 || y >= currentImage.getHeight() || x < 0 || x >= currentImage.getWidth())
				continue;
			
			col.add(Double.valueOf(currentImage.getf(x, y)));
		}
		
		MarsTable table = new MarsTable();
		table.add(col);

		segment.setVariance(table.variance("col"));
		segment.setMedianIntensity((int)table.median("col"));
	}
	
	private void AddToManager(ArrayList<DNASegment> segments, int channel, int t) {
		AddToManager(segments, channel, t, 0);
	}
	
	private int AddToManager(ArrayList<DNASegment> segments, int channel, int t, int startingPeakNum) {
		if (roiManager == null)
			roiManager = new RoiManager();
		int dnaCount = startingPeakNum;
		if (!segments.isEmpty()) {
			for (DNASegment segment : segments) {
				Line line = new Line(segment.getX1() + 0.5, segment.getY1() + 0.5, segment.getX2() + 0.5, segment.getY2() + 0.5);
				if (moleculeNames)
					line.setName("Molecule"+dnaCount);
				else
					line.setName(MarsMath.getUUID58());
				
				if (swapZandT)
					line.setPosition(channel, t + 1, 1);
				else
					line.setPosition(channel, 1, t + 1);
				
				roiManager.addRoi(line);
				dnaCount++;
			}
		}
		return dnaCount;
	}
	
	public ArrayList<Peak> findPeaks(ImagePlus imp, boolean findNegativePeaks) {
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
	
	public ArrayList<Peak> fitPeaks(ImageProcessor imp, ArrayList<Peak> positionList, boolean fitNegativePeaks) {
		ArrayList<Peak> newList = new ArrayList<Peak>();
		
		boolean[] vary = new boolean[5];
		vary[0] = true;
		vary[1] = true;
		vary[2] = true;
		vary[3] = true;
		vary[4] = true;
		
		PeakFitter fitter = new PeakFitter(vary);
		
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
			
			fitter.fitPeak(imp, p, e, fitNegativePeaks);
			
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
	
	@Override
	public void preview() {
		if (preview) {
			image.setOverlay(null);
			image.deleteRoi();
			if (swapZandT || image.getNFrames() < 2) {
				image.setSlice(previewT + 1);
			} else
				image.setPosition(Integer.valueOf(channel) + 1, 1, previewT + 1);
			
			ImagePlus selectedImage = new ImagePlus("current frame", image.getImageStack().getProcessor(image.getCurrentSlice()));
			ArrayList<DNASegment> segments = findDNAs(selectedImage, previewT);
			
			final MutableModuleItem<String> preFrameCount = getInfo().getMutableInput("tDNACount", String.class);
			if (segments.size() > 0) {
				Overlay overlay = new Overlay();
				for (DNASegment segment : segments) {
					Line line = new Line(segment.getX1() + 0.5, segment.getY1() + 0.5, segment.getX2() + 0.5, segment.getY2() + 0.5);
					
					double value = Double.NaN;
					if (previewLabelType.equals("Variance intensity"))
						value = segment.getVariance();
					else if (previewLabelType.equals("Median intensity"))
						value = segment.getMedianIntensity();
					
					if (Double.isNaN(value)) 
						line.setName("");
					if (value > 1_000_000)
						line.setName(DoubleRounder.round(value/1_000_000, 2) + " m");
					else if (value > 1000)
						line.setName(DoubleRounder.round(value/1000, 2) + " k");
					else 
						line.setName((int)value + "");

					overlay.add(line);
				}
				overlay.drawLabels(true);
				overlay.drawNames(true);
				overlay.setLabelColor(new Color(255, 255, 255));
				image.setOverlay(overlay);
				preFrameCount.setValue(this, "count: " + segments.size());
			} else {
				preFrameCount.setValue(this, "count: 0");
			}
		}
	}
	
	@Override
	public void cancel() {
		if (image !=  null) {
			image.setOverlay(null);
			image.setRoi(startingRoi);
		}
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
		builder.addParameter("Gaussian smoothing sigma", String.valueOf(this.gaussSigma));
		builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum Distance", String.valueOf(minimumDistance));
		builder.addParameter("Optimal DNA length", String.valueOf(optimalDNALength));
		builder.addParameter("DNA end search radius y", String.valueOf(yDNAEndSearchRadius));
		builder.addParameter("DNA end search radius x", String.valueOf(xDNAEndSearchRadius));
		builder.addParameter("Filter by median intensity", String.valueOf(medianIntensityFilter));
		builder.addParameter("Median intensity lower bound", String.valueOf(medianIntensityLowerBound));
		builder.addParameter("Filter by Variance", String.valueOf(varianceFilter));
		builder.addParameter("Intensity Variance upper bound", String.valueOf(varianceUpperBound));
		builder.addParameter("Generate peak table", String.valueOf(generateDNATable));
		builder.addParameter("Add to RoiManger", String.valueOf(addToRoiManger));
		builder.addParameter("Process all frames", String.valueOf(allFrames));
		builder.addParameter("Fit peaks", String.valueOf(fitPeaks));
		builder.addParameter("Fit Radius", String.valueOf(fitRadius));
		builder.addParameter("Minimum R-squared", String.valueOf(RsquaredMin));
	}
	
	public MarsTable getDNACountTable() {
		return DNACount;
	}
	
	public MarsTable getDNATable() {
		return DNATable;
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
	
	public void setGaussianSigma(double gaussSigma) {
		this.gaussSigma = gaussSigma;
	}
	
	public double getGaussianSigma() {
		return gaussSigma;
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
	
	public void setOptimalDNALength(int optimalDNALength) {
		this.optimalDNALength = optimalDNALength;
	}
	
	public int getOptimalDNALength() {
		return optimalDNALength;
	}
	
	public void setyDNAEndSearchRadius(int yDNAEndSearchRadius) {
		this.yDNAEndSearchRadius = yDNAEndSearchRadius;
	}
	
	public int getyDNAEndSearchRadius() {
		return yDNAEndSearchRadius;
	}
	
	public void setxDNAEndSearchRadius(int xDNAEndSearchRadius) {
		this.xDNAEndSearchRadius = xDNAEndSearchRadius;
	}
	
	public int getxDNAEndSearchRadius() {
		return xDNAEndSearchRadius;
	}
	
	public void setFilterByVariance(boolean varianceFilter) {
		this.varianceFilter = varianceFilter;
	}
	
	public boolean getFilterByVariance() {
		return this.varianceFilter;
	}
	
	public double getVarianceUpperBound() {
		return varianceUpperBound;
	}
	
	public void setVarianceUpperBound(int varianceUpperBound) {
		this.varianceUpperBound = varianceUpperBound;
	}
	
	public void setFilterByMedianIntensity(boolean varianceFilter) {
		this.varianceFilter = varianceFilter;
	}
	
	public boolean getFilterByMedianIntensity() {
		return varianceFilter;
	}
	
	public void setMedianIntensityLowerBound(int medianIntensityLowerBound) {
		this.medianIntensityLowerBound = medianIntensityLowerBound;
	}
	
	public int getMedianIntensityLowerBound() {
		return medianIntensityLowerBound;
	}
	
	public void setGenerateDNACountTable(boolean generateDNACountTable) {
		this.generateDNACountTable = generateDNACountTable;
	}
	
	public boolean getGenerateDNACountTable() {
		return generateDNACountTable;
	}
	
	public void setGenerateDNATable(boolean generatePeakTable) {
		this.generateDNATable = generatePeakTable;
	}
	
	public boolean getGenerateDNATable() {
		return generateDNATable;
	}
	
	public void setAddToRoiManager(boolean addToRoiManger) {
		this.addToRoiManger = addToRoiManger;
	}
	
	public boolean getAddToRoiManager() {
		return addToRoiManger;
	}

	public void setProcessAllFrames(boolean allFrames) {
		this.allFrames = allFrames;
	}
	
	public boolean getProcessAllSlices() {
		return allFrames;
	}
	
	public void setFitEnds(boolean fitPeaks) {
		this.fitPeaks = fitPeaks;
	}
	
	public boolean getFitEnds() {
		return fitPeaks;
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
	
	//Not sure which ROI library to use at the moment
	//for now we just use this...
	class DNASegment {
		private double x1, y1, x2, y2;
		
		private int medianIntensity;
		
		private double variance;
		
		DNASegment(double x1, double y1, double x2, double y2) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
		}
		
		double getX1() {
			return x1;
		}
		
		double getY1() {
			return y1;
		}
		
		double getX2() {
			return x2;
		}
		
		double getY2() {
			return y2;
		}
		
		void setX1(double x1) {
			this.x1 = x1;
		}
		
		void setY1(double y1) {
			this.y1 = y1;
		}
		
		void setX2(double x2) {
			this.x2 = x2;
		}
		
		void setY2(double y2) {
			this.y2 = y2;
		}
		
		void setMedianIntensity(int medianIntensity) {
			this.medianIntensity = medianIntensity;
		}
		
		int getMedianIntensity() {
			return medianIntensity;
		}
		
		void setVariance(double variance) {
			this.variance = variance;
		}
		
		double getVariance() {
			return variance;
		}
		
		double getLength() {
			return Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));
		}
	}
}
