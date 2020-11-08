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

import java.awt.Polygon;
import java.awt.Rectangle;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.image.PeakTracker;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import io.scif.Metadata;
import io.scif.img.SCIFIOImgPlus;
import io.scif.ome.OMEMetadata;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.FormatService;
import io.scif.services.TranslatorService;
import loci.common.services.ServiceException;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsLength;
import ome.xml.model.enums.UnitsTime;
import ome.xml.model.enums.handlers.UnitsLengthEnumHandler;
import ome.xml.model.enums.handlers.UnitsTimeEnumHandler;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;

@Plugin(type = Command.class, label = "Peak Tracker", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
			label = "Image", weight = 20, mnemonic = 'm'), @Menu(
				label = "Peak Tracker", weight = 10, mnemonic = 'p') })
public class PeakTrackerCommand extends
	DynamicCommand implements Command, Initializable
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

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image to search for Peaks")
	private ImageDisplay imageDisplay;

	/**
	 * ROI
	 */
	@Parameter(required = false)
	private RoiManager roiManager;
	
	@Parameter(label = "Use ROI", persist = false)
	private boolean useROI = true;

	@Parameter(label = "ROI x0", persist = false)
	private int x0;

	@Parameter(label = "ROI y0", persist = false)
	private int y0;

	@Parameter(label = "ROI width", persist = false)
	private int width;

	@Parameter(label = "ROI height", persist = false)
	private int height;

	/**
	 * FINDER SETTINGS
	 */
	@Parameter(label = "Channel", choices = { "a", "b", "c" })
	private String channel = "0";
	
	@Parameter(label = "Use DoG filter")
	private boolean useDogFilter = true;

	@Parameter(label = "DoG filter radius")
	private double dogFilterRadius = 2;

	@Parameter(label = "Detection threshold")
	private double threshold = 50;

	@Parameter(label = "Minimum distance between peaks")
	private int minimumDistance = 4;

	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
		callback = "previewChanged")
	private boolean preview = false;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String tPeakCount = "count: 0";

	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE)
	private int previewT;

	@Parameter(label = "Find negative peaks")
	private boolean findNegativePeaks = false;

	/**
	 * FITTER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String fitterTitle = "Peak fitter settings:";

	@Parameter(label = "Fit radius")
	private int fitRadius = 4;

	@Parameter(label = "Minimum R-squared", style = NumberWidget.SLIDER_STYLE,
		min = "0.00", max = "1.00", stepSize = "0.01")
	private double RsquaredMin = 0;

	/**
	 * TRACKER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String trackerTitle = "Peak tracker settings:";

	@Parameter(label = "Max difference x")
	private double maxDifferenceX = 1;

	@Parameter(label = "Max difference y")
	private double maxDifferenceY = 1;

	@Parameter(label = "Max difference T")
	private int maxDifferenceT = 1;

	@Parameter(label = "Minimum track length")
	private int minTrajectoryLength = 100;
	
	/**
	 * VERBOSE
	 */
	@Parameter(label = "Verbose output")
	private boolean verbose = false;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String integrationTitle = "Peak integration settings:";

	@Parameter(label = "Integrate")
	private boolean integrate = false;

	@Parameter(label = "Inner radius")
	private int integrationInnerRadius = 1;

	@Parameter(label = "Outer radius")
	private int integrationOuterRadius = 3;

	@Parameter(label = "Microscope")
	private String microscope;

	@Parameter(label = "Pixel length")
	private double pixelLength = 1;

	@Parameter(label = "Pixel units", choices = { "pixel", "µm", "nm" })
	private String pixelUnits = "pixel";

	@Parameter(label = "Norpix format")
	private boolean norpixFormat = false;

	@Parameter
	private UIService uiService;

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;

	/**
	 * Map from T to peak list
	 */
	private ConcurrentMap<Integer, List<Peak>> PeakStack;

	/**
	 * Map from T to IJ1 label metadata string
	 */
	private ConcurrentMap<Integer, String> metaDataStack;
	
	private PeakTracker tracker;

	//private Rectangle rect;
	private Interval interval;
	private Roi startingRoi;

	private Dataset dataset;
	private ImagePlus image;
	private MarsOMEMetadata marsOMEMetadata;
	private boolean swapZandT = false;
	
	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else if (dataset != null) image = convertService.convert(dataset,
			ImagePlus.class);
		else return;

		ImgPlus<?> imp = dataset.getImgPlus();

		OMEXMLMetadata omexmlMetadata = null;
		if (!(imp instanceof SCIFIOImgPlus)) {
			logService.info("This image has not been opened with SCIFIO.");
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
				logService.info(
					"Unable to extract OME Metadata. Falling back to IJ1 for metadata.");
			}
			else {
				omexmlMetadata = omeMeta.getRoot();
			}
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
			// Do nothing. Many of the omexmlmetadata methods give NullPointerExceptions
			// if fields are not set.
		}

		String metaUID;
		if (omexmlMetadata.getUUID() != null) metaUID = MarsMath.getUUID58(
			omexmlMetadata.getUUID()).substring(0, 10);
		else metaUID = MarsMath.getUUID58().substring(0, 10);

		marsOMEMetadata = new MarsOMEMetadata(metaUID, omexmlMetadata);

		Rectangle rect;
		if (image.getRoi() == null) {
			rect = new Rectangle(0, 0, image.getWidth() - 1, image.getHeight() - 1);
			final MutableModuleItem<Boolean> useRoifield = getInfo().getMutableInput(
				"useROI", Boolean.class);
			useRoifield.setValue(this, false);
		}
		else {
			rect = image.getRoi().getBounds();
			startingRoi = image.getRoi();
		}

		final MutableModuleItem<String> channelItems = getInfo().getMutableInput(
			"channel", String.class);
		long channelCount = dataset.getChannels();
		ArrayList<String> channels = new ArrayList<String>();
		for (int ch = 0; ch < channelCount; ch++)
			channels.add(String.valueOf(ch));
		channelItems.setChoices(channels);
		channelItems.setValue(this, String.valueOf(image.getChannel() - 1));

		final MutableModuleItem<Integer> imgX0 = getInfo().getMutableInput("x0",
			Integer.class);
		imgX0.setValue(this, rect.x);

		final MutableModuleItem<Integer> imgY0 = getInfo().getMutableInput("y0",
			Integer.class);
		imgY0.setValue(this, rect.y);

		final MutableModuleItem<Integer> imgWidth = getInfo().getMutableInput(
			"width", Integer.class);
		imgWidth.setValue(this, rect.width);

		final MutableModuleItem<Integer> imgHeight = getInfo().getMutableInput(
			"height", Integer.class);
		imgHeight.setValue(this, rect.height);

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

		if (norpixFormat) swapZandT = true;
	}

	@Override
	public void run() {
		updateInterval();

		// Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Peak Tracker");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		PeakStack = new ConcurrentHashMap<>(image.getStackSize());
		metaDataStack = new ConcurrentHashMap<>(image.getStackSize());
		
		double starttime = System.currentTimeMillis();
		logService.info("Finding and Fitting Peaks...");

		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		int frameCount = (swapZandT) ? image.getStackSize() : image.getNFrames();

		MarsUtil.forkJoinPoolBuilder(statusService, logService,
			() -> statusService.showStatus(PeakStack.size(), frameCount,
				"Finding Peaks for " + image.getTitle()), () -> IntStream.range(0, 
						frameCount).parallel().forEach(t -> {
						List<Peak> tpeaks = findPeaksInT(Integer.valueOf(channel), t, useDogFilter, integrate);

						if (tpeaks.size() > 0) PeakStack.put(t, tpeaks);
					}), PARALLELISM_LEVEL);

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");

		tracker = new PeakTracker(maxDifferenceX, maxDifferenceY, maxDifferenceT, 
			minimumDistance, minTrajectoryLength, integrate, verbose, logService,
			pixelLength);

		archive = new SingleMoleculeArchive("archive.yama");
		
		if (norpixFormat)
			marsOMEMetadata = generateNorpixMetadata();

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
		
		if (norpixFormat) getTimeFromNoprixSliceLabels(marsOMEMetadata,
			metaDataStack);
		archive.putMetadata(marsOMEMetadata);

		tracker.track(PeakStack, archive, Integer.valueOf(channel));

		archive.naturalOrderSortMoleculeIndex();

		// Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", SingleMoleculeArchive.class).setLabel(
			archive.getName());

		image.setRoi(startingRoi);

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
	
	private void updateInterval() {
		interval = (useROI) ? Intervals.createMinMax(x0, y0, x0 + width - 1, y0 +
			height - 1) : Intervals.createMinMax(0, 0, image.getWidth() - 1, image
				.getHeight() - 1);

		image.deleteRoi();
		image.setOverlay(null);
	}
	
	private MarsOMEMetadata generateNorpixMetadata() {
		// Generate new MarsOMEMetadata based on NorpixFormat.
		// Flip Z and T
		OMEXMLMetadata omexmlMetadata = null;
		try {
			omexmlMetadata = MarsOMEUtils.createOMEXMLMetadata(omexmlService,
				dataset);
		}
		catch (ServiceException e) {
			e.printStackTrace();
		}
		String metaUID = generateUID(metaDataStack);//MarsMath.getUUID58().substring(0, 10);
		omexmlMetadata.setPixelsSizeX(new PositiveInteger(image.getWidth()), 0);
		omexmlMetadata.setPixelsSizeY(new PositiveInteger(image.getHeight()), 0);
		omexmlMetadata.setPixelsSizeZ(new PositiveInteger(1), 0);
		omexmlMetadata.setPixelsSizeC(new PositiveInteger(1), 0);
		omexmlMetadata.setPixelsSizeT(new PositiveInteger(image.getStackSize()),
			0);
		omexmlMetadata.setPixelsDimensionOrder(DimensionOrder.XYZCT, 0);
		return new MarsOMEMetadata(metaUID, omexmlMetadata);
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> List<Peak> findPeaksInT(int channel, int t, boolean useDogFilter,
		boolean integrate)
	{
		RandomAccessibleInterval<T> img = (swapZandT) ? MarsImageUtils
			.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), t, -1, -1)
			: MarsImageUtils.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), 0,
				channel, t);
			
		// Workaround for IJ1 metadata in slices - Norpix format.
		if (norpixFormat) {
			ImageStack stack = image.getImageStack();
			int index = t + 1;
			if (!swapZandT) index = image.getStackIndex(channel + 1, 1, t + 1);

			//Have to retrieve the image processor to make sure the label has been loaded.
			stack.getProcessor(index);
			String label = stack.getSliceLabel(index);
			metaDataStack.put(t, label);
		}

		List<Peak> peaks = new ArrayList<Peak>();

		if (useDogFilter) {
			RandomAccessibleInterval<FloatType> filteredImg = MarsImageUtils
				.dogFilter(img, dogFilterRadius, opService);
			peaks = MarsImageUtils.findPeaks(filteredImg, interval, t, threshold,
				minimumDistance, findNegativePeaks);
		}
		else peaks = MarsImageUtils.findPeaks(img, interval, t, threshold,
			minimumDistance, findNegativePeaks);

		peaks = MarsImageUtils.fitPeaks(img, peaks, fitRadius, dogFilterRadius,
			findNegativePeaks, RsquaredMin, interval);
		peaks = MarsImageUtils.removeNearestNeighbors(peaks, minimumDistance);

		if (integrate) MarsImageUtils.integratePeaks(img, interval, peaks,
				integrationInnerRadius, integrationOuterRadius);

		return peaks;
	}

	private void getTimeFromNoprixSliceLabels(MarsMetadata marsMetadata,
		Map<Integer, String> metaDataStack)
	{
		try {
			// Set Global Collection Date for the dataset
			int DateTimeIndex1 = metaDataStack.get(0).indexOf("DateTime: ");
			String DateTimeString1 = metaDataStack.get(0).substring(DateTimeIndex1 +
				10);
			marsMetadata.getImage(0).setAquisitionDate(getNorPixDate(
				DateTimeString1));

			final UnitsTimeEnumHandler timehandler = new UnitsTimeEnumHandler();

			// Extract the exact time of collection of all frames..
			final long t0 = getNorPixMillisecondTime(DateTimeString1);

			marsMetadata.getImage(0).planes().forEach(plane -> {
				int dateTimeIndex2 = metaDataStack.get(plane.getT()).indexOf(
					"DateTime: ");
				String DateTimeString2 = metaDataStack.get(plane.getT()).substring(
					dateTimeIndex2 + 10);
				Time dt = null;
				try {
					double millisecondsDt = ((double) getNorPixMillisecondTime(
						DateTimeString2) - t0) / 1000;
					dt = new Time(millisecondsDt, UnitsTimeEnumHandler.getBaseUnit(
						(UnitsTime) timehandler.getEnumeration("s")));
				}
				catch (ParseException | EnumerationException e) {
					e.printStackTrace();
				}
				plane.setDeltaT(dt);
			});
		}
		catch (ParseException e1) {
			// e1.printStackTrace();
		}
	}

	private String generateUID(ConcurrentMap<Integer, String> headerLabels) {
		String allLabels = "";
		for (int i=0;i<headerLabels.size();i++)
			allLabels += headerLabels.get(i);
		
		return MarsMath.getFNV1aBase58(allLabels);
	}
	
	private long getNorPixMillisecondTime(String strTime) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
		Date convertedDate = formatter.parse(strTime.substring(0, strTime.length() -
			4));
		return convertedDate.getTime();
	}

	private Timestamp getNorPixDate(String strTime) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
		Date convertedDate = formatter.parse(strTime.substring(0, strTime.length() -
			4));
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
		String nowAsISO = df.format(convertedDate);
		return new Timestamp(nowAsISO);
	}
	
	@Override
	public void preview() {
		if (preview) {
			updateInterval();

			if (swapZandT) image.setSlice(previewT + 1);
			else image.setPosition(Integer.valueOf(channel) + 1, 1, previewT + 1);

			List<Peak> peaks = findPeaksInT(Integer.valueOf(channel), previewT,
				useDogFilter, false);

			final MutableModuleItem<String> preFrameCount = getInfo().getMutableInput(
				"tPeakCount", String.class);
			if (!peaks.isEmpty()) {
				Polygon poly = new Polygon();

				for (Peak p : peaks) {
					int x = (int) p.getDoublePosition(0);
					int y = (int) p.getDoublePosition(1);
					poly.addPoint(x, y);
				}

				PointRoi peakRoi = new PointRoi(poly);
				image.setRoi(peakRoi);

				preFrameCount.setValue(this, "count: " + peaks.size());
			}
			else {
				preFrameCount.setValue(this, "count: 0");
			}
		}
	}

	@Override
	public void cancel() {
		if (image != null) {
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
		if (image.getOriginalFileInfo() != null && image
			.getOriginalFileInfo().directory != null)
		{
			builder.addParameter("Image Directory", image
				.getOriginalFileInfo().directory);
		}
		builder.addParameter("useROI", String.valueOf(useROI));
		if (useROI) {
			builder.addParameter("ROI x0", String.valueOf(x0));
			builder.addParameter("ROI y0", String.valueOf(y0));
			builder.addParameter("ROI width", String.valueOf(width));
			builder.addParameter("ROI height", String.valueOf(height));
		}
		builder.addParameter("Channel", channel);
		builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum Distance", String.valueOf(minimumDistance));
		builder.addParameter("Find negative peaks", String.valueOf(
			findNegativePeaks));
		builder.addParameter("Fit radius", String.valueOf(fitRadius));
		builder.addParameter("Minimum R-squared", String.valueOf(RsquaredMin));
		builder.addParameter("Verbose output", String.valueOf(verbose));
		builder.addParameter("Max difference x", String.valueOf(
			maxDifferenceX));
		builder.addParameter("Max difference y", String.valueOf(
			maxDifferenceY));
		builder.addParameter("Max difference T", String.valueOf(
			maxDifferenceT));
		builder.addParameter("Minimum track length", String.valueOf(
			minTrajectoryLength));
		builder.addParameter("Integrate", String.valueOf(integrate));
		builder.addParameter("Integration inner radius", String.valueOf(
			integrationInnerRadius));
		builder.addParameter("Integration outer radius", String.valueOf(
			integrationOuterRadius));
		builder.addParameter("Microscope", microscope);
		builder.addParameter("Pixel Length", String.valueOf(this.pixelLength));
		builder.addParameter("Pixel Units", this.pixelUnits);
		builder.addParameter("Swap Z and T", swapZandT);
		builder.addParameter("Norpix Format", String.valueOf(norpixFormat));
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
