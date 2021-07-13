/*
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

package de.mpg.biochem.mars.image.commands;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.Initializable;
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
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.metadata.MarsOMEChannel;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import io.scif.Metadata;
import io.scif.img.SCIFIOImgPlus;
import io.scif.ome.OMEMetadata;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.TranslatorService;
import loci.common.services.ServiceException;
import ome.xml.meta.OMEXMLMetadata;

/**
 * Command for integrating the fluorescence signal from peaks. Input - A list of
 * peaks for integration can be provided as OvalRois or PointRois in the
 * RoiManger. Names should be UIDs. The positions given are integrated for all T 
 * for all colors specified to generate a SingleMoleculeArchive in which all molecule record
 * tables have columns for all integrated colors.
 * 
 * @author Karl Duderstadt
 */
@Plugin(type = Command.class, label = "Molecule Integrator", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
			label = "Image", weight = 20, mnemonic = 'm'), @Menu(
				label = "Molecule Integrator", weight = 30, mnemonic = 'm') })
public class MoleculeIntegratorCommand extends DynamicCommand implements
	Command, Initializable
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
	
	/**
	 * ROIs
	 */
	@Parameter(required = false)
	private RoiManager roiManager;
	
	@Parameter(label = "Use ROI", persist = false)
	private boolean useROI = true;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image for Integration")
	private ImageDisplay imageDisplay;

	@Parameter(label = "Inner Radius")
	private int innerRadius = 1;

	@Parameter(label = "Outer Radius")
	private int outerRadius = 3;

	@Parameter(label = "Microscope")
	private String microscope = "Unknown";
	
	@Parameter(label = "Thread count", required = false, min = "1", max = "120")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String channelsTitle = "Channels:";

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;

	/**
	 * List of IntegrationMaps containing T -> UID peak maps, name and channel.
	 */
	private List<IntegrationMap> peakIntegrationMaps = new ArrayList<>();

	private Dataset dataset;
	private ImagePlus image;
	private String imageID;
	
	private Roi roi;

	private final AtomicInteger progressInteger = new AtomicInteger(0);

	private MarsOMEMetadata marsOMEMetadata;

	private List<MutableModuleItem<String>> channelColors;

	private List<String> channelColorOptions = new ArrayList<String>(Arrays
		.asList("Do not integrate", "Integrate"));

	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else if (dataset == null) return;
		
		if (image.getRoi() == null) {
			final MutableModuleItem<Boolean> useRoifield = getInfo().getMutableInput(
				"useROI", Boolean.class);
			useRoifield.setValue(this, false);
		}
		else roi = image.getRoi();

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
			// Do nothing. Many of the omexmlmetadata methods give
			// NullPointerException if fields are not set.
		}

		imageID = omexmlMetadata.getImageID(0);

		String metaUID;
		if (omexmlMetadata.getUUID() != null) metaUID = MarsMath.getUUID58(
			omexmlMetadata.getUUID()).substring(0, 10);
		else metaUID = MarsMath.getUUID58().substring(0, 10);

		marsOMEMetadata = new MarsOMEMetadata(metaUID, omexmlMetadata);
		
		for (int cIndex = 0; cIndex < marsOMEMetadata.getImage(0).getSizeC() ; cIndex++) {
			if (marsOMEMetadata.getImage(0).getChannel(cIndex).getName() == null)
				marsOMEMetadata.getImage(0).getChannel(cIndex).setName(String.valueOf(cIndex));
		}
			
		List<String> channelNames = marsOMEMetadata.getImage(0).channels().map(
				channel -> channel.getName()).collect(Collectors.toList());
			
		channelColors = new ArrayList<MutableModuleItem<String>>();
		channelNames.forEach(name -> {
			final MutableModuleItem<String> channelChoice =
				new DefaultMutableModuleItem<String>(this, name, String.class);
			channelChoice.setChoices(channelColorOptions);
			channelChoice.setValue(this, "Do not integrate");
			channelColors.add(channelChoice);
			getInfo().addInput(channelChoice);
		});
	}

	@Override
	public void run() {
		if (image != null) {
			image.deleteRoi();
			image.setOverlay(null);
		}
		
		// BUILD LOG
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Molecule Integrator");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		// If running in headless mode. Make sure metadata was initialized.
		if (marsOMEMetadata == null) {
			logService.info("Initializing MarsOMEMetadata...");
			initialize();
		}

		if (peakIntegrationMaps.size() > 0) {
			logService.info("Using IntegrationMaps...");
		}
		else if (peakIntegrationMaps.size() == 0 && roiManager == null) {
			logService.info(
				"No ROIs found in RoiManager and no IntegrationMaps were provided. Nothing to integrate.");
			logService.info(LogBuilder.endBlock(false));
			return;
		}
		else if (peakIntegrationMaps.size() == 0 && roiManager != null && roiManager
			.getCount() > 0)
		{
			logService.info("Building integration lists from ROIs in RoiManager");
			buildIntegrationLists();
		}

		double starttime = System.currentTimeMillis();
		logService.info("Integrating Peaks...");
		
		List<Runnable> tasks = new ArrayList<Runnable>();
		marsOMEMetadata.getImage(0).planes().forEach(plane -> {
			tasks.add(() -> {
				integratePeaksInT(plane.getC(), plane.getT());
				progressInteger.incrementAndGet();
			});
		});

		// INTEGRATE PEAKS
		MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
			.showStatus(progressInteger.get(), marsOMEMetadata.getImage(0)
				.getPlaneCount(), "Integrating Molecules in " + dataset.getName()),
			tasks, nThreads);

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");

		// CREATE MOLECULE ARCHIVE
		archive = new SingleMoleculeArchive("archive.yama");
		archive.putMetadata(marsOMEMetadata);

		Map<Integer, Map<Integer, Double>> channelToTtoDtMap =
			buildChannelToTtoDtMap();

		final int imageIndex = marsOMEMetadata.getImage(0).getImageID();

		Set<String> UIDs = new HashSet<String>();
		for (IntegrationMap integrationMap : peakIntegrationMaps)
			for (int t : integrationMap.getMap().keySet())
				UIDs.addAll(integrationMap.getMap().get(t).keySet());
		
		tasks.clear();
		for (String uid : UIDs) {
			tasks.add(() -> buildMolecule(uid, imageIndex, channelToTtoDtMap));
		}

		progressInteger.set(0);
		MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
			.showStatus(progressInteger.get(), UIDs.size(),
				"Adding molecules to archive..."), tasks ,
			nThreads);
		
		if (image != null) image.setRoi(roi);

		// FINISH UP
		statusService.clearStatus();
		logService.info("Finished in " + DoubleRounder.round((System
			.currentTimeMillis() - starttime) / 60000, 2) + " minutes.");
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

	private void buildIntegrationLists() {
		Interval interval = (useROI) ? Intervals.createMinMax(roi.getBounds().x, roi.getBounds().y, roi.getBounds().x + roi.getBounds().width - 1,
				roi.getBounds().y + roi.getBounds().height - 1) : dataset;
		
		// These are assumed to be OvalRois or PointRois
		// we assume the same positions are integrated in all frames...
		Roi[] rois = roiManager.getRoisAsArray();

		Map<String, Peak> integrationList = new HashMap<String, Peak>();

		// Build integration list
		for (int i = 0; i < rois.length; i++) {
			String UID = rois[i].getName();

			// The pixel origin for OvalRois is at the upper left corner !!
			// The pixel origin for PointRois is at the center !!
			// We always use pixel center as origin when integrating peaks !!
			double pixelOrginOffset = (rois[i] instanceof OvalRoi) ? -0.5 : 0;

			double x = rois[i].getFloatBounds().x + pixelOrginOffset + rois[i]
				.getFloatBounds().width / 2;
			double y = rois[i].getFloatBounds().y + pixelOrginOffset + rois[i]
				.getFloatBounds().height / 2;

			Peak peak = new Peak(x, y);

			if (useROI && roi.contains((int)x, (int)y))
				integrationList.put(UID, peak);
			else if (!useROI)
				integrationList.put(UID, peak);
		}

		// Build integration lists for all T for all colors.
		for (int i = 0; i < channelColors.size(); i++) {
			MutableModuleItem<String> channel = channelColors.get(i);
			String colorOption = channel.getValue(this);

			if (colorOption.equals("Integrate")) addIntegrationMap(channel.getName(), i,
				interval, createColorIntegrationList(channel.getName(),
					integrationList));
		}
	}

	private Map<Integer, Map<Integer, Double>> buildChannelToTtoDtMap() {
		Map<Integer, Map<Integer, Double>> channelToTtoDtMap =
			new HashMap<Integer, Map<Integer, Double>>();

		// Let's build some maps from t to dt for each color...
		for (int channelIndex = 0; channelIndex < marsOMEMetadata.getImage(0)
			.getSizeC(); channelIndex++)
		{
			HashMap<Integer, Double> tToDtMap = new HashMap<Integer, Double>();

			final int finalChannelIndex = channelIndex;
			marsOMEMetadata.getImage(0).planes().filter(plane -> plane
				.getC() == finalChannelIndex).forEach(plane -> tToDtMap.put(plane
					.getT(), plane.getDeltaTinSeconds()));

			channelToTtoDtMap.put(channelIndex, tToDtMap);
		}

		return channelToTtoDtMap;
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> void integratePeaksInT(int c,
		int t)
	{
		RandomAccessibleInterval<T> img = MarsImageUtils.get2DHyperSlice(
			(ImgPlus<T>) dataset.getImgPlus(), 0, c, t);

		for (IntegrationMap integrationMap : peakIntegrationMaps)
			if (integrationMap.getC() == c) MarsImageUtils.integratePeaks(img,
				integrationMap.getInterval(), new ArrayList<Peak>(integrationMap
					.getMap().get(t).values()), innerRadius, outerRadius);
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
					.getC() == channelIndex).forEach(plane -> {
						tToPeakList.put(plane.getT(), duplicateMap(peakMap));
					});
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
		Map<Integer, Map<Integer, Double>> channelToTtoDtMap)
	{
		MarsTable table = new MarsTable();

		// Build columns
		List<DoubleColumn> columns = new ArrayList<DoubleColumn>();
		columns.add(new DoubleColumn(Peak.T));

		for (IntegrationMap integrationMap : peakIntegrationMaps) {
			String name = integrationMap.getName();
			columns.add(new DoubleColumn(name + " Time (s)"));
			columns.add(new DoubleColumn(name + " X"));
			columns.add(new DoubleColumn(name + " Y"));
			columns.add(new DoubleColumn(name));
			columns.add(new DoubleColumn(name + " Background"));
		}

		for (DoubleColumn column : columns)
			table.add(column);

		for (int t = 0; t < marsOMEMetadata.getImage(0).getSizeT(); t++) {
			table.appendRow();
			int row = table.getRowCount() - 1;
			table.set(Peak.T, row, (double) t);

			for (IntegrationMap integrationMap : peakIntegrationMaps) {
				String name = integrationMap.getName();
				if (integrationMap.getMap().containsKey(t) && integrationMap.getMap()
					.get(t).containsKey(UID))
				{
					Peak peak = integrationMap.getMap().get(t).get(UID);
					table.setValue(name, row, channelToTtoDtMap.get(integrationMap.getC())
						.get(t));
					table.setValue(name, row, peak.getIntensity());
					table.setValue(name + " Background", row, peak.getMedianBackground());
					table.setValue(name + " X", row, peak.getX());
					table.setValue(name + " Y", row, peak.getY());
				}
				else {
					table.setValue(name + " Time (s)", row, Double.NaN);
					table.setValue(name, row, Double.NaN);
					table.setValue(name + " Background", row, Double.NaN);
					table.setValue(name + " X", row, Double.NaN);
					table.setValue(name + " Y", row, Double.NaN);
				}
			}
		}

		SingleMolecule molecule = new SingleMolecule(UID, table);

		molecule.setImage(imageIndex);
		molecule.setMetadataUID(marsOMEMetadata.getUID());

		archive.put(molecule);
		progressInteger.incrementAndGet();
	}

	private class IntegrationMap {

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

	public int getNumberOfIntegrationMaps() {
		return peakIntegrationMaps.size();
	}

	public Map<Integer, Map<String, Peak>> getIntegrationMap(String name, int c) {
		Optional<IntegrationMap> peakMap = peakIntegrationMaps.stream().filter(
			m -> m.getName().equals(name) && m.getC() == c).findFirst();
		if (peakMap.isPresent()) return peakMap.get().getMap();
		else return null;
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
		else {
			builder.addParameter("Dataset Name", dataset.getName());
		}
		builder.addParameter("Use ROI", String.valueOf(useROI));
		if (useROI && roi != null) builder.addParameter("ROI", roi.toString());

		builder.addParameter("Microscope", microscope);
		builder.addParameter("Inner radius", String.valueOf(innerRadius));
		builder.addParameter("Outer radius", String.valueOf(outerRadius));
		if (marsOMEMetadata != null) channelColors.forEach(channel -> builder
			.addParameter(channel.getName(), channel.getValue(this)));
		builder.addParameter("ImageID", imageID);
		builder.addParameter("Thread count", nThreads);
	}

	// Getters and Setters
	public SingleMoleculeArchive getArchive() {
		return archive;
	}
	
	public void setRoiManager(RoiManager roiManager) {
		this.roiManager = roiManager;
	}
	
	public RoiManager getRoiManager() {
		return roiManager;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public Dataset getDataset() {
		return dataset;
	}
	
	public void setUseRoi(boolean useROI) {
		this.useROI = useROI;
	}

	public boolean getUseRoi() {
		return useROI;
	}
	
	public void setRoi(Roi roi) {
		this.roi = roi;
	}
	
	public Roi getROI() {
		return this.roi;
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
	
	/**
	 * Method used to set the channels that will be integrated in a script.
	 * Integration types are "Do not integrate", "Both", "Short" or "Long"
	 * 
	 * @param channel Index of the channel to integrate.
	 * @param integrationType The type of integration to perform.
	 */
	public void setIntegrationChannel(int channel, String integrationType) {
		channelColors.get(channel).setValue(this, integrationType);
	}
	
	/**
	 * Method used to set the channels that will be integrated in a script.
	 * Integration types are "Do not integrate", "Both", "Short" or "Long"
	 * 
	 * @param channelName Name of the channel to integrate.
	 * @param integrationType The type of integration to perform.
	 */
	public void setIntegrationChannel(String channelName, String integrationType) {
		channelColors.stream().filter(channelInput -> channelInput.getName().equals(channelName)).findAny().get().setValue(this, integrationType);
	}
	
	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}
	
	public int getThreads() {
		return this.nThreads;
	}
}
