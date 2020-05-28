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

import net.imagej.Dataset;
import net.imagej.ops.Initializable;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.*;
import net.imglib2.view.*;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.util.LogBuilder;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import io.scif.services.TranslatorService;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Plugin(type = Command.class, label = "Overlay Channels", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Overlay Channels", weight = 60, mnemonic = 'o')})
public class OverlayChannelsCommand< T extends NumericType< T > & NativeType< T > > extends DynamicCommand implements Command {
	
	@Parameter
	private LogService logService;
	
	@Parameter
	private StatusService statusService;
	
	@Parameter
	private ConvertService convertService;
	
	@Parameter(label = "Add To Me")
	private Dataset addToMe;
	
	@Parameter(label = "Transform Me")
	private Dataset transformMe;

	//@Parameter(label= "Keep originals")
	//private boolean keep = true;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String affineTitle =
			"Affine2D Transformation Matrix:";
	
	@Parameter(label="m00")
	private double m00 = 1;
	
	@Parameter(label="m01")
	private double m01 = 0;
	
	@Parameter(label="m02")
	private double m02 = 0;
	
	@Parameter(label="m10")
	private double m10 = 0;
	
	@Parameter(label="m11")
	private double m11 = 1;
	
	@Parameter(label="m12")
	private double m12 = 0;
	
	@Parameter(label="Merged Image", type = ItemIO.OUTPUT)
	private ImagePlus imgOut;
	
	//A map from slice to new transformed image
	private ConcurrentMap<Integer, ImagePlus> transformedImageMap;
	
	//For the progress thread
	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
	
	@Override
	public void run() {
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("Overlay Channels");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		logService.info(log);
		
		transformedImageMap = new ConcurrentHashMap<>();
		
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		AffineTransform2D transform = new AffineTransform2D();
		transform.set(m00, m01, m02, m10, m11, m12);
		
		ImagePlus transformMeIP = convertService.convert(transformMe, ij.ImagePlus.class);
		ImagePlus addToMeIP = convertService.convert(addToMe, ij.ImagePlus.class);
		
		ImageStack oldStack = transformMeIP.getImageStack();
		
		double starttime = System.currentTimeMillis();
		logService.info("Transforming and Overlaying channels...");
	    try {
	    	//Start a thread to keep track of the progress of the number of frames that have been processed.
	    	//Waiting call back to update the progress bar!!
	    	Thread progressThread = new Thread() {
	            public synchronized void run() {
                    try {
        		        while(progressUpdating.get()) {
        		        	Thread.sleep(100);
        		        	statusService.showStatus(transformedImageMap.size(), transformMeIP.getStackSize(), "Transforming " + transformMeIP.getTitle());
        		        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
	            }
	        };

	        progressThread.start();
	        
	        forkJoinPool.submit(() -> IntStream.rangeClosed(1, transformMeIP.getStackSize()).parallel().forEach(slice -> { 
	        	ImagePlus sliceImage = new ImagePlus("slice "+slice, oldStack.getProcessor(slice));
	        	
	        	Img< T > img = ImagePlusAdapter.wrap(sliceImage);
	            
	        	RandomAccessibleInterval< T > ra = Views.interval( Views.raster( RealViews.affine(Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory() ), transform ) ), img );

	            ImagePlus transImg = ImageJFunctions.wrap( ra , "transformed");
	        	
	        	transformedImageMap.put(slice, transImg);
	        })).get();
	        
	        //Now we have a map with all the transformed images. We just need to add them to a new stack
	        //and then merge with the untransformed image.
			ImageStack newStack = new ImageStack(transformMeIP.getWidth(), transformMeIP.getHeight());
			
			//I think this just works with references, so it should be super fast...
			//otherwise the stack could be made in the parallel stream but might need 
			//to be placed in a synchronize block...
			for (int slice=1;slice<=transformedImageMap.size();slice++)
				newStack.addSlice(transformedImageMap.get(slice).getProcessor());
			
			ImagePlus[] images = new ImagePlus[2];
			images[0] = addToMeIP;
			images[1] = new ImagePlus("transformed", newStack);

			imgOut = ij.plugin.RGBStackMerge.mergeChannels(images, false);
	        
	        progressUpdating.set(false);
	        
	        statusService.showStatus(1, 1, "Transformations of " + transformMeIP.getTitle() + " - Done!");
	        
	   } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	e.printStackTrace();
			logService.info(LogBuilder.endBlock(false));
			return;
	   } finally {
	      forkJoinPool.shutdown();
	   }
	    
	    logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		
		logService.info(LogBuilder.endBlock(true));
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Image 1", addToMe.getName());
		builder.addParameter("Image 1 Directory", addToMe.getSource());
		builder.addParameter("Image 2", transformMe.getName());
		builder.addParameter("Image 2 Directory", transformMe.getSource());
		builder.addParameter("Affine2D m00", String.valueOf(m00));
		builder.addParameter("Affine2D m01", String.valueOf(m01));
		builder.addParameter("Affine2D m02", String.valueOf(m02));
		builder.addParameter("Affine2D m10", String.valueOf(m10));
		builder.addParameter("Affine2D m11", String.valueOf(m11));
		builder.addParameter("Affine2D m12", String.valueOf(m12));
	}
}