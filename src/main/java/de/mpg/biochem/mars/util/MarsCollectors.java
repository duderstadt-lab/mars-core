package de.mpg.biochem.mars.util;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableRowCollector;

public class MarsCollectors {
	public static MarsTableRowCollector toTable(MarsTable table) {
		return new MarsTableRowCollector(table);
	}
}
