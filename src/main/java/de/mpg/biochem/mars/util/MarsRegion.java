package de.mpg.biochem.mars.util;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;

public class MarsRegion implements JsonConvertibleRecord {
		String name;
		String column;
		String color;
		double start, end, opacity;
		
		public MarsRegion(String name) {
			this.name = name;
			this.start = 0;
			this.end = 0;
			this.column = "Time (s)";
			this.color = "#416ef468";
			this.opacity = 0.2;
		}
		
		public MarsRegion(JsonParser jParser) throws IOException {
			fromJSON(jParser);
		}
		
		public MarsRegion(String name, String column, double start, double end, String color, double opacity) {
			this.name = name;
			this.column = column;
			this.color = color;
			this.start = start;
			this.end = end;
			this.opacity = opacity;
		}

		@Override
		public void toJSON(JsonGenerator jGenerator) throws IOException {
			jGenerator.writeStartObject();
			jGenerator.writeStringField("name", name);
			jGenerator.writeStringField("column", column);
			jGenerator.writeNumberField("start",start);
			jGenerator.writeNumberField("end",end);
			jGenerator.writeStringField("color", color);
			jGenerator.writeNumberField("opacity", opacity);
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
	    		if ("opacity".equals(fieldname)) {
	    			jParser.nextToken();
	    			opacity = jParser.getDoubleValue();
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
		
		public void setOpacity(double opacity) {
			this.opacity = opacity;
		}
		
		public double getOpacity() {
			return opacity;
		}
	}