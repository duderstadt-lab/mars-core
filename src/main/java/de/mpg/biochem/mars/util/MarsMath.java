/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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

package de.mpg.biochem.mars.util;

import java.math.BigInteger;
import java.util.UUID;

import org.decimal4j.util.DoubleRounder;

import com.chrylis.codec.base58.Base58Codec;
import com.chrylis.codec.base58.Base58UUID;

//A collection of useful utility math functions used multiple times throughout MARS.
public class MarsMath {

	private static final BigInteger INIT64 = new BigInteger("cbf29ce484222325",
		16);
	private static final BigInteger PRIME64 = new BigInteger("100000001b3", 16);
	private static final BigInteger MOD64 = new BigInteger("2").pow(64);

	public static double round(double input, int decimalPlacePrecision) {
		double log10 = Math.log10(input);
		if (log10 < 0) {
			decimalPlacePrecision = (int) Math.abs(log10) + decimalPlacePrecision;
			if (decimalPlacePrecision < 0 || decimalPlacePrecision > 18) return input;
		}
		return DoubleRounder.round(input, decimalPlacePrecision);
	}

	public static double[] roundArray(double[] input, int decimalPlacePrecision) {
		double[] output = new double[input.length];
		for (int i = 0; i < input.length; i++) {
			output[i] = DoubleRounder.round(input[i], decimalPlacePrecision);
		}
		return output;
	}

	/**
	 * Creates a histogram from an array of double values.
	 *
	 * @param data    The array of double values to create a histogram from
	 * @param min     The minimum value to consider for the histogram (inclusive)
	 * @param max     The maximum value to consider for the histogram (inclusive)
	 * @param numBins The number of bins to divide the range into
	 * @return An array of doubles where each element contains the count of values
	 *         that fall into the corresponding bin
	 *
	 * @throws IllegalArgumentException if numBins is less than or equal to zero, or if min is greater than or equal to max
	 */
	public static double[] histogram(double[] data, double min, double max, int numBins) {
		if (numBins <= 0) {
			throw new IllegalArgumentException("Number of bins must be positive");
		}
		if (min >= max) {
			throw new IllegalArgumentException("Min must be less than max");
		}

		double[] result = new double[numBins];
		double binWidth = (max - min) / numBins;

		for (double value : data) {
			if (value >= min && value <= max) {
				int binIndex = (int) ((value - min) / binWidth);
				// Handle the case where value == max
				if (binIndex == numBins) {
					binIndex--;
				}
				result[binIndex]++;
			}
		}

		return result;
	}

	/**
	 * Creates a normalized histogram from an array of double values.
	 * The normalized histogram returns the proportion of values in each bin
	 * rather than the raw counts, allowing for easier comparison between
	 * histograms with different sample sizes.
	 *
	 * @param data    The array of double values to create a normalized histogram from
	 * @param min     The minimum value to consider for the histogram (inclusive)
	 * @param max     The maximum value to consider for the histogram (inclusive)
	 * @param numBins The number of bins to divide the range into
	 * @return An array of doubles where each element contains the proportion of values
	 *         that fall into the corresponding bin (values sum to 1.0 if all data points
	 *         are within the min-max range)
	 *
	 * @throws IllegalArgumentException if numBins is less than or equal to zero, or if min is greater than or equal to max
	 */
	public static double[] normalizedHistogram(double[] data, double min, double max, int numBins) {
		if (numBins <= 0) {
			throw new IllegalArgumentException("Number of bins must be positive");
		}
		if (min >= max) {
			throw new IllegalArgumentException("Min must be less than max");
		}

		// First get the raw counts using integers
		int[] counts = new int[numBins];
		double binWidth = (max - min) / numBins;
		int totalCount = 0;

		for (double value : data) {
			if (value >= min && value <= max) {
				int binIndex = (int) ((value - min) / binWidth);
				// Handle the case where value == max
				if (binIndex == numBins) {
					binIndex--;
				}
				counts[binIndex]++;
				totalCount++;
			}
		}

		// Now normalize by dividing each bin by the total count
		double[] normalized = new double[numBins];
		if (totalCount > 0) {  // Prevent division by zero
			for (int i = 0; i < numBins; i++) {
				normalized[i] = (double) counts[i] / totalCount;
			}
		}

		return normalized;
	}

	// Equations and notation taken directly from "An Introduction to Error
	// Analysis" by Taylor 2nd edition
	// y = A + Bx
	// A = output[0] +/- output[1]
	// B = output[2] +/- output[3]
	// error is the STD here.
	public static double[] linearRegression(double[] xData, double[] yData,
		int offset, int length)
	{
		double[] output = new double[4];

		// First we determine delta (Taylor's notation)
		double xSumSquares = 0;
		double xSum = 0;
		double ySum = 0;
		double xySum = 0;
		for (int i = offset; i < offset + length; i++) {
			xSumSquares += xData[i] * xData[i];
			xSum += xData[i];
			ySum += yData[i];
			xySum += xData[i] * yData[i];
		}
		double Delta = length * xSumSquares - xSum * xSum;
		double A = (xSumSquares * ySum - xSum * xySum) / Delta;
		double B = (length * xySum - xSum * ySum) / Delta;

		double ymAmBxSquare = 0;
		for (int i = offset; i < offset + length; i++) {
			ymAmBxSquare += (yData[i] - A - B * xData[i]) * (yData[i] - A - B *
				xData[i]);
		}
		double sigmaY = Math.sqrt(ymAmBxSquare / (length - 2));

		output[0] = A;
		output[1] = sigmaY * Math.sqrt(xSumSquares / Delta);
		output[2] = B;
		output[3] = sigmaY * Math.sqrt(length / Delta);

		return output;
	}

	// Utility methods for creation of base58 encoded UUIDs used for ChronicleMap
	// indexing of molecules.
	public static String getUUID58() {
		Base58UUID bu = new Base58UUID();
		return bu.encode(UUID.randomUUID());
	}

	public static String getUUID58(String uuid) {
		Base58UUID bu = new Base58UUID();
		return bu.encode(UUID.fromString(uuid));
	}

	// method to retrieve the UUID from a base64 encoded UID
	public static UUID getUUID(String uuid58) {
		Base58UUID bu = new Base58UUID();
		return bu.decode(uuid58);
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

	// output[0] is force and output[1] is length
	public static double[] calculateForceAndLength(double variance) {
		ForceCalculator calculator = new ForceCalculator(50 * Math.pow(10, -9),
			6.8 * Math.pow(10, -6));
		return calculator.calculate(variance);
	}

	// output[0] is force and output[1] is length
	public static double[] calculateForceAndLength(double persistenceLength,
		double L0, double temperature, double variance)
	{
		ForceCalculator calculator = new ForceCalculator(persistenceLength, L0,
			temperature);
		return calculator.calculate(variance);
	}
}
