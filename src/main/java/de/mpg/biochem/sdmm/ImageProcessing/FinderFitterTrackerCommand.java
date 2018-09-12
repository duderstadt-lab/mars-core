package de.mpg.biochem.sdmm.ImageProcessing;

import ij.VirtualStack;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.Roi;
import ij.io.Opener;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
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

import de.mpg.biochem.sdmm.molecule.ImageMetaData;
import de.mpg.biochem.sdmm.molecule.MoleculeArchive;
import de.mpg.biochem.sdmm.molecule.MoleculeArchiveService;
import de.mpg.biochem.sdmm.table.ResultsTableService;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.LogBuilder;
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
import net.imagej.table.DoubleColumn;
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

import de.mpg.biochem.sdmm.ImageProcessing.*;

@Plugin(type = Command.class, label = "Peak Tracker", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
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
	    private ResultsTableService resultsTableService;
		
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
		private int w;
		
		@Parameter(label="ROI height", persist=false)
		private int h;
		
		//PEAK FINDER SETTINGS
		@Parameter(label="Use Discoidal Averaging Filter")
		private boolean useDiscoidalAveragingFilter;
		
		@Parameter(label="Inner radius")
		private int DS_innerRadius;
		
		@Parameter(label="Outer radius")
		private int DS_outerRadius;
		
		@Parameter(label="Detection threshold (mean + N * STD)")
		private int threshold;
		
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
		
		@Parameter(label="Check Max Difference Baseline")
		private boolean PeakTracker_ckMaxDifferenceBaseline = false;
		
		@Parameter(label="Max Difference Baseline")
		private double PeakTracker_maxDifferenceBaseline = 5000;
		
		@Parameter(label="Check Max Difference Height")
		private boolean PeakTracker_ckMaxDifferenceHeight = false;
		
		@Parameter(label="Max Difference Height")
		private double PeakTracker_maxDifferenceHeight = 5000;
		
		@Parameter(label="Max Difference X")
		private double PeakTracker_maxDifferenceX = 1;
		
		@Parameter(label="Max Difference Y")
		private double PeakTracker_maxDifferenceY = 1;
		
		@Parameter(label="Check Max Difference Sigma")
		private boolean PeakTracker_ckMaxDifferenceSigma = false;
		
		@Parameter(label="Max Difference Sigma")
		private double PeakTracker_maxDifferenceSigma = 1;
		
		@Parameter(label="Max Difference Slice")
		private int PeakTracker_maxDifferenceSlice = 1;

		@Parameter(label="Min trajectory length")
		private int PeakTracker_minTrajectoryLength = 100;
		
		@Parameter(label="Microscope")
		private String microscope;
		
		@Parameter(label="Format", choices = { "None", "MicroManager", "NorPix"})
		private String imageFormat;
		
		//OUTPUT PARAMETERS
		@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
		private MoleculeArchive archive;
		
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
			
			final MutableModuleItem<Integer> imgWidth = getInfo().getMutableInput("w", Integer.class);
			imgWidth.setValue(this, rect.width);
			
			final MutableModuleItem<Integer> imgHeight = getInfo().getMutableInput("h", Integer.class);
			imgHeight.setValue(this, rect.height);

		}
		@Override
		public void run() {				
			image.deleteRoi();
			
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
			
			maxError = new double[5];
			maxError[0] = PeakFitter_maxErrorBaseline;
			maxError[1] = PeakFitter_maxErrorHeight;
			maxError[2] = PeakFitter_maxErrorX;
			maxError[3] = PeakFitter_maxErrorY;
			maxError[4] = PeakFitter_maxErrorSigma;
			
			//Build log
			LogBuilder builder = new LogBuilder();
			
			String log = builder.buildTitleBlock("Peak Tracker");
			
			addInputParameterLog(builder);
			log += builder.buildParameterList();
			
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
		        
		        statusService.showProgress(100, 100);
		        statusService.showStatus("Peak search for " + image.getTitle() + " - Done!");
		        
		   } catch (InterruptedException | ExecutionException e) {
		        // handle exceptions
		    	e.printStackTrace();
				logService.info(builder.endBlock(false));
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
		    
		    tracker = new PeakTracker(maxDifference, ckMaxDifference, minimumDistance, PeakTracker_minTrajectoryLength, PeakFitter_writeEverything, logService);
		    
		    //Let's make sure we create a unique archive name...
		    String newName = "archive";
		    int num = 1;
		    while (moleculeArchiveService.getArchive(newName + ".yama") != null) {
		    	newName = "archive" + num;
		    	num++;
		    }
		    
		    archive = new MoleculeArchive(newName + ".yama");
		    
		    ImageMetaData metaData = new ImageMetaData(image, microscope, imageFormat, metaDataStack);
			archive.addImageMetaData(metaData);
		    
		    tracker.track(PeakStack, archive);
		    
		    //Reorder Molecules to Natural Order, so there is a common order
		    //if we have to recover..
		    archive.naturalOrderSortMoleculeIndex();
		    
		    //Make sure the output archive has the correct name
			getInfo().getMutableOutput("archive", MoleculeArchive.class).setLabel(archive.getName());
		    
			image.setRoi(startingRoi);
			
			logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
			if (archive.getNumberOfMolecules() == 0) {
				logService.info("No molecules found. Maybe there is a problem with your settings");
				archive = null;
				logService.info(builder.endBlock(false));
			} else {
				logService.info(builder.endBlock(true));
	
				log += builder.endBlock(true);
				archive.addLogMessage(log);
				archive.addLogMessage("   ");			
			}
			statusService.clearStatus();
		}
		
		private ArrayList<Peak> findPeaksInSlice(int slice) {
			//ImageProcessor processor = getProcessor(slice, filenames[slice-1]);
			ImageStack stack = image.getImageStack();
			ImageProcessor processor = stack.getProcessor(slice);
			
			//First we add the header information to the metadata map 
			//to generate the metadata table later in the molecule archive.
			String label = stack.getSliceLabel(slice);
			metaDataStack.put(slice, label);
			
			//Now we do the peak search and find all peaks and fit them for the current slice and return the result
			//which will be put in the concurrentHashMap PeakStack above with the slice as the key.
			ArrayList<Peak> peaks = fitPeaks(processor, findPeaks(new ImagePlus("slice " + slice, processor), slice));
			
			//After fitting some peaks may have moved within the mininmum distance
			//So we remove these always favoring the ones having lower fit error in x and y
			peaks = removeNearestNeighbors(peaks);
			
			return peaks;
		}
		/*
		 * 
		 * 		    
		    
		    int slices = image.getImageStackSize();
		    
		    logService.info("before metadata " + metaDataStack.size());
		    if (metaDataStack.size() < slices) {
		    	//Somehow the metadata parsing did not work
		    	//we have to do it again...
		    	ImageStack stack = image.getImageStack();
		    	for (int i=1;i<=slices;i++) {
					String label = stack.getSliceLabel(i);
					metaDataStack.put(i, label);
		    	}
		    }
		    logService.info("metadata " + metaDataStack.size());
		    
		    
		 * 		  //Let's get a list of the filenames in the VirtualStack...
			//int slices = image.getImageStackSize();
			//filenames = new String[slices];
			//for (int i=0;i<slices;i++) {
			//	filenames[i] = ((VirtualStack)image.getImageStack()).getFileName(i+1);
			//}
		 * 
		public ImageProcessor getProcessor(int slice, String filename) {
			String path = image.getOriginalFileInfo().directory;
			if (path==null) {
	            ImageProcessor ip = new ByteProcessor(image.getWidth(), image.getHeight());
	            label(ip, ""+slice, Color.white);
	            return ip;
	        }
	        Opener opener = new Opener();
	        opener.setSilentMode(true);
	        ImagePlus imp = opener.openImage(path, filename);
	        ImageProcessor ip = null;
	        if (imp!=null) {
	            String info = (String)imp.getProperty("Info");
	            metaDataStack.put(slice, info);
	            ip = imp.getProcessor();
	        } 
	        return ip;
		}
		
		private void label(ImageProcessor ip, String msg, Color color) {
	        int size = image.getHeight()/20;
	        if (size<9) size=9;
	        Font font = new Font("Helvetica", Font.PLAIN, size);
	        ip.setFont(font);
	        ip.setAntialiasedText(true);
	        ip.setColor(color);
	        ip.drawString(msg, size, size*2);
	    }
		*/
		public ArrayList<Peak> findPeaks(ImagePlus imp, int slice) {
			ArrayList<Peak> peaks;
			
			if (useROI) {
		    	peaks = finder.findPeaks(imp, new Roi(x0, y0, w, h), slice);
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
				
				if (peak.isValid()) {
					peak.setValues(p);
					peak.setErrorValues(e);
					newList.add(peak);
				}
			}
			return newList;
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
			builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
			builder.addParameter("useROI", String.valueOf(useROI));
			builder.addParameter("ROI x0", String.valueOf(x0));
			builder.addParameter("ROI y0", String.valueOf(y0));
			builder.addParameter("ROI w", String.valueOf(w));
			builder.addParameter("ROI h", String.valueOf(h));
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
}
