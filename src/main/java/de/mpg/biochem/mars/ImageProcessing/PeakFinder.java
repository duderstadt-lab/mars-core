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
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import ij.ImagePlus;

//import ij.text.TextWindow;

import ij.Prefs;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import net.imglib2.KDTree;

public class PeakFinder<T extends RealType<T>> {
	
	//private static TextWindow log_window;
	
	private boolean findNegativePeaks = false;
	
	private double threshold = 6;
	private int minimumDistance = 8;
	
	private boolean useDiscoidalAveraging = true;
	private int DSinnerRadius = 1;
	private int DSouterRadius = 3;
	
	// This is very similar to a factory class
	// When the Peakfinder is created all the main variables are set...
	// Then there are a series of methods for peak finding that take and img and return different properties
	// Can include a full list of all peaks
	//Any prefiltering like DS should happen before the image is passed to the PeakFinder...
	
	public PeakFinder(double threshold, int minimumDistance, int DSinnerRadius, int DSouterRadius, boolean findNegativePeaks) {
		this.useDiscoidalAveraging = true;
		this.threshold = threshold;
		this.minimumDistance = minimumDistance;
		this.DSinnerRadius = DSinnerRadius;
		this.DSouterRadius = DSouterRadius;
		this.findNegativePeaks = findNegativePeaks;
		
		//log_window = new TextWindow("PeakFinder_Log", "", 400, 600);
	}
	
	public PeakFinder(double threshold, int minimumDistance, boolean findNegativePeaks) {
		this.useDiscoidalAveraging = false;
		this.threshold = threshold;
		this.minimumDistance = minimumDistance;
		this.findNegativePeaks = findNegativePeaks;
		
		//log_window = new TextWindow("PeakFinder_Log", "", 400, 600);
	}
	
	public ArrayList<Peak> findPeaks(ImagePlus ip) {
		return findPeaks(ip, -1);
	}
	
	public ArrayList<Peak> findPeaks(ImagePlus ip, int slice) {
		Roi roi = ip.getRoi();
		if (roi == null) {
			roi = new Roi(new Rectangle(0, 0, ip.getWidth() - 1, ip.getHeight() - 1));
		}
		return findPeaks(ip, roi, slice);
	}
	
	public ArrayList<Peak> findPeaks(ImagePlus ip, Roi region) {
		return findPeaks(ip, region, -1);
	}
	
	public ArrayList<Peak> findPeaks(ImagePlus imageIN, Roi region, int slice) {
		
		ArrayList<Peak> possiblePeaks = new ArrayList<Peak>();
		
		//When the image is wrapped the Roi will be used and not the full image...
		
		//For finding DNAs we need to find negative peaks
		//To make the least amount of changes, I will just invert the image before processing
		//Then flip the values back at the end.
		ImagePlus imp; 
		if (findNegativePeaks) {
			imp = imageIN.duplicate();
			for (int x=0;x<imageIN.getWidth();x++) {
				for (int y=0;y<imageIN.getHeight();y++) {
					imp.getProcessor().setf(x, y, (-1)*imageIN.getProcessor().getf(x, y));
				}
			}
		} else {
			imp = imageIN;
		}
		
		Img< T > image;
		if (useDiscoidalAveraging) {
			//ImagePlus filtered_ip = DiscoidalAveragingFilter.calcDiscoidalAveragedImage(imp.duplicate(), DSinnerRadius, DSouterRadius);
			ImageProcessor filtered_ip = DiscoidalAveragingFilter.calcDiscoidalAveragedImageInfiniteMirror(imp.getProcessor(), DSinnerRadius, DSouterRadius, new Rectangle(0, 0, imp.getWidth(), imp.getHeight()));
			image = ImageJFunctions.wrapReal(new ImagePlus("temp filtered image", filtered_ip));
		} else { 
			image = ImageJFunctions.wrapReal(imp);
		}
		
		Rectangle roi = region.getBounds();
		
		// use a View to define an interval (min and max coordinate, inclusive) to display
		long[] min = new long[2];
		min[0] = roi.x;
		min[1] = roi.y;
		long[] max = new long[2];
		max[0] = roi.x + roi.width - 1;
		max[1] = roi.y + roi.height - 1;
		
		final Iterable< T > roi_region = Views.interval( image, min, max );
		
		Iterator< T > iterator = roi_region.iterator();

		// determine mean and standard deviation for the image...
		double mean = 0;
		double stdDev = 0;
		
		//This is the actual threshold in absolute pixel values...
		double t = 0;
		
		while ( iterator.hasNext() ) {
			mean += iterator.next().getRealDouble();
		}
		
		mean /= roi.width * roi.height;
		
		//Need to reset the iterator to the beginning.
		//Only way I know to do this was by just generating again from the roi_region...
		iterator = roi_region.iterator();
		
		while ( iterator.hasNext() ) {
			
			double d = iterator.next().getRealDouble() - mean;
			
			stdDev += d * d;
		}
		
		stdDev /= roi.width * roi.height;
		stdDev = Math.sqrt(stdDev);
		
		t = mean + threshold * stdDev;
		
		Cursor< T > roiCursor = Views.interval( image, min, max ).cursor();
		//ArrayList<Peak> possiblePeaks = new ArrayList<Peak>();
		
		while (roiCursor.hasNext()) {
			 double pixel = roiCursor.next().getRealDouble();
			
			 if ( pixel > t ) {
				 possiblePeaks.add(new Peak(roiCursor.getIntPosition(0), roiCursor.getIntPosition(1), pixel, slice));
	         }
		}
		
		if (possiblePeaks.isEmpty())
			return null;
		
		//Sort the list from lowest to highest pixel value...
		Collections.sort(possiblePeaks, new Comparator<Peak>(){
			@Override
			public int compare(Peak o1, Peak o2) {
				return Double.compare(o1.getPixelValue(), o2.getPixelValue());		
			}
		});
				 
		//We have to make a copy to pass to the KDTREE because it will change the order and we have already sorted from lowest to highest to pick center of peaks in for loop below.
		//This is a shallow copy, which means it contains exactly the same elements as the first list, but the order can be completely different...
		ArrayList<Peak> KDTreePossiblePeaks = new ArrayList<>(possiblePeaks);
		
		//Allows for fast search of nearest peaks...
		KDTree<Peak> possiblePeakTree = new KDTree<Peak>(KDTreePossiblePeaks, KDTreePossiblePeaks);
		
		RadiusNeighborSearchOnKDTree< Peak > radiusSearch = new RadiusNeighborSearchOnKDTree< Peak >( possiblePeakTree );
		
		//As we loop through all possible peaks and remove those that are too close
		//we will add all the selected peaks to a new array 
		//that will serve as the finalList of actual peaks
		//This whole process is to remove pixels near the center peak pixel that are also above the detection threshold but all part of the same peak...
		ArrayList<Peak> finalPeaks = new ArrayList<Peak>();
			
		
		//It is really important to remember here that possiblePeaks and KDTreePossiblePeaks are different lists but point to the same elements
		//That means if we setNotValid in one it is changing the same object in another that is required for the stuff below to work.
		for (int i=possiblePeaks.size()-1;i>=0;i--) {
			Peak peak = possiblePeaks.get(i);
			if (peak.isValid()) {
				finalPeaks.add(peak);
				
				//Then we remove all possible peaks within the minimumDistance...
				//This will include the peak we just added to the peaks list...
				radiusSearch.search(peak, minimumDistance, false);
				
				for (int j = 0 ; j < radiusSearch.numNeighbors() ; j++ ) {
					radiusSearch.getSampler(j).get().setNotValid();
				}
			}
		}
		
		return finalPeaks;
	}
}
