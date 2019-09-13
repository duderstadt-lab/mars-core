package de.mpg.biochem.mars.molecule;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class RegionOfInterest implements JsonConvertibleRecord {
		String name;
		String column;
		String color;
		double start, end;
		
		public RegionOfInterest(String name) {
			this.name = name;
			this.start = 0;
			this.end = 0;
			this.color = "rgba(50,50,50,0.2)";
		}
		
		public RegionOfInterest(JsonParser jParser) throws IOException {
			fromJSON(jParser);
		}
		
		public RegionOfInterest(String name, String column, double start, double end, String color) {
			this.name = name;
			this.column = column;
			this.color = color;
			this.start = start;
			this.end = end;
		}

		@Override
		public void toJSON(JsonGenerator jGenerator) throws IOException {
			jGenerator.writeStartObject();
			jGenerator.writeStringField("name", name);
			jGenerator.writeStringField("column", column);
			jGenerator.writeNumberField("start",start);
			jGenerator.writeNumberField("end",end);
			jGenerator.writeStringField("color", color);
			jGenerator.writeEndObject();
		}

		@Override
		public void fromJSON(JsonParser jParser) throws IOException {
			//Then we move through fields
	    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
	    		String fieldname = jParser.getCurrentName();
	    		if ("name".equals(fieldname)) {
	    			jParser.nextToken();
	    			name = jParser.getText();
	    		} 
	    		if ("column".equals(fieldname)) {
	    			jParser.nextToken();
	    			column = jParser.getText();
	    		}
	    			
	    		if ("start".equals(fieldname)) {
	    			jParser.nextToken();
	    			start = jParser.getDoubleValue();
	    		}
	    		if ("end".equals(fieldname)) {
	    			jParser.nextToken();
	    			end = jParser.getDoubleValue();
	    		}
	    		if ("color".equals(fieldname)) {
	    			jParser.nextToken();
	    			color = jParser.getText();
	    		}
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
		
		public double getStart() {
			return start;
		}
		
		public void setStart(double start) {
			this.start = start;
		}
		
		public double getEnd() {
			return end;
		}
		
		public void setEnd(double end) {
			this.end = end;
		}
	}