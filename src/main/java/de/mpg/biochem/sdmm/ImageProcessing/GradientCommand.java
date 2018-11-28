package de.mpg.biochem.sdmm.ImageProcessing;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.sdmm.util.LogBuilder;
import de.mpg.biochem.sdmm.util.SDMMMath;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.type.numeric.RealType;
import ij.io.FileSaver;

/**
 * This command calculates the gradient (slope) of consecutive pixels 
 * in the y direction from top to bottom. The resulting gradient images
 * can be saved as an image sequence. 
 * 
 * This tool is used to find the start and end points of stained DNAs.
 *
 * @author Karl Duderstadt
 *
 */
@Plugin(type = Command.class, label = "Gradient Calculator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image Processing", weight = 20,
			mnemonic = 'i'),
		@Menu(label = "Gradient Calculator", weight = 50, mnemonic = 'g')})
public class GradientCommand<T extends RealType< T >> extends DynamicCommand implements Command {
	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
	
	@Parameter(label = "Image")
	private ImagePlus image; 
	
	@Parameter(label = "fitLength (in pixels, must be an odd number)")
	private int fitLength = 7;
	
	@Parameter(label="Output Directory", style="directory")
    private File directory;
	
	//For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	private final AtomicInteger framesDone = new AtomicInteger(0);
	
	private int width, height;
	
	@Override
	public void run() {
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Calculate Y Gradient");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		width = image.getWidth();
		height = image.getHeight();
		
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
        		        	statusService.showStatus(framesDone.intValue(), image.getStackSize(), "Calculating gradient for " + image.getTitle());
        		        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
	            }
	        };

	        progressThread.start();
	        
	        //This will spawn a bunch of threads that will correct the beam profile in individual frames
	        //in parallel
	        forkJoinPool.submit(() -> IntStream.rangeClosed(1, image.getStackSize()).parallel().forEach(i -> frameGradient(i))).get();
	        
	        progressUpdating.set(false);
	        
	        statusService.showProgress(100, 100);
	        statusService.showStatus("Gradient calculation for " + image.getTitle() + " - Done!");
	        
	    } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	e.printStackTrace();
	    	logService.info(builder.endBlock(false));
	    	return;
	    } finally {
	        forkJoinPool.shutdown();
	    }
	    
	    logService.info(builder.endBlock(true));
	}
	
	public void frameGradient(int slice) {
		ImageProcessor currentImage = image.getStack().getProcessor(slice);
		
		FloatProcessor gradProcessor = new FloatProcessor(width, height);
		
		for (int x = 0 ; x < width; x++) {
			for (int y=0 ; y < height ; y++) {
				//for linear fitting using - SDMMMath.linearRegression(xData, yData, offset, length)
				//offset is starting index and length is number of index positions for fitting...
				//This makes it easy to pass a large array and only fit part of it..
				
				double[] output = SDMMMath.linearRegression(getXData(), getYData(x, y, currentImage), 0, fitLength);

				//The linearRegression function returns the following format
				// Equations and notation taken directly from "An Introduction to Error Analysis" by Taylor 2nd edition
				// y = A + Bx
				// A = output[0] +/- output[1]
				// B = output[2] +/- output[3]
				// error is the STD here.

				//We only need to know the slope for the gradProcessor Image (output[2])
				//Operation to set a pixel is setf(int x, int y, float value)
				gradProcessor.setf(x,y,(float)output[2]);
			}
		}
		
		ImagePlus img = new ImagePlus(image.getStack().getShortSliceLabel(slice), gradProcessor);
		String infoString = (String)image.getStack().getSliceLabel(slice);
		if (infoString.contains("{")) 
			img.setProperty("Info", infoString.substring(infoString.indexOf("{")));
		FileSaver saver = new FileSaver(img);
		saver.saveAsTiff(directory.getAbsolutePath() + "/" + image.getStack().getShortSliceLabel(slice) + ".tif");
		
		framesDone.incrementAndGet();
	}
	//UTILITY METHODS

	//For the xData we just need an even list of points as long as fitLength
	private double[] getXData() {
		double[] xDataOut = new double[fitLength];
		for (int i=0;i<fitLength;i++) {
			xDataOut[i] = i;
		}
		return xDataOut;
	}

	//For the yData we need pixel values from imgIn
	//but we need to make sure pixels outside the boundaries
	//are mapped to mirror images..
	private double[] getYData(int Xin, int Yin, ImageProcessor currentImage) {
		double[] yDataOut = new double[fitLength];
		int index = 0;

		int pixelsFromCenter = (fitLength - 1)/2;
		for (int y = Yin - pixelsFromCenter ; y <= Yin + pixelsFromCenter ; y++) {
			yDataOut[index] = getPixelValue(Xin, y, currentImage);	
			index++;
		}
		return yDataOut;
	}

	//Returns pixel value of input image
	//if the position is out of bounds 
	//a mirrored value is given
	private double getPixelValue(int x, int y, ImageProcessor currentImage) {
		if (y < 0)
			y *= -1; 
		else if (y > height - 1)
			y = (height - 1) - (y - height);
			
		//Next for X
		if (x < 0)
			x *= -1; 
		else if (x > width - 1)
			x = (width - 1) - (x - width);

		return (double)currentImage.getf(x, y);
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Image Title", image.getTitle());
		builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
		builder.addParameter("fitLength", String.valueOf(fitLength));
		builder.addParameter("Directory", directory.getAbsolutePath());
	}
	
	public void setImage(ImagePlus image) {
		this.image = image;
	}
	
	public ImagePlus getImage() {
		return image;
	}
	
	public void setFitLength(int fitLength) {
		this.fitLength = fitLength;
	}
	
	public int getFitLength() {
		return fitLength;
	}
	
	public void setDirectory(File directory) {
		this.directory = directory;
	}
	
	public File getDirectory() {
		return directory;
	}
}
