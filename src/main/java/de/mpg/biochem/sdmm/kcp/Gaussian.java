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
