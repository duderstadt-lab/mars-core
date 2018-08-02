package de.mpg.biochem.sdmm.testing;
/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import io.scif.services.FormatService;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.FieldPrinter;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import net.imagej.ImgPlus;
import net.imagej.ops.Ops.Create.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imagej.Dataset;
import io.scif.Metadata;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.sdmm.molecule.MoleculeArchiveService;
import de.mpg.biochem.sdmm.util.SDMMPluginsService;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;

import org.scijava.convert.ConvertService;
import net.imagej.DatasetService;

@Plugin(type = Command.class, menuPath = "Plugins>Show Metadata")
public class GetMetadata implements Command {

    // -- Needed services --

    // for determining the Format of the input image
    @Parameter
    private ConvertService convertService = null;

    @Parameter
    private DatasetService datasetService = null;
    
    @Parameter
    private FormatService formatService;
    
    @Parameter
    private MoleculeArchiveService moleculeArchiveService;

    // for logging errors
    @Parameter
    private LogService log;

    // -- Inputs and outputs to the command --
    
    //@Parameter
    //private Dataset dataset;
    
    @Parameter
    private ImagePlus img;

    // output metadata string
    @Parameter(label = "Metadata", type = ItemIO.OUTPUT)
    private String mString;
    
    @Override
    public void run() {

		ImageStack stack = img.getStack();
		String label = stack.getSliceLabel(img.getCurrentSlice());
		
		JsonFactory jfactory = new JsonFactory();
		try {
			JsonParser jParser = jfactory.createParser(label.substring(label.indexOf("{")));
			
			//Just to skip to the first field
			jParser.nextToken();
			
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jParser.getCurrentName();
				
				log.info("fieldname " + fieldname);
				
				jParser.nextToken();
				
				if (jParser.getCurrentToken().isNumeric()) {
					log.info("Number " + jParser.getValueAsDouble());
				} else {
					log.info("Value " + jParser.getValueAsString());
				}
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
    	
        // we need the file path to determine the file format
        //final String filePath = img.getSource();
    	
        //File folder = new File(img.getOriginalFileInfo().directory);
        
        //ImagePlus imger = dataset.getImgPlus();
        
        //log.info("Imager - " + imger.getSource());
        
        //long[] dims = new long[dataset.numDimensions()];
        //dataset.dimensions(dims);

        //ImgPlus img = dataset.getImgPlus();
    	
    	//log.info("Org File info " + img.getOriginalFileInfo().toString());

    	//log.info("Slices " + img.getNSlices());
    	//log.info(img.getFileInfo().toString());
    	
        //log.info("Title " + img.getTitle());
        //log.info("Properties " + img.getProperties());
        /*
    	File folder = new File(img.getOriginalFileInfo().directory);
    	
    	File[] files = folder.listFiles(); 
    	
		try {	
			
			Format format = formatService.getFormat(files[0].getAbsolutePath());
			
			Metadata metadata = format.createParser().parse(files[0].getAbsolutePath());
			
			log.info("toString " + metadata.toString());
			
			log.info("table to String" + metadata.getTable().toString());
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (FormatException e) {
			e.printStackTrace();
		} 
    	*/
        //Img<FloatType> floatImg = ops.convert().float32(img);

        //RandomAccessibleInterval<FloatType> blurredImg = ops.filter().gauss(floatImg, 3.0);

        //FloatType mean = new FloatType();
        //ops.stats().mean(mean, floatImg);
        //System.out.println("Mean: " + mean.getRealDouble());

        //ops.stats().mean(mean, Views.iterable(blurredImg));
        //System.out.println("Mean: " + mean.getRealDouble());
/*
        File[] files = folder.listFiles(); 
        
        ArrayList<String> fileNames = new ArrayList<String>();
        
        HashMap<String, String> fileToTime = new HashMap<String, String>();
        
        for (int i=0;i<files.length;i++) {
        	String name = files[i].getName();
        	if (name.substring(name.length() - 4, name.length()).equals(".tif")) {
        		fileNames.add(name);
				try {
					Format format = formatService.getFormat(files[i].getAbsolutePath());
					
                	Metadata metadata = format.createParser().parse(files[i].getAbsolutePath());

					fileToTime.put(name, (String)metadata.getTable().get("DateTime"));
				} catch (IOException e) {
					e.printStackTrace();
				} catch (FormatException e) {
					e.printStackTrace();
				}        		
        	}
        }
        */
        //Collections.sort(fileNames, new NaturalOrderComparator());
        
        //for (String fileN:fileNames) {
        //	log.info(fileN + " " + fileToTime.get(fileN));
        //}
        
        /*

        // catch any Format or IO exceptions
        try {

            // determine the Format based on the extension and, if necessary, the source data
            Format format = formatService.getFormat(filePath);

            // create an instance of the associated Parser and parse metadata from the image file
            Metadata metadata = format.createParser().parse(filePath);

            // use FieldPrinter to traverse metadata tree and return as a String
            String metadataTree = new FieldPrinter(metadata).toString();

            // (optional) remove some of the tree formatting to make the metadata easier to read
            //mString = formatMetadata(metadataTree) + "\n";
            //mString += moleculeArchiveService.getFNV1aBase58("180502 142920350950") + "\n";
            //mString += moleculeArchiveService.getFNV1aBase58("180502 142920650419") + "\n";
            
            //mString += "\n";
            mString = "";
            mString += img.getProperties().keySet().toString();
            mString = "\n";
            //mString += img.getPlane(1);
            mString = "\n";
            mString += metadata.getTable().get("DateTime") + "\n";
            mString += metadata.getDatasetName() + "\n";
            		
            SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
            Date frameDate;
			try {
				String frameTime = (String)metadata.getTable().get("DateTime");
				String microSecs = frameTime.substring(frameTime.length() - 3, frameTime.length());
				frameDate = formatter.parse(frameTime.substring(0, frameTime.length() - 3));
				
				long frameMS = frameDate.getTime() + Math.round(Double.parseDouble(microSecs)/1000);

				mString += frameDate.toString() + "\n";
				mString += frameMS + "\n";
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        catch (final FormatException | IOException e) {
            log.error(e);
        }
        */
    }
}
