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

import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;
import ome.units.quantity.Time;
import ome.xml.model.enums.handlers.UnitsTimeEnumHandler;

import de.mpg.biochem.mars.image.PeakFinder;
import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.image.PeakFitter;
import de.mpg.biochem.mars.image.PeakTracker;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.Gaussian2D;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
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
import io.scif.ome.services.OMEXMLService;
import io.scif.services.FormatService;
import io.scif.services.TranslatorService;
import io.scif.util.SCIFIOMetadataTools;
import loci.common.services.ServiceException;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.display.ImageDisplay;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

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
import java.util.Map;
import java.util.TimeZone;
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
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsLength;
import ome.xml.model.enums.UnitsTime;
import ome.xml.model.enums.handlers.UnitsLengthEnumHandler;
import ome.xml.model.enums.handlers.UnitsTimeEnumHandler;
import net.imglib2.img.ImagePlusAdapter;

@Plugin(type = Command.class, label = "Peak Tracker", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Peak Tracker", weight = 10, mnemonic = 'p')})
public class PeakTrackerCommand< T extends RealType< T > & NativeType< T >> extends DynamicCommand implements Command, Initializable {
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
	    private OMEXMLService omexmlService;
	    
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
		
		@Parameter(label="Max difference T")
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
		
		@Parameter(label = "Pixel length")
		private double pixelLength = 1;
		
		@Parameter(label = "Pixel units", choices = { "pixel", "µm", "nm"})
		private String pixelUnits = "pixel";
		
		@Parameter(label="Norpix format")
		private boolean norpixFormat = false;
		
		@Parameter
		private UIService uiService;
		
		//OUTPUT PARAMETERS
		@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
		private SingleMoleculeArchive archive;
		
		//instance of a PeakTracker used for linking all the peaks between detected in each frame.
		private PeakTracker tracker;
		
		//A map with peak lists for each frame for an image stack
		private ConcurrentMap<Integer, List<Peak>> PeakStack;
		
		//A map that will hold all Slice information if the image was opened
		//as an IJ1 image.
		private ConcurrentMap<Integer, String> metaDataStack;
		
		//box region for analysis added to the image.
		private Rectangle rect;
		private Interval interval;
		private Roi startingRoi;
		
		//array for max error margins.
		private double[] maxDifference;
		private boolean[] ckMaxDifference;
		
		//For peak integration
		private List<int[]> innerOffsets;
		private List<int[]> outerOffsets;
		
		private Dataset dataset;
		private ImagePlus image;
		
		private MarsOMEMetadata marsOMEMetadata;
		
		private boolean swapZandT = false;
		
		@Override
		public void initialize() {
			if (imageDisplay == null)
				return;
			
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);

			ImgPlus<?> imp = dataset.getImgPlus();
			
			OMEXMLMetadata omexmlMetadata = null;
			if (!(imp instanceof SCIFIOImgPlus)) {
				logService.info("This image has not been opened with SCIFIO.");
				try {
					omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService, dataset);
				} catch (ServiceException e) {
					e.printStackTrace();
				}
			} else {
				//Attempt to read metadata
				Metadata metadata = (Metadata)dataset.getProperties().get("scifio.metadata.global");			
		        OMEMetadata omeMeta = new OMEMetadata(getContext());
		        if (!translatorService.translate(metadata, omeMeta, true)) {
		        	logService.info("Unable to extract OME Metadata. Falling back to IJ1 for metadata.");
		 		} else {
		 			omexmlMetadata = omeMeta.getRoot();
		 		}
			}
			
			//Ensures that MarsMicromangerFormat correctly sets the ImageID based on the position.
			try {
				if (omexmlMetadata.getDoubleAnnotationCount() > 0 && omexmlMetadata.getDoubleAnnotationID(0).equals("ImageID")) {
					omexmlMetadata.setImageID("Image:" + omexmlMetadata.getDoubleAnnotationValue(0).intValue(), 0);
				}
			} catch (NullPointerException e) {
				//Do nothing. May of the omexmlmetadata methods give NullPointerException if fields are not set.
			}
				
			String metaUID;
		    if (omexmlMetadata.getUUID() != null)
		    	metaUID = MarsMath.getUUID58(omexmlMetadata.getUUID()).substring(0, 10);
		    else
		    	metaUID = MarsMath.getUUID58().substring(0, 10);
		    
		    marsOMEMetadata = new MarsOMEMetadata(metaUID, omexmlMetadata);
			
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
			for (int ch=0; ch<channelCount; ch++)
				channels.add(String.valueOf(ch));
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
			
			if (norpixFormat)
				swapZandT = true;
		}
		
		@Override
		public void run() {
			if (useROI) {
				rect = new Rectangle(x0,y0,width - 1,height - 1);
				interval = Intervals.createMinMax(x0, y0, x0 + width - 1, y0 + height - 1);
			} else {
				rect = new Rectangle(0,0,image.getWidth()-1,image.getHeight()-1);
				interval = Intervals.createMinMax(0, 0, image.getWidth()-1,image.getHeight()-1);
			}
			
			image.setOverlay(null);
			image.deleteRoi();
			
			
			//Check that imageFormat setting is correct...
			String metaDataLogMessage = "";
	        
	        UnitsLengthEnumHandler unitshandler = new UnitsLengthEnumHandler();
	        
	        if (swapZandT) {
	        	//Generate new MarsOMEMetadata based on NorpixFormat.
		    	//Flip Z and T and assume a single
	        	OMEXMLMetadata omexmlMetadata = null;
	        	try {
					omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService, dataset);
				} catch (ServiceException e) {
					e.printStackTrace();
				}
	        	String metaUID = MarsMath.getUUID58().substring(0, 10);
				omexmlMetadata.setPixelsSizeX(new PositiveInteger(image.getWidth()) , 0);
				omexmlMetadata.setPixelsSizeY(new PositiveInteger(image.getHeight()) , 0);
				omexmlMetadata.setPixelsSizeZ(new PositiveInteger(1) , 0);
				omexmlMetadata.setPixelsSizeC(new PositiveInteger(1) , 0);
				omexmlMetadata.setPixelsSizeT(new PositiveInteger(image.getStackSize()), 0);
				omexmlMetadata.setPixelsDimensionOrder(DimensionOrder.XYZCT, 0);
				marsOMEMetadata = new MarsOMEMetadata(metaUID, omexmlMetadata);
			}
	        
	        try {
	        	Length pixelSize = new Length(pixelLength, 
					UnitsLengthEnumHandler.getBaseUnit((UnitsLength) unitshandler.getEnumeration(pixelUnits)));
	        
	        	marsOMEMetadata.getImage(0).setPixelsPhysicalSizeX(pixelSize);
	        	marsOMEMetadata.getImage(0).setPixelsPhysicalSizeY(pixelSize);
				
			} catch (EnumerationException e1) {
				e1.printStackTrace();
			}
			
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
			
			//List<Peak> peaks = new ArrayList<>();
			
			innerOffsets = MarsImageUtils.innerIntegrationOffsets(integrationInnerRadius);
			outerOffsets = MarsImageUtils.outerIntegrationOffsets(integrationInnerRadius, integrationOuterRadius);
			
			//Output first part of log message...
			logService.info(log);
			
			double starttime = System.currentTimeMillis();
			logService.info("Finding and Fitting Peaks...");
			
			final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
			
			if (swapZandT)
				MarsUtil.forkJoinPoolBuilder(statusService, logService, 
						() -> statusService.showStatus(PeakStack.size(), image.getStackSize(), "Finding Peaks for " + image.getTitle()), 
						() -> IntStream.range(0, image.getStackSize()).parallel().forEach(t -> { 
				        	List<Peak> tpeaks = findPeaksInT(Integer.valueOf(channel), t);
				        	
				        	if (tpeaks.size() > 0)
				        		PeakStack.put(t, tpeaks);
				        }), PARALLELISM_LEVEL);
			else
				MarsUtil.forkJoinPoolBuilder(statusService, logService, 
						() -> statusService.showStatus(PeakStack.size(), image.getNFrames(), "Finding Peaks for " + image.getTitle()), 
						() -> IntStream.range(0, image.getNFrames()).parallel().forEach(t -> { 
				        	List<Peak> tpeaks = findPeaksInT(Integer.valueOf(channel), t);
				        	
				        	if (tpeaks.size() > 0)
				        		PeakStack.put(t, tpeaks);
				        }), PARALLELISM_LEVEL);
		    
		    logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		    
		    //Now we have filled the PeakStack with all the peaks found and fit them in each frame and we need to connect them using the peak tracker
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
		    
		    tracker = new PeakTracker(maxDifference, ckMaxDifference, minimumDistance, 
		    		PeakTracker_minTrajectoryLength, integrate, verbose, logService, pixelLength);
		    
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
		    if (norpixFormat)
		    	getTimeFromNoprixSliceLabels(marsOMEMetadata, metaDataStack);
			archive.putMetadata(marsOMEMetadata);
		    
		    tracker.track(PeakStack, archive, Integer.valueOf(channel));
		    
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
		
		private List<Peak> findPeaksInT(int channel, int t) {
			ImageStack stack = image.getImageStack();
			int index = t + 1;
			if (!swapZandT)
				index = image.getStackIndex(channel + 1, 1, t + 1);
			
			ImageProcessor processor = stack.getProcessor(index);
			
			//Workaround for IJ1 metadata in slices - Norpix format.
			if (norpixFormat) {
				String label = stack.getSliceLabel(index);
				metaDataStack.put(t, label);
			}

			//ImagePlus im = new ImagePlus("frame " + t + 1, processor.duplicate());
			
			RandomAccessibleInterval< T > img = MarsImageUtils.get2DHyperSlice((ImgPlus< T >) dataset.getImgPlus(), 0, channel, t);
			
			List<Peak> peaks = new ArrayList<Peak>();
			
			if (useDogFilter) {
				RandomAccessibleInterval< FloatType > filteredImg = MarsImageUtils.dogFilter(img, dogFilterRadius, opService);
				peaks = MarsImageUtils.findPeaks(filteredImg, interval, previewT, threshold, minimumDistance, findNegativePeaks);
			} else
				peaks = MarsImageUtils.findPeaks(img, interval, previewT, threshold, minimumDistance, findNegativePeaks);
			
			peaks = MarsImageUtils.fitPeaks(img, peaks, fitRadius, dogFilterRadius, findNegativePeaks, RsquaredMin, interval);
			peaks = MarsImageUtils.removeNearestNeighbors(peaks, minimumDistance);
			
			if (integrate)
				MarsImageUtils.integratePeaks(img, interval, peaks, innerOffsets, outerOffsets);
			
			return peaks;
		}
		
		private void getTimeFromNoprixSliceLabels(MarsMetadata marsMetadata, Map<Integer, String> metaDataStack) {
			try {
				//Set Global Collection Date for the dataset
				int DateTimeIndex1 = metaDataStack.get(0).indexOf("DateTime: ");
				String DateTimeString1 = metaDataStack.get(0).substring(DateTimeIndex1 + 10);
				marsMetadata.getImage(0).setAquisitionDate(getNorPixDate(DateTimeString1));
				
				final UnitsTimeEnumHandler timehandler = new UnitsTimeEnumHandler();
				
				//Extract the exact time of collection of all frames..
				final long t0 = getNorPixMillisecondTime(DateTimeString1);
				
				marsMetadata.getImage(0).planes().forEach(plane -> {
					int dateTimeIndex2 = metaDataStack.get(plane.getT()).indexOf("DateTime: ");
					String DateTimeString2 = metaDataStack.get(plane.getT()).substring(dateTimeIndex2 + 10);
					Time dt = null;
					try {
						double millisecondsDt = ((double) getNorPixMillisecondTime(DateTimeString2) - t0)/1000;
						dt = new Time(millisecondsDt, UnitsTimeEnumHandler.getBaseUnit((UnitsTime) timehandler.getEnumeration("s")));
					} catch (ParseException | EnumerationException e) {
						e.printStackTrace();
					}
					plane.setDeltaT(dt);
				});
			} catch (ParseException e1) {
				//e1.printStackTrace();
			}
		}
		/*
		//Utility methods
		//Generate a unique ID using a hash of all headerlabel information...
		private String generateUID(ConcurrentMap<Integer, String> headerLabels) {
			String allLabels = "";
			for (int i=0;i<headerLabels.size();i++)
				allLabels += headerLabels.get(i);
			
			return MarsMath.getFNV1aBase58(allLabels);
		}
		*/
		//Returns the time when the frame was collected in milliseconds since 1970
		//Makes sure to properly round microsecond information.
		private long getNorPixMillisecondTime(String strTime) throws ParseException {
			SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
			//For the moment we throw-out the microsecond information.
			//String microSecs = strTime.substring(strTime.length() - 3, strTime.length());
			Date convertedDate = formatter.parse(strTime.substring(0, strTime.length() - 4));
			return convertedDate.getTime();// + Double.parseDouble(microSecs)/1000;
		}
		
		//Utility method
		//Returns the Date as a string
		private Timestamp getNorPixDate(String strTime) throws ParseException {
			SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
			Date convertedDate = formatter.parse(strTime.substring(0, strTime.length() - 4));
			//TimeZone tz = TimeZone.getTimeZone("UTC");
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm"); // Quoted "Z" to indicate UTC, no timezone offset
			//df.setTimeZone(tz);
			String nowAsISO = df.format(convertedDate);
			return new Timestamp(nowAsISO);
		}
		
		@Override
		public void preview() {	
			if (preview) {
				if (useROI) {
					interval = Intervals.createMinMax(x0, y0, x0 + width - 1, y0 + height - 1);
				} else {
					interval = Intervals.createMinMax(0, 0, image.getWidth()-1,image.getHeight()-1);
				}
				
				image.setOverlay(null);
				image.deleteRoi();
				if (swapZandT || norpixFormat || image.getNFrames() < 2) {
					image.setSlice(previewT + 1);
				} else
					image.setPosition(Integer.valueOf(channel) + 1, 1, previewT + 1);
				
				RandomAccessibleInterval< T > img = MarsImageUtils.get2DHyperSlice((ImgPlus< T >) dataset.getImgPlus(), 0, Integer.valueOf(channel), previewT);
				
				List<Peak> peaks = new ArrayList<Peak>();
				
				if (useDogFilter) {
					RandomAccessibleInterval< FloatType > filteredImg = MarsImageUtils.dogFilter(img, dogFilterRadius, opService);
					peaks = MarsImageUtils.findPeaks(filteredImg, interval, previewT, threshold, minimumDistance, findNegativePeaks);
				} else
					peaks = MarsImageUtils.findPeaks(img, interval, previewT, threshold, minimumDistance, findNegativePeaks);
				
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
			if (image !=  null) {
				image.setOverlay(null);
				image.setRoi(startingRoi);
			}
		}
		
		/** Called when the {@link #preview} parameter value changes. */
		protected void previewChanged() {
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
			builder.addParameter("Max difference T", String.valueOf(PeakTracker_maxDifferenceFrame));
			builder.addParameter("Minimum track length", String.valueOf(PeakTracker_minTrajectoryLength));
			builder.addParameter("Integrate", String.valueOf(integrate));
			builder.addParameter("Integration inner radius", String.valueOf(integrationInnerRadius));
			builder.addParameter("Integration outer radius", String.valueOf(integrationOuterRadius));
			builder.addParameter("Microscope", microscope);
			builder.addParameter("Pixel Length", String.valueOf(this.pixelLength));
			builder.addParameter("Pixel Units", this.pixelUnits);
			builder.addParameter("Swap Z and T", swapZandT);
			builder.addParameter("Norpix Format", String.valueOf(norpixFormat));
		}
		
		//Getters and Setters
	    public SingleMoleculeArchive getArchive() {
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
		
		public void setPixelLength(double pixelLength) {
			this.pixelLength = pixelLength;
		}
		
		public double getPixelLength() {
			return this.pixelLength;
		}
		
		public void setPixelUnits(String pixelUnits) {
			this.pixelUnits = pixelUnits;
		}
		
		public String getPixelUnits() {
			return this.pixelUnits;
		}
		
		public void setNorpixFormat(boolean norpixFormat) {
			this.norpixFormat = norpixFormat;
		}
		
		public boolean getNorpixFormat() {
			return norpixFormat;
		}
}
