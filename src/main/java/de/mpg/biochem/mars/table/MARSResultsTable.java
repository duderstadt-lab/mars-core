/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.table;

import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.table.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.util.MARSMath;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.regression.SimpleRegression;

/**
 * MARS implementation of a double precision results table. Based on org.scijava.table.DefaultResultsTable.
 * 
 * @author Karl Duderstadt
 */
public class MARSResultsTable extends AbstractTable<Column<? extends Object>, Object> implements GenericTable {

	private static final long serialVersionUID = 1L;
	
	private MARSResultsTableWindow win;
	
	private StringBuilder sb;
	
	private String name = "MARSResultsTable";
	
    @Parameter
    private StatusService statusService;
	
	/** Creates an empty results table. */
	public MARSResultsTable() {
		super();
	}

	/** Creates an empty results table with the name given. */
	public MARSResultsTable(String name) {
		super();
		this.name = name;
	}
	
	/** Creates a results table with the name given and column headers. */
	public MARSResultsTable(String name, String... headers) {
		super();
		this.name = name;
		for (String header : headers)
			add(createColumn(header));
	}

	public MARSResultsTable(File file) throws JsonParseException, IOException {
		super();
		setName(file.getName());
		if (file.getName().endsWith(".json"))
			loadJSON(file);
		else
			loadCSV(file);
	}
	
	public MARSResultsTable(File file, StatusService statusService) throws JsonParseException, IOException {
		super();
		setName(file.getName());
		
		this.statusService = statusService;
		
		if (file.getName().endsWith(".json"))
			loadJSON(file);
		else
			loadCSV(file);
	}
	
	/** Creates a results table with the given row and column dimensions. */
	public MARSResultsTable(final int columnCount, final int rowCount) {
		super(columnCount, rowCount);
	}

	/** Creates a results table with the given row and column dimensions and name. */
	public MARSResultsTable(final String name, final int columnCount, final int rowCount) {
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
			columns[i] = getColumnHeader(i);
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
	
	public MARSResultsTableWindow getWindow() {
		return win;
	}
	
	public void setWindow(MARSResultsTableWindow win) {
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
	
	public List<Object> getRowAsList(int row) {
	    if ((row<0) || (row>=getRowCount()))
	        throw new IllegalArgumentException("Row out of range: "+row);
	    List<Object> rowList = new ArrayList<Object>();
	    rowList.add(row);
	    for (int i=0; i<getColumnCount(); i++) {
	    	rowList.add(get(i,row));
	    }
	    return rowList;
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
						jGenerator.writeNumberField(getColumnHeader(col), MARSMath.round((double)get(col, row)));
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
			    			if (jParser.getCurrentToken().equals(JsonToken.VALUE_STRING)) {
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
	
	public void loadCSV(File file) {
		String absolutePath = file.getAbsolutePath();
		double size_in_bytes = file.length();
		double readPosition = 0;
		final String lineSeparator =  "\n";
		int currentPercentDone = 0;
		int currentPercent = 0;
				
        Path path = Paths.get(absolutePath);
        boolean csv = absolutePath.endsWith(".csv") || absolutePath.endsWith(".CSV");
        String cellSeparator =  csv?",":"\t";
        
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        	    String header = br.readLine();
        	    readPosition += header.getBytes().length + lineSeparator.getBytes().length;
        	    String[] headings = header.split(cellSeparator);
        	    
        	    int firstColumn = headings.length>0&&headings[0].equals(" ")?1:0;

            for (int i=firstColumn; i<headings.length; i++) {
                headings[i] = headings[i].trim();
            }
            
            boolean[] stringColumn = new boolean[headings.length];

            int row = 0;
            for(String line = null; (line = br.readLine()) != null;) {
        	    String[] items = line.split(cellSeparator);
        	    
        	    //During the first cycle we need to build the table with columns that are either 
                //DoubleColumns or GenericColumns for numbers or strings
            	//We need to detect this by what is in the first row...
        	    if (row == 0) {
        	    	for (int i=firstColumn; i<headings.length;i++) {
        	    		if(items[i].equals("NaN") || items[i].equals("-Infinity") || items[i].equals("Infinity")) {
        	    			//This should be a DoubleColumn
        	    			add(new DoubleColumn(headings[i]));
        	    			stringColumn[i] = false;
        	    		} else {
        	    			double value = Double.NaN;
        	    			try {
         	    				value = Double.parseDouble(items[i]);
         	    			} catch (NumberFormatException e) {}
        	    			
        	    			if (Double.isNaN(value)) {
        	    				add(new GenericColumn(headings[i]));
        	    				stringColumn[i] = true;
        	    			} else {
            	    			add(new DoubleColumn(headings[i]));
        	    				stringColumn[i] = false;
        	    			}
        	    		}
        	    	}
        	    }
        	    
        	    appendRow();
        	    for (int i=firstColumn; i<headings.length;i++) {
        	    	if (stringColumn[i]) {
		    		    setValue(i - firstColumn, row, items[i].trim());
        	    	} else {
        	    		double value = Double.NaN;
		    			try {
		    				value = Double.parseDouble(items[i]);
		    			} catch (NumberFormatException e) {}
		    			
		    			setValue(i - firstColumn, row, value);
        	    	}
        	    }
        	    if (statusService != null) {
	        	    readPosition += line.getBytes().length + lineSeparator.getBytes().length;
	        	    currentPercent = (int)Math.round(readPosition*1000/size_in_bytes);
	        	    if (currentPercent > currentPercentDone) {
	    	    		currentPercentDone = currentPercent;
	    	    		statusService.showStatus(currentPercent, 1000, "Opening file " + file.getName());
	        	    }
        	    }
        	    row++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (statusService != null) {
        	statusService.showProgress(100, 100);
        	statusService.showStatus("Opening file " + file.getName() + " - Done!");
        }
	}
	
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
	
	public void setValue(int col, int row, String value) {
		((GenericColumn)get(getColumnHeader(col))).set(row, value);
	}
	
	public void setValue(String column, int row, double value) {
		if (!hasColumn(column)) {
			DoubleColumn col = new DoubleColumn(column);
			for (int i=0;i<getRowCount();i++) {
				if (i == row)
					col.add(value);
				else
					col.add(Double.NaN);
			}
			add(col);
		} else if (get(column) instanceof DoubleColumn)
			((DoubleColumn)get(column)).set(row, value);
		else if (get(column) instanceof GenericColumn) {
			String str = String.valueOf(value);
			((GenericColumn)get(column)).set(row, str);
		}
	}
	
	public void setValue(String column, int row, String value) {
		if (!hasColumn(column)) {
			GenericColumn col = new GenericColumn(column);
			for (int i=0;i<getRowCount();i++) {
				if (i == row)
					col.add(value);
				else
					col.add("");
			}
			add(col);
		} else if (get(column) instanceof GenericColumn) 
			((GenericColumn)get(column)).set(row, value);
		else if (get(column) instanceof DoubleColumn) {
			double num = Double.NaN;
			try {
				num = Double.valueOf(value);
			} catch(NumberFormatException e) {
			    //Do nothing.. set NaN as value...
			}
			((DoubleColumn)get(column)).set(row, num);
		}
	}
	
	public double getValue(int col, int row) {
		return getValue(getColumnHeader(col), row);
	}
	
	public double getValue(String column, int index) {
		return ((DoubleColumn) get(column)).get(index);
	}
	
	public String getStringValue(String column, int index) {
		return (String)get(column).get(index);
	}
	
	public String getStringValue(int col, int row) {
		return getStringValue(getColumnHeader(col), row);
	}
	
	public boolean hasColumn(String colName) {
		for (String column : getColumnHeadingList()) {
			if (column.equals(colName))
				return true;
		}
		return false;
	}
	
	/**
	 * Finds the maximum of the column values. NaN values are ignored. 
	 * 
	 * @param  column  name of the column.
	 * @return maximum value in the column values. NaN is returned if all values are NaN or the column does not exist.
	 */
	public double max(String column) {
		if (!hasColumn(column))
			return Double.NaN;
		double max = Double.MIN_VALUE;
		for (double value: (DoubleColumn) get(column)) {
			if (Double.isNaN(value))
				continue;
			if (max < value)
				max = value;
		}
		if (max == Double.MIN_VALUE)
			return Double.NaN;
		else
			return max;
	}
	
	/**
	 * Finds the minimum of the column values. NaN values are ignored. 
	 * 
	 * @param  column  name of the column.
	 * @return minimum value in the column values. NaN is returned if all values are NaN or the column does not exist.
	 */
	public double min(String column) {
		if (!hasColumn(column))
			return Double.NaN;
		double min = Double.MAX_VALUE;
		for (double value: (DoubleColumn) get(column)) {
			if (Double.isNaN(value))
				continue;
			if (min > value)
				min = value;
		}
		if (min == Double.MAX_VALUE)
			return Double.NaN;
		else
			return min;
	}
	
	/**
	 * Calculates the mean of the values for the column given. NaN values are ignored. 
	 * 
	 * @param  column  name of the column.
	 * @return mean value of the column values. NaN is returned if all values are NaN or the column does not exist.
	 */
	public double mean(String column) {		
		if (!hasColumn(column))
			return Double.NaN;
		Mean mean = new Mean();
		for (int i = 0; i < getRowCount();i++) {
			if (Double.isNaN(getValue(column, i)))
				continue;
			mean.increment(getValue(column, i));
		}
		return mean.getResult();
	}
	
	/**
	 * Calculates the mean of the values for the meanColumn within the range given for a rowSelectionColumn (inclusive of bounds). 
	 * NaN values are ignored. 
	 * 
	 * @param  meanColumn  name of the column used for calculating the mean.
	 * @param  rowSelectionColumn  name of the column used for filtering a range of values.
	 * @param  lowerBound  smallest value included in the row selection range.
	 * @param  upperBound  largest value included in the row selection range.
	 * @return mean value of the column values. NaN is returned if all values are NaN or one of the columns does not exist.
	 */
	public double mean(String meanColumn, String rowSelectionColumn, double lowerBound, double upperBound) {
		if (!hasColumn(meanColumn) || !hasColumn(rowSelectionColumn))
			return Double.NaN;
		Mean mean = new Mean();
		for (int row = 0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(meanColumn, row)))
				continue;
			if (getValue(rowSelectionColumn, row) >= lowerBound && getValue(rowSelectionColumn, row) <= upperBound)
				mean.increment(getValue(meanColumn, row));
		}
		return mean.getResult();
	}
	
	/**
	 * Calculates the median of the column values. NaN values are ignored. 
	 * 
	 * @param  column  name of the column.
	 * @return median value of the column values. NaN is returned if all values are NaN or the column does not exist.
	 */
	public double median(String column) {
		if (!hasColumn(column))
			return Double.NaN;
		ArrayList<Double> values = new ArrayList<Double>();
		for (int i = 0; i < getRowCount();i++) {
			if (Double.isNaN(getValue(column, i)))
				continue;
			values.add(getValue(column, i));
		}
		Collections.sort(values);
		
		if (values.size() == 0)
			return Double.NaN;
		
		double median;
		if (values.size() % 2 == 0)
		    median = (values.get(values.size()/2) + values.get(values.size()/2 - 1))/2;
		else
		    median = values.get(values.size()/2);
		
		return median;
	}
	
	/**
	 * Finds the median of the values for the medianColumn within the range given for a rowSelectionColumn (inclusive of bounds). 
	 * NaN values are ignored. 
	 * 
	 * @param  medianColumn  name of the column used for finding the median.
	 * @param  rowSelectionColumn  name of the column used for filtering a range of values.
	 * @param  lowerBound  smallest value included in the row selection range.
	 * @param  upperBound  largest value included in the row selection range.
	 * @return median value of the column values. NaN is returned if all values are NaN or one of the columns does not exist.
	 */
	public double median(String medianColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (!hasColumn(medianColumn) || !hasColumn(rowSelectionColumn))
			return Double.NaN;
		ArrayList<Double> values = new ArrayList<Double>();
		for (int row = 0; row < getRowCount();row++) {
			if (Double.isNaN(getValue(medianColumn, row)))
				continue;
			
			if (getValue(rowSelectionColumn, row) >= rangeStart && getValue(rowSelectionColumn, row) <= rangeEnd)
				values.add(getValue(medianColumn, row));
		}
		if (values.size() == 0)
			return Double.NaN;
		
		Collections.sort(values);
		double median;
		if (values.size() % 2 == 0)
		    median = (values.get(values.size()/2) + values.get(values.size()/2 - 1))/2;
		else
		    median = values.get(values.size()/2);
		
		return median;
	}
	
	/**
	 * Calculates the standard deviation of the column. NaN values are ignored. 
	 * 
	 * @param  column  name of the column.
	 * @return standard deviation of the column values. NaN is returned if all values are NaN or the column does not exist.
	 */
	public double std(String column) {
		if (!hasColumn(column))
			return Double.NaN;
		
		StandardDeviation standardDeviation = new StandardDeviation();
		for (int i = 0; i < getRowCount() ; i++) {
			if (Double.isNaN(getValue(column, i)))
				continue;
			standardDeviation.increment(getValue(column, i));
		}
		return standardDeviation.getResult();
	}
	
	/**
	 * Calculates the standard deviation of the values for the stdColumn within the range given for a rowSelectionColumn (inclusive of bounds). 
	 * NaN values are ignored. 
	 * 
	 * @param  stdColumn  name of the column to use for the standard deviation calculation.
	 * @param  rowSelectionColumn  name of the column used for filtering a range of values.
	 * @param  lowerBound  smallest value included in the row selection range.
	 * @param  upperBound  largest value included in the row selection range.
	 * @return standard deviation of the stdColumn for the rowSelection. NaN is returned if all values are NaN or one of the columns does not exist.
	 */
	public double std(String stdColumn, String rowSelectionColumn, double lowerBound, double upperBound) {
		if (!hasColumn(stdColumn) || !hasColumn(rowSelectionColumn))
			return Double.NaN;
		
		StandardDeviation standardDeviation = new StandardDeviation();
		for (int row = 0; row < getRowCount() ; row++) {
			if (Double.isNaN(getValue(stdColumn, row)))
				continue;
			if (getValue(rowSelectionColumn, row) >= lowerBound && getValue(rowSelectionColumn, row) <= upperBound)
				standardDeviation.increment(getValue(stdColumn, row));
		}
		
		return standardDeviation.getResult();
	}
	
	/**
	 * Calculates the median absolute deviation. NaN values are ignored.
	 *
	 * @param column  name of the column.
	 * @return median absolute deviation of the column values. NaN is returned if all values are NaN or the column does not exist.
	 */
	public double mad(String column) {
		if (get(column) == null)
			return Double.NaN;
		double median = median(column);
		
		ArrayList<Double> medianDevs = new ArrayList<Double>();
		
		for (int i = 0; i < getRowCount() ; i++) {
			medianDevs.add(Math.abs(median - getValue(column, i)));
		}
		
		//Now find median of the deviations
		Collections.sort(medianDevs);
		
		double medianDev;
		if (medianDevs.size() % 2 == 0)
		    medianDev = (medianDevs.get(medianDevs.size()/2) + medianDevs.get(medianDevs.size()/2 - 1))/2;
		else
		    medianDev = medianDevs.get(medianDevs.size()/2);
		
		return medianDev;
	}
	
	public double mad(String medianColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (get(medianColumn) == null || get(rowSelectionColumn) == null)
			return Double.NaN;
		double median = median(medianColumn, rowSelectionColumn, rangeStart, rangeEnd);
		
		ArrayList<Double> medianDevs = new ArrayList<Double>();
		for (int i = 0; i < getRowCount() ; i++) {
			if (getValue(rowSelectionColumn, i) >= rangeStart && getValue(rowSelectionColumn, i) <= rangeEnd) {
				medianDevs.add(Math.abs(median - getValue(medianColumn, i)));
			}
		}
		
		//Now find median of the deviations
		Collections.sort(medianDevs);
		
		double medianDev;
		if (medianDevs.size() % 2 == 0)
		    medianDev = (medianDevs.get(medianDevs.size()/2) + medianDevs.get(medianDevs.size()/2 - 1))/2;
		else
		    medianDev = medianDevs.get(medianDevs.size()/2);
		
		return medianDev;
	}
	
	public double sem(String column) {
		return std(column)/Math.sqrt(getRowCount());
	}
	
	public double sem(String meanColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
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
		
		return Math.sqrt(diffSquares/(count-1))/Math.sqrt(count);
	}
	
	public double msd(String column) {
		if (get(column) == null)
			return Double.NaN;
		double mean = mean(column);
		double diffSquares = 0;
		
		int count = 0;
		for (int i = 0; i < getRowCount() ; i++) {
			if (Double.isNaN(getValue(column, i)))
				continue;
			diffSquares += (mean - getValue(column, i))*(mean - getValue(column, i));
			count++;
		}
		return diffSquares/count;
	}
	
	public double msd(String msdColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (get(msdColumn) == null || get(rowSelectionColumn) == null)
			return Double.NaN;
		double mean = mean(msdColumn, rowSelectionColumn, rangeStart, rangeEnd);
		double diffSquares = 0;
		int count = 0;
		for (int i = 0; i < getRowCount() ; i++) {
			if (getValue(rowSelectionColumn, i) >= rangeStart && getValue(rowSelectionColumn, i) <= rangeEnd) {
				if (Double.isNaN(getValue(msdColumn, i)))
					continue;
				diffSquares += (getValue(msdColumn, i) - mean)*(getValue(msdColumn, i) - mean);
				count++;
			}
		}
		return diffSquares/count;
	}
	
	//linear fit of x and y for the range of x values given (inclusive of end points)
	public SimpleRegression linearFit(String xColumn, String yColumn) {
		SimpleRegression linearFit = new SimpleRegression(true);
		if (!hasColumn(xColumn) || !hasColumn(yColumn))
			return new SimpleRegression(true);
		
		for (int row=0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(xColumn, row)) || Double.isNaN(getValue(yColumn, row)))
				continue;
			linearFit.addData(getValue(xColumn, row), getValue(yColumn, row));
		}
		
		return linearFit;
	}
	
	//linear fit of x and y for the range of x values given (inclusive of end points)
	public SimpleRegression linearFit(String xColumn, String yColumn, double rangeStart, double rangeEnd) {
		SimpleRegression linearFit = new SimpleRegression(true);
		if (!hasColumn(xColumn) || !hasColumn(yColumn))
			return linearFit;
		
		int rowStart = 0;
		int rowEnd = 0;
		
		//Find first row in range.
		//NaN values will be skipped
		for (int i = getRowCount() - 1; i >= 0; i--) {
			if (getValue(xColumn, i) >= rangeStart)
				rowStart = i;
		}
		
		//Find last row in range
		//NaN values are skipped
		for (int i = 0; i < getRowCount(); i++) {
			if (getValue(xColumn, i) <= rangeEnd)
				rowEnd = i;
		}
		int length = rowEnd - rowStart;
		if (length < 2) 
			return linearFit;
		
		for (int row=rowStart; row <= rowEnd; row++) {
			if (Double.isNaN(getValue(xColumn, row)) || Double.isNaN(getValue(yColumn, row)))
				continue;
			linearFit.addData(getValue(xColumn, row), getValue(yColumn, row));
		}
		
		return linearFit;
	}

	public boolean sort(String... columns) {
		return sort(true, columns);
	}
	
	//Only tables composed of DoubleColumns are sortable currently.
	//If the table contains another column type sort will not do anything.
	
	public boolean sort(final boolean ascending, String... columns) {
		for (int index=0; index<columns.length; index++) {	
			if (!hasColumn(columns[index]))
				return false;
			if (!(get(columns[index]) instanceof DoubleColumn))
				return false;
		}
		
		final int[] columnIndexes = new int[columns.length];
		
		for (int i = 0; i < columns.length; i++)
			columnIndexes[i] = getColumnIndex(columns[i]);
		
		//Maybe there is a better way using the sort method for Lists directly 
		//without a need for the inner class that also works with GenericColumns
		Collections.sort(new ResultsTableList(this), new Comparator<double[]>() {
			
			@Override
			public int compare(double[] o1, double[] o2) {				
				for (int columnIndex: columnIndexes) {
					int groupDifference = Double.compare(o1[columnIndex], o2[columnIndex]); 
				
					if (groupDifference != 0)
						return ascending ? groupDifference : -groupDifference;
				}
				return 0;
			}
			
		});
		
		
		return true;
	}
	
	public void filter() {
		
	}
	
	public void deleteRows(int[] rows) {
		if (rows.length == 0)
			return;
		
		int pos = 0;
		int rowsIndex = 0;
		for (int i = 0; i < getRowCount(); i++) {
			if (rowsIndex < rows.length) {
				if (rows[rowsIndex] == i) {
					rowsIndex++;
					continue;
				}
			}
			
			if (pos != i) {
				//means we need to move row i to position row.
				for (int j=0;j<getColumnCount();j++)
					set(j, pos, get(j, i));
			}
			pos++;
		}
		
		// delete last rows
		for (int row = getRowCount() - 1; row > pos-1; row--)
			removeRow(row);
	}
	
	public void deleteRows(int firstRow, int LastRow) {
	
	}
	
	@Override
	protected DoubleColumn createColumn(final String header) {
		return new DoubleColumn(header);
	}

	public MARSResultsTable clone() {
		MARSResultsTable table = new MARSResultsTable(this.getName());
		for (int col = 0; col < getColumnCount(); col++) {
			if (get(col) instanceof DoubleColumn) {
				DoubleColumn column = new DoubleColumn(get(col).getHeader());
				for (int row=0;row<getRowCount();row++)
					column.add(getValue(col, row));
				
				table.add(column);
			} else if (get(col) instanceof GenericColumn) {
				GenericColumn column = new GenericColumn(get(col).getHeader());
				for (int row=0;row<getRowCount();row++)
					column.add(getStringValue(col, row));
				
				table.add(column);
			}
		}
		return table;
	}
	
	public void setStatusService(StatusService statusService) {
		this.statusService = statusService;
	}
	
	public StatusService getStatusService() {
		return statusService;
	}
	
	class ResultsTableList extends AbstractList<double[]> {
		private MARSResultsTable table;
		
		public ResultsTableList(MARSResultsTable table) {
			this.table = table;
		}
		
		@Override
		public double[] get(int row) {
			double[] values = new double[table.getColumnCount()];
			
			for (int col = 0; col < values.length; col++)
				values[col] = table.getValue(col, row);
			
			return values;
		}
		
		@Override
		public double[] set(int row, double[] values) {
			double[] old = get(row);
			
			for (int col = 0; col < values.length; col++)
				table.setValue(col, row, values[col]);
			
			return old;
		}
		@Override
		public int size() {
			return table.getRowCount();
		}
		
		@Override
		public void removeRange(int fromIndex, int toIndex) {
			int n = toIndex - fromIndex;
			int m = size();
			
			// move range to the end of the table
			for (int row = fromIndex; row + n < m; row++)
				set(row, get(row + n));
			
			// delete last rows
			for (int row = m - 1; row >= m - n; row--)
				table.removeRow(row);
			
		}
	}
}
