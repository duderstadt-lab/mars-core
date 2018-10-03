package de.mpg.biochem.sdmm.ImageProcessing;

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

import de.mpg.biochem.sdmm.molecule.*;
import de.mpg.biochem.sdmm.table.*;
import de.mpg.biochem.sdmm.util.LogBuilder;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import net.imagej.ops.Initializable;
import net.imagej.table.DoubleColumn;

/**
 * Command for integrating the fluorescence signal from peaks. Input - A list of peaks for integration can be provided as PointRois 
 * in the RoiManger with the format UID_TOP or UID_BOT. The positions given will be integrated for all slices with different colors 
 * divided into different columns based on the Meta data information. 
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
	private int BOTx0 = 0;
	
	@Parameter(label="BOTTOM y0")
	private int BOTy0 = 524;
	
	@Parameter(label="BOTTOM width")
	private int BOTwidth = 1024;
	
	@Parameter(label="BOTTOM height")
	private int BOTheight = 500;
	
	@Parameter(label="Microscope")
	private String microscope = "Dobby";
	
	//@Parameter(label="Format", choices = { "None", "MicroManager", "NorPix"})
	//private String imageFormat = "MicroManager";
	
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
	
	@Override
	public void run() {	
		Rectangle topBoundingRegion = new Rectangle(TOPx0, TOPy0, TOPwidth, TOPheight);
		Rectangle bottomBoundingRegion = new Rectangle(BOTx0, BOTy0, BOTwidth, BOTheight);
		
		//Initialize the maps
		IntensitiesStack = new ConcurrentHashMap<>(image.getStackSize());
		metaDataStack = new ConcurrentHashMap<>(image.getStackSize());
		
		ConcurrentHashMap<String, Boolean> colorsTempMap = new ConcurrentHashMap<String, Boolean>(); 
		Set<String> colorsSet = colorsTempMap.newKeySet(); 
		
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Peak Integrator");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		logService.info(log);
		
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
        	
        	double x = rois[i].getFloatBounds().x;
    		double y = rois[i].getFloatBounds().y;
        	
        	if (subStrings[1].equals("TOP") && topBoundingRegion.contains(x, y)) {
        		peak.setTOPXY(x - 0.5, y - 0.5);
        	} else if (subStrings[1].equals("BOT") && bottomBoundingRegion.contains(x, y)) {
        		peak.setBOTXY(x - 0.5, y - 0.5);
        	} else {
        		//Not inside the regions or not with the correct format.
        		//continue to next peak without adding it.
        		continue;
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
		
		MoleculeIntegrator integrator = new MoleculeIntegrator(innerRadius, outerRadius, topBoundingRegion, bottomBoundingRegion);
			
		double starttime = System.currentTimeMillis();
		logService.info("Integrating Peaks...");
		
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
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
				metaDataStack.put(slice, label);
	        	
				String[] colors = getColors(label);
				
				if (colors[0] != null)
					colorsSet.add(colors[0]);
					
				if (colors[1] != null)
					colorsSet.add(colors[1]);
				
	        	//We also pass the color names.. For a give frame there can only be two colors..
				//If the color value is null then that color is not integrated..
	        	integrator.integratePeaks(processor, IntensitiesStack.get(slice), colors[0], colors[1]);
	        })).get();
	        
	        logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	        logService.info("Building Archive...");
	        
	        //Let's make sure we create a unique archive name...
		    String newName = "archive";
		    int num = 1;
		    while (moleculeArchiveService.getArchive(newName + ".yama") != null) {
		    	newName = "archive" + num;
		    	num++;
		    }
	        archive = new MoleculeArchive(newName + ".yama");
		    
		    ImageMetaData metaData = new ImageMetaData(image, microscope, "MicroManager", metaDataStack);
			archive.addImageMetaData(metaData);
	        
	        //Now we need to use the IntensitiesStack to build the molecule archive...
	        forkJoinPool.submit(() -> IntensitiesStack.get(1).keySet().parallelStream().forEach(UID -> {
	        	SDMMResultsTable table = new SDMMResultsTable();
	        	ArrayList<DoubleColumn> columns = new ArrayList<DoubleColumn>();
	    		
	    		columns.add(new DoubleColumn("x_TOP"));
	    		columns.add(new DoubleColumn("y_TOP"));
	    		columns.add(new DoubleColumn("x_BOT"));
	    		columns.add(new DoubleColumn("y_BOT"));
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
	        		table.set("x_TOP", row, peak.getXTOP());
	        		table.set("y_TOP", row, peak.getYTOP());
	        		table.set("x_BOT", row, peak.getXBOT());
	        		table.set("y_BOT", row, peak.getXTOP());
	        		table.set("slice", row, (double)slice);
	        		
	        		for (String colorName : colorsSet) {
	        			if (peak.getIntensityList().containsKey(colorName)) {
	        				table.set(colorName, row, peak.getIntensity(colorName)[0]);
	        				table.set(colorName + "_background", row, peak.getIntensity(colorName)[1]);
	        			} else {
	        				table.set(colorName, row, Double.NaN);
	        				table.set(colorName + "_background", row, Double.NaN);
	        			}
	        		}
	        	}
	        	
	        	Molecule molecule = new Molecule(UID, table);
	        	molecule.setImageMetaDataUID(metaData.getUID());
	        	
	        	archive.add(molecule);
	        })).get();
	        
	        statusService.showProgress(100, 100);
	        statusService.showStatus("Peak integration for " + image.getTitle() + " - Done!");
	        
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
		
		logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		if (archive.getNumberOfMolecules() == 0) {
			logService.info("No molecules found. Maybe there is a problem with your settings");
			archive = null;
			logService.info(builder.endBlock(false));
		} else {
			logService.info(builder.endBlock(true));

			log += builder.endBlock(true);
			archive.addLogMessage(log);
			archive.addLogMessage("   ");			
		}
		statusService.clearStatus();
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
		builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
		builder.addParameter("Inner Radius", String.valueOf(innerRadius));
		builder.addParameter("Outer Radius", String.valueOf(outerRadius));
		builder.addParameter("Use DualView Regions", String.valueOf(dualView));
		builder.addParameter("Dichroic", dichroic);
		builder.addParameter("FRET", String.valueOf(FRET));
		
		builder.addParameter("TOP x0", String.valueOf(TOPx0));
		builder.addParameter("TOP y0", String.valueOf(TOPy0));
		builder.addParameter("TOP width", String.valueOf(TOPwidth));
		builder.addParameter("TOP height", String.valueOf(TOPheight));
		
		builder.addParameter("BOT x0", String.valueOf(BOTx0));
		builder.addParameter("BOT y0", String.valueOf(BOTy0));
		builder.addParameter("BOT width", String.valueOf(BOTwidth));
		builder.addParameter("BOT height", String.valueOf(BOTheight));
		builder.addParameter("Microscope", microscope);
	}
}
