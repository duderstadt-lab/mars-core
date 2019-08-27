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
package de.mpg.biochem.mars.util;

import java.math.BigInteger;
import java.util.UUID;

import org.decimal4j.util.DoubleRounder;

import com.chrylis.codec.base58.Base58Codec;
import com.chrylis.codec.base58.Base58UUID;

//A collection of useful utility math functions used multiple times throughout MARS.
public class MarsMath {
	//Precision in number of decimal places for output arrays
	//Primarily used for DataTable output
	final static int DECIMAL_PLACE_PRECISION = 7;
	
	private static final BigInteger INIT64  = new BigInteger("cbf29ce484222325", 16);
	private static final BigInteger PRIME64 = new BigInteger("100000001b3",      16);
	private static final BigInteger MOD64   = new BigInteger("2").pow(64);
	
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
	
	//Utility methods for creation of base58 encoded UUIDs used for ChronicleMap indexing of molecules.
	public static String getUUID58() {
		Base58UUID bu = new Base58UUID();
		String uuid58 = bu.encode(UUID.randomUUID());
		return uuid58;
	}
	
	//method to retrieve the UUID from a base64 encoded UID
	public static UUID getUUID(String uuid58) {
		Base58UUID bu = new Base58UUID();
		UUID uuid = bu.decode(uuid58);
		return uuid;
	}
	
	public static String getFNV1aBase58(String str) {
		Base58Codec codec = new Base58Codec();
		return codec.encode(fnv1a_64(str.getBytes()).toByteArray());
	}
	
	public static BigInteger fnv1a_64(byte[] data) {
	    BigInteger hash = INIT64;

	    for (byte b : data) {
	      hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
	      hash = hash.multiply(PRIME64).mod(MOD64);
	    }

	    return hash;
	  }
	
	//output[0] is force and output[1] is length
	public static double[] calculateForceAndLength(double msd) {
		ForceCalculator calculator = new ForceCalculator(50*Math.pow(10, -9), 6.8*Math.pow(10, -6));
		double[] output = calculator.calculate(msd);
		return output;
	}
	
	//output[0] is force and output[1] is length
	public static double[] calculateForceAndLength(double persistenceLength, double L0, double temperature, double msd) {
		ForceCalculator calculator = new ForceCalculator(persistenceLength, L0, temperature);
		double[] output = calculator.calculate(msd);
		return output;
	}
}
