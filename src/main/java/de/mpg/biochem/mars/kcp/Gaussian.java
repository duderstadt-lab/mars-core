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

package de.mpg.biochem.mars.kcp;

public class Gaussian {

	public double x0, y0, x_sigma, y_sigma, normalization, duration;

	// 1D case
	public Gaussian(double X, double X_sigma) {
		x0 = X;
		x_sigma = X_sigma;
		normalization = 1 / (Math.sqrt(2 * Math.PI) * x_sigma);
	}

	public Gaussian(double X, double X_sigma, double duration) {
		x0 = X;
		x_sigma = X_sigma;
		normalization = 1 / (Math.sqrt(2 * Math.PI) * x_sigma);
		this.duration = duration;
	}

	// 2D case
	public Gaussian(double X, double X_sigma, double Y, double Y_sigma) {
		// I guess eventually this function could take a array or something.
		x0 = X;
		y0 = Y;
		x_sigma = X_sigma;
		y_sigma = Y_sigma;
		normalization = 1 / (2 * Math.PI * x_sigma * y_sigma);
	}

	public double getValue(double X, double Y) {
		return normalization * Math.exp(-((X - x0) * (X - x0)) / (2 * x_sigma *
			x_sigma) - ((Y - y0) * (Y - y0)) / (2 * y_sigma * y_sigma));
	}

	public double getValue(double X) {
		return normalization * Math.exp(-((X - x0) * (X - x0)) / (2 * x_sigma *
			x_sigma));
	}

	public double getDuration() {
		return duration;
	}
}
