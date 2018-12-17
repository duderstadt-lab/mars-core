/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.sdmm.ImageProcessing;

import java.awt.Rectangle;

import de.mpg.biochem.sdmm.util.LevenbergMarquardt;
import ij.process.ImageProcessor;

public class PeakFitter {
	
	private boolean[] vary;
	private double precision = 1e-6;
	
	//Assumed 2D Gaussian input here is
	//p[0] = Baseline
	//p[1] = Height
	//p[2] = X
	//p[3] = Y
	//p[4] = Sigma
	
	private LevenbergMarquardt lm = new LevenbergMarquardt() {
		
		//For symmetric gaussian
		@Override
		public double getValue(double[] x, double[] p, double[] dyda) {
			double dx = x[0] - p[2];
			double dy = x[1] - p[3];
			
			double sigmaSq = p[4] * p[4];
			
			dyda[0] = 1;
			dyda[1] = Math.exp(-((dx * dx) / (2 * sigmaSq) + (dy * dy) / (2 * sigmaSq)));
			
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
		for (int i=0;i<vary.length;i++)
			vary[i] = true;
	}
	
	public PeakFitter(boolean[] vary) {
		this.vary = vary;
	}
	
	public PeakFitter(boolean[] vary, double allowable_error) {
		this.vary = vary;
		this.precision = allowable_error;
	}
	
	public void fitPeak(ImageProcessor ip, double[] p, double[] e) {
		fitPeak(ip, p, e, false);
	}

	public void fitPeak(ImageProcessor ip, double[] p, double[] e, boolean findNegativePeaks) {
		Rectangle roi = ip.getRoi();
			
		double[][] xs = new double[roi.width * roi.height][2];
		double[] ys = new double[xs.length];
			
		int n = 0;
		int max = 0;
		int min = 0;
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				xs[n][0] = x;
				xs[n][1] = y;
				ys[n] = ip.getf(x, y);
				
				if (ys[n] > ys[max])
					max = n;
				if (ys[n] < ys[min])
					min = n;
				n++;
			}
		}
		
		//For fitting negative peaks we need to flip the min and max
		if (findNegativePeaks) {
			int tmpMax = max;
			int tmpMin = min;
			max = tmpMin;
			min = tmpMax;
		}
		
		double[] guess = {ys[min], ys[max] - ys[min], xs[max][0], xs[max][1], 1};
		
		if (!Double.isNaN(p[2]) && !Double.isNaN(p[3])) {
			p[0] = ys[min];
			p[1] = ip.getf((int)p[2], (int)p[3]) - p[0];
		}
		
		for (int i = 0; i < p.length; i++)
			if (Double.isNaN(p[i])) p[i] = guess[i];
		
		// fit peak
		lm.precision = precision;
		lm.solve(xs, ys, null, n, p, vary, e, 0.001);
	}
}
