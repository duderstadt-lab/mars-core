package de.mpg.biochem.sdmm.ImageProcessing;

import java.util.HashMap;

//Peak definition for integrations
public class FPeak {
	private String UID;
//	int slice;
	//Short and long wavelengths
	private double xLONG, yLONG, xSHORT, ySHORT;

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
