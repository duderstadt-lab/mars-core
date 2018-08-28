package de.mpg.biochem.sdmm.util;

import org.decimal4j.util.DoubleRounder;

//A collection of useful utility math functions used multiple times throughout the SDMM Plugins.
public class SDMMMath {
	//Precision in number of decimal places for output arrays
	//Primarily used for DataTable output
	final static int DECIMAL_PLACE_PRECISION = 7;
	
	public static double round(double input) {
		return DoubleRounder.round(input, DECIMAL_PLACE_PRECISION);
	}
	
	public static double[] roundArray(double[] input) {
		double[] output = new double[input.length];
		for (int i=0;i<input.length;i++) {
			output[i] = DoubleRounder.round(input[i], DECIMAL_PLACE_PRECISION);
		}
		return output;
	}
	
	// Equations and notation taken directly from "An Introduction to Error Analysis" by Taylor 2nd edition
	// y = A + Bx
	// A = output[0] +/- output[1]
	// B = output[2] +/- output[3]
	// error is the STD here.
	public static double[] linearRegression(double[] xData, double[] yData, int offset, int length) {
		double[] output = new double[4];

		//First we determine delta (Taylor's notation)
		double XsumSquares = 0;
		double Xsum = 0;
		double Ysum = 0;
		double XYsum = 0;
		for (int i = offset; i< offset + length; i++) {
			XsumSquares += xData[i]*xData[i];
			Xsum += xData[i];
			Ysum += yData[i];
			XYsum += xData[i]*yData[i];
		}
		double Delta = length*XsumSquares-Xsum*Xsum;
		double A = (XsumSquares*Ysum-Xsum*XYsum)/Delta;
		double B = (length*XYsum-Xsum*Ysum)/Delta;
		
		double ymAmBxSquare = 0;
		for (int i = offset; i < offset + length; i++) {
			ymAmBxSquare += (yData[i]-A-B*xData[i])*(yData[i]-A-B*xData[i]);
		}
		double sigmaY = Math.sqrt(ymAmBxSquare/(length-2));
		
		output[0] = A;
		output[1] = sigmaY*Math.sqrt(XsumSquares/Delta);		
		output[2] = B;
		output[3] = sigmaY*Math.sqrt(length/Delta);
		
		return output;
	}
}
