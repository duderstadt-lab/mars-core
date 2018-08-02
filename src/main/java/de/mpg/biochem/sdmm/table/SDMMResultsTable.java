package de.mpg.biochem.sdmm.table;

import net.imagej.table.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

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

	// -- Internal methods --

	@Override
	protected DoubleColumn createColumn(final String header) {
		return new DoubleColumn(header);
	}

}