/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
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
package de.mpg.biochem.mars.ImageProcessing;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import net.imagej.ops.Initializable;
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
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image Processing", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Molecule Integrator", weight = 30, mnemonic = 'm')})
public class MoleculeIntegratorCommand extends DynamicCommand implements Command {
	//GENERAL SERVICES NEEDED
	@Parameter
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
	@Parameter(label = "Image for Integration")
	private ImagePlus image; 
	
	//For the moment we just get RoiMode working..
    //@Parameter(label="Get peaks from:", choices = {"RoiMananger", "MoleculeArchive"})
	//private String peaklistInput = "RoiManager";
	
	//@Parameter(label="Molecule Archive", type = ItemIO.BOTH, required=false)
	//private MoleculeArchive archive;
	
	@Parameter(label="Microscope")
	private String microscope = "Dobby";
	
	@Parameter(label="Format", choices = { "None", "MicroManager"})
	private String imageFormat;
	
	@Parameter(label="Colors", choices = {"Detect", "Specify"})
	private String detectColors = "Detect";
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String detectionMessage =
		"Detection is only possible for MicroManager image format.";
	
    @Parameter(label="Dichroic", choices = {"None", "635lpxr", "570lpxr"})
	private String dichroic = "635lpxr";
	
    @Parameter(label="FRET")
	private boolean FRET = false;
	
	@Parameter(label="Inner Radius")
	private int innerRadius = 1;
	
	@Parameter(label="Outer Radius")
	private int outerRadius = 3;
	
	@Parameter(label="Integrate long wavelength")
	private boolean useLongWavelength = true;
	
	@Parameter(label="LONG x0")
	private int LONGx0 = 0;
	
	@Parameter(label="LONG y0")
	private int LONGy0 = 0;
	
	@Parameter(label="LONG width")
	private int LONGwidth = 1024;
	
	@Parameter(label="LONG height")
	private int LONGheight = 500;
	
	@Parameter(label="Long Wavelength Color", required=false)
	private String LONGWavelengthColor = "Red";
	
	@Parameter(label="Integrate short wavelength")
	private boolean useShortWavelength = true;
	
	@Parameter(label="SHORT x0")
	private int SHORTx0 = 0;
	
	@Parameter(label="SHORT y0")
	private int SHORTy0 = 524;
	
	@Parameter(label="SHORT width")
	private int SHORTwidth = 1024;
	
	@Parameter(label="SHORT height")
	private int SHORTheight = 500;
	
	@Parameter(label="Short Wavelength Color", required=false)
	private String SHORTWavelengthColor = "Green";
	
	//OUTPUT PARAMETERS
	@Parameter(label="Molecule Archive", type = ItemIO.OUTPUT)
	private MoleculeArchive archive;
	
	//For each slice there will be a Map of UID to peak.. This slice maps are stored in a 
	//larger map with keys corresponding to the slice numbers.
	private ConcurrentMap<Integer, ConcurrentMap<String, FPeak>> IntensitiesStack;
	
	//A map that will hold all the metadata from individual frames as they are processed
	//this will contain the 'label' information from each image header
	private ConcurrentMap<Integer, String> metaDataStack;
	
	//For the progress thread
	//private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	
	//Used for making JsonParser instances..
    //We make it static because we just need to it make parsers so we don't need multiple copies..
    private static JsonFactory jfactory = new JsonFactory();
    
	//For the progress thread
	//private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	private final AtomicInteger progressInteger = new AtomicInteger(0);
	
	private String statusMessage = "Integrating Molecules...";
	
	@Override
	public void run() {	
		Rectangle longBoundingRegion = new Rectangle(LONGx0, LONGy0, LONGwidth, LONGheight);
		Rectangle shortBoundingRegion = new Rectangle(SHORTx0, SHORTy0, SHORTwidth, SHORTheight);
		
		//Initialize the maps
		IntensitiesStack = new ConcurrentHashMap<>(image.getStackSize());
		metaDataStack = new ConcurrentHashMap<>(image.getStackSize());
		
		ConcurrentHashMap<String, Boolean> colorsTempMap = new ConcurrentHashMap<String, Boolean>(); 
		Set<String> colorsSet = colorsTempMap.newKeySet(); 
		
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Molecule Integrator");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		logService.info(log);
		
		//These are assumed to be PointRois with Names of the format
		//UID or UID_LONG or UID_SHORT...
		//We assume the same positions are integrated in all frames...
		Roi[] rois = roiManager.getRoisAsArray();
		
		ConcurrentMap<String, FPeak> integrationList = new ConcurrentHashMap<String, FPeak>();
		
        for (int i=0;i<rois.length;i++) {
        	//split UID from LONG or SHORT
        	String[] subStrings = rois[i].getName().split("_");
        	String UID = subStrings[0];
        	
        	FPeak peak;
        	
        	if (integrationList.containsKey(UID)) {
        		//point to existing entry...
        		peak = integrationList.get(UID);
        	} else {
        		//create a new entry...
        		peak = new FPeak(UID);
        	}
        	
        	double x = rois[i].getFloatBounds().x;
    		double y = rois[i].getFloatBounds().y;
        	
    		//Here we add the x and y coordinates to the peak 
    		if (useLongWavelength && longBoundingRegion.contains(x, y)) {
    			if (subStrings.length > 1) {
    				if (subStrings[1].equals("LONG"))
    					peak.setLONGXY(x - 0.5, y - 0.5);
    			} else {
    				peak.setLONGXY(x - 0.5, y - 0.5);
    			}
    		}
    		
    		if (useShortWavelength && shortBoundingRegion.contains(x, y)) {
    			if (subStrings.length > 1) {
    				if (subStrings[1].equals("SHORT"))
    					peak.setSHORTXY(x - 0.5, y - 0.5);
    			} else {
    				peak.setSHORTXY(x - 0.5, y - 0.5);
    			}
    		}
    		
        	integrationList.put(UID, peak);
		}
        
        
        IntensitiesStack.put(1, integrationList);
        
        //Now we just copy this integration list for the rest of the frames
        for (int slice=2;slice<=image.getStackSize();slice++) {
        	//have to make a new copy for each slice, so there is a unique FPeak for putting the integration information...
        	ConcurrentMap<String, FPeak> integrationListCopy = new ConcurrentHashMap<String, FPeak>();
        	for (String UID: integrationList.keySet()) {
        		FPeak peakCopy = new FPeak(integrationList.get(UID));
        		integrationListCopy.put(UID, peakCopy);
        	}
        	
        	IntensitiesStack.put(slice, integrationListCopy);
        }
		
		MoleculeIntegrator integrator = new MoleculeIntegrator(innerRadius, outerRadius, longBoundingRegion, shortBoundingRegion);
			
		double starttime = System.currentTimeMillis();
		logService.info("Integrating Peaks...");
		
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		//check in ImageMetaData information exists...
		String testLabel = image.getStack().getSliceLabel(1);
    	if (!imageFormat.equals("None") && testLabel == null) {
			logService.info("No ImageMetaData found. Aborting. The image format setting must be changed to None.");
			logService.info(builder.endBlock(false));
			return;
		}
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
	    try {
	    	
	        //This will spawn a bunch of threads that will analyze frames individually in parallel integrating all peaks
	    	//in the lists in the IntensitiesStack map...
	        forkJoinPool.submit(() -> IntStream.rangeClosed(1, image.getStackSize()).parallel().forEach(slice -> {
	        	//First we get the ImageProcessor for this slice
	        	ImageProcessor processor = image.getStack().getProcessor(slice);
	        	
	        	//IT IS REALLY IMPORTANT THE PROCESSOR IS RETRIEVED BEFORE
	        	//THE LABEL IS RETRIEVED
	        	//In the case of a virtual stack, 
	        	//when the processor is retrieved the label is updated
	        	//SO the processor has to be loaded at lease once for the label information to be correct
	        	//After that it will be stored in the stack object...
	        	
	        	//We add the header information to the metadata map 
				//to generate the metadata table later in the molecule archive.
	        	String label = image.getStack().getSliceLabel(slice);
	        	if (!imageFormat.equals("None")) {
	        		if (label == null) {
	        			logService.info("No ImageMetaData found for frame " + slice + ". The image format setting must be changed.");
	        			return;
	        		}
	        		
					metaDataStack.put(slice, label);
				}
	        	
				String[] colors = new String[2]; 
				
				if (detectColors.equals("Detect")) {
					colors = getColors(label);	
				} else {
					if (useLongWavelength) {
						String longWavelengthColorTrim = LONGWavelengthColor.trim();
					
						if(longWavelengthColorTrim.equals(""))
						longWavelengthColorTrim = null;
					
						colors[0] = longWavelengthColorTrim;
					}
					
					if (useShortWavelength) {
						String shortWavelengthColorTrim = SHORTWavelengthColor.trim();
						
						if(shortWavelengthColorTrim.equals(""))
							shortWavelengthColorTrim = null;
						
						colors[1] = shortWavelengthColorTrim;
					}
				}
				
				
				if (colors[0] != null)
					colorsSet.add(colors[0]);
					
				if (colors[1] != null)
					colorsSet.add(colors[1]);
					
	        	//We also pass the color names.. For a give frame there can only be two colors..
				//If the color value is null then that color is not integrated..
	        	integrator.integratePeaks(processor, IntensitiesStack.get(slice), colors[0], colors[1]);
	        	
	        	progressInteger.incrementAndGet();
	        	statusService.showStatus(progressInteger.get(), image.getStackSize(), statusMessage);
	        })).get();
	        
	        statusMessage = "Building ImageMetaData...";
	        progressInteger.set(0);
	        statusService.showStatus(progressInteger.get(), IntensitiesStack.get(1).keySet().size(), statusMessage);
	        
	        logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	        logService.info("Building ImageMetaData...");
	        
	        //Let's make sure we create a unique archive name...
		    String newName = "archive";
		    int num = 1;
		    while (moleculeArchiveService.getArchive(newName + ".yama") != null) {
		    	newName = "archive" + num;
		    	num++;
		    }
	        archive = new MoleculeArchive(newName + ".yama");
	        
		    MARSImageMetaData metaData = new MARSImageMetaData(image, microscope, imageFormat, metaDataStack);
			archive.putImageMetaData(metaData);
			
			statusMessage = "Adding Molecules to Archive...";
	        progressInteger.set(0);
	        statusService.showStatus(progressInteger.get(), IntensitiesStack.get(1).keySet().size(), statusMessage);
			
	        //Now we need to use the IntensitiesStack to build the molecule archive...
	        forkJoinPool.submit(() -> IntensitiesStack.get(1).keySet().parallelStream().forEach(UID -> {
	        	MARSResultsTable table = new MARSResultsTable();
	        	ArrayList<DoubleColumn> columns = new ArrayList<DoubleColumn>();
	    		
	        	if (useLongWavelength) {
	        		columns.add(new DoubleColumn("x_LONG"));
		    		columns.add(new DoubleColumn("y_LONG"));
	        	} 
	        	
	        	if (useShortWavelength) {
	        		columns.add(new DoubleColumn("x_SHORT"));
		    		columns.add(new DoubleColumn("y_SHORT"));
	        	}
	        	
	    		columns.add(new DoubleColumn("slice"));
	    		
	    		for (String colorName : colorsSet) {
	    			columns.add(new DoubleColumn(colorName));
	    			columns.add(new DoubleColumn(colorName + "_background"));
	    		}
	    		
	    		for (DoubleColumn column : columns)
	    			table.add(column);
	        	
	        	for (int slice=1; slice<=IntensitiesStack.size(); slice++) {
	        		FPeak peak = IntensitiesStack.get(slice).get(UID);
	        		
	        		table.appendRow();
	        		int row = table.getRowCount() - 1;
	        		if (useLongWavelength) {
	        			table.setValue("x_LONG", row, peak.getXLONG());
		        		table.setValue("y_LONG", row, peak.getYLONG());
	        		}
	        		
	        		if (useShortWavelength) {
	        			table.setValue("x_SHORT", row, peak.getXSHORT());
		        		table.setValue("y_SHORT", row, peak.getYSHORT());
	        		}
	        		
	        		table.set("slice", row, (double)slice);
	        		
	        		for (String colorName : colorsSet) {
	        			if (peak.getIntensityList().containsKey(colorName)) {
	        				table.setValue(colorName, row, peak.getIntensity(colorName)[0]);
	        				table.setValue(colorName + "_background", row, peak.getIntensity(colorName)[1]);
	        			} else {
	        				//Should we remove this part...
	        				table.setValue(colorName, row, Double.NaN);
	        				table.setValue(colorName + "_background", row, Double.NaN);
	        			}
	        		}
	        	}
	        	
	        	Molecule molecule = new Molecule(UID, table);
	        	molecule.setImageMetaDataUID(metaData.getUID());
	        	
	        	archive.put(molecule);
	        	
		        progressInteger.incrementAndGet();
		        statusService.showStatus(progressInteger.get(), IntensitiesStack.get(1).keySet().size(), statusMessage);
	        })).get();
	        
	        statusService.showStatus(IntensitiesStack.get(1).keySet().size(), IntensitiesStack.get(1).keySet().size(), "Peak integration for " + image.getTitle() + " - Done!");
	        //statusService.showStatus("Peak integration for " + image.getTitle() + " - Done!");
	    } catch (InterruptedException | ExecutionException e) {
	    	// handle exceptions
	    	e.printStackTrace();
			logService.info(builder.endBlock(false));
	    } finally {
	        forkJoinPool.shutdown();
	    }
	    
	    //Reorder Molecules to Natural Order, so there is a common order
	    //if we have to recover..
	    archive.naturalOrderSortMoleculeIndex();
	    
	    //Make sure the output archive has the correct name
		getInfo().getMutableOutput("archive", MoleculeArchive.class).setLabel(archive.getName());
        
		statusService.clearStatus();
		
		logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		if (archive.getNumberOfMolecules() == 0) {
			logService.info("No molecules integrated. There must be a problem with your settings or RIOs");
			archive = null;
			logService.info(builder.endBlock(false));
		} else {
			logService.info(builder.endBlock(true));

			log += builder.endBlock(true);
			archive.addLogMessage(log);
			archive.addLogMessage("   ");			
		}
	}
	
	//Get color names based on image meta data
	//If only one laser and not a FRET condition
	//one color will remain null
	//colors[0] is for the long region
	//colors[1] is for the short region
	private String[] getColors(String headerLabel) {
		String LONGColor = null;
		String SHORTColor = null;
		
		HashMap<String, String> params = getMetaData(headerLabel);
		
		int laserState = Integer.valueOf(params.get("DigitalIO-State"));
		
		//For the moment we ignore this one
		//int ir808 = getBit(laserState, 0);
		
		int red637 = getBit(laserState, 1);
		int yellow561 = getBit(laserState, 2);
		int green532 = getBit(laserState, 3);
		int blue488 = getBit(laserState, 4);
		int purple405 = getBit(laserState, 5);
		
		if (dichroic.equals("None")) {
			String color = "White";
			
			if (red637 == 1) {
				color = "Red_637";
			} else if (yellow561 == 1) {
				color = "Yellow_561";
			} else if (green532 == 1) {
				color = "Green_532";
			} else if (blue488 == 1) {
				color = "Blue_488";
			} else if (purple405 == 1) {
				color = "Purple_405";
			}
			
			if (useLongWavelength) 
				LONGColor = color;
			else if (useShortWavelength)
				SHORTColor = color;
			
		} else if (dichroic.equals("635lpxr")) {
			//Only color above 635 cutoff
			if (red637 == 1) {
				LONGColor = "Red_637";
			}
			
			//All colors below cutoff
			if (yellow561 == 1) {
				SHORTColor = "Yellow_561";
			} else if (green532 == 1) {
				SHORTColor = "Green_532";
			} else if (blue488 == 1) {
				SHORTColor = "Blue_488";
			} else if (purple405 == 1) {
				SHORTColor = "Purple_405";
			}
			
			//Normal FRET condition for GREEN to RED
			if (green532 == 1 && FRET) {
				LONGColor = "Red_637";
				SHORTColor = "Green_532";
			}
		} else if (dichroic.equals("570lpxr")) {
			//Only colors with emission above 570 cutoff
			if (red637 == 1) {
				LONGColor = "Red_637";
			} else if (yellow561 == 1) {
				LONGColor = "Yellow_561";
			} 
			
			if (green532 == 1) {
				//Really should never do this for this dichroic...
				SHORTColor = "Green_532";
			} else if (blue488 == 1) {
				SHORTColor = "Blue_488";
			} else if (purple405 == 1) {
				SHORTColor = "Purple_405";
			}
			
			//blue to yellow FRET...
			if (blue488 == 1 && FRET) {
				LONGColor = "Yellow_561";
				SHORTColor = "Blue_488";
			}
		}
		
		//Will hold the LONG and SHORT colors
		//in that order
		String[] colors = new String[2];
		if (useLongWavelength) 
			colors[0] = LONGColor;
		else
			colors[0] = null;
		
		if (useShortWavelength)
			colors[1] = SHORTColor;
		else
			colors[1] = null;
		
		return colors;
	}
	
	//Return 0 1 value for kth bit in integer n...
	int getBit(int n, int k) {
	    return (n >> k) & 1;
	}
	
	private HashMap<String, String> getMetaData(String headerLabel) {
		HashMap<String, String> parameters = new HashMap<String, String>();
		
        //Let's get the lasers and filters...
		try {
			JsonParser jParser = jfactory.createParser(headerLabel.substring(headerLabel.indexOf("{")));
			
			//Just to skip to the first field
			jParser.nextToken();
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jParser.getCurrentName();
				
				//Summary is an object, so that will break the loop
				//Therefore, we need to skip over it...
				if ("Summary".equals(fieldname)) {
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
						// We just want to skip over the summary
					}
					//once we have skipped over we just want to continue
					continue;
				}
				
				jParser.nextToken();

				parameters.put(fieldname, jParser.getValueAsString());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return parameters;
	}
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Image Title", image.getTitle());
		if (image.getOriginalFileInfo() != null && image.getOriginalFileInfo().directory != null) {
			builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
		}
		builder.addParameter("Microscope", microscope);
		builder.addParameter("Format", imageFormat);
		builder.addParameter("Colors", detectColors);
		builder.addParameter("Dichroic", dichroic);
		builder.addParameter("FRET", String.valueOf(FRET));
		builder.addParameter("Inner Radius", String.valueOf(innerRadius));
		builder.addParameter("Outer Radius", String.valueOf(outerRadius));
		builder.addParameter("Use Long Wavelength", String.valueOf(useLongWavelength));
		builder.addParameter("LONG x0", String.valueOf(LONGx0));
		builder.addParameter("LONG y0", String.valueOf(LONGy0));
		builder.addParameter("LONG width", String.valueOf(LONGwidth));
		builder.addParameter("LONG height", String.valueOf(LONGheight));
		builder.addParameter("LONG Wavelength Color", LONGWavelengthColor);
		builder.addParameter("Use Short Wavelength", String.valueOf(useShortWavelength));
		builder.addParameter("SHORT x0", String.valueOf(SHORTx0));
		builder.addParameter("SHORT y0", String.valueOf(SHORTy0));
		builder.addParameter("SHORT width", String.valueOf(SHORTwidth));
		builder.addParameter("SHORT height", String.valueOf(SHORTheight));
		builder.addParameter("SHORT Wavelength Color", SHORTWavelengthColor);
	}
	
	//Getters and Setters
    public MoleculeArchive getArchive() {
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
	
	public void setFormat(String imageFormat) {
		this.imageFormat = imageFormat;
	}
	
	public String getFormat() {
		return imageFormat;
	}
	
	public void setColors(String detectColors) {
		this.detectColors = detectColors;
	}
	
	public String getColors() {
		return detectColors;
	}
	
	public void setDichroic(String dichroic) {
		this.dichroic = dichroic;
	}
	
	public String getDichroic() {
		return dichroic;
	}
	
	public void setFRET(boolean FRET) {
		this.FRET = FRET;
	}
	
	public boolean getFRET() {
		return FRET;
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
	
	public void setUseLongWavelength(boolean useLongWavelength) {
		this.useLongWavelength = useLongWavelength;
	}
	
	public boolean getUseLongWavelength() {
		return useLongWavelength;
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
	
	public void setLONGWavelengthColor(String LONGWavelengthColor) {
		this.LONGWavelengthColor = LONGWavelengthColor;
	}
	
	public String getLONGWavelengthColor() {
		return LONGWavelengthColor;
	}
	
	public void setUseShortWavelength(boolean useShortWavelength) {
		this.useShortWavelength = useShortWavelength;
	}
	
	public boolean getUseShortWavelength() {
		return useShortWavelength;
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
	
	public void setSHORTWavelengthColor(String SHORTWavelengthColor) {
		this.SHORTWavelengthColor = SHORTWavelengthColor;
	}
	
	public String getSHORTWavelengthColor() {
		return SHORTWavelengthColor;
	}
	
	public void setRoiManager(RoiManager roiManager) {
		this.roiManager = roiManager;
	}
	
	public RoiManager getRoiManager() {
		return roiManager;
	}
}
