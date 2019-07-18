package de.mpg.biochem.mars.table;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.scijava.table.*;

public class MarsTableRow implements Iterator<MarsTableRow> {
	
	private final MarsResultsTable table;
	private final String[] columnNames;
	private int rowNumber;
	
	private final Map<String, DoubleColumn> doubleColumnMap = new TreeMap<>();
	private final Map<String, GenericColumn> genericColumnMap = new TreeMap<>();
	private final Map<String, Column<?>> columnMap = new TreeMap<>();
	
	public MarsTableRow(MarsResultsTable table) {
        this.table = table;
        columnNames = table.getColumnHeadings();
        rowNumber = -1;
        
        for (int colIndex=0;colIndex < table.getColumnCount(); colIndex++) {
        	Column<?> column = table.get(colIndex);
        	
        	if (column instanceof DoubleColumn) {
        		doubleColumnMap.put(column.getHeader(), (DoubleColumn) column);
        	} 
        	
        	if (column instanceof GenericColumn) {
        		genericColumnMap.put(column.getHeader(), (GenericColumn) column);
        	}
        	
        	columnMap.put(column.getHeader(), column);
        }
	}
	
	//Getters
	
	public double getValue(int columnIndex) {
        return getValue(columnNames[columnIndex]);
    }

    public double getValue(String columnName) {
        return doubleColumnMap.get(columnName).get(rowNumber);
    }
    
    public String getStringValue(int columnIndex) {
        return getStringValue(columnNames[columnIndex]);
    }

    public String getStringValue(String columnName) {
        return (String)genericColumnMap.get(columnName).get(rowNumber);
    }
    
    public Object getObject(String columnName) {
        return columnMap.get(columnName).get(rowNumber);
    }

    public Object getObject(int columnIndex) {
        return columnMap.get(columnNames[columnIndex]).get(rowNumber);
    }
    
    public int getRowNumber() {
        return rowNumber;
    }

    public int columnCount() {
        return table.getColumnCount();
    }

    public List<String> columnNames() {
        return table.getColumnHeadingList();
    }
    
    //Setters
    
    public void setValue(int columnIndex, double value) {
        setValue(columnNames[columnIndex], value);
    }

    public void setValue(String columnName, double value) {
        doubleColumnMap.get(columnName).setValue(rowNumber, value);
    }
    
    public void setStringValue(int columnIndex, String value) {
        setStringValue(columnNames[columnIndex], value);
    }

    public void setStringValue(String columnName, String value) {
        genericColumnMap.get(columnName).set(rowNumber, value);
    }
	
	public void at(int rowNumber) {
        this.rowNumber = rowNumber;
    }
	
	@Override
	public boolean hasNext() {
		return rowNumber < table.getRowCount() - 1;
	}

	@Override
	public MarsTableRow next() {
		rowNumber++;
        return this;
	}

}
