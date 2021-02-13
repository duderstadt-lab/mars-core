package de.mpg.biochem.mars.image;

import net.imglib2.RealLocalizable;

public class PeakPixel implements RealLocalizable {
	public double x, y, pixelValue;
	public boolean valid = true;
	
	public PeakPixel(double x, double y, double pixelValue) {
		this.x = x;
		this.y = y;
		this.pixelValue = pixelValue;
	}
	
	// Override from RealLocalizable interface. So peaks can be passed to KDTree
	// and other imglib2 functions.
	@Override
	public int numDimensions() {
		// We are simple minded and make no effort to think beyond 2 dimensions !
		return 2;
	}

	@Override
	public double getDoublePosition(int arg0) {
		if (arg0 == 0) {
			return x;
		}
		else if (arg0 == 1) {
			return y;
		}
		else {
			return -1;
		}
	}

	@Override
	public float getFloatPosition(int arg0) {
		if (arg0 == 0) {
			return (float) x;
		}
		else if (arg0 == 1) {
			return (float) y;
		}
		else {
			return -1;
		}
	}

	@Override
	public void localize(float[] arg0) {
		arg0[0] = (float) x;
		arg0[1] = (float) y;
	}

	@Override
	public void localize(double[] arg0) {
		arg0[0] = x;
		arg0[1] = y;
	}
}
