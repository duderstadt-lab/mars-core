package de.mpg.biochem.mars.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.util.MarsUtil;
import io.scif.io.Location;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.units.unit.Unit;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.MapPair;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.Timestamp;

import ome.xml.model.enums.handlers.UnitsTimeEnumHandler;

public class MarsOMEPlane extends AbstractJsonConvertibleRecord {

	private int imageIndex;
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
	
	private Map<String, String> customFields = new HashMap<String, String>();

	public MarsOMEPlane(int imageIndex, int planeIndex, OMEXMLMetadata md) {
		super();

		this.imageIndex = imageIndex;
		this.planeIndex = planeIndex;
		this.c = md.getTiffDataFirstC(imageIndex, planeIndex);
		this.z = md.getTiffDataFirstZ(imageIndex, planeIndex);
		this.t = md.getTiffDataFirstT(imageIndex, planeIndex);
		this.ifd = md.getTiffDataIFD(imageIndex, planeIndex);
		this.filename = md.getUUIDFileName(imageIndex, planeIndex);
		this.uuid = md.getUUIDValue(imageIndex, planeIndex);

		this.dt = md.getPlaneDeltaT(imageIndex, planeIndex);
		this.exposureTime = md.getPlaneExposureTime(imageIndex, planeIndex);
		this.posX = -1;
		this.posY = -1;
		this.posZ = -1;
	}
	
	public MarsOMEPlane(JsonParser jParser) throws IOException {
		super();
		fromJSON(jParser);
	}
	
	@Override
	protected void createIOMaps() {
		
		UnitsTimeEnumHandler timehandler = new UnitsTimeEnumHandler();

		setJsonField("image", 
			jGenerator -> jGenerator.writeNumberField("image", imageIndex),
			jParser -> imageIndex = jParser.getIntValue());

		setJsonField("plane", 
			jGenerator -> jGenerator.writeNumberField("plane", planeIndex),
			jParser -> planeIndex = jParser.getIntValue());

		setJsonField("C", 
			jGenerator -> jGenerator.writeNumberField("C", c.getValue()),
			jParser -> c = new NonNegativeInteger(jParser.getIntValue()));
		
		setJsonField("Z", 
			jGenerator -> jGenerator.writeNumberField("Z", z.getValue()),
			jParser -> z = new NonNegativeInteger(jParser.getIntValue()));
		
		setJsonField("T", 
			jGenerator -> jGenerator.writeNumberField("T", t.getValue()),
			jParser -> t = new NonNegativeInteger(jParser.getIntValue()));
		
		setJsonField("ifd", 
			jGenerator -> jGenerator.writeNumberField("ifd", ifd.getValue()),
			jParser -> ifd = new NonNegativeInteger(jParser.getIntValue()));
		
		setJsonField("filename", 
			jGenerator -> jGenerator.writeStringField("filename", filename),
			jParser -> filename = jParser.getText());
	 	
		setJsonField("uuid", 
			jGenerator -> jGenerator.writeStringField("uuid", uuid),
			jParser -> uuid = jParser.getText());
	 	
	 	setJsonField("deltaT",
				jGenerator -> {
					jGenerator.writeObjectFieldStart("deltaT");
					jGenerator.writeNumberField("value", dt.value().doubleValue());
					jGenerator.writeStringField("units", dt.unit().toString());
					jGenerator.writeEndObject();
				},
				jParser -> { 
					double value = Double.NaN;
					String units = "";
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String subfieldname = jParser.getCurrentName();
			    		jParser.nextToken();
			    		if (subfieldname.equals("value"))
			    			value = jParser.getDoubleValue();
			    		
			    		if (subfieldname.equals("units"))
			    			units = jParser.getText();
			    	}
					try {
						dt = new Time(value, (Unit<Time>) timehandler.getEnumeration(units));
					} catch (EnumerationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
	 	
	 	setJsonField("exposureTime",
				jGenerator -> {
					jGenerator.writeObjectFieldStart("exposureTime");
					jGenerator.writeNumberField("value", exposureTime.value().doubleValue());
					jGenerator.writeStringField("units", exposureTime.unit().toString());
					jGenerator.writeEndObject();
				},
				jParser -> { 
					double value = Double.NaN;
					String units = "";
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String subfieldname = jParser.getCurrentName();
			    		jParser.nextToken();
			    		if (subfieldname.equals("value"))
			    			value = jParser.getDoubleValue();
			    		
			    		if (subfieldname.equals("units"))
			    			units = jParser.getText();
			    	}
					try {
						exposureTime = new Time(value, (Unit<Time>) timehandler.getEnumeration(units));
					} catch (EnumerationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
			
	 	setJsonField("posX",
	 		jGenerator -> jGenerator.writeNumberField("posX", posX),
	 		jParser -> posX = jParser.getFloatValue());
	 	
	 	setJsonField("posY",
		 		jGenerator -> jGenerator.writeNumberField("posY", posY),
		 		jParser -> posY = jParser.getFloatValue());
	 	
	 	setJsonField("posZ",
		 		jGenerator -> jGenerator.writeNumberField("posZ", posZ),
		 		jParser -> posZ = jParser.getFloatValue());
	 	
	 	setJsonField("xDrift", 
	 		jGenerator -> jGenerator.writeNumberField("xDrift", xDrift),
	 		jParser -> xDrift = jParser.getDoubleValue());
	 	
	 	setJsonField("yDrift", 
		 		jGenerator -> jGenerator.writeNumberField("yDrift", yDrift),
		 		jParser -> yDrift = jParser.getDoubleValue());
	 	
	 	setJsonField("zDrift", 
		 		jGenerator -> jGenerator.writeNumberField("zDrift", zDrift),
		 		jParser -> zDrift = jParser.getDoubleValue());
	 	
	 	setJsonField("CustomFields", 
	 		jGenerator -> {
				if (customFields.size() > 0) {
					jGenerator.writeArrayFieldStart("CustomFields");
					for (String name : customFields.keySet()) {
						jGenerator.writeStringField(name, customFields.get(name));
					}
					jGenerator.writeEndArray();
				}
		 	}, 
	 		jParser -> {
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    		String fieldname = jParser.getCurrentName();
		    		jParser.nextToken();
		    		customFields.put(fieldname, jParser.getText());
				}
	 		});
	}
	
	public void setField(String field, String value) {
		customFields.put(field, value);
	}
	
	public String getField(String field) {
		return customFields.get(field);
	}
	
	public void setCustomFields(Map<String, String> customFields) {
		this.customFields = customFields;
	}
	
	public double getDeltaTinSeconds() {
		return dt.value(UNITS.SECOND).doubleValue();
	}
	
	public int getImageIndex() {
		return imageIndex;
	}
	
	public int getPlaneIndex() {
		return planeIndex;
	}
	
	public int getC() {
		return c.getValue();
	}
	
	public int getZ() {
		return z.getValue();
	}
	
	public int getT() {
		return t.getValue();
	}
	
	public int getIFD() {
		return ifd.getValue();
	}
	
	public String getFilename() {
		return filename;
	}
	
	public String getUUID() {
		return uuid;
	}
	
	public double getExposureTimeInSeconds() {
		return exposureTime.value(UNITS.SECOND).doubleValue();
	}

	public double getPosX() {
		return posX;
	}
	
	public double getPosY() {
		return posY;
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
