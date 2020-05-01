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
