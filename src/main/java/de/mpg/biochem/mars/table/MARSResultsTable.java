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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.util.MARSMath;
import ij.text.TextWindow;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * MARS implementation of a double precision results table. Based on org.scijava.table.DefaultResultsTable.
 * 
 * @author Karl Duderstadt
 */
//public class MARSResultsTable extends AbstractTable<DoubleColumn, Double> implements ResultsTable {
public class MARSResultsTable extends AbstractTable<Column<? extends Object>, Object> implements GenericTable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private MARSResultsTableWindow win;
	
	private StringBuilder sb;
	
	private String name = new String("MARSResultsTable");
	
	//Special column names for a segments table...
	private String xColumnName, yColumnName;
	
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
	
	public MARSResultsTable(String xColumnName, String yColumnName) {
		super();
		this.xColumnName = xColumnName;
		this.yColumnName = yColumnName;
		this.name = yColumnName + " vs " + xColumnName;
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
	
	public void setValue(int col, int row, String value) {
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
	
	public DoubleColumn getDoubleColumn(String column) {
		return (DoubleColumn)get(column);
	}
	
	public GenericColumn getGenericColumn(String column) {
		return (GenericColumn)get(column);
	}
	
	//Here are some utility methods add for common operations..
	/**
	 * Returns the maximum value in the column.
	 * 
	 * @param  column  name of the column.
	 * @return      maximum value in the column.
	 */
	public double max(String column) {
		if (get(column) == null)
			return Double.NaN;
		double max = (double)get(column, 0);
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
			if (Double.isNaN(getValue(column, i)))
				continue;
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
				if (Double.isNaN(getValue(meanColumn, i)))
					continue;
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
	
	public double slope(String xColumn, String yColumn) {
		if (get(xColumn) == null || get(yColumn) == null)
			return Double.NaN;
		//We will get the slope information using the SMMMath.LinearRegression function for that we need the
		//offset and length...
		double[] xData = this.getColumnAsDoubles(xColumn);
		double[] yData = this.getColumnAsDoubles(yColumn);
		
		if (xData.length < 2) {
			return Double.NaN;
		}
		double[] output = MARSMath.linearRegression(xData, yData, 0, xData.length);
		return output[2];
	}
	
	public double slope(String xColumn, String yColumn, double rangeStart, double rangeEnd) {
		if (get(xColumn) == null || get(yColumn) == null)
			return Double.NaN;
		//We will get the slope information using the SMMMath.LinearRegression function for that we need the
		//offset and length...
		double[] xData = this.getColumnAsDoubles(xColumn);
		double[] yData = this.getColumnAsDoubles(yColumn);
		int offset = 0;
		int endIndex = 0;
		//Let's make sure the end points are always inside the range even if the points given don't exist...
		//First for offset
		for (int i = xData.length - 1; i >= 0; i--) {
			if (xData[i] >= rangeStart) {
				offset = i;
			}
		}
		
		//For the end of the range we search in the other direction.. 
		for (int i = 0; i < xData.length; i++) {
			if (xData[i] <= rangeEnd) {
				endIndex = i;
			}
		}
		int length = endIndex - offset;
		if (length < 2) {
			return Double.NaN;
		}
		double[] output = MARSMath.linearRegression(xData, yData, offset, length);
		return output[2];
	}
	
	public double[] linearFit(String xColumn, String yColumn) {
		if (get(xColumn) == null || get(yColumn) == null)
			return new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN};
		
		//We will get the slope information using the SMMMath.LinearRegression function for that we need the
		//offset and length...
		double[] xData = this.getColumnAsDoubles(xColumn);
		double[] yData = this.getColumnAsDoubles(yColumn);
		
		if (xData.length < 2) {
			return new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN};
		}
		return MARSMath.linearRegression(xData, yData, 0, xData.length);
	}
	
	public double[] linearFit(String xColumn, String yColumn, double rangeStart, double rangeEnd) {
		if (get(yColumn) == null || get(xColumn) == null)
			return new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN};
		//We will get the slope information using the MARSMath.linearRegression function for that we need the
		//offset and length...
		double[] xData = this.getColumnAsDoubles(xColumn);
		double[] yData = this.getColumnAsDoubles(yColumn);
		int offset = 0;
		int endIndex = 0;
		//Let's make sure the end points are always inside the range even if the points given don't exist...
		//First for offset
		for (int i = xData.length - 1; i >= 0; i--) {
			if (xData[i] >= rangeStart) {
				offset = i;
			}
		}
		
		//For the end of the range we search in the other direction.. 
		for (int i = 0; i < xData.length; i++) {
			if (xData[i] <= rangeEnd) {
				endIndex = i;
			}
		}
		int length = endIndex - offset;
		if (length < 2) {
			return new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN};
		}

		return MARSMath.linearRegression(xData, yData, offset, length);
	}
	
	public void sort(final boolean ascending, String column) {
		ResultsTableService.sort(this, ascending, column);
	}
	
	public void sort(final boolean ascending, String group, String column) {
		ResultsTableService.sort(this, ascending, group, column);
	}
	
	public void sort(final boolean ascending, String... column) {
		ResultsTableService.sort(this, ascending, column);
	}
	
	@Override
	protected DoubleColumn createColumn(final String header) {
		return new DoubleColumn(header);
	}
	
	public void setXYColumnNames(String xColumnName, String yColumnName) {
		this.xColumnName = xColumnName;
		this.yColumnName = yColumnName;
		this.name = xColumnName + " vs " + yColumnName;
	}
	
	public String getXColumnName() {
		return this.xColumnName;
	}
	
	public String getYColumnName() {
		return this.yColumnName;
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
}
