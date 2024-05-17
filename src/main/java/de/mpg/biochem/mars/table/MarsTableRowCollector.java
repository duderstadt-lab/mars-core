/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2024 Karl Duderstadt
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

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import org.scijava.table.Column;
import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericColumn;

public class MarsTableRowCollector implements
	Collector<MarsTableRow, MarsTable, MarsTable>
{

	private final MarsTable templateTable;

	public MarsTableRowCollector(MarsTable table) {
		this.templateTable = table;
	}

	@Override
	public Supplier<MarsTable> supplier() {
		return () -> {
			MarsTable table = new MarsTable("Collected MarsTable");
			for (String colHeader : templateTable.getColumnHeadingList()) {
				Column<?> column = templateTable.get(colHeader);

				if (column instanceof DoubleColumn) {
					DoubleColumn col = new DoubleColumn(colHeader);
					table.add(col);
				}

				if (column instanceof GenericColumn) {
					GenericColumn col = new GenericColumn(colHeader);
					table.add(col);
				}
			}
			return table;
		};
	}

	@Override
	public BiConsumer<MarsTable, MarsTableRow> accumulator() {
		return MarsTable::addRow;
	}

	@Override
	public BinaryOperator<MarsTable> combiner() {
		return (table1, table2) -> {
			table1.rows().forEach(table2::addRow);
			return table1;
		};
	}

	@Override
	public Function<MarsTable, MarsTable> finisher() {
		return Function.identity();
	}

	@Override
	public Set<Characteristics> characteristics() {
		Set<Characteristics> characteristics = new HashSet<>();
		characteristics.add(Collector.Characteristics.CONCURRENT);
		characteristics.add(Collector.Characteristics.IDENTITY_FINISH);
		return characteristics;
	}
}
