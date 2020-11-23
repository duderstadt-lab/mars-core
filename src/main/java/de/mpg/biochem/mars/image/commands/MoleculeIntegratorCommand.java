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
 * peaks for integration can be provided as OvalRois or PointRois in the RoiManger with the
 * format UID_LONG or UID_SHORT for long and short wavelengths. The positions
 * given are integrated for all T for all colors specified to generate a 
 * SingleMoleculeArchive in which all molecule record tables have columns for
 * all integrated colors.
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
	 * IMAGE
	 */
	@Parameter(label = "Image for Integration")
	private ImageDisplay imageDisplay;

	@Parameter(label = "Inner Radius")
	private int innerRadius = 1;

	@Parameter(label = "Outer Radius")
	private int outerRadius = 3;

	/**
	 * ROIs
	 */
	@Parameter(required = false)
	private RoiManager roiManager;
	
	@Parameter(label = "LONG x0")
	private int LONGx0 = 0;

	@Parameter(label = "LONG y0")
	private int LONGy0 = 0;

	@Parameter(label = "LONG width")
	private int LONGwidth = 1024;

	@Parameter(label = "LONG height")
	private int LONGheight = 500;

	@Parameter(label = "SHORT x0")
	private int SHORTx0 = 0;

	@Parameter(label = "SHORT y0")
	private int SHORTy0 = 524;

	@Parameter(label = "SHORT width")
	private int SHORTwidth = 1024;

	@Parameter(label = "SHORT height")
	private int SHORTheight = 500;

	@Parameter(label = "Microscope")
	private String microscope = "Unknown";

	@Parameter(label = "FRET short wavelength name")
	private String fretShortName = "Green";

	@Parameter(label = "FRET long wavelength name")
	private String fretLongName = "Red";

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
	private Interval longInterval;
	private Interval shortInterval;
	//private List<String> channelNames;
	
	private final AtomicInteger progressInteger = new AtomicInteger(0);

	private MarsOMEMetadata marsOMEMetadata;

	private List<MutableModuleItem<String>> channelColors;

	private List<String> channelColorOptions = new ArrayList<String>(Arrays
		.asList("None", "FRET", "Short", "Long"));

	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else if (dataset == null) return;

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
				logService.info("Unable to extract OME Metadata. Generating OME metadata from dimensions.");
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

		List<String> channelNames = marsOMEMetadata.getImage(0).channels().map(
			channel -> channel.getName()).collect(Collectors.toList());
		channelColors = new ArrayList<MutableModuleItem<String>>();
		channelNames.forEach(name -> {
			final MutableModuleItem<String> channelChoice =
				new DefaultMutableModuleItem<String>(this, name, String.class);
			channelChoice.setChoices(channelColorOptions);
			channelColors.add(channelChoice);
			getInfo().addInput(channelChoice);
		});
	}

	@Override
	public void run() {
		longInterval = Intervals.createMinMax(LONGx0, LONGy0, LONGx0 + LONGwidth - 1, LONGy0 + LONGheight - 1);
		shortInterval = Intervals.createMinMax(SHORTx0, SHORTy0, SHORTx0 + SHORTwidth - 1, SHORTy0 + SHORTheight - 1);
		
		// BUILD LOG
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Molecule Integrator");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);
		
		//If running in headless mode. Make sure metadata was initialized.
		if (marsOMEMetadata == null) {
			logService.info("Initializing MarsOMEMetadata...");
			initialize();
		}
		
		if (peakIntegrationMaps.size() > 0) {
			logService.info("Using IntegrationMaps...");
		} else if (peakIntegrationMaps.size() == 0 && roiManager == null) {
			logService.info("No ROIs found in RoiManager and no IntegrationMaps were provided. Nothing to integrate.");
			logService.info(LogBuilder.endBlock(false));
			return;	
		} else if (peakIntegrationMaps.size() == 0 && roiManager != null && roiManager.getCount() > 0) {
			logService.info("Building integration lists from ROIs in RoiManager");
			buildIntegrationLists();
		}

		double starttime = System.currentTimeMillis();
		logService.info("Integrating Peaks...");
		
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		// INTEGRATE PEAKS
		MarsUtil.forkJoinPoolBuilder(statusService, logService,
				() -> statusService.showStatus(progressInteger.get(), marsOMEMetadata
						.getImage(0).getPlaneCount(),
						"Integrating Molecules in " + dataset.getName()), () -> marsOMEMetadata.getImage(0).planes().forEach(
							plane -> {
								integratePeaksInT(plane.getC(), plane.getT());
								progressInteger.incrementAndGet();
							}), PARALLELISM_LEVEL);
		
		logService.info("Time: " + DoubleRounder.round((System
			.currentTimeMillis() - starttime) / 60000, 2) + " minutes.");
		
		// CREATE MOLECULE ARCHIVE
		archive = new SingleMoleculeArchive("archive.yama");
		archive.putMetadata(marsOMEMetadata);

		Map<Integer, Map<Integer, Double>> channelToTtoDtMap = buildChannelToTtoDtMap();
		
		final int imageIndex = marsOMEMetadata.getImage(0).getImageID();
		
		Set<String> UIDs = new HashSet<String>();
		for (IntegrationMap integrationMap : peakIntegrationMaps)
			for (int t : integrationMap.getMap().keySet())
				UIDs.addAll(integrationMap.getMap().get(t).keySet());

		progressInteger.set(0);
		MarsUtil.forkJoinPoolBuilder(statusService, logService,
				() -> statusService.showStatus(progressInteger.get(), UIDs.size(), "Adding molecules to archive..."), 
				() -> UIDs.parallelStream().forEach(UID -> 
						buildMolecule(UID, imageIndex, channelToTtoDtMap)), PARALLELISM_LEVEL);

		archive.naturalOrderSortMoleculeIndex();
		
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
		Rectangle longBoundingRegion = new Rectangle(LONGx0, LONGy0, LONGwidth,
				LONGheight);
		Rectangle shortBoundingRegion = new Rectangle(SHORTx0, SHORTy0, SHORTwidth,
				SHORTheight);
			
		// These are assumed to be OvalRois or PointRois with names of the format
		// UID or UID_LONG or UID_SHORT...
		// We assume the same positions are integrated in all frames...
		Roi[] rois = roiManager.getRoisAsArray();

		Map<String, Peak> shortIntegrationList = new HashMap<String, Peak>();
		Map<String, Peak> longIntegrationList = new HashMap<String, Peak>();

		// Build single T integration lists for short and long wavelengths.
		for (int i = 0; i < rois.length; i++) {
			// split UID from LONG or SHORT
			String[] subStrings = rois[i].getName().split("_");
			String UID = subStrings[0];
			
			//The pixel origin for OvalRois is at the upper left corner !!
			//The pixel origin for PointRois is at the center !!
			//We always use pixel center as origin when integrating peaks !!
			double pixelOrginOffset = (rois[i] instanceof OvalRoi) ? -0.5 : 0;

			double x = rois[i].getFloatBounds().x + pixelOrginOffset + rois[i].getFloatBounds().width/2;
			double y = rois[i].getFloatBounds().y + pixelOrginOffset + rois[i].getFloatBounds().height/2;

			Peak peak = new Peak(UID, x, y);

			if (subStrings.length > 1) {
				if (subStrings[1].equals("LONG") && longBoundingRegion.contains(x, y)) longIntegrationList.put(UID, peak);
				else if (subStrings[1].equals("SHORT") && shortBoundingRegion.contains(x, y)) shortIntegrationList.put(UID,
					peak);
			} else {
				if (longBoundingRegion.contains(x, y)) longIntegrationList.put(UID, peak);
				else if (shortBoundingRegion.contains(x, y)) shortIntegrationList.put(UID, peak);
			}
		}

		// Build integration lists for all T for all colors.
		for (int i = 0; i < channelColors.size(); i++) {
			MutableModuleItem<String> channel = channelColors.get(i);
			String colorOption = channel.getValue(this);

			if (colorOption.equals("Short")) 
				addIntegrationMap(channel.getName(), i, 0, createColorIntegrationList(channel.getName(), shortIntegrationList));
			else if (colorOption.equals("Long"))
				addIntegrationMap(channel.getName(), i, 1, createColorIntegrationList(channel.getName(), longIntegrationList));
			else if (colorOption.equals("FRET")) {
				addIntegrationMap(channel.getName() + " " + fretShortName, i, 0, createColorIntegrationList(channel.getName(), shortIntegrationList));
				addIntegrationMap(channel.getName() + " " + fretLongName, i, 1, createColorIntegrationList(channel.getName(), longIntegrationList));
			}
		}
	}
	
	private Map<Integer, Map<Integer, Double>> buildChannelToTtoDtMap() {
		Map<Integer, Map<Integer, Double>> channelToTtoDtMap = new HashMap<Integer, Map<Integer, Double>>();

		// Let's build some maps from t to dt for each color...
		for (int channelIndex = 0; channelIndex < marsOMEMetadata.getImage(0).getSizeC(); channelIndex++) {
			HashMap<Integer, Double> tToDtMap = new HashMap<Integer, Double>();

			final int finalChannelIndex = channelIndex;
			marsOMEMetadata.getImage(0).planes().filter(plane -> plane.getC() == finalChannelIndex).forEach(plane -> 
				tToDtMap.put(plane.getT(), plane.getDeltaTinSeconds()));

			channelToTtoDtMap.put(channelIndex, tToDtMap);
		}
		
		return channelToTtoDtMap;
	}
	
	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> void integratePeaksInT(int c, int t) {	
		RandomAccessibleInterval<T> img = MarsImageUtils.get2DHyperSlice((ImgPlus< T >) dataset.getImgPlus(), 0, c, t);
		
		for (IntegrationMap integrationMap : peakIntegrationMaps)
			if (integrationMap.getC() == c)
				if (integrationMap.getRegion() == 0)
					MarsImageUtils.integratePeaks(img, shortInterval, new ArrayList<Peak>(integrationMap.getMap().get(t).values()), innerRadius, outerRadius);
				else if (integrationMap.getRegion() == 1)
					MarsImageUtils.integratePeaks(img, longInterval, new ArrayList<Peak>(integrationMap.getMap().get(t).values()), innerRadius, outerRadius);
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
	
	private void buildMolecule(String UID, int imageIndex, Map<Integer, Map<Integer, Double>> channelToTtoDtMap) {
		MarsTable table = new MarsTable();

		// Build columns
		List<DoubleColumn> columns = new ArrayList<DoubleColumn>();
		columns.add(new DoubleColumn("T"));

		for (IntegrationMap integrationMap : peakIntegrationMaps) {
			String name = integrationMap.getName();
			columns.add(new DoubleColumn(name + " Time (s)"));
			columns.add(new DoubleColumn(name + " x"));
			columns.add(new DoubleColumn(name + " y"));
			columns.add(new DoubleColumn(name));
			columns.add(new DoubleColumn(name + " background"));
		}

		for (DoubleColumn column : columns)
			table.add(column);

		for (int t = 0; t < marsOMEMetadata.getImage(0).getSizeT(); t++) {
			table.appendRow();
			int row = table.getRowCount() - 1;
			table.set("T", row, (double) t);

			for (IntegrationMap integrationMap : peakIntegrationMaps) {
				String name = integrationMap.getName();
				if (integrationMap.getMap().containsKey(t) && integrationMap.getMap().get(t).containsKey(UID)) {
					Peak peak = integrationMap.getMap().get(t).get(UID);
					table.setValue(name, row, channelToTtoDtMap.get(integrationMap.getC()).get(t));
					table.setValue(name, row, peak.getIntensity());
					table.setValue(name + " background", row, peak.getMedianBackground());
					table.setValue(name + " x", row, peak.getX());
					table.setValue(name + " y", row, peak.getY());
				}
				else {
					table.setValue(name + " Time (s)", row, Double.NaN);
					table.setValue(name, row, Double.NaN);
					table.setValue(name + " background", row, Double.NaN);
					table.setValue(name + " x", row, Double.NaN);
					table.setValue(name + " y", row, Double.NaN);
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
		private final int region;
		private final Map<Integer, Map<String, Peak>> peakMap;
		
		IntegrationMap(String name, int c, int region, Map<Integer, Map<String, Peak>> peakMap) {
			this.name = name;
			this.c = c;
			this.region = region;
			this.peakMap = peakMap;
		}
		
		String getName() {
			return name;
		}
		
		int getC() {
			return c;
		}
		
		int getRegion() {
			return region;
		}
		
		Map<Integer, Map<String, Peak>> getMap() {
			return peakMap;
		}
	}
	
	/**
	 * This method accepts maps the specify peak locations
	 * that should be integrated in the form of a map first
	 * to T and then a Map From UID to Peak.
	 * 
	 * @param name Name of the peaks, usually the color.
	 * @param c The channel index to integrate.
	 * @param region The region of the ideal to integration. 0 for short and 1 for long.
	 * @param integrationMap Map from T to Map from UID to Peak.
	 */
	public void addIntegrationMap(String name, int c, int region, Map<Integer, Map<String, Peak>> integrationMap) {
		//Make sure all entries have a unique name and channel 
		//by replacing existing entries with new ones.
		for (int index=0; index < peakIntegrationMaps.size(); index++) {
			IntegrationMap m = peakIntegrationMaps.get(index);
			if (m.getName().equals(name) && m.getC() == c) {
				peakIntegrationMaps.remove(index);
				index--;
			}
		}
			
		peakIntegrationMaps.add(new IntegrationMap(name, c, region, integrationMap));
	}
	
	public int getNumberOfIntegrationMaps() {
		return peakIntegrationMaps.size();
	}
	
	public Map<Integer, Map<String, Peak>> getIntegrationMap(String name, int c, int region) {
		Optional<IntegrationMap> peakMap = peakIntegrationMaps.stream().filter(m -> m.getName().equals(name) && m.getC() == c && m.getRegion() == region).findFirst();
		if (peakMap.isPresent())
			return peakMap.get().getMap();
		else
			return null;
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
		} else
			builder.addParameter("Dataset Name", dataset.getName());
		
		builder.addParameter("Microscope", microscope);
		builder.addParameter("Inner Radius", String.valueOf(innerRadius));
		builder.addParameter("Outer Radius", String.valueOf(outerRadius));
		builder.addParameter("LONG x0", String.valueOf(LONGx0));
		builder.addParameter("LONG y0", String.valueOf(LONGy0));
		builder.addParameter("LONG width", String.valueOf(LONGwidth));
		builder.addParameter("LONG height", String.valueOf(LONGheight));
		builder.addParameter("SHORT x0", String.valueOf(SHORTx0));
		builder.addParameter("SHORT y0", String.valueOf(SHORTy0));
		builder.addParameter("SHORT width", String.valueOf(SHORTwidth));
		builder.addParameter("SHORT height", String.valueOf(SHORTheight));
		builder.addParameter("FRET short wavelength name", fretShortName);
		builder.addParameter("FRET short wavelength name", fretLongName);
		if (marsOMEMetadata != null)
			channelColors.forEach(channel -> builder.addParameter(channel.getName(),
				channel.getValue(this)));
		builder.addParameter("ImageID", imageID);
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

	public void setLONGx0(int LONGx0) {
		this.LONGx0 = LONGx0;
	}

	public int getLONGx0() {
		return LONGx0;
	}

	public void setLONGy0(int LONGy0) {
		this.LONGy0 = LONGy0;
	}

	public int getLONGy0() {
		return LONGy0;
	}

	public void setLONGWidth(int LONGwidth) {
		this.LONGwidth = LONGwidth;
	}

	public int getLONGWidth() {
		return LONGwidth;
	}

	public void setLONGHeight(int LONGheight) {
		this.LONGheight = LONGheight;
	}

	public int getLONGHeight() {
		return LONGheight;
	}

	public void setSHORTx0(int SHORTx0) {
		this.SHORTx0 = SHORTx0;
	}

	public int getSHORTx0() {
		return SHORTx0;
	}

	public void setSHORTy0(int SHORTy0) {
		this.SHORTy0 = SHORTy0;
	}

	public int getSHORTy0() {
		return SHORTy0;
	}

	public void setSHORTWidth(int SHORTwidth) {
		this.SHORTwidth = SHORTwidth;
	}

	public int getSHORTWidth() {
		return SHORTwidth;
	}

	public void setSHORTHeight(int SHORTheight) {
		this.SHORTheight = SHORTheight;
	}

	public int getSHORTHeight() {
		return SHORTheight;
	}
}
