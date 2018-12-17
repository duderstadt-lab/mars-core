/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.sdmm.ImageProcessing;

import java.util.HashMap;

//Peak definition for integrations
public class FPeak {
	private String UID;
//	int slice;
	//Short and long wavelengths
	private double xLONG = Double.NaN;
	private double yLONG = Double.NaN;
	private double xSHORT = Double.NaN;
	private double ySHORT = Double.NaN;

	//List of integrated intensities for each molecule
	//for each color
	private HashMap<String, double[]> IntensityList;
	
	public FPeak(String UID) {
		this.UID = UID;
		IntensityList = new HashMap<String, double[]>();
	}
	
	public FPeak(FPeak peak) {
		this.UID = peak.getUID();
		this.xLONG = peak.getXLONG();
		this.yLONG = peak.getYLONG();
		this.xSHORT = peak.getXSHORT();
		this.ySHORT = peak.getYSHORT();
		
		IntensityList = new HashMap<String, double[]>();
		
		for (String color : peak.getIntensityList().keySet()) {
			double[] values = new double[2];
			values[0] = peak.getIntensity(color)[0];
			values[1] = peak.getIntensity(color)[1];
			IntensityList.put(color, values);
		}
	}
	
	//Setters
	public void setLONGXY(double xLONG, double yLONG) {
		this.xLONG = xLONG;
		this.yLONG = yLONG;
	}
	
	public void setSHORTXY(double xSHORT, double ySHORT) {
		this.xSHORT = xSHORT;
		this.ySHORT = ySHORT;
	}
	
	public void setIntensity(String color, double[] intensity) {
		IntensityList.put(color, intensity);
	}
	
	//Getters
	public double getXLONG() {
		return xLONG;
	}
	public double getYLONG() {
		return yLONG;
	}
	public double getXSHORT() {
		return xSHORT;
	}
	public double getYSHORT() {
		return ySHORT;
	}
	public String getUID() {
		return UID;
	}
	
	public HashMap<String, double[]> getIntensityList() {
		return IntensityList;
	}
	
	public double[] getIntensity(String color) {
		return IntensityList.get(color);
	}
	
}
