/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.mars.table;

import ij.measure.ResultsTable;

import java.util.AbstractList;

public class ResultsTableList extends AbstractList<double[]> {

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
