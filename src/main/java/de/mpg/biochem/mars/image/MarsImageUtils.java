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
package de.mpg.biochem.mars.image;

import java.util.ArrayList;
import java.util.List;

import de.mpg.biochem.mars.util.Gaussian2D;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

import net.imagej.ops.OpService;

public class MarsImageUtils {
	
	public static List<int[]> innerIntegrationOffsets(int integrationInnerRadius) {
		ArrayList<int[]> innerOffsets = new ArrayList<int[]>();
		
		for (int y = -integrationInnerRadius; y <= integrationInnerRadius; y++) {
			for (int x = -integrationInnerRadius; x <= integrationInnerRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));
	
				if (d <= integrationInnerRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					innerOffsets.add(pos);
				}
			}
		}
		return innerOffsets;
	}
	
	public static List<int[]> outerIntegrationOffsets(int integrationInnerRadius, int integrationOuterRadius) {
		ArrayList<int[]> outerOffsets = new ArrayList<int[]>();
		
		for (int y = -integrationOuterRadius; y <= integrationOuterRadius; y++) {
			for (int x = -integrationOuterRadius; x <= integrationOuterRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));
	
				if (d > integrationInnerRadius && d <= integrationOuterRadius) {
					int[] pos = new int[2];
					pos[0] = x;
					pos[1] = y;
					outerOffsets.add(pos);
				}
			}
		}
		return outerOffsets;
	}
	
	public static <T extends RealType< T >> List<Peak> findPeaks(Img< T > img, int t, 
			double threshold, int minimumDistance, boolean findNegativePeaks) {
		ArrayList<Peak> peaks;
		
		PeakFinder finder = new PeakFinder(threshold, minimumDistance, findNegativePeaks);
	
		peaks = finder.findPeaks(img, t);
		
		if (peaks == null)
			peaks = new ArrayList<Peak>();
		
		return peaks;
	}
	
	public static <T extends RealType< T >> Img<FloatType> dogFilter(ImagePlus imp, double dogFilterRadius, OpService opService) {
		Img<FloatType> converted = opService.convert().float32((Img< T >)ImagePlusAdapter.wrap( imp ));

        Img<FloatType> dog = opService.create().img(converted);

        final double sigma1 = dogFilterRadius / Math.sqrt( 2 ) * 0.9;
		final double sigma2 = dogFilterRadius / Math.sqrt( 2 ) * 1.1;

		opService.filter().dog(dog, converted, sigma2, sigma1);
		
		return dog;
	}
	
	public static <T extends RealType< T >> List<Peak> findPeaksInRoi(Img< T > img, int t, 
			double threshold, int minimumDistance, boolean findNegativePeaks, 
			int x0, int y0, int width, int height) {
		ArrayList<Peak> peaks;
		
		PeakFinder finder = new PeakFinder(threshold, minimumDistance, findNegativePeaks);
		
		peaks = finder.findPeaks(img, Intervals.createMinMax(x0, y0, x0 + width - 1, y0 + height - 1), t);
		
		if (peaks == null)
			peaks = new ArrayList<Peak>();
		
		return peaks;
	}
	
	public static ArrayList<Peak> fitPeaks(ImageProcessor imp, ArrayList<Peak> positionList, int fitRadius, int dogFilterRadius, boolean findNegativePeaks) {
		
		ArrayList<Peak> newList = new ArrayList<Peak>();
		
		int fitWidth = fitRadius * 2 + 1;
		
		for (Peak peak: positionList) {
			
			imp.setRoi(new Roi(peak.getX() - fitRadius, peak.getY() - fitRadius, fitWidth, fitWidth));
			
			double[] p = new double[5];
			p[0] = Double.NaN;
			p[1] = Double.NaN;
			p[2] = peak.getX();
			p[3] = peak.getY();
			p[4] = dogFilterRadius/2;
			double[] e = new double[5];
			
			fitter.fitPeak(imp, p, e, findNegativePeaks);
			
			// First we reset valid since it was set to false for all peaks
			// during the finding step to avoid finding the same peak twice.
			peak.setValid();
			
			for (int i = 0; i < p.length && peak.isValid(); i++) {
				if (Double.isNaN(p[i]))
					peak.setNotValid();
			}

			//If the x, y, sigma values are negative reject the peak
			//but we can have negative height p[0] or baseline p[1]
			if (p[2] < 0 || p[3] < 0 || p[4] < 0) {
				peak.setNotValid();
			}
			
			double Rsquared = 0;
			if (peak.isValid()) {
				Gaussian2D gauss = new Gaussian2D(p);
				Rsquared = calcR2(gauss, imp);
				if (Rsquared <= RsquaredMin)
					peak.setNotValid();
			}
			
			if (peak.isValid()) {
				peak.setValues(p);
				peak.setRsquared(Rsquared);
				
				//Integrate intensity
				if (integrate) {
					//Type casting from double to int rounds down always, so we have to add 0.5 offset to be correct.
					//Math.round() is be an alternative option...
					double[] intensity = integratePeak(imp, (int)(peak.getX() + 0.5), (int)(peak.getY() + 0.5), rect);
					peak.setIntensity(intensity[0]);
				}
				
				newList.add(peak);
			}
		}
		return newList;
	}

}
