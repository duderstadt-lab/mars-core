package de.mpg.biochem.mars.util;

public class Gaussian2D {
	public double baseline, height, x, y, sigma;

	public Gaussian2D(double[] p) {
		baseline = p[0];
		height = p[1];
		x = p[2];
		y = p[3];
		sigma = p[4];
	}
	
	public Gaussian2D(double baseline, double height, double x, double y, double sigma) {
		this.baseline = baseline;
		this.height = height;
		this.x = x;
		this.y = y;
		this.sigma = sigma;
	}
	
	public double getValue(int X, int Y) {
		return height*Math.exp(-((X-x)*(X-x))/(2*sigma*sigma)-((Y-y)*(Y-y))/(2*sigma*sigma)) + baseline;
	}
}
