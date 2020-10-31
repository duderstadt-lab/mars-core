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

import java.util.Map;

import org.scijava.table.DoubleColumn;
import net.imglib2.RealLocalizable;

public class Peak implements RealLocalizable {

	String UID, colorName;
	double t, c;
	Peak forwardLink, backwardLink;
	double x, y, height, baseline, sigma;
	double xError, yError, heightError, baselineError, sigmaError;
	double pixelValue, Rsquared;

	double intensity, medianBackground;
	boolean valid = true;

	public Peak(String UID) {
		this.UID = UID;
	}

	public Peak(String UID, double x, double y) {
		this.UID = UID;
		this.x = x;
		this.y = y;
	}

	public Peak(double[] values, double[] errors) {
		baseline = values[0];
		height = values[1];
		x = values[2];
		y = values[3];
		sigma = values[4];

		baselineError = errors[0];
		heightError = errors[1];
		xError = errors[2];
		yError = errors[3];
		sigmaError = errors[4];
	}

	public Peak(double x, double y, double height, double baseline, double sigma,
		double t)
	{
		this.x = x;
		this.y = y;
		this.height = height;
		this.baseline = baseline;
		this.sigma = sigma;
		this.t = t;
	}

	public Peak(double x, double y, double pixelValue, double t) {
		this.x = x;
		this.y = y;
		this.pixelValue = pixelValue;
		this.t = t;
	}

	public Peak(Peak peak) {
		this.x = peak.x;
		this.y = peak.y;
		this.baseline = peak.baseline;
		this.height = peak.height;
		this.sigma = peak.sigma;
		this.xError = peak.xError;
		this.yError = peak.yError;
		this.baselineError = peak.baselineError;
		this.heightError = peak.heightError;
		this.sigmaError = peak.sigmaError;
		this.pixelValue = peak.pixelValue;
		this.UID = peak.UID;
		this.colorName = peak.colorName;
		this.t = peak.t;
		this.c = peak.c;
		this.intensity = peak.intensity;
		this.medianBackground = peak.medianBackground;
	}

	// Getters
	public double getX() {
		return x;
	}

	public void setX(double x) {
		this.x = x;
	}

	public double getXError() {
		return xError;
	}

	public double getY() {
		return y;
	}

	public void setY(double y) {
		this.y = y;
	}

	public double getYError() {
		return yError;
	}

	public double getHeight() {
		return height;
	}

	public double getHeightError() {
		return heightError;
	}

	public double getBaseline() {
		return baseline;
	}

	public double getBaselineError() {
		return baselineError;
	}

	public double getSigma() {
		return sigma;
	}

	public double getSigmaError() {
		return sigmaError;
	}

	public double getPixelValue() {
		return pixelValue;
	}

	public boolean isValid() {
		return valid;
	}

	public String getUID() {
		return UID;
	}

	public double getT() {
		return t;
	}

	public void setT(double t) {
		this.t = t;
	}

	public double getC() {
		return c;
	}

	public void setC(double c) {
		this.c = c;
	}

	public void setColorName(String colorName) {
		this.colorName = colorName;
	}

	public String getColorName() {
		return colorName;
	}

	// Setters
	public void setValues(double[] values) {
		this.baseline = values[0];
		this.height = values[1];
		this.x = values[2];
		this.y = values[3];
		this.sigma = values[4];
	}

	public void setErrorValues(double[] errors) {
		this.baselineError = errors[0];
		this.heightError = errors[1];
		this.xError = errors[2];
		this.yError = errors[3];
		this.sigmaError = errors[4];
	}

	public void addToColumns(Map<String, DoubleColumn> columns) {
		if (columns.containsKey("T")) columns.get("T").add(t);
		if (columns.containsKey("x")) columns.get("x").add(x);
		if (columns.containsKey("y")) columns.get("y").add(y);
		if (columns.containsKey("Intensity")) columns.get("Intensity").add(
			intensity);
		if (columns.containsKey("baseline")) columns.get("baseline").add(baseline);
		if (columns.containsKey("height")) columns.get("height").add(height);
		if (columns.containsKey("sigma")) columns.get("sigma").add(sigma);
		if (columns.containsKey("R2")) columns.get("R2").add(Rsquared);
	}

	// used for pixel sort in the peak finder
	// and for rejection of bad fits.
	public void setValid() {
		valid = true;
	}

	public void setNotValid() {
		valid = false;
	}

	public void setUID(String UID) {
		this.UID = UID;
	}

	// Sets the reference to the next peak in the trajectory
	public void setForwardLink(Peak link) {
		this.forwardLink = link;
	}

	// Gets the reference to the next peak in the trajectory
	public Peak getForwardLink() {
		return forwardLink;
	}

	// Sets the reference to the previous peak in the trajectory
	public void setBackwardLink(Peak link) {
		this.backwardLink = link;
	}

	// Gets the reference to the previous peak in the trajectory
	public Peak getBackwardLink() {
		return backwardLink;
	}

	public void setIntensity(double intensity) {
		this.intensity = intensity;
	}

	public double getIntensity() {
		return intensity;
	}

	public void setMedianBackground(double medianBackground) {
		this.medianBackground = medianBackground;
	}

	public double getMedianBackground() {
		return medianBackground;
	}

	public void setRsquared(double Rsquared) {
		this.Rsquared = Rsquared;
	}

	public double getRSquared() {
		return Rsquared;
	}

	public void reset(double x, double y, double pixelValue, int t) {
		this.x = x;
		this.y = y;
		this.pixelValue = pixelValue;
		this.t = t;

		valid = true;
		UID = null;
		forwardLink = null;
		backwardLink = null;
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
