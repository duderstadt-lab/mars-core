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

package de.mpg.biochem.mars.metadata;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;
import net.imglib2.realtransform.AffineTransform3D;
import ome.xml.model.primitives.NonNegativeInteger;

public class MarsBdvSource extends AbstractJsonConvertibleRecord implements
	JsonConvertibleRecord
{

	private String name, path;
	private boolean driftCorrect, isN5;
	private String n5Dataset;
	private int channel = 0;
	private AffineTransform3D affine3D;

	public MarsBdvSource(String name) {
		super();
		this.name = name;
		driftCorrect = false;
		path = "";
		n5Dataset = "";
		isN5 = false;
		setAffineTransform2D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
	}

	public MarsBdvSource(JsonParser jParser) throws IOException {
		super();
		name = "";
		driftCorrect = false;
		path = "";
		n5Dataset = "";
		isN5 = false;
		setAffineTransform2D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
		fromJSON(jParser);
	}

	@Override
	protected void createIOMaps() {

		setJsonField("name", jGenerator -> jGenerator.writeStringField("name",
			name), jParser -> name = jParser.getText());
		
		setJsonField("isN5", jGenerator -> jGenerator.writeBooleanField(
				"isN5", isN5), jParser -> isN5 = jParser
					.getBooleanValue());

		setJsonField("driftCorrect", jGenerator -> jGenerator.writeBooleanField(
			"driftCorrect", driftCorrect), jParser -> driftCorrect = jParser
				.getBooleanValue());

		setJsonField("path", jGenerator -> jGenerator.writeStringField(
			"path", path ), jParser -> path = jParser.getText());
		
		setJsonField("dataset", jGenerator -> jGenerator.writeStringField(
				"dataset", n5Dataset ), jParser -> n5Dataset = jParser.getText());
		
		setJsonField("channel", jGenerator -> jGenerator.writeNumberField(
				"channel", channel), jParser -> channel = jParser.getIntValue());

		setJsonField("affineTransform3D", jGenerator -> {
			// Jackson 2.9.9 compatible stuff
			// jGenerator.writeFieldName("AffineTransform3D");
			// jGenerator.writeArray(getTransformAsArray(), 0, 12);

			// Jackson 2.6.5 version of above
			jGenerator.writeFieldName("affineTransform3D");
			jGenerator.writeStartArray();
			for (double num : getTransformAsArray())
				jGenerator.writeNumber(num);
			jGenerator.writeEndArray();
		}, jParser -> {
			double[] trans = new double[12];
			int index = 0;
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				trans[index] = jParser.getDoubleValue();
				index++;
			}
			affine3D.set(trans[0], trans[1], trans[2], trans[3], trans[4], trans[5],
				trans[6], trans[7], trans[8], trans[9], trans[10], trans[11]);
		});

		/*
		 * Remove me in a future release.
		 */
		
		setJsonField("Name", null, 
				jParser -> name = jParser.getText());

		setJsonField("DriftCorrect", null,
				jParser -> driftCorrect = jParser.getBooleanValue());

		setJsonField("pathToXml", null, 
				jParser -> path = jParser.getText());

		setJsonField("AffineTransform3D", null, 
			jParser -> {
				double[] trans = new double[12];
				int index = 0;
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					trans[index] = jParser.getDoubleValue();
					index++;
				}
				affine3D.set(trans[0], trans[1], trans[2], trans[3], trans[4], trans[5],
					trans[6], trans[7], trans[8], trans[9], trans[10], trans[11]);
		});
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean getCorrectDrift() {
		return driftCorrect;
	}

	public void setCorrectDrift(boolean driftCorrect) {
		this.driftCorrect = driftCorrect;
	}
	
	public boolean isN5() {
		return this.isN5;
	}
	
	public void setN5(boolean isN5) {
		this.isN5 = isN5;
	}
	
	public int getChannel() {
		return channel;
	}
	
	public void setChannel(int channel) {
		this.channel = channel;
	}
	
	public void setN5Dataset(String n5Dataset) {
		this.n5Dataset = n5Dataset;
	}
	
	public String getN5Dataset() {
		return n5Dataset;
	}

	// See
	// https://forum.image.sc/t/applying-affine-matrix-result-from-2d-3d-registration-to-images/22298/8
	// for mapping info
	public void setAffineTransform2D(double m00, double m01, double m02,
		double m10, double m11, double m12)
	{
		AffineTransform3D affine = new AffineTransform3D();
		affine.set(m00, m01, 0, m02, m10, m11, 0, m12, 0, 0, 1, 0, 0, 0, 0, 1);
		affine3D = affine;
	}

	public AffineTransform3D getAffineTransform3D() {
		return affine3D;
	}

	public AffineTransform3D getAffineTransform3D(double dX, double dY) {
		AffineTransform3D affine = affine3D.copy();
		affine.set(affine.get(0, 3) - dX, 0, 3);
		affine.set(affine.get(1, 3) - dY, 1, 3);
		return affine;
	}

	private double[] getTransformAsArray() {
		double[] trans = new double[12];
		for (int row = 0; row < 3; row++)
			for (int column = 0; column < 4; column++)
				trans[row * 4 + column] = affine3D.get(row, column);

		return trans;
	}
}
