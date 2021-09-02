/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2021 Karl Duderstadt
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

package de.mpg.biochem.mars.object.commands;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.RealMask;
import net.imglib2.roi.Regions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.convert.ConvertService;
import org.scijava.event.EventService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.image.PeakShape;
import de.mpg.biochem.mars.image.PeakTracker;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.object.ObjectArchive;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import io.scif.Metadata;
import io.scif.img.SCIFIOImgPlus;
import io.scif.ome.OMEMetadata;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.FormatService;
import io.scif.services.TranslatorService;
import loci.common.services.ServiceException;
import ome.units.quantity.Length;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsLength;
import ome.xml.model.enums.handlers.UnitsLengthEnumHandler;
import ome.xml.model.primitives.PositiveInteger;

import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory.Boundary;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.numeric.IntegerType;

import java.util.Iterator;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.geom.real.Polygon2D;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;

import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.type.logic.BitType;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;

@Plugin(type = Command.class, label = "Object Tracker", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Image", weight = 1, mnemonic = 'i'), @Menu(
				label = "Object Tracker", weight = 4, mnemonic = 'o') })
public class ObjectTrackerCommand extends DynamicCommand implements Command,
	Initializable, Previewable
{

	/**
	 * SERVICES
	 */
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
	private EventService eventService;
	
	@Parameter
	private UIService uiService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image to search for Objects")
	private ImageDisplay imageDisplay;

	/**
	 * ROI
	 */
	@Parameter(required = false)
	private RoiManager roiManager;
	
	/**
	 * INPUT SETTINGS
	 */
	
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel, tabbedPaneWidth:450")
	private String inputGroup = "Input";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Input")
	private String inputFigure = "ImageInput.png";
	
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Input, align:center")
	private String imageName = "name";

	@Parameter(label = "Region",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Input", choices = { "whole image",
			"ROI from image", "ROIs from manager" })
	private String region = "whole image";

	@Parameter(label = "Channel", choices = { "a", "b", "c" }, style = "group:Input", persist = false)
	private String channel = "0";
	
	/**
	 * FINDER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String findGroup = "Find";
	
	@Parameter(label = "Use local otsu", style = "group:Find")
	private boolean useLocalOstu = true;

	@Parameter(label = "Otsu radius", style = "group:Find")
	private long otsuRadius = 50;

	@Parameter(label = "Minimum object center separation", style = "group:Find")
	private int minimumDistance = 4;
	
	@Parameter(label = "Use area filter", style = "group:Find")
	private boolean useAreaFilter = true;
	
	@Parameter(label = "Minimum area", style = "group:Find")
	private double minArea = 1;

	/**
	 * FITTER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String contourGroup = "Contour";

	@Parameter(label = "Linear interpolation factor", style = "group:Contour")
	private double interpolationFactor = 1;

	/**
	 * TRACKER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String trackGroup = "Track";
	
	@Parameter(label = "Max ΔX", style = "group:Track")
	private double maxDifferenceX = 1;

	@Parameter(label = "Max ΔY", style = "group:Track")
	private double maxDifferenceY = 1;

	@Parameter(label = "Max ΔT", style = "group:Track")
	private int maxDifferenceT = 1;

	@Parameter(label = "Minimum length", style = "group:Track")
	private int minTrajectoryLength = 100;
	
	/**
	 * OUTPUT SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String outputGroup = "Output";
	
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Output")
	private String outputFigure = "ObjectArchive.png";
	
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Output, align:center")
	private String outputArchiveType = "type: ObjectArchive";
	
	@Parameter(label = "Microscope", style = "group:Output", required = false)
	private String microscope = "unknown";

	@Parameter(label = "Pixel length", style = "group:Output")
	private double pixelLength = 1;

	@Parameter(label = "Pixel units", style = "group:Output", choices = { "pixel", "µm", "nm" })
	private String pixelUnits = "pixel";
	
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Output")
	private final String excludeTitle = "List of time points to exclude (T0, T1-T2, ...)";
	
	@Parameter(label = "Exclude", style = "group:Output", required = false)
	private String excludeTimePointList = "";
	
	@Parameter(label = "Verbose", style = "group:Output")
	private boolean verbose = false;
	
	@Parameter(label = "Metadata UID",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Output", choices = { "unique from dataset",
				"random" })
	private String metadataUIDSource = "random";
	
	/**
	 * THREADS
	 */
	
	@Parameter(label = "Threads", required = false, min = "1", max = "120", style = "group:Output")
	private int nThreads = Runtime.getRuntime().availableProcessors();
	
	/**
	 * PREVIEW SETTINGS
	 */
	
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String previewGroup = "Preview";
	
	@Parameter(label = "Timeout (s)", style = "group:Preview")
	private int previewTimeout = 10;
	
	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
		callback = "previewChanged", style = "group:Preview")
	private boolean preview = false;

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Preview")
	private String tObjectCount = "count: 0";

	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE + ", group:Preview",
		persist = false)
	private int previewT;

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Object Archive", type = ItemIO.OUTPUT)
	private ObjectArchive archive;

	/**
	 * Maps from T to object labels for each Roi
	 */
	private List<ConcurrentMap<Integer, List<Peak>>> objectLabelsStack;

	private PeakTracker tracker;

	private boolean swapZandT = false;
	
	private Roi[] rois;
	private Roi imageRoi;

	private Dataset dataset;
	private ImagePlus image;
	
	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else return;

		if (image.getRoi() != null)
			imageRoi = image.getRoi();
		
		if (dataset != null) {
			final MutableModuleItem<String> imageNameItem = getInfo().getMutableInput(
					"imageName", String.class);
			imageNameItem.setValue(this, dataset.getName());
		}

		final MutableModuleItem<String> channelItems = getInfo().getMutableInput(
			"channel", String.class);
		long channelCount = dataset.getChannels();
		ArrayList<String> channels = new ArrayList<String>();
		for (int ch = 0; ch < channelCount; ch++)
			channels.add(String.valueOf(ch));
		channelItems.setChoices(channels);
		channelItems.setValue(this, String.valueOf(image.getChannel() - 1));

		final MutableModuleItem<Integer> preFrame = getInfo().getMutableInput(
			"previewT", Integer.class);
		if (image.getNFrames() < 2) {
			preFrame.setValue(this, image.getSlice() - 1);
			preFrame.setMaximumValue(image.getStackSize() - 1);
			swapZandT = true;
		}
		else {
			preFrame.setValue(this, image.getFrame() - 1);
			preFrame.setMaximumValue(image.getNFrames() - 1);
		}
	}

	@Override
	public void run() {
		if (dataset == null && image != null)
			dataset = convertService.convert(image, Dataset.class);
		
		if (dataset.dimension(dataset.dimensionIndex(Axes.TIME)) < 2) swapZandT = true;

		if (region.equals("ROI from image")) {
			rois = new Roi[1];
			rois[0] = imageRoi;
		} else if (region.equals("ROIs from manager")) {
			rois = roiManager.getRoisAsArray();
		} else {
			rois = new Roi[1];
			rois[0] = new Roi(new Rectangle(0, 0, (int)dataset.dimension(0), (int)dataset.dimension(1)));
		}
		
		if (image != null) {
			image.deleteRoi();
			image.setOverlay(null);
		}

		// Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Object Tracker");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		objectLabelsStack = new ArrayList<>();

		double starttime = System.currentTimeMillis();
		logService.info("Finding and Fitting Peaks...");

		int zDim = dataset.getImgPlus().dimensionIndex(Axes.Z);
		int zSize = (int) dataset.getImgPlus().dimension(zDim);

		int tDim = dataset.getImgPlus().dimensionIndex(Axes.TIME);
		int tSize = (int) dataset.getImgPlus().dimension(tDim);

		final int frameCount = (swapZandT) ? zSize : tSize;
		
		//build list of timepoints to process...
		List<int[]> excludeTimePoints = new ArrayList<int[]>();
		if (excludeTimePointList.length() > 0) {
			try {
				final String[] excludeArray = excludeTimePointList.split(",");
				for (int i=0; i<excludeArray.length; i++) {
					String[] endPoints = excludeArray[i].split("-");
					int start = Integer.valueOf(endPoints[0].trim());
					int end = (endPoints.length > 1) ? Integer.valueOf(endPoints[1].trim()) : start;
		
					excludeTimePoints.add(new int[] {start, end});
				}
			} catch (NumberFormatException e) {
				logService.info("NumberFormatException encountered when parsing exclude list. Tracking all time points.");
				excludeTimePoints = new ArrayList<int[]>();
			}
		}
		
		for (int i = 0; i < rois.length; i++)
			objectLabelsStack.add(new ConcurrentHashMap<Integer, List<Peak>>());
		
		List<Integer> processTimePoints = new ArrayList<Integer>();
		List<Runnable> tasks = new ArrayList<Runnable>();
		for (int t=0; t<frameCount; t++) {
			boolean processedTimePoint = true;
			for (int index=0; index<excludeTimePoints.size(); index++)
				if (excludeTimePoints.get(index)[0] <= t && t <= excludeTimePoints.get(index)[1]) {
					processedTimePoint = false;
					break;
				}
			
			if (processedTimePoint) {
				processTimePoints.add(t);
				final int theT = t;
				tasks.add(() -> {
					List<List<Peak>> objectLabels = findObjectsInT(Integer.valueOf(channel), theT, rois);
					for (int i = 0; i < rois.length; i++)
						if (objectLabels.get(i).size() > 0) objectLabelsStack.get(i).put(theT, objectLabels.get(i));
				});
			}
		}
 
		MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
			.showStatus(objectLabelsStack.get(0).size(), frameCount, "Finding objects for " + dataset
				.getName()), tasks, nThreads);

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");

		tracker = new PeakTracker(maxDifferenceX, maxDifferenceY, maxDifferenceT,
			minimumDistance, minTrajectoryLength, verbose, logService,
			pixelLength);

		archive = new ObjectArchive("archive.yama");

		MarsOMEMetadata marsOMEMetadata = buildOMEMetadata();

		try {
			UnitsLengthEnumHandler unitshandler = new UnitsLengthEnumHandler();
			Length pixelSize = new Length(pixelLength, UnitsLengthEnumHandler
				.getBaseUnit((UnitsLength) unitshandler.getEnumeration(pixelUnits)));

			marsOMEMetadata.getImage(0).setPixelsPhysicalSizeX(pixelSize);
			marsOMEMetadata.getImage(0).setPixelsPhysicalSizeY(pixelSize);
		}
		catch (EnumerationException e1) {
			e1.printStackTrace();
		}

		archive.putMetadata(marsOMEMetadata);

		for (int i=0; i < rois.length; i++)
			tracker.track(objectLabelsStack.get(i), archive, Integer.valueOf(channel), processTimePoints, nThreads);

		// Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", ObjectArchive.class).setLabel(
			archive.getName());

		if (image != null && imageRoi != null) image.setRoi(imageRoi);

		try {
			Thread.sleep(100);
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		statusService.showProgress(1, 1);

		logService.info("Finished in " + DoubleRounder.round((System
			.currentTimeMillis() - starttime) / 60000, 2) + " minutes.");
		if (archive.getNumberOfMolecules() == 0) {
			logService.info(
				"No molecules found. Maybe there is a problem with your settings");
			archive = null;
			logService.info(LogBuilder.endBlock(false));
		}
		else {
			logService.info(LogBuilder.endBlock(true));

			log += "\n" + LogBuilder.endBlock(true);
			archive.logln(log);
			archive.logln("   ");
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> List<List<Peak>> findObjectsInT(
		int channel, int t, Roi[] processingRois)
	{
		RandomAccessibleInterval<T> img = (swapZandT) ? MarsImageUtils
			.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), t, -1, -1)
			: MarsImageUtils.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), 0,
				channel, t);
			
        List<List<Peak>> objectLabelLists = new ArrayList<List<Peak>>();
		for (int roiIndex = 0; roiIndex < processingRois.length; roiIndex++) {
			Interval interval = Intervals.createMinMax(processingRois[roiIndex].getBounds().x, processingRois[roiIndex].getBounds().y, 
					processingRois[roiIndex].getBounds().x + processingRois[roiIndex].getBounds().width - 1, processingRois[roiIndex].getBounds().y + processingRois[roiIndex].getBounds().height - 1);
	       
	        double[] scaleFactors = new double[] {interpolationFactor, interpolationFactor};
	        NLinearInterpolatorFactory<T> interpolator = new NLinearInterpolatorFactory<T>();
	        
	        Interval newInterval = Intervals.createMinMax(Math.round(interval.min(0) * interpolationFactor), 
	        		Math.round(interval.min(1) * interpolationFactor), Math.round(interval.max(0) * interpolationFactor), Math.round(interval.max(1) * interpolationFactor));
	        
	        IntervalView<T> scaledImg = Views.interval(Views.raster(RealViews.affineReal(
					Views.interpolate(Views.extendMirrorSingle(img), interpolator),
					new Scale(scaleFactors))), newInterval);
	        
	        final RandomAccessibleInterval<BitType> binaryImg = (RandomAccessibleInterval<BitType>) opService.run("create.img", scaledImg, new BitType());
	        
	        if (useLocalOstu) {
				opService.run("threshold.otsu", binaryImg, scaledImg, new HyperSphereShape(this.otsuRadius), 
						new OutOfBoundsMirrorFactory<T, RandomAccessibleInterval<T>>(Boundary.SINGLE));
			} else {
				opService.run("threshold.otsu", binaryImg, scaledImg);
			}
	
			final RandomAccessibleInterval<UnsignedShortType> indexImg = (RandomAccessibleInterval<UnsignedShortType>) opService.run("create.img", binaryImg, new UnsignedShortType());
	        final ImgLabeling<Integer, UnsignedShortType> labeling = new ImgLabeling<>(indexImg);
	
	        opService.run("labeling.cca", labeling, binaryImg, StructuringElement.FOUR_CONNECTED);
	        
	        List<Peak> objects = new ArrayList<Peak>();
			
	        LabelRegions< Integer > regions = new LabelRegions< Integer >(labeling);
	        Iterator< LabelRegion< Integer > > iterator = regions.iterator();
	        while ( iterator.hasNext() ) {
	        	LabelRegion< Integer > region = iterator.next();
	        	Polygon2D poly = opService.geom().contour( region, true );
	        	float[] xPoints = new float[poly.numVertices()];
	        	float[] yPoints = new float[poly.numVertices()];
	        	for (int i=0; i<poly.numVertices(); i++) {
	        		RealLocalizable p = poly.vertex(i);
	        		xPoints[i] = p.getFloatPosition(0);
	        		yPoints[i] = p.getFloatPosition(1);
	        	}
	        	PolygonRoi r = new PolygonRoi( xPoints, yPoints, Roi.POLYGON);
	        	r = new PolygonRoi(r.getInterpolatedPolygon(1, false), Roi.POLYGON);
	        	r = smoothPolygonRoi(r);
	        	r = new PolygonRoi(r.getInterpolatedPolygon(Math.min(2, r.getNCoordinates() * 0.1), false), Roi.POLYGON);
	        	
	        	double[] xs = new double[r.getFloatPolygon().xpoints.length];
	        	double[] ys = new double[r.getFloatPolygon().ypoints.length];
	        	for (int i=0; i< xs.length; i++) {
	        		xs[i] = r.getFloatPolygon().xpoints[i]/interpolationFactor;
	        		ys[i] = r.getFloatPolygon().ypoints[i]/interpolationFactor;
	        	}
	        	
	        	Peak peak = PeakShape.createPeak(xs, ys);
	        	final double area = peak.getShape().area();
	        	peak.setProperty("area", area);
	        	
	        	
	        	if (useAreaFilter) { 
	        		if (area > minArea) 
	        			objects.add(peak);
	        	} else
	        		objects.add(peak);
	        }
	
			objects = MarsImageUtils.removeNearestNeighbors(objects, minimumDistance);
			
			//Set the T for the Peaks
			objects.forEach(p -> p.setT(t));
			
			objectLabelLists.add(objects);
		}
		
		return objectLabelLists;
	}
	
	private PolygonRoi smoothPolygonRoi(PolygonRoi r) {
	    FloatPolygon poly = r.getFloatPolygon();
	    FloatPolygon poly2 = new FloatPolygon();
	    int nPoints = poly.npoints;
	    for (int i = 0; i < nPoints; i += 2) {
	        int iMinus = (i + nPoints - 1) % nPoints;
	        int iPlus = (i + 1) % nPoints;
	        poly2.addPoint((poly.xpoints[iMinus] + poly.xpoints[iPlus] + poly.xpoints[i])/3,
	                (poly.ypoints[iMinus] + poly.ypoints[iPlus] + poly.ypoints[i])/3);
	    }
//				return new PolygonRoi(poly2, r.getType());
	    return new PolygonRoi(poly2, Roi.POLYGON);
	}

	private MarsOMEMetadata buildOMEMetadata() {
		ImgPlus<?> imp = dataset.getImgPlus();

		OMEXMLMetadata omexmlMetadata = null;
		if (!(imp instanceof SCIFIOImgPlus)) {
			logService.info("This image has not been opened with SCIFIO. Creating OME Metadata...");
			try {
				omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService,
					dataset);
			} catch (ServiceException e) {
				e.printStackTrace();
			}
		}
		else {
			Metadata metadata = (Metadata) dataset.getProperties().get(
				"scifio.metadata.global");
			
			OMEMetadata omeMeta = new OMEMetadata(getContext());
			if (!translatorService.translate(metadata, omeMeta, true)) {
				logService.info(
					"Unable to extract OME Metadata. Creating...");
				try {
					omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService,
							dataset);
				} catch (ServiceException e) {
					e.printStackTrace();
				}
			}
			else {
				omexmlMetadata = omeMeta.getRoot();
			}
			
			omexmlMetadata.setImageName(metadata.get(0).getName(), 0);
		}
		
		if (swapZandT) {
			int sizeT = omexmlMetadata.getPixelsSizeT(0).getNumberValue().intValue();
			int sizeZ = omexmlMetadata.getPixelsSizeZ(0).getNumberValue().intValue();
			
			omexmlMetadata.setPixelsSizeT(new PositiveInteger(sizeZ), 0);
			omexmlMetadata.setPixelsSizeZ(new PositiveInteger(sizeT), 0);
		}

		// Ensures that MarsMicromangerFormat correctly sets the ImageID based on
		// the position.
		try {
			if (omexmlMetadata.getDoubleAnnotationCount() > 0 && omexmlMetadata
				.getDoubleAnnotationID(0).equals("ImageID"))
			{
				omexmlMetadata.setImageID("Image:" + omexmlMetadata
					.getDoubleAnnotationValue(0).intValue(), 0);
			}
		}
		catch (NullPointerException e) {
			// Do nothing. Many of the omexmlmetadata methods give
			// NullPointerExceptions
			// if fields are not set.
		}

		String metaUID;
		if (metadataUIDSource.equals("unique from dataset")) {
			metaUID = MarsOMEUtils.generateMetadataUIDfromDataset(omexmlMetadata);
			
			if (metaUID == null) {
				logService.info("Failed to generate unique metadata uid. Using random generated metadata uid.");
			} else metaUID = MarsMath.getUUID58().substring(0, 10);
		} else metaUID = MarsMath.getUUID58().substring(0, 10);

		return new MarsOMEMetadata(metaUID, omexmlMetadata);
	}

	@Override
	public void preview() {
		if (preview) {
			if (swapZandT) image.setSlice(previewT + 1);
			else image.setPosition(Integer.valueOf(channel) + 1, 1, previewT + 1);
			
			ExecutorService es = Executors.newSingleThreadExecutor();
			try {
				es.submit(() -> {
					if (imageRoi == null && image.getRoi() != null)
						imageRoi = image.getRoi();
					
					if (region.equals("ROI from image")) {
						rois = new Roi[1];
						rois[0] = imageRoi;
					} else if (region.equals("ROIs from manager")) {
						rois = roiManager.getRoisAsArray();
					} else {
						rois = new Roi[1];
						rois[0] = new Roi(new Rectangle(0, 0, (int)dataset.dimension(0), (int)dataset.dimension(1)));
					}

					List<List<Peak>> objectLabelLists = findObjectsInT(Integer.valueOf(channel), previewT, rois);
					
					if (Thread.currentThread().isInterrupted())
						return;
		
					int labelCount = 0;
					Overlay overlay = new Overlay();
					for (List<Peak> objectLabels : objectLabelLists) {
						for (Peak p : objectLabels) {
							float[] xs = new float[p.getShape().x.length];
							float[] ys = new float[p.getShape().y.length];
				        	for (int i=0; i< xs.length; i++) {
				        		xs[i] = (float) (p.getShape().x[i] + 0.5);
				        		ys[i] = (float) (p.getShape().y[i] + 0.5);
				        	}
							PolygonRoi r = new PolygonRoi(xs, ys, Roi.POLYGON);
							overlay.add(r);
							
							if (Thread.currentThread().isInterrupted())
								return;
							
							labelCount++;
						}
					}
					
					final String countString = "count: " + labelCount;
					final MutableModuleItem<String> preFrameCount = getInfo().getMutableInput(
							"tObjectCount", String.class);
					preFrameCount.setValue(this, countString);

					SwingUtilities.invokeLater( () -> {
						System.out.println("invoking");
						if (image != null) {
							image.deleteRoi();
							image.setOverlay(overlay);
							System.out.println("set overlay");
							
							for (Window window : Window.getWindows())
								if (window instanceof JDialog && ((JDialog) window).getTitle().equals(getInfo().getLabel()))
									MarsUtil.updateJLabelTextInContainer(((JDialog) window), "count: ", countString);
						}
					});
				}).get(previewTimeout, TimeUnit.SECONDS);
			}
			catch (TimeoutException e1) {
				es.shutdownNow();
				uiService.showDialog(
						"Preview took too long. Try a smaller region, a smaller local radius, or try again with a longer delay before preview timeout.",
						MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
				cancel();
			}
			catch (InterruptedException | ExecutionException e2) {
				es.shutdownNow();
				cancel();
			}
			es.shutdownNow();
		}
	}

	@Override
	public void cancel() {
		if (image != null) {
			image.setOverlay(null);
			image.setRoi(imageRoi);
		}
	}

	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
		if (!preview) cancel();
	}

	private void addInputParameterLog(LogBuilder builder) {
		if (image != null) {
			builder.addParameter("Image Title", image.getTitle());
			if (image.getOriginalFileInfo() != null && image
				.getOriginalFileInfo().directory != null)
			{
				builder.addParameter("Image Directory", image
					.getOriginalFileInfo().directory);
			}
		}
		else builder.addParameter("Dataset Name", dataset.getName());

		builder.addParameter("Region", region);
		if (region.equals("ROI from image") && imageRoi != null) builder.addParameter("ROI from image", imageRoi.toString());
		builder.addParameter("Channel", channel);
		builder.addParameter("Local otsu radius", String.valueOf(otsuRadius));
		builder.addParameter("Minimum distance", String.valueOf(minimumDistance));
		builder.addParameter("Interpolation factor", String.valueOf(interpolationFactor));
		builder.addParameter("Use area filter", String.valueOf(useAreaFilter));
		builder.addParameter("Minimum area", String.valueOf(minArea));
		builder.addParameter("Verbose output", String.valueOf(verbose));
		builder.addParameter("Max difference x", String.valueOf(maxDifferenceX));
		builder.addParameter("Max difference y", String.valueOf(maxDifferenceY));
		builder.addParameter("Max difference T", String.valueOf(maxDifferenceT));
		builder.addParameter("Minimum track length", String.valueOf(
			minTrajectoryLength));
		builder.addParameter("Microscope", microscope);
		builder.addParameter("Pixel Length", String.valueOf(this.pixelLength));
		builder.addParameter("Pixel Units", this.pixelUnits);
		builder.addParameter("Exclude time points", excludeTimePointList);
		builder.addParameter("Swap Z and T", swapZandT);
		builder.addParameter("Thread count", nThreads);
		builder.addParameter("Metadata UID source", metadataUIDSource);
	}

	// Getters and Setters
	public ObjectArchive getArchive() {
		return archive;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public void setImagePlus(ImagePlus image) {
		this.image = image;
	}

	public ImagePlus getImagePlus() {
		return image;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getRegion() {
		return region;
	}
	
	public void setRois(Roi[] rois) {
		this.rois = rois;
		this.region = "ROIs from manager";
	}
	
	public Roi[] getROIs() {
		return this.rois;
	}

	public void setChannel(int channel) {
		this.channel = String.valueOf(channel);
	}

	public int getChannel() {
		return Integer.valueOf(channel);
	}
	
	public void setUseLocalOstu(boolean useLocalOstu) {
		this.useLocalOstu = useLocalOstu;
	}
	
	public boolean getUseLocalOstu() {
		return useLocalOstu;
	}

	public void setLocalOtsuRadius(int otsuRadius) {
		this.otsuRadius = otsuRadius;
	}

	public double getLocalOtsuRadius() {
		return otsuRadius;
	}

	public void setMinimumDistance(int minimumDistance) {
		this.minimumDistance = minimumDistance;
	}

	public int getMinimumDistance() {
		return minimumDistance;
	}
	
	public void setInterpolationFactor(double interpolationFactor) {
		this.interpolationFactor = interpolationFactor;
	}
	
	public double getInterpolationFactor() {
		return this.interpolationFactor;
	}
	
	public void setUseAreaFilter(boolean useAreaFilter) {
		this.useAreaFilter = useAreaFilter;
	}
	
	public boolean getUseAreaFilter() {
		return this.useAreaFilter;
	}
	
	public void setMinimumArea(double minArea) {
		this.minArea = minArea;
	}
	
	public double getMinimumArea() {
		return this.minArea;
	}

	public void setVerboseOutput(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean getVerboseOutput() {
		return verbose;
	}

	public void setMaxDifferenceX(double PeakTracker_maxDifferenceX) {
		this.maxDifferenceX = PeakTracker_maxDifferenceX;
	}

	public double getMaxDifferenceX() {
		return maxDifferenceX;
	}

	public void setMaxDifferenceY(double maxDifferenceY) {
		this.maxDifferenceY = maxDifferenceY;
	}

	public double getMaxDifferenceY() {
		return maxDifferenceY;
	}

	public void setMaxDifferenceT(int maxDifferenceT) {
		this.maxDifferenceT = maxDifferenceT;
	}

	public int getMaxDifferenceT() {
		return maxDifferenceT;
	}

	public void setMinimumTrackLength(int minTrajectoryLength) {
		this.minTrajectoryLength = minTrajectoryLength;
	}

	public int getMinimumTrackLength() {
		return minTrajectoryLength;
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
	
	public void setExcludedTimePointsList(String excludeTimePointList) {
		this.excludeTimePointList = excludeTimePointList;
	}
	
	public String getExcludedTimePointsList() {
		return this.excludeTimePointList;
	}
	
	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}
	
	public int getThreads() {
		return this.nThreads;
	}
	
	public void setMetadataUIDSource(String metadataUIDSource) {
		this.metadataUIDSource = metadataUIDSource;
	}
	
	public String getMetadataUIDSource() {
		return this.metadataUIDSource;
	}
}
