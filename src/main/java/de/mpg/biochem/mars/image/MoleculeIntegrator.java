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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import ij.gui.Roi;
import ij.process.ImageProcessor;

/**
 * Class for calculating the integrated fluorescence given a peak list and image processor. 
 * 
 * @author Karl Duderstadt
 * 
 */
public class MoleculeIntegrator {
	int innerRadius, outerRadius;
	
	private ArrayList<int[]> innerOffsets;
	private ArrayList<int[]> outerOffsets;

	public MoleculeIntegrator (int innerRadius, int outerRadius) {
		this.innerRadius = innerRadius;
		this.outerRadius = outerRadius;
		
		BuildOffsets();
	}
	
	private void BuildOffsets() {
		innerOffsets = new ArrayList<int[]>();
		outerOffsets = new ArrayList<int[]>();
		
		for (int y = -outerRadius; y <= outerRadius; y++) {
			for (int x = -outerRadius; x <= outerRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));

				if (d <= innerRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					innerOffsets.add(pos);
				} else if (d <= outerRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					outerOffsets.add(pos);
				}
			}
		}
	}
	
	public void integratePeaks(ImageProcessor ip, Map<String, Peak> integrationList, Rectangle region) {
		for (String UID : integrationList.keySet()) {
			Peak peak = integrationList.get(UID);
			double[] intensity = integratePeak(ip, (int)peak.getX(), (int)peak.getY(), region); 
			peak.setIntensity(intensity[0]);
			peak.setMedianBackground(intensity[1]);
		}
	}
	
	private double[] integratePeak(ImageProcessor ip, int x, int y, Rectangle region) {
		if (x == Double.NaN || y == Double.NaN) {
			double[] NULLinten = new double[2];
			NULLinten[0] = Double.NaN;
			NULLinten[1] = Double.NaN;
			return NULLinten;
		}
		
		double Intensity = 0;
		int innerPixels = 0;
		
		ArrayList<Float> outerPixelValues = new ArrayList<Float>();
		
		for (int[] circleOffset: innerOffsets) {
			Intensity += (double)getPixelValue(ip, x + circleOffset[0], y + circleOffset[1], region);
			innerPixels++;
		}
		
		for (int[] circleOffset: outerOffsets) {
			outerPixelValues.add(getPixelValue(ip, x + circleOffset[0], y + circleOffset[1], region));
		}
		
		//Find the Median background value...
		Collections.sort(outerPixelValues);
		double outerMedian;
		if (outerPixelValues.size() % 2 == 0)
		    outerMedian = ((double)outerPixelValues.get(outerPixelValues.size()/2) + (double)outerPixelValues.get(outerPixelValues.size()/2 - 1))/2;
		else
		    outerMedian = (double) outerPixelValues.get(outerPixelValues.size()/2);
		
		Intensity -= outerMedian*innerPixels;
		
		double[] inten = new double[2];
		inten[0] = Intensity;
		inten[1] = outerMedian;

		return inten;
	}
	
	//Infinite Mirror images to prevent out of bounds issues
	private static float getPixelValue(ImageProcessor proc, int x, int y, Rectangle subregion) {
		//First for x if needed
		if (x < subregion.x) {
			int before = subregion.x - x;
			if (before > subregion.width)
				before = subregion.width - before % subregion.width;
			x = subregion.x + before - 1;
		} else if (x > subregion.x + subregion.width - 1) {
			int beyond = x - (subregion.x + subregion.width - 1);
			if (beyond > subregion.width)
				beyond = subregion.width - beyond % subregion.width;
			x = subregion.x + subregion.width - beyond; 
		}
			
		//Then for y
		if (y < subregion.y) {
			int before = subregion.y - y;
			if (before > subregion.height)
				before = subregion.height - before % subregion.height;
			y = subregion.y + before - 1;
		} else if (y > subregion.y + subregion.height - 1) {
			int beyond = y - (subregion.y + subregion.height - 1);
			if (beyond > subregion.height)
				beyond = subregion.height - beyond % subregion.height;
			y = subregion.y + subregion.height - beyond;  
		}
		
		return proc.getf(x, y);
	}
}
