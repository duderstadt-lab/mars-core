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
