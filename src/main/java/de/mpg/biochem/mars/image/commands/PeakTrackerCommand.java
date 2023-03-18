/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

import io.scif.Metadata;
import io.scif.img.SCIFIOImgPlus;
import io.scif.ome.OMEMetadata;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.FormatService;
import io.scif.services.TranslatorService;

import java.awt.Rectangle;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.RealMask;
import net.imglib2.roi.Regions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import net.imglib2.view.Views;
import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.image.PeakTracker;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
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
import loci.common.services.ServiceException;
import ome.units.quantity.Length;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsLength;
import ome.xml.model.enums.handlers.UnitsLengthEnumHandler;
import ome.xml.model.primitives.PositiveInteger;

@Plugin(type = Command.class, label = "Peak Tracker", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Image", weight = 1, mnemonic = 'i'), @Menu(
				label = "Peak Tracker", weight = 3, mnemonic = 'p') })
public class PeakTrackerCommand extends DynamicCommand implements Command,
	Initializable
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
	private MoleculeArchiveService moleculeArchiveService;

	@Parameter
	private UIService uiService;

	@Parameter
	private DisplayService displayService;

	@Parameter
	private PlatformService platformService;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image to search for peaks")
	private ImageDisplay imageDisplay;

	/**
	 * ROI
	 */
	@Parameter(required = false)
	private RoiManager roiManager;

	/**
	 * INPUT SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "groupLabel, tabbedPaneWidth:460", persist = false)
	private final String inputGroup = "Input";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Input",
		persist = false)
	private final String inputFigure = "ImageInput.png";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Input, align:center", persist = false)
	private final String imageName = "?";

	@Parameter(label = "Region",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Input",
		choices = { "whole image", "ROI from image", "ROIs from manager" }, persist = false)
	private String region = "whole image";

	@Parameter(label = "Channel", choices = { "a", "b", "c" },
		style = "group:Input", persist = false)
	private String channel = "0";

	/**
	 * FINDER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String findGroup = "Find";

	@Parameter(label = "DoG filter", style = "group:Find")
	private boolean useDogFilter = true;

	@Parameter(label = "DoG radius", style = "group:Find")
	private double dogFilterRadius = 2;

	@Parameter(label = "Threshold", style = "group:Find")
	private double threshold = 50;

	@Parameter(label = "Peak separation", style = "group:Find")
	private int minimumDistance = 4;

	@Parameter(label = "Negative peaks", style = "group:Find")
	private boolean findNegativePeaks = false;

	/**
	 * FITTER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String fitGroup = "Fit";

	@Parameter(label = "Radius", style = "group:Fit")
	private int fitRadius = 4;

	@Parameter(label = "R-squared", style = NumberWidget.SLIDER_STYLE +
		", group:Fit", min = "0.00", max = "1.00", stepSize = "0.01")
	private double RsquaredMin = 0;

	/**
	 * TRACKER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String trackGroup = "Track";

	@Parameter(label = "Max ΔX", style = "group:Track")
	private double maxDifferenceX = 1;

	@Parameter(label = "Max ΔY", style = "group:Track")
	private double maxDifferenceY = 1;

	@Parameter(label = "Max ΔT", style = "group:Track")
	private int maxDifferenceT = 1;

	@Parameter(label = "Minimum length", style = "group:Track")
	private int minTrajectoryLength = 100;

	/**
	 * INTEGRATION SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String integrateGroup = "Integrate";

	@Parameter(label = "Integrate", style = "group:Integrate")
	private boolean integrate = false;

	@Parameter(label = "Inner radius", style = "group:Integrate")
	private int integrationInnerRadius = 2;

	@Parameter(label = "Outer radius", style = "group:Integrate")
	private int integrationOuterRadius = 4;

	/**
	 * PROCESS SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String processGroup = "Process";

	@Parameter(label = "Use grid", style = "group:Process")
	private boolean gridProcess = false;

	@Parameter(label = "Horizontal grid regions", style = "group:Process")
	private int horizontalGridRegions = 1;

	@Parameter(label = "Vertical grid regions", style = "group:Process")
	private int verticalGridRegions = 5;

	@Parameter(label = "New virtual archive name", style = "group:Process")
	private final String archiveName = "archive.yama.store";

	@Parameter(label = "New virtual archive location", style="group:Process, directory")
	private File virtualArchiveLocation;

	/**
	 * OUTPUT SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String outputGroup = "Output";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Output")
	private final String outputFigure = "SingleMoleculeArchive.png";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Output, align:center")
	private final String outputArchiveType = "type: SingleMoleculeArchive";

	@Parameter(label = "Microscope", style = "group:Output", required = false)
	private String microscope = "unknown";

	@Parameter(label = "Pixel length", style = "group:Output")
	private double pixelLength = 1;

	@Parameter(label = "Pixel units", style = "group:Output", choices = { "pixel",
		"µm", "nm" })
	private String pixelUnits = "pixel";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Output")
	private final String excludeTitle =
		"List of time points to exclude (T0, T1-T2, ...)";

	@Parameter(label = "Exclude", style = "group:Output", required = false)
	private String excludeTimePointList = "";

	@Parameter(label = "Verbose", style = "group:Output")
	private boolean verbose = false;

	@Parameter(label = "Metadata UID",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Output",
		choices = { "unique from dataset", "random" })
	private String metadataUIDSource = "random";

	/**
	 * THREADS
	 */

	@Parameter(label = "Threads", required = false, min = "1", max = "120",
		style = "group:Output")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	/**
	 * PREVIEW SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String previewGroup = "Preview";

	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
		callback = "previewChanged", style = "group:Preview")
	private final boolean preview = false;

	@Parameter(label = "Roi", style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE +
		", group:Preview", choices = { "circle", "point" })
	private String previewRoiType;

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Preview",
		persist = false)
	private final String tPeakCount = "count: 0";

	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE +
		", group:Preview", persist = false)
	private int previewT;

	@Parameter(label = "Timeout (s)", style = "group:Preview")
	private final int previewTimeout = 10;

	@Parameter(label = "Help",
		description = "View a web page detailing Peak Tracker options",
		callback = "openWebPage", persist = false)
	private Button openWebPage;

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;

	/**
	 * Map from T to IJ1 label metadata string
	 */
	private ConcurrentMap<Integer, String> metaDataStack;

	private PeakTracker tracker;

	private Roi[] inputRois;
	private Roi imageRoi;

	private Dataset dataset;
	private ImagePlus image;
	private boolean swapZandT = false;

	public PeakTrackerCommand() {
	}

	@Override
	public void initialize() {
		if (dataset == null && imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else return;

		if (image.getRoi() != null) {
			imageRoi = image.getRoi();
			region = "ROI from image";
			final MutableModuleItem<String> regionItem = getInfo().getMutableInput(
				"region", String.class);
			regionItem.setValue(this, "ROI from image");
		}

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
		if (image.getNFrames() < 2 && image.getZ() > 1) {
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
		if (dataset == null && image != null) dataset = convertService.convert(
			image, Dataset.class);

		if (dataset.dimension(dataset.dimensionIndex(Axes.TIME)) < 2) swapZandT =
			true;

		if (image != null && imageRoi == null && image.getRoi() != null) imageRoi =
			image.getRoi();

		Roi[] rois = (inputRois != null) ? inputRois : new Roi[1];
		if (inputRois == null)
			if (region.equals("ROI from image")) {
				rois[0] = imageRoi;
			}
			else if (region.equals("ROIs from manager")) {
				rois = roiManager.getRoisAsArray();
			}
			else {
				rois[0] = new Roi(0, 0, (int) dataset.dimension(0),
					(int) dataset.dimension(1));
			}

		if (image != null) {
			image.deleteRoi();
			image.setOverlay(null);
		}

		// Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Peak Tracker");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		List<int[]> excludeTimePoints = excludedTimePointList();

		double starttime = System.currentTimeMillis();

		//We do not grid process int he case of ROIs from manager...
		if (gridProcess && !region.equals("ROIs from manager")) {
			archive = new SingleMoleculeArchive("archive.yama");
			for (int gridH = 0; gridH < horizontalGridRegions; gridH++) {
				for (int gridV = 0; gridV < verticalGridRegions; gridV++) {
					double gridStartTime = System.currentTimeMillis();
					int rx = (int)(rois[0].getXBase() + (rois[0].getFloatWidth()/horizontalGridRegions)*gridH);
					int ry = (int)(rois[0].getYBase() + (rois[0].getFloatHeight()/verticalGridRegions)*gridV);
					int rw = (int)Math.ceil(rois[0].getFloatWidth()/horizontalGridRegions);
					int rh = (int)Math.ceil(rois[0].getFloatHeight()/verticalGridRegions);
					logService.info("Finding and Fitting Peaks in grid region H: " + (gridH + 1) + " of " + horizontalGridRegions
							+ " V: " + (gridV + 1) + " of " + verticalGridRegions + " (" + rx + ", " + ry + ", " + rw + ", " + rh + ")");
					Roi[] gridRois = new Roi[1];
					gridRois[0] = new Roi(rx, ry, rw, rh);
					process(excludeTimePoints, gridRois);
					logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
							gridStartTime) / 60000, 2) + " minutes.");

					if (gridH == 0 && gridV == 0) {
						try {
							File virtualArchiveDirectory = new File(virtualArchiveLocation.getAbsolutePath() + "/" + archiveName);
							archive.saveAsVirtualStore(virtualArchiveDirectory);
							archive = new SingleMoleculeArchive(virtualArchiveDirectory);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				}
			}
			try {
				archive.save();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			logService.info("Finding and Fitting Peaks...");
			archive = new SingleMoleculeArchive("archive.yama");
			process(excludeTimePoints, rois);
		}

		// Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", SingleMoleculeArchive.class).setLabel(
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
			uiService.showDialog(
				"No molecules found. Maybe there is a problem with your settings. " +
					"Make sure molecules are detected using preview. If not try lowering the " +
					"detection threshold.", MessageType.ERROR_MESSAGE,
				OptionType.DEFAULT_OPTION);
		}
		else {
			logService.info(LogBuilder.endBlock(true));

			log += "\n" + LogBuilder.endBlock(true);
			archive.logln(log);
			archive.logln("   ");
		}
	}

	private List<int[]> excludedTimePointList() {
		List<int[]> excludeTimePoints = new ArrayList<int[]>();
		if (excludeTimePointList.length() > 0) {
			try {
				final String[] excludeArray = excludeTimePointList.split(",");
				for (int i = 0; i < excludeArray.length; i++) {
					String[] endPoints = excludeArray[i].split("-");
					int start = Integer.valueOf(endPoints[0].trim());
					int end = (endPoints.length > 1) ? Integer.valueOf(endPoints[1]
							.trim()) : start;

					excludeTimePoints.add(new int[] { start, end });
				}
			}
			catch (NumberFormatException e) {
				logService.info(
						"NumberFormatException encountered when parsing exclude list. Tracking all time points.");
				excludeTimePoints = new ArrayList<int[]>();
			}
		}
		return excludeTimePoints;
	}

	private void process(List<int[]> excludeTimePoints, Roi[] rois) {
		//Map from T to label peak lists
		List<ConcurrentMap<Integer, List<Peak>>> peakLabelsStack = new ArrayList<>();
		metaDataStack = new ConcurrentHashMap<>();

		int zDim = dataset.getImgPlus().dimensionIndex(Axes.Z);
		int zSize = (int) dataset.getImgPlus().dimension(zDim);

		int tDim = dataset.getImgPlus().dimensionIndex(Axes.TIME);
		int tSize = (int) dataset.getImgPlus().dimension(tDim);

		final int frameCount = (swapZandT) ? zSize : tSize;

		for (int i = 0; i < rois.length; i++)
			peakLabelsStack.add(new ConcurrentHashMap<Integer, List<Peak>>());

		List<Integer> processTimePoints = new ArrayList<Integer>();
		List<Runnable> tasks = new ArrayList<Runnable>();
		for (int t = 0; t < frameCount; t++) {
			boolean processedTimePoint = true;
			for (int index = 0; index < excludeTimePoints.size(); index++)
				if (excludeTimePoints.get(index)[0] <= t && t <= excludeTimePoints.get(
						index)[1]) {
					processedTimePoint = false;
					break;
				}

			if (processedTimePoint) {
				processTimePoints.add(t);
				final int theT = t;
				tasks.add(() -> {
					List<List<Peak>> labelPeaks = findPeaksInT(Integer.valueOf(channel),
							theT, useDogFilter, integrate, rois);
					for (int i = 0; i < rois.length; i++)
						if (labelPeaks.get(i).size() > 0) peakLabelsStack.get(i).put(theT,
								labelPeaks.get(i));
				});
			}
		}

		MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
				.showStatus(peakLabelsStack.get(0).size(), frameCount,
						"Finding Peaks for " + dataset.getName()), tasks, nThreads);

		tracker = new PeakTracker(maxDifferenceX, maxDifferenceY, maxDifferenceT,
				minimumDistance, minTrajectoryLength, verbose, logService, pixelLength);

		if (archive.getNumberOfMetadatas() == 0) {
			MarsOMEMetadata marsOMEMetadata = buildOMEMetadata();

			try {
				UnitsLengthEnumHandler unitshandler = new UnitsLengthEnumHandler();
				Length pixelSize = new Length(pixelLength, UnitsLengthEnumHandler
						.getBaseUnit((UnitsLength) unitshandler.getEnumeration(pixelUnits)));

				marsOMEMetadata.getImage(0).setPixelsPhysicalSizeX(pixelSize);
				marsOMEMetadata.getImage(0).setPixelsPhysicalSizeY(pixelSize);
			} catch (EnumerationException e1) {
				e1.printStackTrace();
			}

			archive.putMetadata(marsOMEMetadata);
		}

		for (int i = 0; i < rois.length; i++)
			tracker.track(peakLabelsStack.get(i), archive, Integer.valueOf(channel),
					processTimePoints, nThreads);
	}
	
	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> List<List<Peak>> findPeaksInT(
		int channel, int t, boolean useDogFilter, boolean integrate, Roi[] processingRois)
	{
		return findPeaksInT(channel, t, useDogFilter, integrate, processingRois, 1);
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> List<List<Peak>> findPeaksInT(
		int channel, int t, boolean useDogFilter, boolean integrate, Roi[] processingRois, 
		int numThreads)
	{
		RandomAccessibleInterval<T> img = (swapZandT) ? MarsImageUtils
			.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), t, -1, -1)
			: MarsImageUtils.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), 0,
				channel, t);

		// Workaround for IJ1 metadata in slices - Norpix format.
		if (!preview && image != null) {
			ImageStack stack = image.getImageStack();
			int index = t + 1;
			if (!swapZandT) index = image.getStackIndex(channel + 1, 1, t + 1);

			// Have to retrieve the image processor to make sure the label has been
			// loaded.
			stack.getProcessor(index);
			String label = stack.getSliceLabel(index);
			metaDataStack.put(t, label);
		}

		RandomAccessibleInterval<FloatType> filteredImg = null;
		//To improve performance when processing using grid, only calculate
		// the dog image within each grid region as needed in the loop below
		if (useDogFilter && !gridProcess) filteredImg = MarsImageUtils.dogFilter(img,
			dogFilterRadius, numThreads);

		List<List<Peak>> labelPeakLists = new ArrayList<List<Peak>>();
		for (int i = 0; i < processingRois.length; i++) {
			List<Peak> peaks = new ArrayList<Peak>();

			if (useDogFilter && gridProcess) filteredImg = MarsImageUtils.dogFilter(Views.interval( img,
				new long[] { (long)processingRois[i].getXBase(), (long)processingRois[i].getYBase() },
				new long[]{ (long)processingRois[i].getXBase() + (long)processingRois[i].getFloatWidth(),
				(long)processingRois[i].getYBase() + (long)processingRois[i].getFloatHeight() } ),
				dogFilterRadius, numThreads);

			RealMask roiMask = convertService.convert(processingRois[i],
				RealMask.class);
			IterableRegion<BoolType> iterableROI = MarsImageUtils.toIterableRegion(
				roiMask, img);

			if (useDogFilter) peaks = MarsImageUtils.findPeaks(filteredImg, Regions
				.sample(iterableROI, filteredImg), t, threshold, minimumDistance,
				findNegativePeaks);
			else peaks = MarsImageUtils.findPeaks(img, Regions.sample(iterableROI,
				img), t, threshold, minimumDistance, findNegativePeaks);

			peaks = MarsImageUtils.fitPeaks(img, img, peaks, fitRadius,
					dogFilterRadius, findNegativePeaks, RsquaredMin);
			peaks = MarsImageUtils.removeNearestNeighbors(peaks, minimumDistance);

			if (integrate) MarsImageUtils.integratePeaks(img, img, peaks,
				integrationInnerRadius, integrationOuterRadius);

			labelPeakLists.add(peaks);
		}

		return labelPeakLists;
	}

	private MarsOMEMetadata buildOMEMetadata() {
		ImgPlus<?> imp = dataset.getImgPlus();

		OMEXMLMetadata omexmlMetadata = null;
		if (!(imp instanceof SCIFIOImgPlus)) {
			logService.info(
				"This image has not been opened with SCIFIO. Creating OME Metadata...");
			try {
				omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService,
					dataset);
			}
			catch (ServiceException e) {
				e.printStackTrace();
			}
		}
		else {
			Metadata metadata = (Metadata) dataset.getProperties().get(
				"scifio.metadata.global");

			OMEMetadata omeMeta = new OMEMetadata(getContext());
			if (!translatorService.translate(metadata, omeMeta, true)) {
				logService.info("Unable to extract OME Metadata. Creating...");
				try {
					omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService,
						dataset);
				}
				catch (ServiceException e) {
					e.printStackTrace();
				}
			}
			else {
				omexmlMetadata = omeMeta.getRoot();
			}

			omexmlMetadata.setImageName(metadata.get(0).getName(), 0);

			// Check for SliceLabels
			if (metadata.get(0).getTable().containsKey("SliceLabels")) {
				String[] sliceLabels = (String[]) metadata.get(0).getTable().get(
					"SliceLabels");
				metaDataStack.clear();

				for (int i = 0; i < sliceLabels.length; i++)
					metaDataStack.put(i, sliceLabels[i]);
			}
		}

		if (swapZandT) {
			int sizeT = omexmlMetadata.getPixelsSizeT(0).getNumberValue().intValue();
			int sizeZ = omexmlMetadata.getPixelsSizeZ(0).getNumberValue().intValue();

			omexmlMetadata.setPixelsSizeT(new PositiveInteger(sizeZ), 0);
			omexmlMetadata.setPixelsSizeZ(new PositiveInteger(sizeT), 0);
		}

		// Check format
		if (metaDataStack.containsKey(0) && metaDataStack.get(0).contains(
			"DateTime: "))
		{
			// Must be Norpix format..
			logService.info("Reading Norpix Format");

			String metaUID = (metadataUIDSource.equals("unique from dataset"))
				? generateUID(metaDataStack) : MarsMath.getUUID58().substring(0, 10);
			omexmlMetadata.setPixelsDimensionOrder(DimensionOrder.XYZCT, 0);

			MarsOMEMetadata marsOMEMetadata = new MarsOMEMetadata(metaUID,
				omexmlMetadata);

			MarsOMEUtils.getTimeFromNoprixSliceLabels(marsOMEMetadata, metaDataStack);

			return marsOMEMetadata;
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
				logService.info(
					"Failed to generate unique metadata uid. Using random generated metadata uid.");
			}
			else metaUID = MarsMath.getUUID58().substring(0, 10);
		}
		else metaUID = MarsMath.getUUID58().substring(0, 10);

		return new MarsOMEMetadata(metaUID, omexmlMetadata);
	}

	private String generateUID(ConcurrentMap<Integer, String> headerLabels) {
		String allLabels = "";
		for (int i = 0; i < headerLabels.size(); i++)
			allLabels += headerLabels.get(i);

		return MarsMath.getFNV1aBase58(allLabels);
	}

	@Override
	public void preview() {
		if (preview) {
			if (swapZandT) image.setSlice(previewT + 1);
			else image.setPosition(Integer.valueOf(channel) + 1, 1, previewT + 1);

			ExecutorService es = Executors.newSingleThreadExecutor();
			try {
				es.submit(() -> {
					if (imageRoi == null && image.getRoi() != null) imageRoi = image
						.getRoi();

					Roi[] rois = new Roi[1];
					if (region.equals("ROI from image")) {
						rois[0] = imageRoi;
					}
					else if (region.equals("ROIs from manager")) {
						rois = roiManager.getRoisAsArray();
					}
					else {
						rois[0] = new Roi(new Rectangle(0, 0, (int) dataset.dimension(0),
							(int) dataset.dimension(1)));
					}

					List<List<Peak>> labelPeakLists = findPeaksInT(Integer.valueOf(
						channel), previewT, useDogFilter, false, rois, Runtime.getRuntime().availableProcessors());

					if (Thread.currentThread().isInterrupted()) return;

					int peakCount = 0;
					Overlay overlay = new Overlay();
					if (previewRoiType.equals("point")) {
						FloatPolygon poly = new FloatPolygon();
						for (List<Peak> labelPeaks : labelPeakLists)
							for (Peak p : labelPeaks) {
								poly.addPoint(p.getDoublePosition(0), p.getDoublePosition(1));
								peakCount++;

								if (Thread.currentThread().isInterrupted()) return;
							}

						PointRoi peakRoi = new PointRoi(poly);

						overlay.add(peakRoi);
					}
					else {
						for (List<Peak> labelPeaks : labelPeakLists)
							for (Peak p : labelPeaks) {
								// The pixel origin for OvalRois is at the upper left corner
								// !!!!
								// The pixel origin for PointRois is at the center !!!
								final OvalRoi ovalRoi = new OvalRoi(p.getDoublePosition(0) +
									0.5 - integrationInnerRadius, p.getDoublePosition(1) + 0.5 -
										integrationInnerRadius, integrationInnerRadius * 2,
									integrationInnerRadius * 2);
								// ovalRoi.setStrokeColor(Color.CYAN.darker());
								overlay.add(ovalRoi);
								peakCount++;
								if (Thread.currentThread().isInterrupted()) return;
							}
					}
					if (Thread.currentThread().isInterrupted()) return;

					final String countString = "count: " + peakCount;
					final MutableModuleItem<String> preFrameCount = getInfo()
						.getMutableInput("tPeakCount", String.class);
					preFrameCount.setValue(this, countString);

					SwingUtilities.invokeLater(() -> {
						if (image != null) {
							image.deleteRoi();
							image.setOverlay(overlay);

							for (Window window : Window.getWindows())
								if (window instanceof JDialog && ((JDialog) window).getTitle()
									.equals(getInfo().getLabel())) MarsUtil
										.updateJLabelTextInContainer(window, "count: ",
											countString);
						}
					});
				}).get(previewTimeout, TimeUnit.SECONDS);
			}
			catch (TimeoutException e1) {
				es.shutdownNow();
				uiService.showDialog(
					"Preview took too long. Try a smaller region, a higher threshold, or try again with a longer delay before preview timeout.",
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

	protected void openWebPage() {
		try {
			String urlString =
				"https://duderstadt-lab.github.io/mars-docs/docs/image/PeakTracker/";
			URL url = new URL(urlString);
			platformService.open(url);
		}
		catch (Exception e) {
			// do nothing
		}
	}

	@Override
	public void cancel() {
		if (image != null) {
			image.setOverlay(null);
			if (imageRoi != null) image.setRoi(imageRoi);
		}
	}

	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
		if (!preview) cancel();
	}

	private void addInputParameterLog(LogBuilder builder) {
		if (image != null) {
			builder.addParameter("Image title", image.getTitle());
			if (image.getOriginalFileInfo() != null && image
				.getOriginalFileInfo().directory != null)
			{
				builder.addParameter("Image directory", image
					.getOriginalFileInfo().directory);
			}
		}
		else builder.addParameter("Dataset name", dataset.getName());

		builder.addParameter("Region", region);
		if (region.equals("ROI from image") && imageRoi != null) builder
			.addParameter("ROI from image", imageRoi.toString());
		builder.addParameter("Channel", channel);
		builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum distance", String.valueOf(minimumDistance));
		builder.addParameter("Find negative peaks", String.valueOf(
			findNegativePeaks));
		builder.addParameter("Fit radius", String.valueOf(fitRadius));
		builder.addParameter("Minimum R-squared", String.valueOf(RsquaredMin));
		builder.addParameter("Verbose output", String.valueOf(verbose));
		builder.addParameter("Max difference X", String.valueOf(maxDifferenceX));
		builder.addParameter("Max difference Y", String.valueOf(maxDifferenceY));
		builder.addParameter("Max difference T", String.valueOf(maxDifferenceT));
		builder.addParameter("Minimum track length", String.valueOf(
			minTrajectoryLength));
		builder.addParameter("Integrate", String.valueOf(integrate));
		builder.addParameter("Integration inner radius", String.valueOf(
			integrationInnerRadius));
		builder.addParameter("Integration outer radius", String.valueOf(
			integrationOuterRadius));
		builder.addParameter("Use grid", String.valueOf(gridProcess));
		builder.addParameter("Horizontal grid regions", String.valueOf(
				horizontalGridRegions));
		builder.addParameter("Vertical grid regions", String.valueOf(
				verticalGridRegions));
		builder.addParameter("Microscope", microscope);
		builder.addParameter("Pixel length", String.valueOf(this.pixelLength));
		builder.addParameter("Pixel units", this.pixelUnits);
		builder.addParameter("Exclude time points", excludeTimePointList);
		builder.addParameter("Swap Z and T", swapZandT);
		builder.addParameter("Thread count", nThreads);
		builder.addParameter("Metadata UID source", metadataUIDSource);
	}

	// Getters and Setters
	public SingleMoleculeArchive getArchive() {
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
		this.inputRois = rois;
		this.region = "ROIs from manager";
	}

	public Roi[] getROIs() {
		return this.inputRois;
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

	public void setGridProcess(boolean gridProcess) {
		this.gridProcess = gridProcess;
	}

	public boolean getGridProcess() {
		return gridProcess;
	}

	public void setHorizontalGridRegions(int horizontalGridRegions) {
		this.horizontalGridRegions = horizontalGridRegions;
	}

	public int getHorizontalGridRegions() {
		return horizontalGridRegions;
	}

	public void setVerticalGridRegions(int verticalGridRegions) {
		this.verticalGridRegions = verticalGridRegions;
	}

	public int getVerticalGridRegions() {
		return verticalGridRegions;
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
