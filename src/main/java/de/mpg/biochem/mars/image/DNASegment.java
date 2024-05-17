/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2024 Karl Duderstadt
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

package de.mpg.biochem.mars.image;

import net.imglib2.RealLocalizable;

public class DNASegment implements RealLocalizable {

	private double x1, y1, x2, y2;
	private int medianIntensity;
	private double variance;
	public static final String X1 = "X1";
	public static final String Y1 = "Y1";
	public static final String X2 = "X2";
	public static final String Y2 = "Y2";
	public static final String MEDIAN_INTENSITY = "Median_intensity";
	public static final String INTENSITY_VARIANCE = "Intensity_variance";
	public static final String LENGTH = "Length";

	public DNASegment(double x1, double y1, double x2, double y2) {
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
	}

	public double getX1() {
		return x1;
	}

	public double getY1() {
		return y1;
	}

	public double getX2() {
		return x2;
	}

	public double getY2() {
		return y2;
	}

	public double getXCenter() { return x1 + (x2 - x1) / 2;}

	public double getYCenter() { return y1 + (y2 - y1) / 2;}

	public void setX1(double x1) {
		this.x1 = x1;
	}

	public void setY1(double y1) {
		this.y1 = y1;
	}

	public void setX2(double x2) {
		this.x2 = x2;
	}

	public void setY2(double y2) {
		this.y2 = y2;
	}
	
	public double getLength() {
		return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
	}
	
	public double getPositionOnDNA(double x, double y, double DNALength) {
		return Math.sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1)) *
			(DNALength / getLength());
	}

	public void setMedianIntensity(int medianIntensity) {
		this.medianIntensity = medianIntensity;
	}

	public int getMedianIntensity() {
		return medianIntensity;
	}

	public void setVariance(double variance) {
		this.variance = variance;
	}

	public double getVariance() {
		return variance;
	}

	//Override from RealLocalizable interface so peaks can be passed to
	// KDTree and other ImgLib2 functions.
	@Override
	public int numDimensions() {
		// We make no effort to think beyond 2 dimensions !
		return 2;
	}

	@Override
	public double getDoublePosition(int arg0) {
		if (arg0 == 0) {
			return getXCenter();
		}
		else if (arg0 == 1) {
			return getYCenter();
		}
		else {
			return -1;
		}
	}

	@Override
	public float getFloatPosition(int arg0) {
		if (arg0 == 0) {
			return (float) getXCenter();
		}
		else if (arg0 == 1) {
			return (float) getYCenter();
		}
		else {
			return -1;
		}
	}

	@Override
	public void localize(float[] arg0) {
		arg0[0] = (float) getXCenter();
		arg0[1] = (float) getYCenter();
	}

	@Override
	public void localize(double[] arg0) {
		arg0[0] = getXCenter();
		arg0[1] = getYCenter();
	}
}
