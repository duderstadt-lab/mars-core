package de.mpg.biochem.sdmm.ImageProcessing;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

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

import de.mpg.biochem.sdmm.molecule.*;
import de.mpg.biochem.sdmm.table.*;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import net.imagej.ops.Initializable;

/**
 * Command for integrating the fluorescence signal from peaks. Two types of inputs are possible. 
 * A list of peaks for integration can be provided as PointRois in the RoiManger with the format UID_TOP or UID_BOT. 
 * In this case, the positions given will be integrated for all slices with different colors divided into different columns based on the
 * Meta data information. Alternatively, a {@link MoleculeArchive} can be provided in which case the x, y positions for all molecules 
 * will be integrated and added to each molecule record with color information gathered in a frame by frame manner based on the meta data with 
 * different colors placed in different columns.
 * 
 * @author Karl Duderstadt
 * 
 */
@Plugin(type = Command.class, label = "Molecule Integrator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image Processing", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Molecule Integrator", weight = 30, mnemonic = 'm')})
public class MoleculeIntegratorCommand extends DynamicCommand implements Command {
	//GENERAL SERVICES NEEDED
	@Parameter(required=false)
	private RoiManager roiManager;
	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
    
	@Parameter
    private ResultsTableService resultsTableService;
	
	//INPUT IMAGE
	@Parameter(label = "Image for Integration")
	private ImagePlus image; 
	
    @Parameter(label="Get peaks from:", choices = {"RoiMananger", "MoleculeArchive"})
	private String peaklistInput = "RoiManager";
	
	@Parameter(label="Molecule Archive", type = ItemIO.BOTH, required=false)
	private MoleculeArchive archive;
	
	@Parameter(label="Inner Radius")
	private int innerRadius = 1;
	
	@Parameter(label="Outer Radius")
	private int outerRadius = 3;
	
	@Parameter(label = "Use DualView Regions")
	private boolean dualView = true;
	
    @Parameter(label="Dichroic", choices = {"635lpxr", "570lpxr"})
	private String dichroic = "635lpxr";
	
    @Parameter(label="FRET")
	private boolean FRET = false;
    
	//ROI SETTINGS
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String integrationBoundariesMessage =
		"These will define integration boundaries for each part of the dual view.";
	
	@Parameter(label="TOP x0")
	private int TOPx0 = 0;
	
	@Parameter(label="TOP y0")
	private int TOPy0 = 0;
	
	@Parameter(label="TOP width")
	private int TOPwidth = 1024;
	
	@Parameter(label="TOP height")
	private int TOPheight = 500;
	
	@Parameter(label="BOTTOM x0")
	private int BOTTOMx0 = 0;
	
	@Parameter(label="BOTTOM y0")
	private int BOTTOMy0 = 524;
	
	@Parameter(label="BOTTOM width")
	private int BOTTOMwidth = 1024;
	
	@Parameter(label="BOTTOM height")
	private int BOTTOMheight = 500;
	
	@Parameter(label="Microscope")
	private String microscope = "Dobby";
	
	@Parameter(label="Format", choices = { "None", "MicroManager", "NorPix"})
	private String imageFormat = "MicroManager";
	
	//For each slice there will be a Map of UID to peak.. This slice maps are stored in a 
	//larger map with keys corresponding to the slice numbers.
	private ConcurrentMap<Integer, ConcurrentMap<String, FPeak>> IntensitiesStack;
	
	//A map that will hold all the metadata from individual frames as they are processed
	//this will contain the 'label' information from each image header
	private ConcurrentMap<Integer, String> metaDataStack;
	
	//For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	
	//Used for making JsonParser isntances..
    //We make it static becasue we just need to it make parsers so we don't need multiple copies..
    private static JsonFactory jfactory = new JsonFactory();
	
	@Override
	public void run() {	
		Rectangle topBoundingRegion = new Rectangle(TOPx0, TOPy0, TOPwidth, TOPheight);
		Rectangle bottomBoundingRegion = new Rectangle(BOTTOMx0, BOTTOMy0, BOTTOMwidth, BOTTOMheight);
		
		//Initialize the maps
		IntensitiesStack = new ConcurrentHashMap<>(image.getStackSize());
		metaDataStack = new ConcurrentHashMap<>(image.getStackSize());
		
		if (peaklistInput.equals("RoiManager")) {
			//These are assumed to be PointRois with Names of the format
			//UID_TOP or UID_BOT...
			//We assume the same positions are integrated in all frames...
			Roi[] rois = roiManager.getRoisAsArray();
			
			ConcurrentMap<String, FPeak> integrationList = new ConcurrentHashMap<String, FPeak>();
			
	        for (int i=0;i<rois.length;i++) {
	        	//split UID from TOP or BOT
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
	        	
	        	if (subStrings[1].equals("TOP")) {
	        		peak.setTOPXY(rois[i].getFloatBounds().x - 0.5, rois[i].getFloatBounds().y - 0.5);
	        	} else if (subStrings[1].equals("BOT")) {
	        		peak.setBOTXY(rois[i].getFloatBounds().x - 0.5, rois[i].getFloatBounds().y - 0.5);
	        	} else {
	        		//This is the wrong type of Rois...
	        		//Should throw an error.
	        		return;
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
		} else {
			//Means we are using the molecule archive to build a peak list for integration...
			//TO DO build the integration maps from the molecule archive...
		}
		
		MoleculeIntegrator integrator = new MoleculeIntegrator(innerRadius, outerRadius, topBoundingRegion, bottomBoundingRegion);
			
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
	    try {
	    	//Start a thread to keep track of the progress of the number of frames that have been processed.
	    	//Waiting call back to update the progress bar!!
	    	Thread progressThread = new Thread() {
	            public synchronized void run() {
                    try {
        		        while(progressUpdating.get()) {
        		        	Thread.sleep(100);
        		        	statusService.showStatus(IntensitiesStack.size(), image.getStackSize(), "Integrating Molecules for " + image.getTitle());
        		        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
	            }
	        };

	        progressThread.start();
	        
	        //This will spawn a bunch of threads that will analyze frames individually in parallel and put the results into the PeakStack map as lists of
	        //peaks with the slice number as a key in the map for each list...
	        forkJoinPool.submit(() -> IntStream.rangeClosed(1, image.getStackSize()).parallel().forEach(slice -> {
	        	//First we add the header information to the metadata map 
				//to generate the metadata table later in the molecule archive.
				String label = image.getStack().getSliceLabel(slice);
				metaDataStack.put(slice, label);
	        	
				String[] colors = getColors(label);
				
	        	//We also pass the color names.. For a give frame there can only be two colors..
	        	integrator.integratePeaks(image.getStack().getProcessor(slice), IntensitiesStack.get(slice), colors[0], colors[1]);
	        })).get();
	        
	        progressUpdating.set(false);
	        
	        statusService.showProgress(100, 100);
	        statusService.showStatus("Peak search for " + image.getTitle() + " - Done!");
	        
	    } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    } finally {
	        forkJoinPool.shutdown();
	    }
	    
	    //Now we need to use the IntensitiesStack to build the molecule archive...
	}
	
	//Get color names based on image meta data
	//If only one laser and not a FRET condition
	//one color will remain null
	//colors[0] is for the top region
	//colors[1] is for the bottom region
	private String[] getColors(String headerLabel) {
		String TOPColor = null;
		String BOTColor = null;
		
		HashMap<String, String> params = getMetaData(headerLabel);
		
		int laserState = Integer.valueOf(params.get("DigitalIO-State"));
		
		//For the moment we ignore this one
		//int ir808 = getBit(laserState, 0);
		
		int red637 = getBit(laserState, 1);
		int yellow561 = getBit(laserState, 2);
		int green532 = getBit(laserState, 3);
		int blue488 = getBit(laserState, 4);
		int purple405 = getBit(laserState, 5);
		
		if (dichroic.equals("635lpxr")) {
			//Only color above 635 cutoff
			if (red637 == 1) {
				TOPColor = "Red_637";
			}
			
			//All colors below cutoff
			if (yellow561 == 1) {
				BOTColor = "Yellow_561";
			} else if (green532 == 1) {
				BOTColor = "Green_532";
			} else if (blue488 == 1) {
				BOTColor = "Blue_488";
			} else if (purple405 == 1) {
				BOTColor = "Purple_405";
			}
			
			//Normal FRET condition for GREEN to RED
			if (green532 == 1 && FRET) {
				TOPColor = "Red_637";
				BOTColor = "Green_532";
			}
		} else if (dichroic.equals("570lpxr")) {
			//Only colors with emission above 570 cutoff
			if (red637 == 1) {
				TOPColor = "Red_637";
			} else if (yellow561 == 1) {
				TOPColor = "Yellow_561";
			} 
			
			if (green532 == 1) {
				//Really should never do this for this dichroic...
				BOTColor = "Green_532";
			} else if (blue488 == 1) {
				BOTColor = "Blue_488";
			} else if (purple405 == 1) {
				BOTColor = "Purple_405";
			}
			
			//blue to yellow FRET...
			if (blue488 == 1 && FRET) {
				TOPColor = "Yellow_561";
				BOTColor = "Blue_488";
			}
		}
		
		//Will hold the TOP and BOT colors
		//in that order
		String[] colors = new String[2];
		colors[0] = TOPColor;
		colors[1] = BOTColor;
		
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
				jParser.nextToken();

				parameters.put(fieldname, jParser.getValueAsString());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return parameters;
	}
}
