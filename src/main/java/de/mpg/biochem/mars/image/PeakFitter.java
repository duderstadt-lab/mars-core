/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
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

import java.awt.Rectangle;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import de.mpg.biochem.mars.util.LevenbergMarquardt;

public class PeakFitter {

	private boolean[] vary;
	private double precision = 1e-6;

	// Assumed 2D Gaussian input here is
	// p[0] = Baseline
	// p[1] = Height
	// p[2] = X
	// p[3] = Y
	// p[4] = Sigma

	private LevenbergMarquardt lm = new LevenbergMarquardt() {

		// For symmetric gaussian
		@Override
		public double getValue(double[] x, double[] p, double[] dyda) {
			double dx = x[0] - p[2];
			double dy = x[1] - p[3];

			double sigmaSq = p[4] * p[4];

			dyda[0] = 1;
			dyda[1] = Math.exp(-((dx * dx) / (2 * sigmaSq) + (dy * dy) / (2 *
				sigmaSq)));

			double d3 = p[1] * dyda[1];

			dyda[2] = (d3 * dx) / sigmaSq;
			dyda[3] = (d3 * dy) / sigmaSq;
			dyda[4] = (d3 * (dx * dx + dy * dy)) / (sigmaSq * p[4]);

			return p[0] + d3;
		}

		/*
		 * For non-symmetric gaussian.
		 * 
		@Override
		public double getValue(double[] x, double[] p, double[] dyda) {
			double dx = x[0] - p[2];
			double dy = x[1] - p[3];
			
			double d1 = p[4] * p[4];
			double d2 = p[5] * p[5];
			
			dyda[0] = 1;
			dyda[1] = Math.exp(-((dx * dx) / (2 * d1) + (dy * dy) / (2 * d2)));
			
			double d3 = p[1] * dyda[1];
			
			dyda[2] = (d3 * dx) / d1;
			dyda[3] = (d3 * dy) / d2;
			dyda[4] = (d3 * dx * dx) / (d1 * p[4]);
			dyda[5] = (d3 * dy * dy) / (d2 * p[5]);
			
			return p[0] + d3;
		}
		*/
	};

	public PeakFitter() {
		vary = new boolean[5];
		for (int i = 0; i < vary.length; i++)
			vary[i] = true;
	}

	public PeakFitter(boolean[] vary) {
		this.vary = vary;
	}

	public PeakFitter(boolean[] vary, double allowable_error) {
		this.vary = vary;
		this.precision = allowable_error;
	}

	public <T extends RealType<T> & NativeType<T>> void fitPeak(
		RandomAccessible<T> img, double[] p, double[] e, Rectangle roi)
	{
		fitPeak(img, p, e, roi, false);
	}

	public <T extends RealType<T> & NativeType<T>> void fitPeak(
		RandomAccessible<T> img, double[] p, double[] e, Rectangle roi,
		boolean findNegativePeaks)
	{
		// Rectangle roi = ip.getRoi();

		double[][] xs = new double[roi.width * roi.height][2];
		double[] ys = new double[xs.length];

		int n = 0;
		int max = 0;
		int min = 0;

		RandomAccess<T> ra = img.randomAccess();

		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				xs[n][0] = x;
				xs[n][1] = y;
				ys[n] = ra.setPositionAndGet(x, y).getRealDouble();

				if (ys[n] > ys[max]) max = n;
				if (ys[n] < ys[min]) min = n;
				n++;
			}
		}

		// For fitting negative peaks we need to flip the min and max
		if (findNegativePeaks) {
			int tmpMax = max;
			int tmpMin = min;
			max = tmpMin;
			min = tmpMax;
		}

		double[] guess = { ys[min], ys[max] - ys[min], xs[max][0], xs[max][1], 1 };

		if (!Double.isNaN(p[2]) && !Double.isNaN(p[3])) {
			p[0] = ys[min];
			p[1] = ra.setPositionAndGet((int) p[2], (int) p[3]).getRealDouble() -
				p[0];
		}

		for (int i = 0; i < p.length; i++)
			if (Double.isNaN(p[i])) p[i] = guess[i];

		// fit peak
		lm.precision = precision;
		lm.solve(xs, ys, null, n, p, vary, e, 0.001);
	}
}
