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

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.decimal4j.util.DoubleRounder;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.DefaultMutableModuleItem;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.image.MoleculeIntegrator;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEChannel;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import io.scif.Metadata;
import io.scif.img.SCIFIOImgPlus;
import io.scif.ome.OMEMetadata;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.TranslatorService;
import loci.common.services.ServiceException;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.Initializable;
import ome.xml.meta.OMEXMLMetadata;

import org.scijava.table.DoubleColumn;

/**
 * Command for integrating the fluorescence signal from peaks. Input - A list of peaks for integration can be provided as PointRois 
 * in the RoiManger with the format UID_LONG or UID_SHORT for long and short wavelengths. The positions given will be integrated 
 * for all slices with different colors divided into different columns based on the Meta data information. 
 * 
 * @author Karl Duderstadt
 * 
 */
@Plugin(type = Command.class, label = "Molecule Integrator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Molecule Integrator", weight = 30, mnemonic = 'm')})
public class MoleculeIntegratorCommand extends DynamicCommand implements Command, Initializable {

	@Parameter
	private RoiManager roiManager;
	
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
	
	//INPUT IMAGE
	@Parameter(label = "Image for Integration")
	private ImageDisplay imageDisplay; 
	
	@Parameter(label="Inner Radius")
	private int innerRadius = 1;
	
	@Parameter(label="Outer Radius")
	private int outerRadius = 3;
	
	@Parameter(label="LONG x0")
	private int LONGx0 = 0;
	
	@Parameter(label="LONG y0")
	private int LONGy0 = 0;
	
	@Parameter(label="LONG width")
	private int LONGwidth = 1024;
	
	@Parameter(label="LONG height")
	private int LONGheight = 500;
	
	@Parameter(label="SHORT x0")
	private int SHORTx0 = 0;
	
	@Parameter(label="SHORT y0")
	private int SHORTy0 = 524;
	
	@Parameter(label="SHORT width")
	private int SHORTwidth = 1024;
	
	@Parameter(label="SHORT height")
	private int SHORTheight = 500;
	
	@Parameter(label="Microscope")
	private String microscope = "Unknown";
	
	@Parameter(label="FRET short wavelength name")
	private String fretShortName = "Green";
	
	@Parameter(label="FRET long wavelength name")
	private String fretLongName = "Red";
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String channelsTitle =
		"Channels:";
	
	//OUTPUT PARAMETERS
	@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;
	
	//Map containing all peaks organized by color name, then indexT, then UID...
	private Map<String, Map<Integer, Map<String, Peak>>> mapToAllPeaks;
	
	//For the progress thread
	private final AtomicInteger progressInteger = new AtomicInteger(0);
	
	private static String statusMessage = "Integrating Molecules...";

	private Dataset dataset;
	private ImagePlus image;
	private String imageID;
	
	private MarsOMEMetadata marsOMEMetadata;
	
	private List<MutableModuleItem<String>> channelColors;
	
	private List<String> channelColorOptions = new ArrayList<String>( Arrays.asList("None", "FRET", "Short", "Long") );
	
	@Override
	public void initialize() {
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
	        	logService.info("Unable to extract OME Metadata.");
	 		} else {
	 			omexmlMetadata = omeMeta.getRoot();
	 		}
		}
		
		//Ensures that MarsMicromangerFormat correctly sets the ImageID based on the position.
		//Ensures that MarsMicromangerFormat correctly sets the ImageID based on the position.
		try {
			if (omexmlMetadata.getDoubleAnnotationCount() > 0 && omexmlMetadata.getDoubleAnnotationID(0).equals("ImageID")) {
				omexmlMetadata.setImageID("Image:" + omexmlMetadata.getDoubleAnnotationValue(0).intValue(), 0);
			}
		} catch (NullPointerException e) {
			//Do nothing. May of the omexmlmetadata methods give NullPointerException if fields are not set.
		}
		
		imageID = omexmlMetadata.getImageID(0);
		
		String metaUID;
	    if (omexmlMetadata.getUUID() != null)
	    	metaUID = MarsMath.getUUID58(omexmlMetadata.getUUID()).substring(0, 10);
	    else
	    	metaUID = MarsMath.getUUID58().substring(0, 10);
	    
	    marsOMEMetadata = new MarsOMEMetadata(metaUID, omexmlMetadata);
	    
	    List<String> channelNames = marsOMEMetadata.getImage(0).channels().map(channel -> channel.getName()).collect(Collectors.toList());
	    channelColors = new ArrayList<MutableModuleItem<String>>();
	    channelNames.forEach(name -> {
	    	final MutableModuleItem<String> channelChoice = new DefaultMutableModuleItem<String>(this, name, String.class);
	    	channelChoice.setChoices(channelColorOptions);
	    	channelColors.add(channelChoice);
	    	getInfo().addInput(channelChoice);
	    });
	}
	
	@Override
	public void run() {	
		Rectangle longBoundingRegion = new Rectangle(LONGx0, LONGy0, LONGwidth, LONGheight);
		Rectangle shortBoundingRegion = new Rectangle(SHORTx0, SHORTy0, SHORTwidth, SHORTheight);
		
		mapToAllPeaks = new ConcurrentHashMap<>();
		
		//Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Molecule Integrator");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		logService.info(log);
		
		//These are assumed to be PointRois with names of the format
		//UID or UID_LONG or UID_SHORT...
		//We assume the same positions are integrated in all frames...
		Roi[] rois = roiManager.getRoisAsArray();
		
		Map<String, Peak> shortIntegrationList = new HashMap<String, Peak>();
		Map<String, Peak> longIntegrationList = new HashMap<String, Peak>();
		
		//Build integration lists for short and long wavelengths.
        for (int i=0;i<rois.length;i++) {
        	//split UID from LONG or SHORT
        	String[] subStrings = rois[i].getName().split("_");
        	String UID = subStrings[0];

        	double x = rois[i].getFloatBounds().x;
    		double y = rois[i].getFloatBounds().y;
    		
    		Peak peak = new Peak(UID, x - 0.5, y - 0.5);
        	
    		if (longBoundingRegion.contains(x, y))
    			longIntegrationList.put(UID, peak);
    		else if (shortBoundingRegion.contains(x, y))
    			shortIntegrationList.put(UID, peak);
    		
		}
        
        String fretChannelName = null;
        
        //Build integration lists for all time points for each color.
        for (int i=0; i < channelColors.size(); i++) {
        	MutableModuleItem<String> channel = channelColors.get(i);
        	String colorOption = channel.getValue(this);
        	
        	if (colorOption.equals("Short"))
        		mapToAllPeaks.put(channel.getName(), createColorIntegrationList(channel.getName(), shortIntegrationList));
        	else if (colorOption.equals("Long"))
        		mapToAllPeaks.put(channel.getName(), createColorIntegrationList(channel.getName(), longIntegrationList));
        	else if (colorOption.equals("FRET")) {
        		fretChannelName = channel.getName();
        		mapToAllPeaks.put(channel.getName() + " " + fretShortName, 
        				createColorIntegrationList(channel.getName(), shortIntegrationList));

        		mapToAllPeaks.put(channel.getName() + " " + fretLongName, 
        				createColorIntegrationList(channel.getName(), longIntegrationList));
        	}
        }
		
		MoleculeIntegrator integrator = new MoleculeIntegrator(innerRadius, outerRadius);
			
		double starttime = System.currentTimeMillis();
		logService.info("Integrating Peaks...");
		
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
	    try {	
	    	forkJoinPool.submit(() -> marsOMEMetadata.getImage(0).planes().forEach(plane -> {	    		
	    		ImageProcessor processor = image.getImageStack().getProcessor(plane.getPlaneIndex() + 1);

	        	int c = plane.getC();
	        	int t = plane.getT();

	        	String colorName = channelColors.get(c).getName();

	        	if (channelColors.get(c).getValue(this).equals("FRET")) {
	        		integrator.integratePeaks(processor, 
	        				mapToAllPeaks.get(colorName + " " + fretShortName).get(t), shortBoundingRegion);
	        		
	        		integrator.integratePeaks(processor, 
	        				mapToAllPeaks.get(colorName + " " + fretLongName).get(t), longBoundingRegion);
	        	} else if (channelColors.get(c).getValue(this).equals("Short")) {
	        		integrator.integratePeaks(processor, 
	        				mapToAllPeaks.get(colorName).get(t), shortBoundingRegion);
	        	} else if (channelColors.get(c).getValue(this).equals("Long")) {
	        		integrator.integratePeaks(processor, 
	        				mapToAllPeaks.get(colorName).get(t), longBoundingRegion);
	        	}
	        	
	        	progressInteger.incrementAndGet();
	        	statusService.showStatus(progressInteger.get(), marsOMEMetadata.getImage(0).getPlaneCount(), statusMessage);
	        })).get();

	        logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	        
	        //Let's make sure we create a unique archive name...
		    String newName = "archive";
		    int num = 1;
		    while (moleculeArchiveService.getArchive(newName + ".yama") != null) {
		    	newName = "archive" + num;
		    	num++;
		    }
	        archive = new SingleMoleculeArchive(newName + ".yama");
			archive.putMetadata(marsOMEMetadata);
			
			HashMap<String, HashMap<Integer, Double>> channelToTtoDtMap = new HashMap<String, HashMap<Integer, Double>>();
			
			//Let's build some maps from t to dt for each color...
			for (String colorName : mapToAllPeaks.keySet()) {
				HashMap<Integer, Double> tToDtMap = new HashMap<Integer, Double>();
				final String fretCName = fretChannelName;
				
				marsOMEMetadata.getImage(0).planes()
					.filter(plane -> {
						if (fretCName != null && colorName.startsWith(fretCName))
							return channelColors.get(plane.getC()).getName().equals(fretCName);
						else
							return channelColors.get(plane.getC()).getName().equals(colorName);
					})
					.forEach(plane -> {
						tToDtMap.put(plane.getT(), plane.getDeltaTinSeconds());
					});

				channelToTtoDtMap.put(colorName, tToDtMap);
			}
			
			statusMessage = "Adding Molecules to Archive...";
	        progressInteger.set(0);
	        statusService.showStatus(progressInteger.get(), shortIntegrationList.keySet().size(), statusMessage);
	        
	        final int imageIndex = marsOMEMetadata.getImage(0).getImageID();
			
	        //Now we need to use the IntensitiesStack to build the molecule archive...
	        forkJoinPool.submit(() -> shortIntegrationList.keySet().parallelStream().forEach(UID -> {
	        	MarsTable table = new MarsTable();
	        	
	        	//Build columns
	        	List<DoubleColumn> columns = new ArrayList<DoubleColumn>();
	        	columns.add(new DoubleColumn("T"));
	        	
	    		for (String colorName : mapToAllPeaks.keySet()) {
	    			columns.add(new DoubleColumn(colorName + " Time (s)"));
	    			columns.add(new DoubleColumn(colorName));
	    			columns.add(new DoubleColumn(colorName + " background"));
	    		}
	    		
	    		for (DoubleColumn column : columns)
	    			table.add(column);

		        for (int t=0; t<marsOMEMetadata.getImage(0).getSizeT(); t++) {
		        	table.appendRow();
	        		int row = table.getRowCount() - 1;
	        		table.set("T", row, (double)t);
		        	
		        	for (String colorName : mapToAllPeaks.keySet()) {
	        			if (mapToAllPeaks.get(colorName).containsKey(t)) {
	        				Peak peak = mapToAllPeaks.get(colorName).get(t).get(UID);
	        				if (channelToTtoDtMap.get(colorName).containsKey(t))
	        					table.setValue(colorName + " Time (s)", row, channelToTtoDtMap.get(colorName).get(t));
	        				else
	        					table.setValue(colorName + " Time (s)", row, Double.NaN);
	        				table.setValue(colorName, row, peak.getIntensity());
	        				table.setValue(colorName + " background", row, peak.getMedianBackground());
	        			} else {
	        				table.setValue(colorName + " Time (s)", row, Double.NaN);
	        				table.setValue(colorName, row, Double.NaN);
	        				table.setValue(colorName + " background", row, Double.NaN);
	        			}
		        	}
	    		}
	    		
	        	SingleMolecule molecule = new SingleMolecule(UID, table);
	        	
	        	//This command works on single positions but they might have different indexes
	        	molecule.setImage(imageIndex);
	        	molecule.setMetadataUID(marsOMEMetadata.getUID());
	        	if (longIntegrationList.containsKey(UID)) {
	        		molecule.setParameter("x_LONG", longIntegrationList.get(UID).getX());
	        		molecule.setParameter("y_LONG", longIntegrationList.get(UID).getY());
	        	}
        		
        		if (shortIntegrationList.containsKey(UID)) {
        			molecule.setParameter("x_SHORT", shortIntegrationList.get(UID).getX());
        			molecule.setParameter("y_SHORT", shortIntegrationList.get(UID).getY());
        		}
	        	
	        	archive.put(molecule);
	        	
		        progressInteger.incrementAndGet();
		        statusService.showStatus(progressInteger.get(), shortIntegrationList.keySet().size(), statusMessage);
	        })).get();
	        
	        statusService.showStatus(1, 1, "Peak integration for " + image.getTitle() + " - Done!");
	    } catch (InterruptedException | ExecutionException e) {
	    	// handle exceptions
	    	e.printStackTrace();
			logService.info(LogBuilder.endBlock(false));
	    } finally {
	        forkJoinPool.shutdown();
	    }
	    
	    //Reorder Molecules to Natural Order, so there is a common order
	    //if we have to recover..
	    archive.naturalOrderSortMoleculeIndex();
	    
	    //Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", SingleMoleculeArchive.class).setLabel(archive.getName());
        
		statusService.clearStatus();
		
		logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		if (archive.getNumberOfMolecules() == 0) {
			logService.info("No molecules integrated. There must be a problem with your settings or RIOs");
			archive = null;
			logService.info(LogBuilder.endBlock(false));
		} else {
			logService.info(LogBuilder.endBlock(true));
			
			archive.logln(log);
			archive.logln(LogBuilder.endBlock(true));
			archive.logln("   ");			
		}
	}
	
	private Map<Integer, Map<String, Peak>> createColorIntegrationList(String name, Map<String, Peak> peakMap) {
		Map<Integer, Map<String, Peak>> tToPeakList = new HashMap<>();

		for (MarsOMEChannel channel : marsOMEMetadata.getImage(0).getChannels().values())
			if (channel.getName().startsWith(name)) {
				int channelIndex = channel.getChannelIndex();
				marsOMEMetadata.getImage(0).planes().filter(plane -> plane.getC() == channelIndex).forEach(plane -> {
					tToPeakList.put(plane.getT(), duplicateMap(peakMap));
				});
			}

		return tToPeakList;
	}
	
	private Map<String, Peak> duplicateMap(Map<String, Peak> peakList) {
		Map<String, Peak> newList = new HashMap<>();
    	for (String UID: peakList.keySet())
    		newList.put(UID, new Peak(peakList.get(UID)));
		return newList;
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Image Title", image.getTitle());
		if (image.getOriginalFileInfo() != null && image.getOriginalFileInfo().directory != null) {
			builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
		}
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
		channelColors.forEach(channel -> builder.addParameter(channel.getName(), channel.getValue(this)));
		builder.addParameter("ImageID", imageID);
	}
	
	//Getters and Setters
    public SingleMoleculeArchive getArchive() {
    	return archive;
    }
	
	public void setImage(ImagePlus image) {
		this.image = image;
	}
	
	public ImagePlus getImage() {
		return image;
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
	
	public void setRoiManager(RoiManager roiManager) {
		this.roiManager = roiManager;
	}
	
	public RoiManager getRoiManager() {
		return roiManager;
	}
}
