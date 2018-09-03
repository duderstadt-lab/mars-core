package de.mpg.biochem.sdmm.ImageProcessing;

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
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;

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
		
		//New we copy the image and apply the DS operation...
		ImageProcessor duplicate = ip.duplicate();
		
		double innerMean;
		double outerMean;
		int innerPixels;
		int outerPixels;
		
		int offset;
		int count = ip.getPixelCount();
		
		Rectangle roi = ip.getRoi();
		
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
						innerMean += duplicate.getf(circleOffset);
						innerPixels++;
					}
				}
				
				for (int circleOffset: outerOffsets) {
					circleOffset += offset;
					
					if (circleOffset >= 0 && circleOffset < count) {
						outerMean += duplicate.getf(circleOffset);
						outerPixels++;
					}
				}
				
				innerMean /= innerPixels;
				outerMean /= outerPixels;
				innerMean -= outerMean;
				
				if (innerMean > 0)
					ip.setf(offset, (float)innerMean);
				else
					ip.setf(offset, 0);
				
				offset++;
			}
		}
		
		ImagePlus ImgOut = new ImagePlus(title + " (DS Filtered)", duplicate);
		return ImgOut;
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
    public static < T extends RealType < T > > Img< T >
        imglib2DSCalc(
            Img< T > source,
            int innerRadius, int outerRadius )
    {
        RandomAccess< T > ra = source.randomAccess(source);
        
        log_window = new TextWindow("DS_Log", "", 400, 600);
    	
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
		
		double time = System.currentTimeMillis();
		
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
		

		time = System.currentTimeMillis() - time;
		time /= 1000;
		log_window.append("Execution Time: " + time + "  seconds");
 
        return result;
    }
}
