package de.mpg.biochem.sdmm.molecule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class MoleculeArchiveProperties {
	//Contains general information about the molecule archive
	//Number of molecules, ImageMetaData, tags, average size in bytes.
	//Any information we about to know without having to read the entire archive in directly.
	
	private int numberOfMolecules;
	private double averageMoleculeSize;
	private int numImageMetaData;
	private String comments;
	private LinkedHashSet<String> tags;
	private LinkedHashSet<String> parameters;
	
	private MoleculeArchive parent;
	
	public MoleculeArchiveProperties(MoleculeArchive parent) {
		numberOfMolecules = 0;
		averageMoleculeSize = 0;
		numImageMetaData = 0;
		comments = "";
		tags = new LinkedHashSet<String>();
		parameters = new LinkedHashSet<String>();
		
		this.parent = parent;
	}
	
	public MoleculeArchiveProperties(JsonParser jParser, MoleculeArchive parent) {
		numberOfMolecules = 0;
		averageMoleculeSize = 0;
		numImageMetaData = 0;
		comments = "";
		tags = new LinkedHashSet<String>();
		parameters = new LinkedHashSet<String>();
		
		this.parent = parent;
		
		try {
			fromJSON(jParser);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeObjectFieldStart("MoleculeArchiveProperties");

		//These fields should always exist...
		jGenerator.writeNumberField("numberOfMolecules", numberOfMolecules);
		jGenerator.writeNumberField("averageMoleculeSize", averageMoleculeSize);
		jGenerator.writeNumberField("numImageMetaData", numImageMetaData);
		
		//Write out arrays of tags if tags have been added.
		if (tags.size() > 0) {
			jGenerator.writeFieldName("tags");
			jGenerator.writeStartArray();
			Iterator<String> iterator = tags.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());	
			jGenerator.writeEndArray();
		}
		
		if (!comments.equals(""))
			jGenerator.writeStringField("Comments", comments);
		
		jGenerator.writeEndObject();
	}
	
	public void fromJSON(JsonParser jParser) throws IOException {
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    String fieldname = jParser.getCurrentName();
		    
		    if ("numberOfMolecules".equals(fieldname)) {
		        jParser.nextToken();
		        numberOfMolecules = jParser.getValueAsInt();
		        //parent.getLogService().info("setting number of molecules " + numberOfMolecules);
		    }
		 
		    if ("averageMoleculeSize".equals(fieldname)) {
		        jParser.nextToken();
		        averageMoleculeSize = jParser.getDoubleValue();
		        //parent.getLogService().info("setting size " + averageMoleculeSize);
		    }
		 
		    if ("numImageMetaData".equals(fieldname)) {
		        jParser.nextToken();
		        numImageMetaData = jParser.getIntValue();
		    }
		    
		    if("tags".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            tags.add(jParser.getText());
		        }
		    }
		    
		    if("Comments".equals(fieldname)) {
		    	jParser.nextToken();
		    	comments = jParser.getText();
		    }
		}
	}
	
	//Getters and Setters
	public void removeTag(String tag) {
		tags.remove(tag);
	}
	
	public void addTag(String tag) {
		tags.add(tag);
	}
	
	public LinkedHashSet<String> getTags() {
		return tags;
	}
	
	public void addParameter(String parameter) {
		parameters.add(parameter);
	}
	
	public void removeParameter(String parameter) {
		tags.remove(parameter);
	}
	
	public LinkedHashSet<String> getParameters() {
		return parameters;
	}
	
	public void setNumberOfMolecules(int numMolecules) {
		this.numberOfMolecules = numMolecules;
	}
	
	public int getNumberOfMolecules() {
		return numberOfMolecules;
	}
	
	public void setAverageMoleculeSize(double averageMoleculeSize) {
		this.averageMoleculeSize = averageMoleculeSize;
	}
	
	public double getAverageMoleculeSize() {
		return averageMoleculeSize;
	}
	
	public void setNumImageMetaData(int numImageMetaData) {
		this.numImageMetaData = numImageMetaData;
	}
	
	public void incrementNumImageMetaData() {
		numImageMetaData++;
	}
	
	public void decrementNumImageMetaData() {
		numImageMetaData--;
	}
	
	public int getNumImageMetaData() {
		return numImageMetaData;
	}
	
	public String getComments() {
		return comments;
	}
	
	public void addComment(String comment) {
		this.comments += comment;
	}
	
	public void setComments(String comments) {
		this.comments = comments;
	}
	
	public Molecule getMoleculeWrapper() {
		return new Molecule(this);
	}
}
