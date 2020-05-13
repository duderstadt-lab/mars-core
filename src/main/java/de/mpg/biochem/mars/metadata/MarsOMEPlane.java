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
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.MapPair;
import ome.xml.model.primitives.NonNegativeInteger;

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
		//For now we add everything.. Maybe in the future the c, z, t can be excluded 
		//and instead generated from the global axis information...
		
		//Ugh maybe make a shorter method for this...
		//Add to output map
		outputMap.put("image", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("image", imageIndex);
	 	}, IOException.class));
		outputMap.put("plane", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("plane", planeIndex);
	 	}, IOException.class));
		outputMap.put("C", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("C", c.getValue());
	 	}, IOException.class));
		outputMap.put("Z", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("Z", z.getValue());
	 	}, IOException.class));
		outputMap.put("T", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("T", t.getValue());
	 	}, IOException.class));
		outputMap.put("ifd", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("ifd", ifd.getValue());
	 	}, IOException.class));
		outputMap.put("filename", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeStringField("filename", filename);
	 	}, IOException.class));
		outputMap.put("uuid", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeStringField("uuid", uuid);
	 	}, IOException.class));
		outputMap.put("deltaT", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("deltaT", dt);
	 	}, IOException.class));
		outputMap.put("exposureTime", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("exposureTime", exposureTime);
	 	}, IOException.class));
		outputMap.put("posX", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("posX", posX);
	 	}, IOException.class));
		outputMap.put("posY", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("posY", posY);
	 	}, IOException.class));
		outputMap.put("posZ", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("posZ", posZ);
	 	}, IOException.class));
		outputMap.put("xDrift", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("xDrift", xDrift);
	 	}, IOException.class));
		outputMap.put("yDrift", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("yDrift", yDrift);
	 	}, IOException.class));
		outputMap.put("zDrift", MarsUtil.catchConsumerException(jGenerator -> {
	  		jGenerator.writeNumberField("zDrift", zDrift);
	 	}, IOException.class));
		outputMap.put("CustomFields", MarsUtil.catchConsumerException(jGenerator -> {
			if (customFields.size() > 0) {
				jGenerator.writeArrayFieldStart("CustomFields");
				for (String name : customFields.keySet()) {
					jGenerator.writeStringField(name, customFields.get(name));
				}
				jGenerator.writeEndArray();
			}
	 	}, IOException.class));
		
		//Add to input map
		inputMap.put("image", MarsUtil.catchConsumerException(jParser -> {
			imageIndex = jParser.getIntValue();
	 	}, IOException.class));
		inputMap.put("plane", MarsUtil.catchConsumerException(jParser -> {
	  		planeIndex = jParser.getIntValue();
	 	}, IOException.class));
		inputMap.put("C",MarsUtil.catchConsumerException(jParser -> {
	  		c = new NonNegativeInteger(jParser.getIntValue());
	 	}, IOException.class));
		inputMap.put("Z", MarsUtil.catchConsumerException(jParser -> {
			z = new NonNegativeInteger(jParser.getIntValue());
	 	}, IOException.class));
		inputMap.put("T", MarsUtil.catchConsumerException(jParser -> {
			t = new NonNegativeInteger(jParser.getIntValue());
	 	}, IOException.class));
		inputMap.put("ifd", MarsUtil.catchConsumerException(jParser -> {
			ifd = new NonNegativeInteger(jParser.getIntValue());
	 	}, IOException.class));
		inputMap.put("filename", MarsUtil.catchConsumerException(jParser -> {
	  		filename = jParser.getText();
	 	}, IOException.class));
		inputMap.put("uuid", MarsUtil.catchConsumerException(jParser -> {
	  		uuid = jParser.getText();
	 	}, IOException.class));
		inputMap.put("deltaT", MarsUtil.catchConsumerException(jParser -> {
	  		dt = jParser.getFloatValue();
	 	}, IOException.class));
		inputMap.put("exposureTime", MarsUtil.catchConsumerException(jParser -> {
	  		exposureTime = jParser.getFloatValue();
	 	}, IOException.class));
		inputMap.put("posX", MarsUtil.catchConsumerException(jParser -> {
	  		posX = jParser.getFloatValue();
	 	}, IOException.class));
		inputMap.put("posY", MarsUtil.catchConsumerException(jParser -> {
	  		posY = jParser.getFloatValue();
	 	}, IOException.class));
		inputMap.put("posZ", MarsUtil.catchConsumerException(jParser -> {
			posZ = jParser.getFloatValue();
	 	}, IOException.class));
		inputMap.put("xDrift", MarsUtil.catchConsumerException(jParser -> {
	  		xDrift = jParser.getDoubleValue();
	 	}, IOException.class));
		inputMap.put("yDrift", MarsUtil.catchConsumerException(jParser -> {
	  		yDrift = jParser.getDoubleValue();
	 	}, IOException.class));
		inputMap.put("zDrift", MarsUtil.catchConsumerException(jParser -> {
	  		zDrift = jParser.getDoubleValue();
	 	}, IOException.class));
		inputMap.put("CustomFields", MarsUtil.catchConsumerException(jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
	    		String fieldname = jParser.getCurrentName();
	    		jParser.nextToken();
	    		customFields.put(fieldname, jParser.getText());
			}
	 	}, IOException.class));
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
	
	public float getDeltaT() {
		return dt;
	}
	
	public int getPlaneIndex() {
		return planeIndex;
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
