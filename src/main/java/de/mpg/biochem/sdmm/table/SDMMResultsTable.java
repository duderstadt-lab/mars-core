package de.mpg.biochem.sdmm.table;

import net.imagej.table.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.sdmm.util.SDMMMath;
import ij.text.TextWindow;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * SDMM implementation of a double precision results table. Based on net.imagej.table.DefaultResultsTable.
 * 
 * @author Karl Duderstadt
 */
//public class SDMMResultsTable extends AbstractTable<DoubleColumn, Double> implements ResultsTable {
public class SDMMResultsTable extends AbstractTable<Column<? extends Object>, Object> implements GenericTable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	//Just added for debugging
	//private TextWindow log_window;
	
	private SDMMResultsTableWindow win;
	
	private StringBuilder sb;
	
	private String name = new String("SDMMResultsTable");
	
	/** Creates an empty results table. */
	public SDMMResultsTable() {
		super();
	}

	/** Creates an empty results table with the name given. */
	public SDMMResultsTable(String name) {
		super();
		this.name = name;
	}
	
	public SDMMResultsTable(File file) throws JsonParseException, IOException {
		super();
		loadJSON(file);
	}
	
	/** Creates a results table with the given row and column dimensions. */
	public SDMMResultsTable(final int columnCount, final int rowCount) {
		super(columnCount, rowCount);
	}

	/** Creates a results table with the given row and column dimensions and name. */
	public SDMMResultsTable(final String name, final int columnCount, final int rowCount) {
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
	
	public ArrayList<String> getColumnHeadingList() {
		ArrayList<String> columns = new ArrayList<String>();
	
		for (int i=0;i<getColumnCount();i++) {
			if(!columns.contains(getColumnHeader(i)))
				columns.add(getColumnHeader(i));
		}
		
		return columns;
	}
	
	public SDMMResultsTableWindow getWindow() {
		return win;
	}
	
	public void setWindow(SDMMResultsTableWindow win) {
		this.win = win;
	}
	
	public double[] getColumnAsDoubles(String column) {
		if (get(column) instanceof DoubleColumn) { 
			double[] col_array = new double[getRowCount()];
			for (int i=0;i<col_array.length;i++) {
				col_array[i] = ((DoubleColumn)get(column)).getValue(i);
			}
			return col_array;
		} else {
			return null;
		}
	}
	
	//IMPORT/EXPORT METHODS
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
	
	//saveAs method for JSON Table format output...
	public boolean saveAsJSON(String path) {
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
			return false;
		}
		return true;
	}
	
	//open method for JSON Table format input...
	//Assumes all columns are type number
	//if not this will fail
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
	
	@Override
	public String toString() {
		return name;
	}
	
	public void setValue(int col, int row, double value) {
		setValue(getColumnHeader(col), row, value);
	}
	
	public void setStringValue(int col, int row, String value) {
		((GenericColumn)get(getColumnHeader(col))).set(row, value);
	}
	
	public void setValue(String column, int row, double value) {
		((DoubleColumn)get(column)).set(row, value);
	}
	
	public void setValue(String column, int row, String value) {
		((GenericColumn)get(column)).set(row, value);
	}
	
	public double getValue(int col, int row) {
		return getValue(getColumnHeader(col), row);
	}
	
	public double getValue(String column, int index) {
		return getDoubleColumn(column).get(index);
	}
	
	public String getStringValue(String column, int index) {
		return (String)get(column).get(index);
	}
	
	public boolean hasColumn(String colName) {
		for (String column : getColumnHeadingList()) {
			if (column.equals(colName))
				return true;
		}
		return false;
	}
	
	public DoubleColumn getDoubleColumn(String column) {
		return (DoubleColumn)get(column);
	}
	
	public GenericColumn getGenericColumn(String column) {
		return (GenericColumn)get(column);
	}
	
	//Here are some utility methods add for common operations..
	public double max(String column) {
		if (get(column) == null)
			return Double.NaN;
		double max = (double)get(column, 0);
		//order doesn't matter so we use an iterator
		for (double value: getDoubleColumn(column)) {
			if (max < value)
				max = value;
		}
		return max;
	}
	
	public double min(String column) {
		if (get(column) == null)
			return Double.NaN;
		double min = getDoubleColumn(column).get(0);
		//order doesn't matter so we use an iterator
		for (double value: getDoubleColumn(column)) {
			if (min > value)
				min = value;
		}
		return min;
	}
	
	public double mean(String column) {
		if (get(column) == null)
			return Double.NaN;
		double sum = 0;
		int count = 0;
		for (int i = 0; i < getRowCount();i++) {
			sum += getValue(column, i);
			count++;
		}
		return sum/count;
	}
	
	public double mean(String meanColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (get(meanColumn) == null || get(rowSelectionColumn) == null)
			return Double.NaN;
		double sum = 0;
		int count = 0;
		for (int i = 0; i < getRowCount();i++) {
			if (getValue(rowSelectionColumn, i) >= rangeStart && getValue(rowSelectionColumn, i) <= rangeEnd) {
				sum += getValue(meanColumn, i);
				count++;
			}
		}
		return sum/count;
	}
	
	public double median(String column) {
		if (get(column) == null)
			return Double.NaN;
		ArrayList<Double> values = new ArrayList<Double>();
		for (int i = 0; i < getRowCount();i++) {
			values.add(getValue(column, i));
		}
		Collections.sort(values);
		
		double median;
		if (values.size() % 2 == 0)
		    median = (values.get(values.size()/2) + values.get(values.size()/2 - 1))/2;
		else
		    median = values.get(values.size()/2);
		
		return median;
	}
	
	public double median(String column, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (get(column) == null || get(rowSelectionColumn) == null)
			return Double.NaN;
		ArrayList<Double> values = new ArrayList<Double>();
		for (int i = 0; i < getRowCount();i++) {
			if (getValue(rowSelectionColumn, i) >= rangeStart && getValue(rowSelectionColumn, i) <= rangeEnd) {
				values.add(getValue(column, i));
			}
		}
		Collections.sort(values);
		
		double median;
		if (values.size() % 2 == 0)
		    median = (values.get(values.size()/2) + values.get(values.size()/2 - 1))/2;
		else
		    median = values.get(values.size()/2);
		
		return median;
	}
	
	public double std(String column) {
		if (get(column) == null)
			return Double.NaN;
		double mean = mean(column);
		double diffSquares = 0;
		for (int i = 0; i < getRowCount() ; i++) {
			diffSquares += (mean - getValue(column, i))*(mean - getValue(column, i));
		}
		
		return Math.sqrt(diffSquares/(getRowCount()-1));
	}
	
	public double std(String meanColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (get(meanColumn) == null || get(rowSelectionColumn) == null)
			return Double.NaN;
		double mean = mean(meanColumn, rowSelectionColumn, rangeStart, rangeEnd);
		double diffSquares = 0;
		int count = 0;
		for (int i = 0; i < getRowCount() ; i++) {
			if (getValue(rowSelectionColumn, i) >= rangeStart && getValue(rowSelectionColumn, i) <= rangeEnd) {
				diffSquares += (mean - getValue(meanColumn, i))*(mean - getValue(meanColumn, i));
				count++;
			}
		}
		
		return Math.sqrt(diffSquares/(count-1));
	}
	
	public double msd(String column) {
		if (get(column) == null)
			return Double.NaN;
		double mean = mean(column);
		double diffSquares = 0;
		for (int i = 0; i < getRowCount() ; i++) {
			diffSquares += (mean - getValue(column, i))*(mean - getValue(column, i));
		}
		return diffSquares/getRowCount();
	}
	
	public double msd(String msdColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (get(msdColumn) == null || get(rowSelectionColumn) == null)
			return Double.NaN;
		double mean = mean(msdColumn, rowSelectionColumn, rangeStart, rangeEnd);
		double diffSquares = 0;
		int count = 0;
		for (int i = 0; i < getRowCount() ; i++) {
			if (getValue(rowSelectionColumn, i) >= rangeStart && getValue(rowSelectionColumn, i) <= rangeEnd) {
				diffSquares += (getValue(msdColumn, i) - mean)*(getValue(msdColumn, i) - mean);
				count++;
			}
		}
		return diffSquares/count;
	}
	
	public double slope(String slopeColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (get(slopeColumn) == null || get(rowSelectionColumn) == null)
			return Double.NaN;
		//We will get the slope information using the SMMMath.LinearRegression function for that we need the
		//offset and length...
		double[] yData = this.getColumnAsDoubles(slopeColumn);
		double[] xData = this.getColumnAsDoubles(rowSelectionColumn);
		int offset = 0;
		int endIndex = 0;
		for (int i = 0; i < xData.length; i++) {
			if (xData[i] <= rangeStart) {
				offset = i;
			}
			
			if (xData[i] <= rangeEnd) {
				endIndex = i;
			}
		}
		int length = endIndex - offset;
		double[] output = SDMMMath.linearRegression(xData, yData, offset, length);
		return output[2];
	}
	
	@Override
	protected DoubleColumn createColumn(final String header) {
		return new DoubleColumn(header);
	}
}