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

import net.imagej.ops.AbstractOp;
import net.imagej.ops.Op;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import java.awt.Rectangle;
import java.util.ArrayList;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import net.imglib2.img.Img;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;

@Plugin(type = Op.class, name = "DiscoidalAveragingFilter")
public class DiscoidalAveragingFilter<T extends RealType< T >> extends AbstractOp {
	
	@Parameter
	private int innerRadius;
	
	@Parameter
	private int outerRadius;

	@Parameter(label = "Image to Filter")
	private ImagePlus input;

	@Parameter(label = "Filtered Image", type = ItemIO.OUTPUT)
	private ImagePlus output;
	
	private static TextWindow log_window;

	@Override
	public void run() {
		output = calcDiscoidalAveragedImage(input, innerRadius, outerRadius);
	}
	
	public static ImagePlus calcDiscoidalAveragedImage(ImagePlus ImgIn, int innerRadius, int outerRadius) {
		return calcDiscoidalAveragedImage(ImgIn.getProcessor(), ImgIn.getTitle(), innerRadius, outerRadius);
	}
	
	public static ImagePlus calcDiscoidalAveragedImage(ImageProcessor ip, String title, int innerRadius, int outerRadius) {
		//First we general index offset for the local regions for averaging...
		int[] innerOffsets;
		int[] outerOffsets;
		
		int width = ip.getWidth();
		
		ArrayList<Integer> innerOffsetList = new ArrayList<Integer>();
		ArrayList<Integer> outerOffsetList = new ArrayList<Integer>();
		
		for (int y = -outerRadius; y <= outerRadius; y++) {
			for (int x = -outerRadius; x <= outerRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));
				int offset = x + y * width;

				if (d <= innerRadius)
					innerOffsetList.add(offset);
				
				if (d == outerRadius)
					outerOffsetList.add(offset);
				
			}
		}
		
		innerOffsets = new int[innerOffsetList.size()];
		outerOffsets = new int[outerOffsetList.size()];
		
		for (int i = 0; i < innerOffsets.length; i++)
			innerOffsets[i] = innerOffsetList.get(i);
		
		for (int i = 0; i < outerOffsets.length; i++)
			outerOffsets[i] = outerOffsetList.get(i);
		
		//Now we copy the image and apply the DS operation...
		ImageProcessor duplicate = ip.duplicate();
		
		double innerMean;
		double outerMean;
		int innerPixels;
		int outerPixels;
		
		int offset;
		int count = duplicate.getPixelCount();
		
		Rectangle roi = duplicate.getRoi();
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			
			offset = y * width + roi.x;
			
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				
				innerMean = 0;
				outerMean = 0;
				innerPixels = 0;
				outerPixels = 0;
				
				for (int circleOffset: innerOffsets) {
					circleOffset += offset;
					
					if (circleOffset >= 0 && circleOffset < count) {
						innerMean += ip.getf(circleOffset);
						innerPixels++;
					}
				}
				
				for (int circleOffset: outerOffsets) {
					circleOffset += offset;
					
					if (circleOffset >= 0 && circleOffset < count) {
						outerMean += ip.getf(circleOffset);
						outerPixels++;
					}
				}
				
				innerMean /= innerPixels;
				outerMean /= outerPixels;
				innerMean -= outerMean;
				
				if (innerMean > 0)
					duplicate.setf(offset, (float)innerMean);
				else
					duplicate.setf(offset, 0);
				
				offset++;
			}
		}
		
		ImagePlus ImgOut = new ImagePlus(title + " (DS Filtered)", duplicate);
		return ImgOut;
	}
	
	public static ImageProcessor calcDiscoidalAveragedImageInfiniteMirror(ImageProcessor ip, int innerRadius, int outerRadius) {
		return calcDiscoidalAveragedImageInfiniteMirror(ip, innerRadius, outerRadius, new Rectangle(0, 0, ip.getWidth(), ip.getHeight()));
	}
	
	public static ImageProcessor calcDiscoidalAveragedImageInfiniteMirror(ImageProcessor ip, int innerRadius, int outerRadius, Rectangle roi) {
		//First we generate x y offset lists for the inner and outer regions
		ArrayList<int[]> innerOffsets = new ArrayList<int[]>();
		ArrayList<int[]> outerOffsets = new ArrayList<int[]>();
		
		for (int y = -outerRadius; y <= outerRadius; y++) {
			for (int x = -outerRadius; x <= outerRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));

				if (d <= innerRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					innerOffsets.add(pos);
				}
				
				if (d == outerRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					outerOffsets.add(pos);
				}
			}
		}
		
		ip.setRoi(roi);
		ImageProcessor region = ip.crop();
		ip.resetRoi();
		
		double innerMean;
		double outerMean;
		int innerPixels;
		int outerPixels;
		
		for (int y = 0; y < region.getHeight(); y++) {
			for (int x = 0; x < region.getWidth(); x++) {
				innerMean = 0;
				outerMean = 0;
				innerPixels = 0;
				outerPixels = 0;
				
				for (int[] circleOffset: innerOffsets) {
					innerMean += (double)getPixelValue(ip, x + circleOffset[0], y + circleOffset[1]);
					innerPixels++;
				}
				
				for (int[] circleOffset: outerOffsets) {
					outerMean += (double)getPixelValue(ip, x + circleOffset[0], y + circleOffset[1]);
					outerPixels++;
				}
				
				innerMean /= innerPixels;
				outerMean /= outerPixels;
				innerMean -= outerMean;
				
				if (innerMean > 0)
					region.setf(x, y, (float)innerMean);
				else
					region.setf(x, y, 0);
			}
		}
		
		return region;
	}
	
	//This function will perform a mirror operation
	//At the edges for out of bounds regions...
	private static float getPixelValue(ImageProcessor proc, int x, int y) {
		int w = proc.getWidth();
		int h = proc.getHeight();
		
		//First for x if needed
		if (x < 0) {
			x *= -1;
		} else if (x > w - 1) {
			x = (w - 1) - (x - w); 
		}
			
		//Then for y
		if (y < 0) {
			y *= -1;
		} else if (y > h - 1) {
			y = (h - 1) - (y - h); 
		}
			
		//If it is really far out of bound we might need to run this method a couple times
		//Will this get caught in an infinite loop ? I hope not..
		if (x < 0 || x > w - 1 || y < 0 || y > h - 1)
			getPixelValue(proc, x, y);
			
		return proc.getf(x, y);
	}
	
	
	/**
     * finds the mean value within the outer radius and subtracts it from the inner radius region. 
     * Placing the result in the output image.
     *
     * @param source - the image data to work on already representing the ROI of interest...
     * @param out - the filtered image
     * @param innerRadius - the inner radius.
     * @param outerRadius - the outer radius.
     * @return - a Discoidal averaged image.
     */
	/*
    public static < T extends RealType < T > > ImagePlus calcDiscoidalAveragedImageInfiniteMirror(ImagePlus ImgIn, int innerRadius, int outerRadius, Rectangle roi, String title ) {
    	ImagePlus duplicateImage = new ImagePlus(title + " (DS Filtered)", ImgIn.getProcessor());
    	
    	Img< T > source = (Img< T >)ImagePlusAdapter.wrap( duplicateImage );
    	
    	RandomAccessibleInterval< T > roiView = Views.interval( source, new long[] { (long)roi.getX(), (long)roi.getY() }, new long[]{ (long)(roi.getX() + roi.getWidth()), (long)(roi.getY() + roi.getHeight()) } );
    	
    	RandomAccessible< T > mirroredSource = Views.extendMirrorSingle( roiView );

        //long[] min = new long[ source.numDimensions() ];
        //long[] max = new long[ source.numDimensions() ];

        //for ( int d = 0; d < source.numDimensions(); ++d )
        //{
            // we add/subtract another 30 pixels here to illustrate
            // that it is really infinite and does not only work once
        //    min[ d ] = -source.dimension( d );
        //    max[ d ] = source.dimension( d ) * 2 - 1;
       // }

        // define the Interval on the infinite random accessible
        //FinalInterval interval = new FinalInterval( min, max );

        // create a Cursor that iterates over the source to calculate the mean of the outer region.
        //mirroredSource = Views.interval( mirroredSource, interval );
    	
    	
        //RandomAccess< T > ra = source.randomAccess(source);
        
    	RandomAccess< T > ra = mirroredSource.randomAccess();
    	
        //log_window = new TextWindow("DS_Log", "", 400, 600);
    	
        // Create a new image for the output
        Img< T > result = source.factory().create(source);
        Cursor< T > resultCursor = result.localizingCursor();
        
        //This limits usage to 2D...
  		//Will need to be updated for 3D if that is every important...
  		int width = (int)source.dimension(0);
  		//int height = (int)source.dimension(1);
  		int count = (int)Intervals.numElements(source);
  		
  		long[] pos = new long[source.numDimensions()];
  		long[] dims = new long[source.numDimensions()];
  		source.dimensions(dims);

        //generate Offsets
        //For the moment we calculate the inner and outer offsets for all images
        //this might lead to a little extra time but it allows for smooth static calls
        //outside the context of ImageOp usage...
    	long[] innerOffsets;
    	long[] outerOffsets;
    	
    	ArrayList<Long> innerOffsetList = new ArrayList<Long>();
		ArrayList<Long> outerOffsetList = new ArrayList<Long>();
		
		for (int y = -outerRadius; y <= outerRadius; y++) {
			for (int x = -outerRadius; x <= outerRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));
				long offset = x + y * width;

				if (d <= innerRadius) {
					innerOffsetList.add(offset);
					//continue;
				}
				
				//This is different from the previous version...
				//Should we instead use the outerregion ? When I quickly tested it didn't seem to make much of a difference.
				if (d == outerRadius) {
					outerOffsetList.add(offset);
				}
			}
		}
		
		innerOffsets = new long[innerOffsetList.size()];
		outerOffsets = new long[outerOffsetList.size()];
		
		for (int i = 0; i < innerOffsets.length; i++)
			innerOffsets[i] = innerOffsetList.get(i);
		
		for (int i = 0; i < outerOffsets.length; i++)
			outerOffsets[i] = outerOffsetList.get(i);
    	
    	double innerMean;
		double outerMean;
		long innerPixels;
		long outerPixels;
		
		long offset = 0;
		
		//double time = System.currentTimeMillis();
		
		while (resultCursor.hasNext()) {
			resultCursor.fwd();
			
			offset = resultCursor.getIntPosition(0) + resultCursor.getIntPosition(1) * width;
				
			innerMean = 0;
			outerMean = 0;
			innerPixels = 0;
			outerPixels = 0;

			for (long circleOffset: innerOffsets) {
				circleOffset += offset;
				
				if (circleOffset >= 0 && circleOffset < count) {
					IntervalIndexer.indexToPosition(circleOffset, dims, pos);
					ra.setPosition(pos);
					innerMean += ra.get().getRealDouble();
					innerPixels++;
				}
			}
			
			for (long circleOffset: outerOffsets) {
				circleOffset += offset;
				
				if (circleOffset >= 0 && circleOffset < count) {
					IntervalIndexer.indexToPosition(circleOffset, dims, pos);
					ra.setPosition(pos);
				    outerMean += ra.get().getRealDouble();
					outerPixels++;
				}
			}
			
			innerMean /= innerPixels;
			outerMean /= outerPixels;
			innerMean -= outerMean;

			if (innerMean > 0)
				resultCursor.get().setReal(innerMean);
			else
				resultCursor.get().setReal(0);
			
		}
		

		//time = System.currentTimeMillis() - time;
		//time /= 1000;
		//log_window.append("Execution Time: " + time + "  seconds");
 
		return duplicateImage;
    }
    */
}
