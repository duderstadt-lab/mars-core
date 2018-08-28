package de.mpg.biochem.sdmm.table;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.sdmm.util.SDMMMath;
import net.imagej.table.*;


/**
 * SDMM implementation of a generic results table for storing both double values and strings. Based on net.imagej.table.DefaultGeneicTable.
 * 
 * @author Karl Duderstadt
 */

public class SDMMGenericTable extends AbstractTable<Column<? extends Object>, Object> implements GenericTable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private StringBuilder sb;
	
	private String name = new String("GenericResultsTable");
	
	/** Creates an empty results table with the name given. */
	public SDMMGenericTable(String name) {
		super();
		this.name = name;
	}
	
	/** Creates a results table with the given row and column dimensions. */
	public SDMMGenericTable(final int columnCount, final int rowCount) {
		super(columnCount, rowCount);
	}
	
	/** Creates a results table with the given row and column dimensions and name. */
	public SDMMGenericTable(final String name, final int columnCount, final int rowCount) {
		super(columnCount, rowCount);
		this.name = name;
	}
	
	// -- DoubleResultsTable methods --
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public String[] getColumnHeadings() {
		String[] columns = new String[getColumnCount()];
		for (int i=0;i<columns.length;i++) {
			columns[i] = get(i).getHeader();
		}
		return columns;
	}
	
	public boolean save(String path) {
	    try {
	        saveAs(path);
	        return true;
	    } catch (IOException e) {
	        return false;
	    }
	}
	
	public boolean saveAs(File file) {
	    try {
	        saveAs(file.getAbsolutePath());
	        return true;
	    } catch (IOException e) {
	        return false;
	    }
	}
	
	//Outputs the table in csv format
	public void saveAs(String path) throws IOException {
	    if (getRowCount() == 0) return;
	    PrintWriter pw = null;
	    FileOutputStream fos = new FileOutputStream(path);
	    BufferedOutputStream bos = new BufferedOutputStream(fos);
	    pw = new PrintWriter(bos);
	
	    //Now let's build the header
	    StringBuilder sb = new StringBuilder(200);
	    String heading;
	    for (int i=0; i<getColumnCount(); i++) {
	        heading = getColumnHeader(i);
	        sb.append(heading);
	        if (i!=getColumnCount()-1) sb.append(',');
	    }
	    pw.println(new String(sb));
	    
	    for (int i=0; i<getRowCount(); i++)
	        pw.println(getRowAsString(i));
	    pw.close();
	}
	
	/** Returns a tab or comma delimited string representing the
	given row, where 0<=row<=counter-1. */
	public String getRowAsString(int row) {
	    if ((row<0) || (row>=getRowCount()))
	        throw new IllegalArgumentException("Row out of range: "+row);
	    if (sb==null)
	        sb = new StringBuilder(200);
	    else
	        sb.setLength(0);
	    for (int i=0; i<getColumnCount(); i++) {
	        String value = String.valueOf(get(i,row));
	        if (value.contains(","))
	            value = "\""+value+"\"";
	        sb.append(value);
	        if (i!=getColumnCount()-1) sb.append(',');
	    }
	    return new String(sb);
	}
	
	//jackson custom JSON serialization in table format with schema and data objects
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeStartObject();
		if (getColumnCount() > 0) {
			//First we need to write the table schema
			jGenerator.writeObjectFieldStart("schema");
			jGenerator.writeArrayFieldStart("fields");
			for (int i=0;i<getColumnCount();i++) {
				jGenerator.writeStartObject();
				jGenerator.writeStringField("name", getColumnHeader(i));
				if(get(i) instanceof GenericColumn) {
					jGenerator.writeStringField("type", "string");
				} else if (get(i) instanceof DoubleColumn) {
					jGenerator.writeStringField("type", "number");
				}
				jGenerator.writeEndObject();
			}
			jGenerator.writeEndArray();
			jGenerator.writeEndObject();
				
			//Actual table data
			jGenerator.writeArrayFieldStart("data");
			for (int row=0;row<getRowCount();row++) {
				jGenerator.writeStartObject();
				for (int col=0;col<getColumnCount();col++) {
					if (get(col) instanceof GenericColumn) {
						jGenerator.writeStringField(getColumnHeader(col), (String)get(col, row));
					} else if (get(col) instanceof DoubleColumn) {
						jGenerator.writeNumberField(getColumnHeader(col), SDMMMath.round((double)get(col, row)));
					}
				}
				jGenerator.writeEndObject();
			}
			jGenerator.writeEndArray();
		}
		jGenerator.writeEndObject();
	}
		
	//jackson custom JSON deserialization in table format with schema and data objects
	public boolean fromJSON(JsonParser jParser) throws IOException {			
		//First we move past object start
    	jParser.nextToken();
    	
    	//Then we move through fields
    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
    		String fieldname_L1 = jParser.getCurrentName();
    		
    		if ("schema".equals(fieldname_L1)) {
    			//First we move past object start
    	    	jParser.nextToken();
    	    	
    	    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
    	    		String fieldname_L2 = jParser.getCurrentName();
    	    		if ("fields".equals(fieldname_L2)) {
    	    			//Have to move past array start
    		    		jParser.nextToken();
    		    		
    		    		String columnName = "";
    	    			while (jParser.nextToken() != JsonToken.END_ARRAY) {
    	    				while (jParser.nextToken() != JsonToken.END_OBJECT) {   	
	    	    				String fieldname_L3 = jParser.getCurrentName();
	    	    				
	    	    				if ("name".equals(fieldname_L3)) {
	    	    					jParser.nextToken();
	    	    					columnName = jParser.getText();
	    	    				}
	    	    					
	    	    				if ("type".equals(fieldname_L3)) {
	    	    					jParser.nextToken();

	    	    					if ("number".equals(jParser.getText())) {
	    	    						add(new DoubleColumn(columnName));
	    	    					} else if ("string".equals(jParser.getText())) {
	    	    						add(new GenericColumn(columnName));
	    	    					}
	    	    				}
    	    				}
	    		    		
    	    			}
    	    		}
    	    	}
    		}
    		
    		if ("data".equals(fieldname_L1)) {
    			//First we move past array start
    	    	jParser.nextToken();
    			
	    		while (jParser.nextToken() != JsonToken.END_ARRAY) {
	    			appendRow();
	    			int rowIndex = getRowCount() - 1;
		    		while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    			String colname = jParser.getCurrentName();
		    			
		    			//move to value token
		    			jParser.nextToken();
		    			if (get(colname) instanceof DoubleColumn) {
			    			if (jParser.currentToken().equals(JsonToken.VALUE_STRING)) {
			    				String str = jParser.getValueAsString();
			    				if (Objects.equals(str, new String("Infinity"))) {
			    					setValue(colname, rowIndex, Double.POSITIVE_INFINITY);
			    				} else if (Objects.equals(str, new String("-Infinity"))) {
			    					setValue(colname, rowIndex, Double.NEGATIVE_INFINITY);
			    				} else if (Objects.equals(str, new String("NaN"))) {
			    					setValue(colname, rowIndex, Double.NaN);
			    				}
			    			} else {
			    				setValue(colname, rowIndex, jParser.getDoubleValue());
			    			}
		    			} else if (get(colname) instanceof GenericColumn) {
		    				setValue(colname, rowIndex, jParser.getValueAsString());
		    			}
		    		}
	    		}
    		}
    	}
    	return true;
	}
	
	public void setValue(String column, int row, double value) {
		((DoubleColumn)get(column)).set(row, value);
	}
	
	public void setValue(String column, int row, String value) {
		((GenericColumn)get(column)).set(row, value);
	}
	
	//saveAs method for JSON Table format output...
	public void saveAsJSON(String path) {
		try {
			if (!path.endsWith(".json")) {
				path += ".json";
			}
			
			OutputStream stream = new BufferedOutputStream(new FileOutputStream(new File(path)));
			
			JsonGenerator jGenerator;
			JsonFactory jfactory = new JsonFactory();
			jGenerator = jfactory.createGenerator(stream, JsonEncoding.UTF8);
			
			toJSON(jGenerator);
			
			jGenerator.close();
			
			//flush and close streams...
			stream.flush();
			stream.close();		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//open method for JSON Table format input...
	private void loadJSON(File file) throws JsonParseException, IOException {
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
		
		this.name = file.getName();

		JsonParser jParser;
		JsonFactory jfactory = new JsonFactory();
		jParser = jfactory.createParser(inputStream);
		
		fromJSON(jParser);
		
		jParser.close();
		inputStream.close();
	}
	
	// -- Internal methods --
	
	@Override
	protected GenericColumn createColumn(final String header) {
		return new GenericColumn(header);
	}
}
