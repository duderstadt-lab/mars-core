/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2021 Karl Duderstadt
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

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.AllowedSolution;
import org.apache.commons.math3.analysis.solvers.BracketingNthOrderBrentSolver;

//From http://commons.apache.org/proper/commons-math/userguide/analysis.html
public class MotionBlurForceCalculator implements UnivariateFunction {

	final double relativeAccuracy = 1.0e-12;
	final double absoluteAccuracy = 1.0e-9;
	final int maxOrder = 5;
	final static double kB = 1.380648528 * Math.pow(10, -23);
	final static double vicosityOfWater = 8.90 * Math.pow(10, -4);// Pa·s
	double temperature = 296.15;

	BracketingNthOrderBrentSolver solver;

	double persistenceLength, L0, rawVariance, integrationTime, beadRadius, sa;

	public MotionBlurForceCalculator(double persistenceLength, double L0,
		double integrationTime, double beadRadius)
	{
		this.persistenceLength = persistenceLength;
		this.L0 = L0;
		this.integrationTime = integrationTime;

		solver = new BracketingNthOrderBrentSolver(relativeAccuracy,
			absoluteAccuracy, maxOrder);
	}

	public MotionBlurForceCalculator(double persistenceLength, double L0,
		double temperature, double integrationTime, double beadRadius)
	{
		this.temperature = temperature;
		this.persistenceLength = persistenceLength;
		this.L0 = L0;
		this.integrationTime = integrationTime;
		this.beadRadius = beadRadius;

		solver = new BracketingNthOrderBrentSolver(relativeAccuracy,
			absoluteAccuracy, maxOrder);
	}

	public double[] calculate(double rawVariance) {
		this.rawVariance = rawVariance;
		double length = Double.NaN;
		try {
			// the length must be longer than 0.1 nm and shorter than 1/10000 th of
			// full length
			length = solver.solve(100, this, Math.pow(10, -10), L0 - L0 / 10000,
				AllowedSolution.ANY_SIDE);
		}
		catch (LocalException le) {
			length = Double.NaN;
		}

		double[] output = new double[3];
		output[0] = getWLCForce(length);
		output[1] = length;
		output[2] = sa;

		return output;
	}

	public double value(double length) {
		double wlcForce = getWLCForce(length);
		double equiForce = getEquipartitionForce(length, wlcForce);
		return equiForce - wlcForce;
	}

	public double getWLCForce(double length) {
		double a = kB * temperature / persistenceLength;
		return a * (0.25 * (Math.pow(1 - length / L0, -2)) - 0.25 + length / L0);
	}

	public double getEquipartitionForce(double length, double force) {
		// Update variance correction
		double alpha = (force * integrationTime) / (length * 6 * Math.PI *
			vicosityOfWater * beadRadius);
		this.sa = (2 / alpha) - (2 / (alpha * alpha)) * (1 - Math.exp(-alpha));
		double motionBlurCorrectedVariance = rawVariance / sa;

		return (kB * temperature * length) / motionBlurCorrectedVariance;
	}

	private static class LocalException extends RuntimeException {

		// the x value that caused the problem
		private final double x;

		public LocalException(double x) {
			this.x = x;
		}

		public double getX() {
			return x;
		}

	}
}
