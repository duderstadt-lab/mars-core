/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
package de.mpg.biochem.mars.image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import net.imglib2.FinalInterval;
import net.imglib2.util.Intervals;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import net.imglib2.KDTree;

public class DogPeakFinder<T extends RealType<T>> {
	
	private double threshold = 46;
	private int minimumDistance = 8;
	private boolean findNegativePeaks = false;
	
	public DogPeakFinder(double threshold, int minimumDistance, boolean findNegativePeaks) {
		this.threshold = threshold;
		this.minimumDistance = minimumDistance;
		this.findNegativePeaks = findNegativePeaks;
	}
	
	public ArrayList<Peak> findPeaks(Img<T> image) {
		return findPeaks(image, -1);
	}
	
	public ArrayList<Peak> findPeaks(Img<T> image, int slice) {
		long[] min = new long[2];
		long[] max = new long[2];
		image.min(min);
		image.max(max);
		FinalInterval interval = Intervals.createMinMax( min[0], min[1], max[0], max[1] ); 
		return findPeaks(image, interval, slice);
	}
	
	public ArrayList<Peak> findPeaks(Img<T> image, FinalInterval interval) {
		return findPeaks(image, interval, -1);
	}
	
	public ArrayList<Peak> findPeaks(Img<T> image, FinalInterval interval, int slice) {
		
		ArrayList<Peak> possiblePeaks = new ArrayList<Peak>(); 
		
		Cursor< T > roiCursor = Views.interval( image, interval ).cursor();
		
		if (!findNegativePeaks) {
			while (roiCursor.hasNext()) {
				 double pixel = roiCursor.next().getRealDouble();
				 
				 if ( pixel > threshold ) {
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
		} else {
			while (roiCursor.hasNext()) {
				 double pixel = roiCursor.next().getRealDouble();
				 
				 if ( pixel < threshold*(-1) ) {
					 possiblePeaks.add(new Peak(roiCursor.getIntPosition(0), roiCursor.getIntPosition(1), pixel, slice));
		         }
			}
		
			if (possiblePeaks.isEmpty())
				return null;
			
			//Sort the list from highest to lowest pixel value...
			Collections.sort(possiblePeaks, new Comparator<Peak>(){
				@Override
				public int compare(Peak o1, Peak o2) {
					return Double.compare(o2.getPixelValue(), o1.getPixelValue());		
				}
			});
		}
				 
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
