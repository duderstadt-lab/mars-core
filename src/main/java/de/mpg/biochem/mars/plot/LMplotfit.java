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
package de.mpg.biochem.mars.plot;
import de.mpg.biochem.mars.util.LevenbergMarquardt;

public class LMplotfit extends LevenbergMarquardt {
	private static final double deltaParameter = 1e-6;
	private static final double factor = 10;

	public double chiSquared = 0.0;
	public int iterations;
	
	private LMFunction function;
	private int nParameters;
	private double precision;
	private double maxIterations;

	private double[] delta;
	private double[][] alpha;
	private double[][] beta;
	private double[][] covar;
	double[][] identityMatrix;
	
	public LMplotfit(LMFunction function, int nParameters) {
		this(function, nParameters, 0.00001, 10000);
	}
	
	public LMplotfit(LMFunction function, int nParameters, double precision, int maxIterations) {
		this.function = function;
		this.precision = precision;
		this.maxIterations = maxIterations;
		this.nParameters = nParameters;
		
		delta = new double[nParameters];
		alpha = new double[nParameters][nParameters];
		beta = new double[nParameters][1];
		covar = new double[nParameters][nParameters];
		identityMatrix = new double[nParameters][nParameters];
	}
	
	//Needs to somehow be integrated into LM better and use this method properly...
	public double getValue(double[] x, double[] p, double[] dyda) {
		return 0;	
	}
	
	private final double getChiSquared(double[] parameters, double[][] x, double[] y, double[] sigma) {
		double sumOfSquares = 0.0;
		double residual;
		
		if (sigma != null) {	// chi squared
			for (int i = 0; i < x.length; i++) {
				residual = (function.getValue(x[i], parameters) - y[i]) / sigma[i];
				sumOfSquares += residual * residual;
			}
		}
		else {	// sum of squares
			for (int i = 0; i < x.length; i++) {
				residual = function.getValue(x[i], parameters) - y[i];
				sumOfSquares += residual * residual;
			}
		}
		
		return sumOfSquares;
	}
	
	private final void calculateJacobian(double[] parameters, double[][] x, double[] y, double[][] jacobian) {
		for (int i = 0; i < nParameters; i++) {
			parameters[i] += deltaParameter;
			
			for (int j = 0; j < x.length; j++)
				jacobian[j][i] = function.getValue(x[j], parameters);
			
			parameters[i] -= 2.0 * deltaParameter;
			
			for (int j = 0; j < x.length; j++) {
				jacobian[j][i] -= function.getValue(x[j], parameters);
				jacobian[j][i] /= 2.0 * deltaParameter;
			}
			
			parameters[i] += deltaParameter;
		}
	}
	
	public double solve(double[] parameters, boolean[] vary, double[][] x, double[] y, double[] sigma, double lambda, double[] stdDev) {
		double before;
		double[][] jacobian = new double[x.length][nParameters];
		
		iterations = 0;
		chiSquared = getChiSquared(parameters, x, y, sigma);
		
		calculateJacobian(parameters, x, y, jacobian);
		
		do {
			// alpha
			for (int i = 0; i < nParameters; i++) {
				
				for (int j = 0; j < nParameters; j++) {
					alpha[j][i] = 0.0;
					
					for (int k = 0; k < x.length; k++)
						alpha[j][i] += jacobian[k][j] * jacobian[k][i];
					
					covar[j][i] = alpha[j][i];
				}
				
				alpha[i][i] += lambda * alpha[i][i];
			}
			
			// beta
			for (int i = 0; i < nParameters; i++) {
				
				beta[i][0] = 0.0;
				
				for (int j = 0; j < x.length; j++)
					beta[i][0] += jacobian[j][i] * (y[j] - function.getValue(x[j], parameters));
				
			}
			
			gaussJordan(alpha, beta);
			
			for (int i = 0; i < nParameters; i++)
				delta[i] = parameters[i] + beta[i][0];
			
			before = chiSquared;
			chiSquared = getChiSquared(delta, x, y, sigma);
			
			if (chiSquared < before) {
				lambda /= factor;
				
				// adjust parameters
				for (int i = 0; i < delta.length; i++) {
					if (vary == null || vary[i])
						parameters[i] = delta[i];
				}
				
				calculateJacobian(parameters, x, y, jacobian);
			}
			else
				lambda *= factor;
			
		} while (++iterations < maxIterations && Math.abs(before - chiSquared) > precision);
		
		if (stdDev != null) {
			// determine standard deviation of parameters
			for (int i = 0; i < identityMatrix.length; i++) {
				for (int j = 0; j < identityMatrix.length; j++)
					identityMatrix[i][j] = 0;
				identityMatrix[i][i] = 1;
			}
			
			gaussJordan(covar, identityMatrix);
			
			for (int i = 0; i < identityMatrix.length; i++)
				stdDev[i] = Math.sqrt(identityMatrix[i][i] * chiSquared / (x.length - parameters.length));
		}
		
		return lambda;
	}
	
	public final void gaussJordan(double[][] left, double[][] right) {
		int n = left.length;
		int rCols = right[0].length;
		
		for (int i = 0; i < n; i++) {
			
			// find pivot
			int max = i;
			
			for (int j = i + 1; j < n; j++) {
				if (Math.abs(left[j][i]) > Math.abs(left[max][i]))
					max = j;
			}
			
			// swap rows
			double[] t = left[i];
			left[i] = left[max];
			left[max] = t;
			
			t = right[i];
			right[i] = right[max];
			right[max] = t;
			
			// reduce
			for (int j = 0; j < n; j++) {
				
				if (j != i) {
					double d = left[j][i] / left[i][i];
					
					left[j][i] = 0;
					
					for (int k = i + 1; k < n; k++)
						left[j][k] -= d * left[i][k];
					
					for (int k = 0; k < rCols; k++)
						right[j][k] -= d * right[i][k];
				}
			}
		}
		
		for (int i = 0; i < n; i++) {
			double d = left[i][i];
			for (int k = 0; k < rCols; k++)
				right[i][k] /= d;
			left[i][i] = 1;
		}
	}
	
	public static void main(String[] args) {
		
		class Gaussian implements LMFunction {
			@Override
			public double getValue(double[] x, double[] parameters) {
				double a = parameters[0];
				double b = parameters[1];
				double c = parameters[2];
				double d = parameters[3];
				
				return a + b * Math.exp(-(x[0] - c) * (x[0] - c) / (2 * d * d));
			}
		} 
		
		double[][] x = {{0}, {1}, {2}, {3}, {4}, {5}, {6},
				{7}, {8}, {9}, {10}, {11}, {12}, {13}};
		
		double y[] = {3365.333251953, 3206.923095703, 3215.769287109,
				3474.846191406, 4320.333496094, 5953.307617188,
				7291.846191406, 7010.307617188, 5404.307617188,
				4016.153808594, 3668.281982422, 3543.769287109,
				3320.820556641, 3248.000000000};
		
		LMplotfit lm = new LMplotfit(new Gaussian(), 4);
		
		double[] p = new double[4];
		double[] s = new double[4];
		double timeSum = 0;
		int iterations = 1000;
		
		for (int i = 0; i < iterations; i++) {
			p[0] = 3365;
			p[1] = 4000;
			p[2] = 5;
			p[3] = 2;
			
			double start = System.nanoTime();
			lm.solve(p, null, x, y, null, 0.001, s);
			double time = System.nanoTime() - start;
			timeSum += time;
			System.out.printf("duration : %fns\n", time);
		}
		
		System.out.printf("average running time (after %d times) : %fns\n", iterations, timeSum / iterations);
		
		System.out.printf("iterations : %d\n", lm.iterations);
		System.out.printf("sum of squares : %f\n", lm.chiSquared);
		
		System.out.println("parameters :");
		for (int i = 0; i < p.length; i++)
			System.out.printf("%f, ", p[i]);
		System.out.println();
		
		System.out.println("standard deviation :");
		for (int i = 0; i < s.length; i++)
			System.out.printf("%f, ", s[i]);
		System.out.println();
		
		// calculating R^2
		double mean = 0;
		for (int i = 0; i < y.length; i++)
			mean += y[i];
		mean /= y.length;
		
		double sst = 0;
		for (int i = 0; i < y.length; i++) {
			double deviation = y[i] - mean;
			sst += deviation * deviation;
		}
		
		double sse = lm.chiSquared;
		
		int n = y.length;
		// the calculation 1.0 - (sse / (n - p)) / (sst / (n - 1)) is not entirely correct
		// http://en.wikipedia.org/wiki/Coefficient_of_determination
		// R^2 = 1 - VARerr / VARtot
		// VARerr = SSerr / (n - p - 1)
		// VARtot = SStot / (n - 1)
		System.out.printf("R^2 : %f\n", 1.0 - (sse / (n - p.length)) / (sst / (n - 1)));
		
		
		
		double[][] l = {{2, 1, -1}, {-3, -1, 2}, {-2, 1, 2}};
		double[][] r = {{8}, {-11}, {-3}};
		
		lm.gaussJordan(l, r);
		
		printMatrices(l, r);
	}
	
	private static void printMatrices(double[][] l, double[][] r) {
		
		System.out.println("left :");
		for (int i = 0; i < l.length; i++) {
			for (int j = 0; j < l[i].length; j++) 
				System.out.printf("%f, ", l[i][j]);
			System.out.println();
		}
		
		System.out.println("right :");
		for (int i = 0; i < r.length; i++) {
			for (int j = 0; j < r[i].length; j++) 
				System.out.printf("%f, ", r[i][j]);
			System.out.println();
		}
		
	}
}
