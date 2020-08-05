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

public class MarsTableRowCollector implements Collector<MarsTableRow, MarsTable, MarsTable> {
	
	private MarsTable templateTable;
	
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
		return (table, row) -> table.addRow(row);
	}

	@Override
	public BinaryOperator<MarsTable> combiner() {
		return (table1, table2) -> {
	        table1.rows().forEach(row -> table2.addRow(row));
	        return table1;
	    };
	}

	@Override
	public Function<MarsTable, MarsTable> finisher() {
		return Function.identity();
	}

	@Override
	public Set<Characteristics> characteristics() {
		Set<Characteristics> characteristics = new HashSet<Characteristics>();
		characteristics.add(Collector.Characteristics.CONCURRENT);
		characteristics.add(Collector.Characteristics.IDENTITY_FINISH);
		return characteristics;
	}
}
