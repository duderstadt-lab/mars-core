package de.mpg.biochem.sdmm.ImageProcessing;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.sdmm.table.ResultsTableService;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.ops.Initializable;
import net.imglib2.type.numeric.RealType;

/**
 * This plugin corrects images that have a beam profile. The plugin requires
 * two images: an image with beam profile and the image that needs to be corrected.
 * For each pixel at position x, y the following is calculated:
 * 
 * (Image(x,y) - electronic_offset) / ((Background(x,y)  - electronic_offset) / (maximum_pixel_background - electronic_offset))
 *
 * @author Karl Duderstadt
 * @author C.M. Punter (c.m.punter@rug.nl)
 *
 */
@Plugin(type = Command.class, label = "Beam Profile Correction", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image Processing", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Beam Profile Correction", weight = 20, mnemonic = 'b')})
public class BeamProfileCorrection<T extends RealType< T >> extends DynamicCommand implements Command {
	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private ConvertService convertService = null;
    
    @Parameter(label = "Image to correct")
    private Dataset dataset;
   
    @Parameter(label = "Background image")
    private Dataset background_dataset;
	
	//@Parameter(label = "Image to correct")
	//private ImagePlus image; 
	
	//@Parameter(label = "Background image")
	//private ImagePlus backgroundImage;
	
	@Parameter(label="Electronic_offset")
	private double electronicOffset = 1400;
	
	@Override
	public void run() {
		ImagePlus image = convertService.convert(dataset, ImagePlus.class);
		ImagePlus backgroundImage = convertService.convert(background_dataset, ImagePlus.class);
		ImageProcessor backgroundIp = backgroundImage.getProcessor();

		// determine maximum pixel value
		double maximumPixelValue = backgroundIp.getf(0, 0);
		
		for (int y = 0; y < backgroundIp.getHeight(); y++) {
			for (int x = 0; x < backgroundIp.getWidth(); x++) {
				
				double value = backgroundIp.getf(x, y);
				
				if (value > maximumPixelValue)
					maximumPixelValue = value;
				
			}
		}
		
		//Need to parallelize this process !!
		
		// subtract electronic offset and
		// divide by background
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				
				double backgroundValue = (backgroundIp.getf(x, y) - electronicOffset) / (maximumPixelValue - electronicOffset);
				double value = image.getProcessor().getf(x, y) - electronicOffset;
				
				image.getProcessor().setf(x, y, (float)Math.abs(value / backgroundValue));
			}
		}
		
	}
}
