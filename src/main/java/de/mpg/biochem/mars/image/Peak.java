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
import java.util.concurrent.ConcurrentHashMap;

import net.imglib2.RealLocalizable;

import org.scijava.table.DoubleColumn;

/**
 * Stores information about detected 2D intensity peaks. Implements
 * {@link RealLocalizable} to allow for KDTree searches. Depending on the
 * implementation, Peaks can be assigned a UID, colorName, Rsquared, as well as
 * t and c position. All parameters from subpixel localization are included as
 * well as error margins. Peaks can be linked to other peaks. The valid
 * parameter is used for convenience in tracking algorithms.
 * 
 * @author Karl Duderstadt
 */
public class Peak implements RealLocalizable {

	private String UID, colorName;
	private Peak forwardLink, backwardLink;
	
	//Attempted converting some of these to boxed primatives but that took way too much memory.
	private double x, y, pixelValue, height, baseline, sigma, r2, medianBackground, intensity;
	private int c, t;
	private boolean valid = true;
	
	public static final String HEIGHT = "height";
	public static final String BASELINE = "baseline";
	public static final String SIGMA = "sigma";
	public static final String R2 = "R2";
	public static final String MEDIAN_BACKGROUND = "medianBackground";
	public static final String INTENSITY = "intensity";

	/**
	 * Polygon2D attached to the peak. left null if unused.
	 */
	private PeakShape peakShape;
	
	private Map< String, Double > properties;

	public Peak(String UID) {
		this.UID = UID;
	}

	public Peak(String UID, double x, double y) {
		this.UID = UID;
		this.x = x;
		this.y = y;
	}

	public Peak(double[] values) {
		baseline = values[0];
		height = values[1];
		x = values[2];
		y = values[3];
		sigma = values[4];
	}

	public Peak(double x, double y, double height, double baseline, double sigma,
		int t)
	{
		this.x = x;
		this.y = y;
		this.baseline = baseline;
		this.height = height;
		this.sigma = sigma;
		this.t = t;
	}

	public Peak(double x, double y, double pixelValue, int t) {
		this.x = x;
		this.y = y;
		this.pixelValue = pixelValue;
		this.t = t;
	}
	
	public Peak(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	public Peak(Peak peak) {
		this.baseline = peak.baseline;
		this.height = peak.height;
		this.sigma = peak.sigma;
		this.x = peak.getX();
		this.y = peak.getY();
		this.pixelValue = peak.pixelValue;
		this.UID = peak.UID;
		this.colorName = peak.colorName;
		this.t = peak.t;
		this.c = peak.c;
		this.r2 = peak.r2;
		this.medianBackground = peak.medianBackground; 
		this.intensity = peak.intensity;
		
		if (peak.getProperties() != null)
			for (String key : peak.getProperties().keySet())
			    setProperty(key, peak.getProperties().get(key));
	}

	// Getters
	public double getX() {
		return x;
	}

	public void setX(double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}

	public void setY(double y) {
		this.y = y;
	}

	public void setHeight(double height) {
		this.height = height;
	}
	
	public double getHeight() {
		return height;
	}

	public void setBaseline(double baseline) {
		this.baseline = baseline;
	}
	
	public double getBaseline() {
		return baseline;
	}

	public double getSigma() {
		return sigma;
	}
	
	public PeakShape getShape() {
		return peakShape;
	}
	
	public void setShape(PeakShape peakShape) {
		this.peakShape = peakShape;
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

	public int getT() {
		return t;
	}

	public void setT(int t) {
		this.t = t;
	}

	public int getC() {
		return c;
	}

	public void setC(int c) {
		this.c = c;
	}

	public void setColorName(String colorName) {
		this.colorName = colorName;
	}

	public String getColorName() {
		return colorName;
	}

	public Map< String, Double > getProperties() {
		return properties;
	}
	
	// Setters
	public void setProperty(String name, Double value) {
		if (properties == null)
			properties = new ConcurrentHashMap<>();
		
		properties.put(name, value);
	}
	
	public void setValues(double[] values) {
		this.baseline = values[0];
		this.height = values[1];
		this.x = values[2];
		this.y = values[3];
		this.sigma = values[4];
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

	public void setRsquared(double r2) {
		this.r2 = r2;
	}

	public double getRSquared() {
		return r2;
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
