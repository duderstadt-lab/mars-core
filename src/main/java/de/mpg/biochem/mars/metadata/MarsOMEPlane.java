/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import ome.units.UNITS;
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsTime;
import ome.xml.model.enums.handlers.UnitsTimeEnumHandler;
import ome.xml.model.primitives.NonNegativeInteger;

public class MarsOMEPlane extends AbstractJsonConvertibleRecord implements
	GenericModel
{

	private MarsOMEImage image;

	// This is the imageID from the original collection.
	// Might not be the same as the imageIndex positions for storage.
	private int imageID;
	private int planeIndex;
	private NonNegativeInteger c;
	private NonNegativeInteger z;
	private NonNegativeInteger t;
	private NonNegativeInteger ifd;
	private String filename;
	private String uuid;

	private Time dt;
	private Time exposureTime;
	private float posX;
	private float posY;
	private float posZ;

	private double xDrift;
	private double yDrift;
	private double zDrift;

	private Map<String, String> stringFields =
			new LinkedHashMap<>();
	private Map<String, Double> valueFields = new LinkedHashMap<>();

	public MarsOMEPlane() {
		super();
	}

	public MarsOMEPlane(MarsOMEImage image, OMEXMLMetadata md, int imageIndex,
		int planeIndex)
	{
		super();

		this.image = image;

		this.planeIndex = planeIndex;
		this.c = md.getPlaneTheC(imageIndex, planeIndex);
		this.z = md.getPlaneTheZ(imageIndex, planeIndex);
		this.t = md.getPlaneTheT(imageIndex, planeIndex);
		//this.ifd = md.getTiffDataIFD(imageIndex, planeIndex);
		//this.filename = md.getUUIDFileName(imageIndex, planeIndex);
		//this.uuid = md.getUUIDValue(imageIndex, planeIndex);

		this.dt = md.getPlaneDeltaT(imageIndex, planeIndex);
		this.exposureTime = md.getPlaneExposureTime(imageIndex, planeIndex);
		this.posX = -1;
		this.posY = -1;
		this.posZ = -1;

		if (md.getImageID(imageIndex) != null) {
			try {
				this.imageID = Integer.parseInt(md.getImageID(imageIndex).substring(6));
			}
			catch (NumberFormatException e) {
				this.imageID = imageIndex;
			}
		}
		else this.imageID = imageIndex;
	}

	public MarsOMEPlane(MarsOMEImage image, int imageIndex, int planeIndex,
		NonNegativeInteger Z, NonNegativeInteger C, NonNegativeInteger T)
	{
		super();

		this.image = image;

		this.imageID = imageIndex;
		this.planeIndex = planeIndex;
		this.c = C;
		this.z = Z;
		this.t = T;

		this.posX = -1;
		this.posY = -1;
		this.posZ = -1;
	}

	public MarsOMEPlane(JsonParser jParser, MarsOMEImage image)
		throws IOException
	{
		super();
		this.image = image;
		fromJSON(jParser);
	}

	@Override
	protected void createIOMaps() {

		UnitsTimeEnumHandler timeHandler = new UnitsTimeEnumHandler();

		setJsonField("imageID", jGenerator -> jGenerator.writeNumberField("imageID",
			imageID), jParser -> imageID = jParser.getIntValue());

		setJsonField("plane", jGenerator -> jGenerator.writeNumberField("plane",
			planeIndex), jParser -> planeIndex = jParser.getIntValue());

		setJsonField("c", jGenerator -> {
			if (c != null) jGenerator.writeNumberField("c", c.getValue());
		}, jParser -> c = new NonNegativeInteger(jParser.getIntValue()));

		setJsonField("z", jGenerator -> {
			if (z != null) jGenerator.writeNumberField("z", z.getValue());
		}, jParser -> z = new NonNegativeInteger(jParser.getIntValue()));

		setJsonField("t", jGenerator -> {
			if (t != null) jGenerator.writeNumberField("t", t.getValue());
		}, jParser -> t = new NonNegativeInteger(jParser.getIntValue()));

		setJsonField("ifd", jGenerator -> {
			if (ifd != null) jGenerator.writeNumberField("ifd", ifd.getValue());
		}, jParser -> ifd = new NonNegativeInteger(jParser.getIntValue()));

		setJsonField("filename", jGenerator -> {
			if (filename != null) jGenerator.writeStringField("filename", filename);
		}, jParser -> filename = jParser.getText());

		setJsonField("uuid", jGenerator -> {
			if (uuid != null) jGenerator.writeStringField("uuid", uuid);
		}, jParser -> uuid = jParser.getText());

		setJsonField("deltaT", jGenerator -> {
			if (dt != null) {
				jGenerator.writeObjectFieldStart("deltaT");
				jGenerator.writeNumberField("value", dt.value().doubleValue());
				jGenerator.writeStringField("units", dt.unit().getSymbol());
				jGenerator.writeEndObject();
			}
		}, jParser -> {
			double value = Double.NaN;
			String units = "";
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String subFieldName = jParser.getCurrentName();
				jParser.nextToken();
				if (subFieldName.equals("value")) value = jParser.getDoubleValue();

				if (subFieldName.equals("units")) units = jParser.getText();
			}
			try {
				dt = new Time(value, UnitsTimeEnumHandler.getBaseUnit(
					(UnitsTime) timeHandler.getEnumeration(units)));
			}
			catch (EnumerationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

		setJsonField("exposureTime", jGenerator -> {
			if (exposureTime != null) {
				jGenerator.writeObjectFieldStart("exposureTime");
				jGenerator.writeNumberField("value", exposureTime.value()
					.doubleValue());
				jGenerator.writeStringField("units", exposureTime.unit().getSymbol());
				jGenerator.writeEndObject();
			}
		}, jParser -> {
			double value = Double.NaN;
			String units = "";
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String subFieldName = jParser.getCurrentName();
				jParser.nextToken();
				if (subFieldName.equals("value")) value = jParser.getDoubleValue();

				if (subFieldName.equals("units")) units = jParser.getText();
			}
			try {
				exposureTime = new Time(value, UnitsTimeEnumHandler.getBaseUnit(
					(UnitsTime) timeHandler.getEnumeration(units)));
			}
			catch (EnumerationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

		setJsonField("posX", jGenerator -> jGenerator.writeNumberField("posX",
			posX), jParser -> posX = jParser.getFloatValue());

		setJsonField("posY", jGenerator -> jGenerator.writeNumberField("posY",
			posY), jParser -> posY = jParser.getFloatValue());

		setJsonField("posZ", jGenerator -> jGenerator.writeNumberField("posZ",
			posZ), jParser -> posZ = jParser.getFloatValue());

		setJsonField("xDrift", jGenerator -> jGenerator.writeNumberField("xDrift",
			xDrift), jParser -> xDrift = jParser.getDoubleValue());

		setJsonField("yDrift", jGenerator -> jGenerator.writeNumberField("yDrift",
			yDrift), jParser -> yDrift = jParser.getDoubleValue());

		setJsonField("zDrift", jGenerator -> jGenerator.writeNumberField("zDrift",
			zDrift), jParser -> zDrift = jParser.getDoubleValue());

		setJsonField("stringFields", jGenerator -> {
			if (stringFields.size() > 0) {
				jGenerator.writeObjectFieldStart("stringFields");
				for (String name : stringFields.keySet())
					jGenerator.writeStringField(name, stringFields.get(name));
				jGenerator.writeEndObject();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jParser.getCurrentName();
				jParser.nextToken();
				stringFields.put(fieldName, jParser.getText());
			}
		});

		setJsonField("valueFields", jGenerator -> {
			if (stringFields.size() > 0) {
				jGenerator.writeObjectFieldStart("valueFields");
				for (String name : valueFields.keySet())
					jGenerator.writeNumberField(name, valueFields.get(name));
				jGenerator.writeEndObject();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jParser.getCurrentName();
				jParser.nextToken();
				valueFields.put(fieldName, jParser.getDoubleValue());
			}
		});

		/*
		 * 
		 * The fields below are needed for backwards compatibility.
		 * 
		 * Please remove for a future release.
		 * 
		 */

		setJsonField("C", null, jParser -> c = new NonNegativeInteger(jParser
			.getIntValue()));

		setJsonField("Z", null, jParser -> z = new NonNegativeInteger(jParser
			.getIntValue()));

		setJsonField("T", null, jParser -> t = new NonNegativeInteger(jParser
			.getIntValue()));

		setJsonField("StringFields", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jParser.getCurrentName();
				jParser.nextToken();
				stringFields.put(fieldName, jParser.getText());
			}
		});

		setJsonField("ValueFields", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jParser.getCurrentName();
				jParser.nextToken();
				valueFields.put(fieldName, jParser.getDoubleValue());
			}
		});
	}

	@Override
	public String toString() {
		return "C = " + c + " | Z = " + z + " | T = " + t;
	}

	@Override
	public Iterable<List<String>> getInformationsRow() {
		List<List<String>> rows = new ArrayList<>();

		rows.add(Arrays.asList("TiffData ID", Integer.toString(this.planeIndex)));
		if (ifd != null) rows.add(Arrays.asList("IFD", this.ifd.toString()));
		if (c != null) rows.add(Arrays.asList("C position", this.c.toString()));
		if (z != null) rows.add(Arrays.asList("Z position", this.z.toString()));
		if (t != null) rows.add(Arrays.asList("T position", this.t.toString()));

		if (this.getDeltaTinSeconds() >= 0) {
			rows.add(Arrays.asList("dt", this.dt.value().doubleValue() + " " + this.dt
				.unit().getSymbol()));
		}
		else {
			rows.add(Arrays.asList("dt", ""));
		}

		if (this.getExposureTimeInSeconds() >= 0) {
			rows.add(Arrays.asList("Exposure time", this.exposureTime.value()
				.doubleValue() + " " + this.exposureTime.unit().getSymbol()));
		}
		else {
			rows.add(Arrays.asList("Exposure time", ""));
		}

		if (this.posX >= 0) {
			rows.add(Arrays.asList("Position X", this.posX + " µm"));
		}
		else {
			rows.add(Arrays.asList("Position X", ""));
		}

		if (this.posY >= 0) {
			rows.add(Arrays.asList("Position Y", this.posY + " µm"));
		}
		else {
			rows.add(Arrays.asList("Position Y", ""));
		}

		if (this.posZ >= 0) {
			rows.add(Arrays.asList("Position Z", this.posZ + " µm"));
		}
		else {
			rows.add(Arrays.asList("Position Z", ""));
		}

		rows.add(Arrays.asList("Filename", this.filename));
		rows.add(Arrays.asList("UUID", this.uuid));

		rows.add(Arrays.asList("xDrift", String.valueOf(xDrift)));
		rows.add(Arrays.asList("yDrift", String.valueOf(yDrift)));
		rows.add(Arrays.asList("zDrift", String.valueOf(zDrift)));

		for (String field : stringFields.keySet())
			rows.add(Arrays.asList(field, stringFields.get(field)));

		for (String field : valueFields.keySet())
			rows.add(Arrays.asList(field, String.valueOf(valueFields.get(field))));

		return rows;
	}

	public void setField(String field, double value) {
		valueFields.put(field, value);
	}

	public double getField(String field) {
		return valueFields.get(field);
	}

	public boolean hasField(String field) {
		return valueFields.containsKey(field);
	}

	public void setFields(Map<String, Double> valueFields) {
		this.valueFields = valueFields;
	}

	public Map<String, Double> getFields() {
		return valueFields;
	}

	public void setStringField(String field, String value) {
		stringFields.put(field, value);
	}

	public boolean hasStringField(String field) {
		return stringFields.containsKey(field);
	}

	public String getStringField(String field) {
		return stringFields.get(field);
	}

	public Map<String, String> getStringFields() {
		return stringFields;
	}

	public void setStringFields(Map<String, String> stringFields) {
		this.stringFields = stringFields;
	}

	public MarsOMEImage getImage() {
		return image;
	}

	public void setImage(MarsOMEImage image) {
		this.image = image;
	}

	public void setDeltaT(Time dt) {
		this.dt = dt;
	}

	public double getDeltaTinSeconds() {
		if (dt != null) return dt.value(UNITS.SECOND).doubleValue();
		return -1;
	}

	public void setImageID(int imageID) {
		this.imageID = imageID;
	}

	public int getImageID() {
		return imageID;
	}

	public void setPlaneIndex(int planeIndex) {
		this.planeIndex = planeIndex;
	}

	public int getPlaneIndex() {
		return planeIndex;
	}

	public void setC(NonNegativeInteger c) {
		this.c = c;
	}

	public int getC() {
		return c.getValue();
	}

	public void setZ(NonNegativeInteger z) {
		this.z = z;
	}

	public int getZ() {
		return z.getValue();
	}

	public void setT(NonNegativeInteger t) {
		this.t = t;
	}

	public int getT() {
		return t.getValue();
	}

	public void setIFD(NonNegativeInteger ifd) {
		this.ifd = ifd;
	}

	public int getIFD() {
		return ifd.getValue();
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public String getFilename() {
		return filename;
	}

	public void setUUID(String uuid) {
		this.uuid = uuid;
	}

	public String getUUID() {
		return uuid;
	}

	public void setExposureTime(Time exposureTime) {
		this.exposureTime = exposureTime;
	}

	public double getExposureTimeInSeconds() {
		if (exposureTime != null) return exposureTime.value(UNITS.SECOND)
			.doubleValue();
		return -1;
	}

	public void setPosX(float posX) {
		this.posX = posX;
	}

	public double getPosX() {
		return posX;
	}

	public void setPosY(float posY) {
		this.posY = posY;
	}

	public double getPosY() {
		return posY;
	}

	public void setPosZ(float posZ) {
		this.posZ = posZ;
	}

	public double getPosZ() {
		return posZ;
	}

	public double getXDrift() {
		return xDrift;
	}

	public void setXDrift(double xDrift) {
		this.xDrift = xDrift;
	}

	public double getYDrift() {
		return yDrift;
	}

	public void setYDrift(double yDrift) {
		this.yDrift = yDrift;
	}

	public double getZDrift() {
		return zDrift;
	}

	public void setZDrift(double zDrift) {
		this.zDrift = zDrift;
	}
}
