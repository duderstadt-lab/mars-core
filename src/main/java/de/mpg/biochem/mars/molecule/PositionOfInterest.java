package de.mpg.biochem.mars.molecule;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class PositionOfInterest implements JsonConvertibleRecord {
		String name;
		String column;
		String color;
		double position;
		
		PositionOfInterest() {
			
		}
		
		PositionOfInterest(JsonParser jParser) throws IOException {
			fromJSON(jParser);
		}
		
		PositionOfInterest(String name, String column, double position, String color) {
			this.name = name;
			this.column = column;
			this.color = color;
			this.position = position;
		}

		@Override
		public void toJSON(JsonGenerator jGenerator) throws IOException {
			jGenerator.writeStartObject();
			jGenerator.writeStringField("name", name);
			jGenerator.writeStringField("column", column);
			jGenerator.writeNumberField("position",position);
			jGenerator.writeStringField("color", color);
			jGenerator.writeEndObject();
		}

		@Override
		public void fromJSON(JsonParser jParser) throws IOException {
			//Then we move through fields
	    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
	    		String fieldname = jParser.getCurrentName();
	    		if ("name".equals(fieldname))
	    			name = jParser.getText();
	    		if ("column".equals(fieldname))
	    			column = jParser.getText();
	    		if ("position".equals(fieldname))
	    			position = jParser.getDoubleValue();
	    		if ("color".equals(fieldname))
	    			color = jParser.getText();
	    	}
		}
		
		//Getters and Setters
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getColumn() {
			return column;
		}
		
		public void setColumn(String column) {
			this.column = column;
		}
		
		public String getColor() {
			return color;
		}
		
		public void setColor(String color) {
			this.color = color;
		}
		
		public double getPosition() {
			return position;
		}
		
		public void setPosition(double position) {
			this.position = position;
		}
	}