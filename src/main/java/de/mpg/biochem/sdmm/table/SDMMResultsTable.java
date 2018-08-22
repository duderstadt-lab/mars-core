package de.mpg.biochem.sdmm.table;

import net.imagej.table.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

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
public class SDMMResultsTable extends AbstractTable<DoubleColumn, Double> implements ResultsTable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private SDMMResultsTableWindow win;
	
	private StringBuilder sb;
	
	private String name = new String("DoubleResultsTable");
	
	/** Creates an empty results table. */
	public SDMMResultsTable() {
		super();
	}

	/** Creates an empty results table with the name given. */
	public SDMMResultsTable(String name) {
		super();
		this.name = name;
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
		double[] col_array = new double[getRowCount()];
		for (int i=0;i<col_array.length;i++) {
			col_array[i] = get(column).getValue(i);
		}
		return col_array;
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

	@Override
	public ImgPlus<DoubleType> img() {
		final Img<DoubleType> img = new ResultsImg(this);
		final AxisType[] axes = { Axes.X, Axes.Y };
		final String name = "Results";
		final ImgPlus<DoubleType> imgPlus =
			new ImgPlus<>(img, name, axes);
		// TODO: Once ImgPlus has a place for row & column labels, add those too.
		return imgPlus;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	//This used to be a method in ResultsTable
	//I reimplement it here but I guess they might add it in the future...
	public double getValue(String column, int row) {
		return getValue(getColumnIndex(column), row);
	}
	
	//Also reimplementing this method from ResultTable
	//maybe they add it later...
	public void setValue(String column, int row, double value) {
		setValue(getColumnIndex(column), row, value);
	}
	
	//Here are some utility methods add for common operations..
	public double max(String column) {
		if (get(column) == null)
			return Double.NaN;
		double max = get(column, 0);
		//order doesn't matter so we use an iterator
		for (double value: get(column)) {
			if (max < value)
				max = value;
		}
		return max;
	}
	
	public double min(String column) {
		if (get(column) == null)
			return Double.NaN;
		double min = get(column, 0);
		//order doesn't matter so we use an iterator
		for (double value: get(column)) {
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
		if (get(meanColumn) == null || rowSelectionColumn == null)
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
	
	public double std(String column) {
		if (get(column) == null)
			return Double.NaN;
		double mean = mean(column);
		double diffSquares = 0;
		for (int i = 0; i < getRowCount() ; i++) {
			diffSquares += (mean - get(column, i))*(mean - get(column, i));
		}
		
		return Math.sqrt(diffSquares/(getRowCount()-1));
	}
	
	public double std(String meanColumn, String rowSelectionColumn, double rangeStart, double rangeEnd) {
		if (get(meanColumn) == null || rowSelectionColumn == null)
			return Double.NaN;
		double mean = mean(meanColumn, rowSelectionColumn, rangeStart, rangeEnd);
		double diffSquares = 0;
		int count = 0;
		for (int i = 0; i < getRowCount() ; i++) {
			if (getValue(rowSelectionColumn, i) >= rangeStart && getValue(rowSelectionColumn, i) <= rangeEnd) {
				diffSquares += (mean - get(meanColumn, i))*(mean - get(meanColumn, i));
				count++;
			}
		}
		
		return Math.sqrt(diffSquares/(count-1));
	}

	// -- Internal methods --

	@Override
	protected DoubleColumn createColumn(final String header) {
		return new DoubleColumn(header);
	}

}