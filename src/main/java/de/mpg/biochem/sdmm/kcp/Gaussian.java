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
package de.mpg.biochem.sdmm.kcp;

public class Gaussian {
	public double x0, y0, x_sigma, y_sigma, normalization, duration;
	//1D case
	public Gaussian(double X, double X_sigma) {
		x0 = X;
		x_sigma = X_sigma;
		normalization = 1/(Math.sqrt(2*Math.PI)*x_sigma);
	}
	public Gaussian(double X, double X_sigma, double duration) {
		x0 = X;
		x_sigma = X_sigma;
		normalization = 1/(Math.sqrt(2*Math.PI)*x_sigma);
		this.duration = duration;
	}
	//2D case
	public Gaussian(double X, double X_sigma, double Y, double Y_sigma) {
		// I guess eventually this function could take a array or something.
		x0 = X;
		y0 = Y;
		x_sigma = X_sigma;
		y_sigma = Y_sigma;
		normalization = 1/(2*Math.PI*x_sigma*y_sigma);
	}
	public double getValue(double X, double Y) {
		return normalization*Math.exp(-((X-x0)*(X-x0))/(2*x_sigma*x_sigma)-((Y-y0)*(Y-y0))/(2*y_sigma*y_sigma));
	}
	public double getValue(double X) {
		return normalization*Math.exp(-((X-x0)*(X-x0))/(2*x_sigma*x_sigma));
	}
	public double getDuration() {
		return duration;
	}
}
