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

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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

import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.ops.Initializable;
import net.imglib2.type.numeric.RealType;
import ij.io.FileSaver;

/**
 * This command corrects images that have a beam profile. The command requires
 * two images: an image with beam profile and the image that needs to be corrected.
 * For each pixel at position x, y the following is calculated:
 * 
 * (Image(x,y) - electronic_offset) / ((Background(x,y)  - electronic_offset) / (maximum_pixel_background - electronic_offset))
 *
 * @author Karl Duderstadt
 * @author C.M. Punter
 *
 */
@Plugin(type = Command.class, label = "Beam Profile Correction", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Beam Profile Corrector", weight = 20, mnemonic = 'b')})
public class BeamProfileCorrectionCommand<T extends RealType< T >> extends DynamicCommand implements Command {
	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
	
	@Parameter(label = "Image to correct")
	private ImagePlus image; 
	
	@Parameter(label = "Background image")
	private ImagePlus backgroundImage;
	
	@Parameter(label="Electronic offset")
	private double electronicOffset = 0;
	
	@Parameter(label="Save sequence to directory")
	private boolean saveToDisk = true;
	
	@Parameter(label="Output Directory", style="directory", required=false)
    private File directory;
	
	//For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	private final AtomicInteger framesDone = new AtomicInteger(0);
	
	ImageProcessor backgroundIp;
	double maximumPixelValue;
	
	@Override
	public void run() {
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Beam Profile Correction");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//We assume there is just a single frame..
		backgroundIp = backgroundImage.getProcessor();

		// determine maximum pixel value
		maximumPixelValue = backgroundIp.getf(0, 0);
		
		for (int y = 0; y < backgroundIp.getHeight(); y++) {
			for (int x = 0; x < backgroundIp.getWidth(); x++) {
				
				double value = backgroundIp.getf(x, y);
				
				if (value > maximumPixelValue)
					maximumPixelValue = value;
				
			}
		}
		
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
        		        	statusService.showStatus(framesDone.intValue(), image.getStackSize(), "Correcting beam profile for " + image.getTitle());
        		        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
	            }
	        };

	        progressThread.start();
	        
	        //This will spawn a bunch of threads that will correct the beam profile in individual frames
	        //in parallel
	        forkJoinPool.submit(() -> IntStream.rangeClosed(1, image.getStackSize()).parallel().forEach(i -> correctFrame(i))).get();
	        
	        progressUpdating.set(false);
	        
	        statusService.showProgress(100, 100);
	        statusService.showStatus("Beam profile correction for " + image.getTitle() + " - Done!");
	        
	    } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	e.getStackTrace();
	    	logService.info(builder.endBlock(false));
	    	return;
	    } finally {
	        forkJoinPool.shutdown();
	    }

	    //Need to add if statement to check if headless or not
	    //This might crash a headless run...
	    if (!saveToDisk)
	    	image.updateAndDraw();
	    logService.info(builder.endBlock(true));
	}
	
	public void correctFrame(int slice) {
		ImageProcessor currentImage = image.getStack().getProcessor(slice);
		
		ImageProcessor newImage; 
		if (saveToDisk) 
			newImage = currentImage.createProcessor(currentImage.getWidth(), currentImage.getHeight());
		else 
			newImage = currentImage;
		
		// subtract electronic offset and
		// divide by background
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				
				double backgroundValue = (backgroundIp.getf(x, y) - electronicOffset) / (maximumPixelValue - electronicOffset);
				double value = currentImage.getf(x, y) - electronicOffset;
				
				newImage.setf(x, y, (float)Math.abs(value / backgroundValue));
			}
		}
		
		if (saveToDisk) {
			ImagePlus img = new ImagePlus(image.getStack().getShortSliceLabel(slice), newImage);
			String infoString = (String)image.getStack().getSliceLabel(slice);
			if (infoString.contains("{")) 
				img.setProperty("Info", infoString.substring(infoString.indexOf("{")));
			FileSaver saver = new FileSaver(img);
			saver.saveAsTiff(directory.getAbsolutePath() + "/" + image.getStack().getShortSliceLabel(slice) + ".tif");
		}
		
		framesDone.incrementAndGet();
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Image Title", image.getTitle());
		if (image.getOriginalFileInfo() != null && image.getOriginalFileInfo().directory != null) {
			builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
		}
		builder.addParameter("Background Image Title", backgroundImage.getTitle());
		if (backgroundImage.getOriginalFileInfo() != null && backgroundImage.getOriginalFileInfo().directory != null) {
			builder.addParameter("Background Image Directory", backgroundImage.getOriginalFileInfo().directory);
		}
		builder.addParameter("Electronic offset", String.valueOf(electronicOffset));
		builder.addParameter("Save to Disk", String.valueOf(saveToDisk));
		if (saveToDisk)
			builder.addParameter("Directory", directory.getAbsolutePath());
	}
	
	public void setImage(ImagePlus image) {
		this.image = image;
	}
	
	public ImagePlus getImage() {
		return image;
	}
	
	public void setBackgroundImage(ImagePlus backgroundImage) {
		this.backgroundImage = backgroundImage;
	}
	
	public ImagePlus getBackgroundImage() {
		return backgroundImage;
	}
	
	public void setElectronicOffset(double electronicOffset) {
		this.electronicOffset = electronicOffset;
	}
	
	public double getElectronicOffset() {
		return electronicOffset;
	}
	
	public void setSaveToDisk(boolean saveToDisk) {
		this.saveToDisk = saveToDisk;
	}
	
	public boolean getSaveToDisk() {
		return saveToDisk;
	}
	
	public void setDirectory(File directory) {
		this.directory = directory;
	}
	
	public File getDirectory() {
		return directory;
	}

}
