package de.mpg.biochem.sdmm.ImageProcessing;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentMap;

import de.mpg.biochem.sdmm.molecule.MoleculeArchive;
import ij.gui.Roi;
import ij.process.ImageProcessor;

/**
 * Class for calculating the integrated fluorescence given a peak list and image stack. 
 * We separate this from the Command to provide maximal flexibility in use.
 * 
 * @author Karl Duderstadt
 * 
 */
public class MoleculeIntegrator {
	Rectangle topBoundingRegion, bottomBoundingRegion;
	int innerRadius, outerRadius;
	
	private ArrayList<int[]> innerOffsets;
	private ArrayList<int[]> outerOffsets;

	public MoleculeIntegrator (int innerRadius, int outerRadius, Rectangle topBoundingRegion, Rectangle bottomBoundingRegion) {
		this.innerRadius = innerRadius;
		this.outerRadius = outerRadius;
		this.topBoundingRegion = topBoundingRegion;
		this.bottomBoundingRegion = bottomBoundingRegion;
		
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
	
	public void integratePeaks(ImageProcessor ip, ConcurrentMap<String, FPeak> integrationList, String colorTOP, String colorBOT) {
		for (String UID : integrationList.keySet()) {
			FPeak peak = integrationList.get(UID);
			if (colorTOP != null) {
				peak.setIntensity(colorTOP, integratePeak(ip, (int)peak.getXTOP(), (int)peak.getYTOP(), topBoundingRegion));
			}
			
			if (colorBOT != null) {
				peak.setIntensity(colorBOT, integratePeak(ip, (int)peak.getXBOT(), (int)peak.getYBOT(), bottomBoundingRegion));
			}
		}
	}
	
	private double[] integratePeak(ImageProcessor ip, int x, int y, Rectangle region) {
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
