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

package de.mpg.biochem.mars.image;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import net.imglib2.RealLocalizable;

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
public class Peak extends AbstractJsonConvertibleRecord implements RealLocalizable {
	
	public static AtomicLong idGenerator = new AtomicLong( -1 );
	
	private long id, forwardLinkID, backwardLinkID;

	private String trackUID, colorName;
	private Peak forwardLink, backwardLink;
	private double x, y, pixelValue;
	private int c, t;
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
	
	public Peak(JsonParser jParser) throws IOException {
		super();
		fromJSON(jParser);
	}

	public Peak(double[] values) {
		this.id = idGenerator.incrementAndGet();
		
		setProperty(BASELINE, values[0]);
		setProperty(HEIGHT, values[1]);
		x = values[2];
		y = values[3];
		setProperty(SIGMA, values[4]);
	}

	public Peak(double x, double y, double height, double baseline, double sigma,
		int t)
	{
		this.id = idGenerator.incrementAndGet();
		
		this.x = x;
		this.y = y;
		setProperty(BASELINE, baseline);
		setProperty(HEIGHT, height);
		setProperty(SIGMA, sigma);
		this.t = t;
	}

	public Peak(double x, double y, double pixelValue, int t) {
		this.id = idGenerator.incrementAndGet();
		
		this.x = x;
		this.y = y;
		this.pixelValue = pixelValue;
		this.t = t;
	}
	
	public Peak(double x, double y) {
		this.id = idGenerator.incrementAndGet();
		
		this.x = x;
		this.y = y;
	}
	
	public Peak(Peak peak) {
		this.x = peak.getX();
		this.y = peak.getY();
		this.pixelValue = peak.pixelValue;
		this.trackUID = peak.trackUID;
		this.colorName = peak.colorName;
		this.t = peak.t;
		this.c = peak.c;
		this.id = peak.id;

		this.backwardLinkID = peak.backwardLinkID;
		this.forwardLinkID = peak.forwardLinkID;
		
		this.forwardLink = peak.forwardLink;
		this.backwardLink = peak.backwardLink;

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

	public String getTrackUID() {
		return trackUID;
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
		properties.put(name, value);
	}
	
	public void setValues(double[] values) {
		this.properties.put(BASELINE, values[0]);
		this.properties.put(HEIGHT, values[1]);
		this.x = values[2];
		this.y = values[3];
		this.properties.put(SIGMA, values[4]);
	}

	// used for pixel sort in the peak finder
	// and for rejection of bad fits.
	public void setValid() {
		valid = true;
	}

	public void setNotValid() {
		valid = false;
	}

	public void setTrackUID(String trackUID) {
		this.trackUID = trackUID;
	}

	// Sets the reference to the next peak in the trajectory
	public void setForwardLink(Peak link) {
		this.forwardLink = link;
	}

	// Gets the reference to the next peak in the trajectory
	public Peak getForwardLink() {
		return forwardLink;
	}
	
	// Sets the ID to the next peak in the trajectory
	public void setForwardLinkID(long forwardLinkID) {
		this.forwardLinkID = forwardLinkID;
	}

	// Gets the ID to the next peak in the trajectory
	public long getForwardLinkUID() {
		return forwardLinkID;
	}

	// Sets the reference to the previous peak in the trajectory
	public void setBackwardLink(Peak link) {
		this.backwardLink = link;
	}

	// Gets the reference to the previous peak in the trajectory
	public Peak getBackwardLink() {
		return backwardLink;
	}
	
	// Sets the ID to the previous peak in the trajectory
	public void setBackwardLinkID(long backwardLinkID) {
		this.backwardLinkID = backwardLinkID;
	}

	// Gets the ID to the previous peak in the trajectory
	public long getBackwardLinkID() {
		return backwardLinkID;
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

	@Override
	protected void createIOMaps() {
		setJsonField("id", jGenerator -> jGenerator.writeNumberField("id", id),
				jParser -> id = jParser.getLongValue());
		
		setJsonField("trackUID", jGenerator -> jGenerator.writeStringField("trackUID", trackUID),
				jParser -> trackUID = jParser.getText());
		
		setJsonField("colorName", jGenerator -> jGenerator.writeStringField("colorName", colorName),
				jParser -> colorName = jParser.getText());
		
		setJsonField("x", jGenerator -> jGenerator.writeNumberField("x",
				x), jParser -> x = jParser.getDoubleValue());
		
		setJsonField("y", jGenerator -> jGenerator.writeNumberField("y",
				y), jParser -> y = jParser.getDoubleValue());
		
		setJsonField("pixelValue", jGenerator -> jGenerator.writeNumberField("pixelValue",
				pixelValue), jParser -> pixelValue = jParser.getDoubleValue());	
		
		setJsonField("c", jGenerator -> jGenerator.writeNumberField("c",
				c), jParser -> c = jParser.getIntValue());
		
		setJsonField("t", jGenerator -> jGenerator.writeNumberField("t",
				t), jParser -> t = jParser.getIntValue());
				
		setJsonField("valid", jGenerator -> jGenerator.writeBooleanField("valid",
				valid), jParser -> valid = jParser.getBooleanValue());
		
		setJsonField("forwardLinkID", jGenerator -> jGenerator.writeNumberField("forwardLinkID", forwardLinkID),
				jParser -> forwardLinkID = jParser.getLongValue());
		
		setJsonField("backwardLinkID", jGenerator -> jGenerator.writeNumberField("backwardLinkID", backwardLinkID),
				jParser -> backwardLinkID = jParser.getLongValue());
		
		setJsonField("properties", jGenerator -> {
			if (properties.size() > 0) {
				jGenerator.writeFieldName("properties");
				jGenerator.writeStartObject();
				for (String key : properties.keySet())
					jGenerator.writeNumberField(key, properties.get(key));
				jGenerator.writeEndObject();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String field = jParser.getCurrentName();
				jParser.nextToken();
				properties.put(field, jParser.getDoubleValue());
			}
		});
		
		//TODO Add PeakShape
	}
}
