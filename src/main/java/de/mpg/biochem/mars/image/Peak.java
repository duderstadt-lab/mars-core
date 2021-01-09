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
	private double x, y, t, c, pixelValue;
	private boolean valid = true;

	/**
	 * Polygon2D attached to the peak. left null if unused.
	 */
	private PeakShape peakShape;

	public static final String HEIGHT = "height";
	public static final String BASELINE = "baseline";
	public static final String SIGMA = "sigma";
	public static final String R2 = "R2";
	public static final String MEDIAN_BACKGROUND = "medianBackground";
	public static final String INTENSITY = "intensity";
	
	private final Map< String, Double > properties = new ConcurrentHashMap<>();

	public Peak(String UID) {
		this.UID = UID;
	}

	public Peak(String UID, double x, double y) {
		this.UID = UID;
		this.x = x;
		this.y = y;
	}

	public Peak(double[] values) {
		setProperty(BASELINE, values[0]);
		setProperty(HEIGHT, values[1]);
		x = values[2];
		y = values[3];
		setProperty(SIGMA, values[4]);
	}

	public Peak(double x, double y, double height, double baseline, double sigma,
		double t)
	{
		this.x = x;
		this.y = y;
		setProperty(BASELINE, baseline);
		setProperty(HEIGHT, height);
		setProperty(SIGMA, sigma);
		this.t = t;
	}

	public Peak(double x, double y, double pixelValue, double t) {
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
		this.x = peak.getX();
		this.y = peak.getY();
		this.pixelValue = peak.pixelValue;
		this.UID = peak.UID;
		this.colorName = peak.colorName;
		this.t = peak.t;
		this.c = peak.c;
		
		for (String key : peak.getProperties().keySet())
		    this.properties.put(key, peak.getProperties().get(key));
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

	public double getHeight() {
		return this.properties.get(HEIGHT).doubleValue();
	}

	public double getBaseline() {
		return this.properties.get(BASELINE).doubleValue();
	}

	public double getSigma() {
		return this.properties.get(SIGMA).doubleValue();
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
	
	public Map< String, Double > getProperties() {
		return properties;
	}

	// Setters
	public void setProperty(String name, Double value) {
		properties.put(name, value);
	}
	
	public void setValues(double[] values) {
		this.properties.put(BASELINE, values[0]);
		this.properties.put(HEIGHT, values[1]);
		this.x = values[2];
		this.y = values[3];
		this.properties.put(SIGMA, values[4]);
	}

	public void addToColumns(Map<String, DoubleColumn> columns) {
		if (columns.containsKey("T")) columns.get("T").add(t);
		if (columns.containsKey("x")) columns.get("x").add(x);
		if (columns.containsKey("y")) columns.get("y").add(y);
		if (columns.containsKey(INTENSITY)) columns.get(INTENSITY).add(properties.get(INTENSITY).doubleValue());
		if (columns.containsKey(BASELINE)) columns.get(BASELINE).add(properties.get(BASELINE).doubleValue());
		if (columns.containsKey(HEIGHT)) columns.get(HEIGHT).add(properties.get(HEIGHT).doubleValue());
		if (columns.containsKey(SIGMA)) columns.get(SIGMA).add(properties.get(SIGMA).doubleValue());
		if (columns.containsKey(R2)) columns.get(R2).add(properties.get(R2).doubleValue());
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
		this.properties.put(INTENSITY, intensity);
	}

	public double getIntensity() {
		return this.properties.get(INTENSITY).doubleValue();
	}

	public void setMedianBackground(double medianBackground) {
		this.properties.put(MEDIAN_BACKGROUND, medianBackground);
	}

	public double getMedianBackground() {
		return this.properties.get(MEDIAN_BACKGROUND).doubleValue();
	}

	public void setRsquared(double R2) {
		this.properties.put("R2", R2);
	}

	public double getRSquared() {
		if (properties.containsKey("R2"))
			return this.properties.get(R2).doubleValue();
		else
			return 0;
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
