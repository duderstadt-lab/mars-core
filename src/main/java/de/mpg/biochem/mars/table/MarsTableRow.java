/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package de.mpg.biochem.mars.table;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.scijava.table.*;

public class MarsTableRow implements Iterator<MarsTableRow> {

	private final MarsTable table;
	private final String[] columnNames;
	private int rowNumber;

	private final Map<String, DoubleColumn> doubleColumnMap = new TreeMap<>();
	private final Map<String, GenericColumn> genericColumnMap = new TreeMap<>();
	private final Map<String, Column<?>> columnMap = new TreeMap<>();

	public MarsTableRow(MarsTable table) {
		this.table = table;
		columnNames = table.getColumnHeadings();
		rowNumber = -1;

		for (int colIndex = 0; colIndex < table.getColumnCount(); colIndex++) {
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

	// Getters

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
		return (String) genericColumnMap.get(columnName).get(rowNumber);
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

	// Setters

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
