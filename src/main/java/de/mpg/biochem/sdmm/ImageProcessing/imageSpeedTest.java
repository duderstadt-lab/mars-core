package de.mpg.biochem.sdmm.ImageProcessing;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgOpener;
import net.imagej.ops.Initializable;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;

//@Plugin(type = Command.class,
//menuPath = "Plugins>SDMM Plugins>Image Processing>Image Speed Test")
public class imageSpeedTest<T extends RealType<T>> implements Command {

	@Parameter(label="Input image")
	private Img< T > img;
	
	@Parameter
	private ImagePlus iPlus;
	
	private static TextWindow log_window;
	
	private int[] innerOffsets;
	private int[] outerOffsets;
	
	@Override
	public void run() {
		log_window = new TextWindow("SpeedTest_Log", "", 400, 600);
		
		double time = System.currentTimeMillis();
		//IJ1 image read operation
		setCircleOffsets(iPlus.getWidth(), 1, 5);
		calcDS_IJ1(iPlus.getProcessor());
		/*
		double mean = 0;
		ImageProcessor ip = iPlus.getProcessor();
		Rectangle roi = ip.getRoi();
		
		int offset;
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			
			offset = y * ip.getWidth() + roi.x;
			
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				mean += ip.getf(offset + x);
			}
			
		}
		*/
		
		time = System.currentTimeMillis() - time;
		time /= 1000;
		log_window.append("IJ1 Execution Time: " + time + "  seconds");
		
		time = System.currentTimeMillis();
		//Imglib2 image read operation
		calcDS(img, 1, 5);
		time = System.currentTimeMillis() - time;
		time /= 1000;
		log_window.append("Imglib2 Execution Time: " + time + "  seconds");
	}
	
	public static < T extends RealType<T> > Img< T > calcDS(
        Img< T > source,
        int innerRadius, int outerRadius )
		{
		    RandomAccess< T > ra = source.randomAccess(source);
			
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
			return result;
		}
	
	public void setCircleOffsets(int width, int innerRadius, int outerRadius) {
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
	}
	
	public void calcDS_IJ1(ImageProcessor ip) {
		
		ImageProcessor duplicate = ip.duplicate();
		
		double innerMean;
		double outerMean;
		int innerPixels;
		int outerPixels;
		
		int offset;
		int width = ip.getWidth();
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
	}
		
}
