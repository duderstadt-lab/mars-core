package de.mpg.biochem.sdmm.table;

import ij.measure.ResultsTable;

import java.util.AbstractList;

public class ResultsTableList extends AbstractList<double[]> {

	private SDMMResultsTable table;
	
	public ResultsTableList(SDMMResultsTable table) {
		this.table = table;
	}
	
	@Override
	public double[] get(int row) {

		double[] values = new double[table.getColumnCount()];
		
		for (int col = 0; col < values.length; col++)
			values[col] = table.get(col, row);
		
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
