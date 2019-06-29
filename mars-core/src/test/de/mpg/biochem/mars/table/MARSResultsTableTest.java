package de.mpg.biochem.mars.table;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scijava.table.DoubleColumn;

class MARSResultsTableTest {

	//@BeforeEach
	//void setUp() throws Exception {
	//}

	//TEST max()
	@Test
	void max() {
		MARSResultsTable table = this.buildTestArrayTable();
		assertEquals(4995.646673, table.max("col0"));
	}
	
	@Test
	void maxNaNs() {
		MARSResultsTable table = buildTestArrayNaNsTable();
		assertEquals(4995.646673, table.max("col0"));
	}
	
	@Test
	void maxAllNaNs() {
		MARSResultsTable table = buildTestArrayAllNaNsTable();
		assertEquals(Double.NaN, table.max("col0"));
	}
	
	//TEST min()
	@Test
	void min() {
		MARSResultsTable table = buildTestArrayTable();
		assertEquals(-4882.151576, table.min("col0"));
	}
	
	@Test
	void minNaNs() {
		MARSResultsTable table = buildTestArrayNaNsTable();
		assertEquals(-4882.151576, table.min("col0"));
	}
	
	@Test
	void minAllNaNs() {
		MARSResultsTable table = buildTestArrayAllNaNsTable();
		assertEquals(Double.NaN, table.min("col0"));
	}
	
	//TEST mean()
	@Test
	void mean() {
		MARSResultsTable table = this.buildTestArrayTable();
		assertEquals(-457.490631682, table.mean("col0"));
	}
	
	@Test
	void meanNaNs() {
		MARSResultsTable table = buildTestArrayNaNsTable();
		assertEquals(-457.490631682, table.mean("col0"));
	}
	
	@Test
	void meanAllNaNs() {
		MARSResultsTable table = buildTestArrayAllNaNsTable();
		assertEquals(Double.NaN, table.mean("col0"));
	}
	
	//TEST median()
	@Test
	void median() {
		MARSResultsTable table = this.buildTestArrayTable();
		assertEquals(-786.38545115, table.median("col0"));
	}
	
	@Test
	void medianNaNs() {
		MARSResultsTable table = buildTestArrayNaNsTable();
		assertEquals(-786.38545115, table.median("col0"));
	}
	
	@Test
	void medianAllNaNs() {
		MARSResultsTable table = buildTestArrayAllNaNsTable();
		assertEquals(Double.NaN, table.median("col0"));
	}
	
	//CONSTANTS AND UTILITY METHODS

	private MARSResultsTable buildTestArrayTable() {
		MARSResultsTable table = new MARSResultsTable();
		DoubleColumn col0 = new DoubleColumn("col0");
		for (double value : testArray)
			col0.add(value);
		
		table.add(col0);
		
		return table;
	}
	
	private MARSResultsTable buildTestArrayNaNsTable() {
		MARSResultsTable table = new MARSResultsTable();
		DoubleColumn col0 = new DoubleColumn("col0");
		for (double value : testArrayNaNs)
			col0.add(value);
		
		table.add(col0);
		
		return table;
	}
	
	private MARSResultsTable buildTestArrayAllNaNsTable() {
		MARSResultsTable table = new MARSResultsTable();
		DoubleColumn col0 = new DoubleColumn("col0");
		for (double value : testArrayAllNaNs)
			col0.add(value);
		
		table.add(col0);
		
		return table;
	}
	
	private final double[] testArray = {721.4053492, -2340.864487, -2694.189541, 4995.646673, 1643.211239, 3860.546144, 
			815.7884318, -1007.139018, -1404.076795, 195.7610837, -1785.972797, 190.3236654, -4411.370252, -4743.992124, 
			-1015.504958, -2371.327814, -1253.05302, 1880.42598, 2403.053577, 3225.70651, 3197.5338, 1430.499206, 
			-1569.147553, -833.1068533, 4869.277227, -401.6722113, -263.3618484, 3597.692509, -478.9343676, 2446.011308, 
			-2355.304877, -3960.300897, -2194.073013, -538.2221627, 1589.697328, -591.7635183, -3285.584816, 3609.117657, 
			-4393.357614, -742.773464, -1938.162097, -932.6219098, -1763.380138, -4545.133429, -4441.702275, -3118.083929, 
			-1619.414716, -3854.88565, 2525.164109, -1827.672271, -434.4189716, 3742.582315, -4434.650675, 2300.292346, 
			618.3404286, 2054.073997, 1650.418286, 4640.574408, -437.0977777, 353.1586727, -1850.484199, -2027.520054, 
			-3559.296065, 2620.35719, 1800.380686, -112.9199202, -1755.952663, 3584.271061, 4995.583324, -1773.42819, 
			1769.444419, -3867.960143, -577.9060965, -4377.209655, 4941.997092, 4578.256938, -1646.14796, -2699.880246, 
			1246.094114, -4005.604619, 1967.859112, 2195.915889, -2539.674577, -2181.147607, -849.8377472, -4810.946999, 
			2239.147404, -4658.640019, 2253.321032, -2392.739537, -2466.804294, -4724.617614, -4700.818179, -829.9974383, 
			299.5609289, -4882.151576, 3537.51919, 832.2358674, -3007.967915, -2885.338512};
	
	private final double[] testArrayNaNs = {721.4053492, -2340.864487, -2694.189541, 4995.646673, 1643.211239, 3860.546144, 
			815.7884318, -1007.139018, -1404.076795, 195.7610837, -1785.972797, 190.3236654, -4411.370252, -4743.992124, 
			-1015.504958, -2371.327814, -1253.05302, 1880.42598, 2403.053577, 3225.70651, 3197.5338, 1430.499206, 
			-1569.147553, -833.1068533, 4869.277227, -401.6722113, -263.3618484, 3597.692509, -478.9343676, 2446.011308, 
			-2355.304877, -3960.300897, -2194.073013, -538.2221627, 1589.697328, -591.7635183, -3285.584816, 3609.117657, 
			-4393.357614, -742.773464, Double.NaN, -1938.162097, -932.6219098, Double.NaN, -1763.380138, -4545.133429, -4441.702275, -3118.083929, 
			-1619.414716, -3854.88565, 2525.164109, -1827.672271, -434.4189716, 3742.582315, -4434.650675, 2300.292346, 
			618.3404286, 2054.073997, 1650.418286, 4640.574408, -437.0977777, 353.1586727, -1850.484199, -2027.520054, 
			-3559.296065, 2620.35719, 1800.380686, -112.9199202, -1755.952663, 3584.271061, 4995.583324, -1773.42819, 
			1769.444419, -3867.960143, -577.9060965, -4377.209655, 4941.997092, 4578.256938, -1646.14796, -2699.880246, 
			1246.094114, -4005.604619, 1967.859112, 2195.915889, -2539.674577, -2181.147607, -849.8377472, -4810.946999, 
			2239.147404, -4658.640019, 2253.321032, -2392.739537, -2466.804294, -4724.617614, -4700.818179, -829.9974383, 
			299.5609289, -4882.151576, 3537.51919, 832.2358674, -3007.967915, -2885.338512, Double.NaN};
	
	private final double[] testArrayAllNaNs = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
}
