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
import io.scif.services.TranslatorService;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.display.ImageDisplay;
import org.scijava.Initializable;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
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
import org.scijava.module.DefaultMutableModuleItem;
import org.scijava.module.MutableModuleItem;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.metadata.MarsOMEChannel;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import loci.common.services.ServiceException;
import ome.xml.meta.OMEXMLMetadata;

/**
 * Command for integrating the fluorescence signal from spot. A list of peaks
 * for integration can be provided as OvalRois or PointRois in the RoiManger
 * with the format UID_REGION1, UID_REGION2, etc. for peaks in different
 * subregions of a multiview image representing different wavelengths. The
 * positions given are integrated for all T for all channels specified to
 * generate a SingleMoleculeArchive in which all molecule record tables have
 * columns for all integrated colors.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = Command.class, label = "Molecule Integrator (multiview)",
	menu = { @Menu(label = MenuConstants.PLUGINS_LABEL,
		weight = MenuConstants.PLUGINS_WEIGHT,
		mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
			weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
				label = "Image", weight = 1, mnemonic = 'i'), @Menu(
					label = "Molecule Integrator (multiview)", weight = 6,
					mnemonic = 'm') })
public class MoleculeIntegratorMultiViewCommand extends DynamicCommand
	implements Command, Initializable
{

	/**
	 * SERVICES
	 */
	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MarsTableService resultsTableService;

	@Parameter
	private TranslatorService translatorService;

	@Parameter
	private OMEXMLService omexmlService;

	@Parameter
	private ConvertService convertService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	@Parameter
	private PlatformService platformService;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image for Integration")
	private ImageDisplay imageDisplay;

	/**
	 * ROIs
	 */
	@Parameter(required = false)
	private RoiManager roiManager;

	/**
	 * INPUT SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel",
		persist = false)
	private final String inputGroup = "Input";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Input",
		persist = false)
	private final String inputFigure = "MoleculeIntegratorDualInput.png";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Input, align:center", persist = false)
	private final String imageName = "?";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Input, align:center", persist = false)
	private final String roiCount = "No ROIs in manager!";

	/**
	 * BOUNDARY SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String regionGroup = "Boundaries";

	/**
	 * INTEGRATION SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String integrateGroup = "Integration";

	@Parameter(label = "Inner Radius", style = "group:Integration")
	private int innerRadius = 2;

	@Parameter(label = "Outer Radius", style = "group:Integration")
	private int outerRadius = 4;

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

	@Parameter(label = "Microscope", style = "group:Output")
	private String microscope = "Unknown";

	@Parameter(label = "Threads", required = false, min = "1", max = "120",
		style = "group:Output")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	@Parameter(label = "Metadata UID",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Output",
		choices = { "unique from dataset", "random" })
	private String metadataUIDSource = "random";

	@Parameter(label = "Verbose", style = "group:Output")
	private boolean verbose = false;

	@Parameter(label = "Help",
		description = "View a web page detailing Molecule Integrator (Multiview) options",
		callback = "openWebPage", persist = false)
	private Button openWebPage;

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;

	/**
	 * List of IntegrationMaps containing T -> UID peak maps, name and channel.
	 */
	private final List<IntegrationMap> peakIntegrationMaps = new ArrayList<>();

	private Dataset dataset;
	private ImagePlus image;
	private String imageID;

	private final AtomicInteger progressInteger = new AtomicInteger(0);

	private MarsOMEMetadata marsOMEMetadata;
	private OMEXMLMetadata omexmlMetadata;

	private List<MutableModuleItem<String>> channelColors;
	private List<String> regionNames;
	private final Map<String, MutableModuleItem<Integer>> regionBoundaryInputItems =
			new HashMap<>();
	private final Map<String, Interval> regionIntervals =
			new HashMap<>();

	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else if (dataset == null) return;

		if (dataset != null) {
			final MutableModuleItem<String> imageNameItem = getInfo().getMutableInput(
				"imageName", String.class);
			if (dataset.dimension(Axes.TIME) == 1)
				imageNameItem.setValue(this, "<p style=\"text-align:center;color:red\">" + dataset.getName() + "</p>" +
					"\n<p style=\"color:red\">WARNING: The image selected has only one time point.</p>\n" +
					"<p style=\"color:red\">Usually, a sequence of time points are integrated. Are </p>\n" +
					"<p style=\"color:red\">you sure the correct image was selected?</p>\n");
			else
				imageNameItem.setValue(this, dataset.getName());

			ImgPlus<?> imp = dataset.getImgPlus();

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
				// Attempt to read metadata
				Metadata metadata = (Metadata) dataset.getProperties().get(
						"scifio.metadata.global");
				OMEMetadata omeMeta = new OMEMetadata(getContext());
				if (!translatorService.translate(metadata, omeMeta, true)) {
					logService.info(
							"Unable to extract OME Metadata. Generating OME metadata from dimensions.");
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
			}
		}

		if (roiManager != null) {
			final MutableModuleItem<String> roiCountItem = getInfo().getMutableInput(
				"roiCount", String.class);

			Roi[] roiArray = roiManager.getRoisAsArray();
			regionNames = getSubRegionNames();

			Map<String, Integer> roiCountMap = new HashMap<>();
			regionNames.forEach(name -> roiCountMap.put(name, 0));
			for (Roi roi : roiArray) {
				regionNames.forEach(name -> {
					if (roi.getName().endsWith(name)) roiCountMap.put(name, roiCountMap
						.get(name) + 1);
				});
			}

			StringBuilder regionInfo = new StringBuilder("Discovered " + regionNames.size() +
					" ROI sets for integration:");
			for (String name : regionNames)
				regionInfo.append(" ").append(roiCountMap.get(name)).append(" ").append(name);
			roiCountItem.setValue(this, regionInfo.toString());

			// Add all boundary regions...
			List<String> boundaryNames = new ArrayList<>(Arrays.asList(" X0",
					" Y0", " width", " height"));
			regionNames.forEach(region -> boundaryNames.forEach(boundaryName -> {
				String regionBoundaryName = region + boundaryName;
				MutableModuleItem<Integer> regionBoundary =
						new DefaultMutableModuleItem<>(this, regionBoundaryName,
								Integer.class);
				regionBoundary.setWidgetStyle("group:Boundaries");
				regionBoundaryInputItems.put(regionBoundaryName, regionBoundary);
				getInfo().addInput(regionBoundary);
			}));
		}

		// Ensures that MarsMicromanagerFormat correctly sets the ImageID based on
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
			/*
			 Do nothing. Many of the {@link ome.xml.meta.OMEXMLMetadata} methods give
			 NullPointerException if fields are not set.
			*/
		}

		imageID = omexmlMetadata.getImageID(0);

		List<String> channelNames = new ArrayList<>();
		for (int cIndex = 0; cIndex < omexmlMetadata.getChannelCount(0); cIndex++)
			if (omexmlMetadata.getChannelName(0, cIndex) != null) channelNames.add(
				omexmlMetadata.getChannelName(0, cIndex));
			else channelNames.add(String.valueOf(cIndex));

		// Don't build channel inputs when running in a script
		if (peakIntegrationMaps.size() == 0) {
			List<String> channelRegionOptions = new ArrayList<>(Arrays.asList(
					"Do not integrate", "All"));
			channelRegionOptions.addAll(regionNames);

			channelColors = new ArrayList<>();
			channelNames.forEach(name -> {
				final MutableModuleItem<String> channelChoice =
						new DefaultMutableModuleItem<>(this, name, String.class);
				channelChoice.setChoices(channelRegionOptions);
				channelChoice.setValue(this, "Do not integrate");
				channelColors.add(channelChoice);
				channelChoice.setWidgetStyle("group:Integration");
				getInfo().addInput(channelChoice);
			});
		}
	}

	@Override
	public void run() {
		// If running headless make sure to initialize that is required for this
		// command
		if (omexmlMetadata == null) initialize();

		// BUILD LOG
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Molecule Integrator (multiview)");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		String metaUID = null;
		if (metadataUIDSource.equals("unique from dataset")) {
			String uniqueMetadataUID = MarsOMEUtils.generateMetadataUIDfromDataset(
				omexmlMetadata);
			metaUID = uniqueMetadataUID;

			if (uniqueMetadataUID == null) logService.info(
				"Could not generate unique metadata UID from this dataset. Using randomly generated metadata UID.");
		}

		if (metaUID == null) metaUID = MarsMath.getUUID58().substring(0, 10);

		marsOMEMetadata = new MarsOMEMetadata(metaUID, omexmlMetadata);

		for (int cIndex = 0; cIndex < marsOMEMetadata.getImage(0)
			.getSizeC(); cIndex++)
		{
			if (marsOMEMetadata.getImage(0).getChannel(cIndex).getName() == null)
				marsOMEMetadata.getImage(0).getChannel(cIndex).setName(String.valueOf(
					cIndex));
		}

		if (peakIntegrationMaps.size() > 0) {
			logService.info("Using IntegrationMaps...");
		}
		else //noinspection ConstantValue
			if (peakIntegrationMaps.size() == 0 && roiManager == null) {
			logService.info(
				"No ROIs found in RoiManager and no IntegrationMaps were provided. Nothing to integrate.");
			logService.info(LogBuilder.endBlock(false));
			return;
		}
		else //noinspection ConstantValue
				if (peakIntegrationMaps.size() == 0 && roiManager != null && roiManager
			.getCount() > 0)
		{
			logService.info("Building integration lists from ROIs in RoiManager");
			// Build regionIntervals map using regionBoundaryInputItems provided in
			// the dialog
			for (String region : regionNames) {
				int x0 = regionBoundaryInputItems.get(region + " X0").getValue(this);
				int y0 = regionBoundaryInputItems.get(region + " Y0").getValue(this);
				int width = regionBoundaryInputItems.get(region + " width").getValue(
					this);
				int height = regionBoundaryInputItems.get(region + " height").getValue(
					this);
				this.setRegionBoundaries(region, x0, y0, width, height);
			}
			buildIntegrationLists();
		}

		double startTime = System.currentTimeMillis();
		logService.info("Integrating Peaks...");

		List<Runnable> tasks = new ArrayList<>();
		marsOMEMetadata.getImage(0).planes().forEach(plane -> tasks.add(() -> {
			integratePeaksInT(plane.getC(), plane.getT());
			progressInteger.incrementAndGet();
		}));

		// INTEGRATE PEAKS
		MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
			.showStatus(progressInteger.get(), marsOMEMetadata.getImage(0)
				.getPlaneCount(), "Integrating Molecules in " + dataset.getName()),
			tasks, nThreads);

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			startTime) / 60000, 2) + " minutes.");

		// CREATE MOLECULE ARCHIVE
		archive = new SingleMoleculeArchive("archive.yama");
		archive.putMetadata(marsOMEMetadata);

		Map<Integer, Map<Integer, Double>> channelToTtoDtMap = MarsOMEUtils
			.buildChannelToTtoDtMap(marsOMEMetadata);

		final int imageIndex = marsOMEMetadata.getImage(0).getImageID();

		Set<String> UIDs = new HashSet<>();
		for (IntegrationMap integrationMap : peakIntegrationMaps)
			for (int t : integrationMap.getMap().keySet())
				UIDs.addAll(integrationMap.getMap().get(t).keySet());

		tasks.clear();
		
		boolean containsDt = MarsOMEUtils.checkOMEMetadataForDt(marsOMEMetadata);
		
		for (String uid : UIDs) {
			tasks.add(() -> buildMolecule(uid, imageIndex, channelToTtoDtMap, containsDt));
		}

		progressInteger.set(0);
		MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
			.showStatus(progressInteger.get(), UIDs.size(),
				"Adding molecules to archive..."), tasks, nThreads);

		// FINISH UP
		statusService.clearStatus();
		logService.info("Finished in " + DoubleRounder.round((System
			.currentTimeMillis() - startTime) / 60000, 2) + " minutes.");
		if (archive.getNumberOfMolecules() == 0) {
			logService.info(
				"No molecules integrated. There must be a problem with your settings or ROIs.");
			archive = null;
			logService.info(LogBuilder.endBlock(false));
		}
		else {
			logService.info(LogBuilder.endBlock(true));

			archive.logln(log);
			archive.logln(LogBuilder.endBlock(true));
			archive.logln("   ");
		}
	}

	private List<String> getSubRegionNames() {
		Set<String> uniqueSuffixes = new HashSet<>();

		int roiNum = roiManager.getCount();
		for (int i = 0; i < roiNum; i++) {
			String name = roiManager.getRoi(i).getName();
			if (name.contains("_")) uniqueSuffixes.add(name.substring(name.indexOf(
				"_") + 1));
		}
		return new ArrayList<>(uniqueSuffixes);
	}

	private void buildIntegrationLists() {
		// These are assumed to be OvalRois or PointRois
		// we assume the same positions are integrated in all frames...
		Roi[] rois = roiManager.getRoisAsArray();

		Map<String, Map<String, Peak>> integrationListsMap =
				new HashMap<>();
		for (String region : regionIntervals.keySet())
			integrationListsMap.put(region, new HashMap<>());

		// Build single T integration lists for all regions.
		for (Roi points : rois) {
			if (!points.getName().contains("_")) continue;

			String UID = points.getName().substring(0, points.getName().indexOf(
					"_"));
			String region = points.getName().substring(points.getName().indexOf(
					"_") + 1);

			// The pixel origin for OvalRois is in the upper left corner.
			// The pixel origin for PointRois is in the center.
			// We always use pixel center as origin when integrating peaks.
			double pixelOriginOffset = (points instanceof OvalRoi) ? -0.5 : 0;

			double x = points.getFloatBounds().x + pixelOriginOffset + points
					.getFloatBounds().width / 2;
			double y = points.getFloatBounds().y + pixelOriginOffset + points
					.getFloatBounds().height / 2;

			if (MarsImageUtils.intervalContains(regionIntervals.get(region), x, y))
				integrationListsMap.get(region).put(UID, new Peak(x, y));
		}

		// Build integration lists for all T for all colors.
		for (int i = 0; i < channelColors.size(); i++) {
			MutableModuleItem<String> channel = channelColors.get(i);
			String regionOption = channel.getValue(this);

			if (regionOption.equals("All")) {
				for (String region : regionIntervals.keySet())
					addIntegrationMap(channel.getName() + "_" + region, i, regionIntervals
						.get(region), createColorIntegrationList(channel.getName(),
							integrationListsMap.get(region)));
			}
			else if (regionIntervals.containsKey(regionOption)) {
				addIntegrationMap(channel.getName() + "_" + regionOption, i,
					regionIntervals.get(regionOption), createColorIntegrationList(channel
						.getName(), integrationListsMap.get(regionOption)));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> void integratePeaksInT(int c,
		int t)
	{
		RandomAccessibleInterval<T> img = MarsImageUtils.get2DHyperSlice(
			(ImgPlus<T>) dataset.getImgPlus(), 0, c, t);

		for (IntegrationMap integrationMap : peakIntegrationMaps)
			if (integrationMap.getC() == c) MarsImageUtils.integratePeaks(img,
				integrationMap.getInterval(), new ArrayList<>(integrationMap
							.getMap().get(t).values()), innerRadius, outerRadius, verbose);
	}

	private Map<Integer, Map<String, Peak>> createColorIntegrationList(
		String name, Map<String, Peak> peakMap)
	{
		Map<Integer, Map<String, Peak>> tToPeakList = new HashMap<>();

		for (MarsOMEChannel channel : marsOMEMetadata.getImage(0).getChannels()
			.values())
			if (channel.getName().startsWith(name)) {
				int channelIndex = channel.getChannelIndex();
				marsOMEMetadata.getImage(0).planes().filter(plane -> plane
					.getC() == channelIndex).forEach(plane -> tToPeakList.put(plane.getT(), duplicateMap(peakMap)));
			}

		return tToPeakList;
	}

	private Map<String, Peak> duplicateMap(Map<String, Peak> peakList) {
		Map<String, Peak> newList = new HashMap<>();
		for (String UID : peakList.keySet())
			newList.put(UID, new Peak(peakList.get(UID)));
		return newList;
	}

	private void buildMolecule(String UID, int imageIndex,
		Map<Integer, Map<Integer, Double>> channelToTtoDtMap, boolean containsDt)
	{
		MarsTable table = new MarsTable();

		table.add(new DoubleColumn("T"));
		for (IntegrationMap integrationMap : peakIntegrationMaps) {
			String name = integrationMap.getName();
			if (containsDt) table.add(new DoubleColumn(name + "_Time_(s)"));
			table.add(new DoubleColumn(name + "_X"));
			table.add(new DoubleColumn(name + "_Y"));
			table.add(new DoubleColumn(name));
			table.add(new DoubleColumn(name + "_Median_Background"));
			if (verbose) {
				table.add(new DoubleColumn(name + "_Uncorrected"));
				table.add(new DoubleColumn(name + "_Mean_Background"));
			}
		}

		for (int t = 0; t < marsOMEMetadata.getImage(0).getSizeT(); t++) {
			table.appendRow();
			int row = table.getRowCount() - 1;
			table.set("T", row, (double) t);

			for (IntegrationMap integrationMap : peakIntegrationMaps) {
				String name = integrationMap.getName();
				if (integrationMap.getMap().containsKey(t) && integrationMap.getMap()
					.get(t).containsKey(UID))
				{
					Peak peak = integrationMap.getMap().get(t).get(UID);
					if (containsDt) table.setValue(name + "_Time_(s)", row, channelToTtoDtMap.get(
						integrationMap.getC()).get(t));
					table.setValue(name + "_X", row, peak.getX());
					table.setValue(name + "_Y", row, peak.getY());
					table.setValue(name, row, peak.getIntensity());
					table.setValue(name + "_Median_Background", row, peak
						.getMedianBackground());
					if (verbose) {
						table.setValue(name + "_Uncorrected", row, peak.getProperties().get(
							Peak.UNCORRECTED_INTENSITY));
						table.setValue(name + "_Mean_Background", row, peak
							.getMeanBackground());
					}
				}
				else {
					if (containsDt) table.setValue(name + "_Time_(s)", row, Double.NaN);
					table.setValue(name + "_X", row, Double.NaN);
					table.setValue(name + "_Y", row, Double.NaN);
					table.setValue(name, row, Double.NaN);
					table.setValue(name + "_Median_Background", row, Double.NaN);
					if (verbose) {
						table.setValue(name + "_Uncorrected", row, Double.NaN);
						table.setValue(name + "_Mean_Background", row, Double.NaN);
					}
				}
			}
		}

		SingleMolecule molecule = new SingleMolecule(UID, table);

		molecule.setImage(imageIndex);
		molecule.setMetadataUID(marsOMEMetadata.getUID());

		archive.put(molecule);
		progressInteger.incrementAndGet();
	}

	private static class IntegrationMap {

		private final String name;
		private final int c;
		private final Interval interval;
		private final Map<Integer, Map<String, Peak>> peakMap;

		IntegrationMap(final String name, final int c, final Interval interval,
			final Map<Integer, Map<String, Peak>> peakMap)
		{
			this.name = name;
			this.c = c;
			this.interval = interval;
			this.peakMap = peakMap;
		}

		String getName() {
			return name;
		}

		int getC() {
			return c;
		}

		Interval getInterval() {
			return interval;
		}

		Map<Integer, Map<String, Peak>> getMap() {
			return peakMap;
		}
	}

	/**
	 * This method accepts maps the specify peak locations that should be
	 * integrated in the form of a map first to T and then a Map From UID to Peak.
	 * 
	 * @param name Name of the peaks, usually the color.
	 * @param c The channel index to integrate.
	 * @param interval The interval used for integration. Beyond will be mirrored.
	 * @param integrationMap Map from T to Map from UID to Peak.
	 */
	public void addIntegrationMap(final String name, final int c,
		final Interval interval,
		final Map<Integer, Map<String, Peak>> integrationMap)
	{
		// Make sure all entries have a unique name and channel
		// by replacing existing entries with new ones.
		for (int index = 0; index < peakIntegrationMaps.size(); index++) {
			IntegrationMap m = peakIntegrationMaps.get(index);
			if (m.getName().equals(name) && m.getC() == c) {
				peakIntegrationMaps.remove(index);
				index--;
			}
		}

		peakIntegrationMaps.add(new IntegrationMap(name, c, interval,
				integrationMap));
	}

	@SuppressWarnings("unused")
	protected void openWebPage() {
		try {
			String urlString =
				"https://duderstadt-lab.github.io/mars-docs/docs/image/MoleculeIntegratorMultiView/";
			URL url = new URL(urlString);
			platformService.open(url);
		}
		catch (Exception e) {
			// do nothing
		}
	}

	public int getNumberOfIntegrationMaps() {
		return peakIntegrationMaps.size();
	}

	@SuppressWarnings("unused")
	public Map<Integer, Map<String, Peak>> getIntegrationMap(String name, int c) {
		Optional<IntegrationMap> peakMap = peakIntegrationMaps.stream().filter(
			m -> m.getName().equals(name) && m.getC() == c).findFirst();
		return peakMap.map(IntegrationMap::getMap).orElse(null);
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

		builder.addParameter("Microscope", microscope);
		builder.addParameter("Inner radius", String.valueOf(innerRadius));
		builder.addParameter("Outer radius", String.valueOf(outerRadius));
		for (String region : regionIntervals.keySet()) {
			String interval = "[" + regionIntervals.get(region).min(0) + " " +
				regionIntervals.get(region).max(0) + " " + regionIntervals.get(region)
					.min(1) + " " + regionIntervals.get(region).min(1) + "]";
			builder.addParameter(region + " interval", interval);
		}
		for (String regionBoundaryName : regionBoundaryInputItems.keySet())
			builder.addParameter(regionBoundaryName, regionBoundaryInputItems.get(regionBoundaryName).getValue(this));
		if (channelColors != null) for (MutableModuleItem<String> channel : channelColors) {
			builder.addParameter(channel.getName() + " integration regions", channel
					.getValue(this));
		}
		if (channelColors != null && marsOMEMetadata != null) channelColors.forEach(channel -> builder
			.addParameter(channel.getName(), channel.getValue(this)));
		builder.addParameter("ImageID", imageID);
		builder.addParameter("Thread count", nThreads);
		builder.addParameter("Metadata UID source", metadataUIDSource);
		builder.addParameter("Verbose", String.valueOf(verbose));
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

	public void setMicroscope(String microscope) {
		this.microscope = microscope;
	}

	public String getMicroscope() {
		return microscope;
	}

	public void setInnerRadius(int innerRadius) {
		this.innerRadius = innerRadius;
	}

	public int getInnerRadius() {
		return innerRadius;
	}

	public void setOuterRadius(int outerRadius) {
		this.outerRadius = outerRadius;
	}

	public int getOuterRadius() {
		return outerRadius;
	}

	public void setRegionBoundaries(String name, int x0, int y0, int width,
		int height)
	{
		regionIntervals.put(name, Intervals.createMinMax(x0, y0, x0 + width - 1,
			y0 + height - 1));
	}

	@SuppressWarnings("unused")
	public void setRegionInterval(String name, Interval interval) {
		regionIntervals.put(name, interval);
	}

	public Map<String, Interval> getIntervals() {
		return regionIntervals;
	}

	public Interval getInterval(String name) {
		return regionIntervals.get(name);
	}

	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}

	public int getThreads() {
		return this.nThreads;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean getVerbose() {
		return verbose;
	}

	public void setMetadataUIDSource(String metadataUIDSource) {
		this.metadataUIDSource = metadataUIDSource;
	}

	public String getMetadataUIDSource() {
		return this.metadataUIDSource;
	}
}
