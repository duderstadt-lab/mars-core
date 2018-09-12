package de.mpg.biochem.sdmm.ImageProcessing;

import java.util.HashMap;

//Peak definition for integrations
public class FPeak {
	private String UID;
//	int slice;
	private double xTOP, yTOP, xBOT, yBOT;

	//List of integrated intensities for each molecule
	//for each color
	private HashMap<String, double[]> IntensityList;
	
	public FPeak(String UID) {
		this.UID = UID;
		IntensityList = new HashMap<String, double[]>();
	}
	
	public FPeak(FPeak peak) {
		this.UID = peak.getUID();
		this.xTOP = peak.getXTOP();
		this.yTOP = peak.getYTOP();
		this.xBOT = peak.getXBOT();
		this.yBOT = peak.getYBOT();
		
		IntensityList = new HashMap<String, double[]>();
		
		for (String color : peak.getIntensityList().keySet()) {
			double[] values = new double[2];
			values[0] = peak.getIntensity(color)[0];
			values[1] = peak.getIntensity(color)[1];
			IntensityList.put(color, values);
		}
	}
	
	//Setters
	public void setTOPXY(double xTOP, double yTOP) {
		this.xTOP = xTOP;
		this.yTOP = yTOP;
	}
	
	public void setBOTXY(double xBOT, double yBOT) {
		this.xBOT = xBOT;
		this.yBOT = yBOT;
	}
	
	public void setIntensity(String color, double[] intensity) {
		IntensityList.put(color, intensity);
	}
	
	//Getters
	public double getXTOP() {
		return xTOP;
	}
	public double getYTOP() {
		return yTOP;
	}
	public double getXBOT() {
		return xBOT;
	}
	public double getYBOT() {
		return yBOT;
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
