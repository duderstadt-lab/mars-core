package de.mpg.biochem.mars.table;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scijava.table.DoubleColumn;

class MARSResultsTableTest {

	/*
	 * TEST max()
	 */
	
	@Test
	void max() {
		MARSResultsTable table = buildTestArrayTable();
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
	
	@Test
	void maxNoColumn() {
		MARSResultsTable table = buildTestArrayTable();
		assertEquals(Double.NaN, table.max("not here"));
	}
	
	/*
	 * TEST min()
	 */
	
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
	
	@Test
	void minNoColumn() {
		MARSResultsTable table = buildTestArrayTable();
		assertEquals(Double.NaN, table.min("not here"));
	}
	
	/*
	 * TEST mean()
	 */
	
	@Test
	void mean() {
		MARSResultsTable table = buildTestArrayTable();
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
	
	@Test
	void meanNoColumn() {
		MARSResultsTable table = buildTestArrayTable();
		assertEquals(Double.NaN, table.mean("not here"));
	}
	
	/*
	 * TEST mean() for selected rows
	 */
	
	@Test
	void meanSelectedRows() {
		MARSResultsTable table = buildTestXYTable();
		assertEquals(1115.5639359, table.mean("col1", "col0", 3, 3.5));
	}
	
	@Test
	void meanSelectedRowsNaNs() {
		MARSResultsTable table = buildTestXYNaNsTable();
		assertEquals(1115.5639359, table.mean("col1", "col0", 3, 3.5));
	}
	
	@Test
	void meanSelectedRowsAllNaNs() {
		MARSResultsTable table = buildTestXYAllNaNsTable();
		assertEquals(Double.NaN, table.mean("col1", "col0", 3, 3.5));
	}
	
	@Test
	void meanSelectedRowsNoColumn() {
		MARSResultsTable table = buildTestXYTable();
		assertEquals(Double.NaN, table.mean("Not a Column", "col0", 3, 3.5));
	}
	
	/*
	 * TEST median()
	 */
	
	@Test
	void median() {
		MARSResultsTable table = buildTestArrayTable();
		assertEquals(-786.38545115, table.median("col0"));
	}
	
	@Test
	void medianOdd() {
		MARSResultsTable table = this.buildTestArrayOddTable();
		assertEquals(-742.773464, table.median("col0"));
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
	
	@Test
	void medianNoColumn() {
		MARSResultsTable table = buildTestArrayTable();
		assertEquals(Double.NaN, table.median("not here"));
	}
	
	/*
	 * TEST median() for selected rows
	 */
	
	@Test
	void medianSelectedRows() {
		MARSResultsTable table = buildTestXYTable();
		assertEquals(-401.6722113, table.median("col1", "col0", 2, 4));
	}
	
	@Test
	void medianSelectedRowsNaNs() {
		MARSResultsTable table = buildTestXYNaNsTable();
		assertEquals(-401.6722113, table.median("col1", "col0", 2, 4));
	}
	
	@Test
	void medianSelectedRowsAllNaNs() {
		MARSResultsTable table = buildTestXYAllNaNsTable();
		assertEquals(Double.NaN, table.median("col1", "col0", 2, 4));
	}
	
	@Test
	void medianSelectedRowsNoColumn() {
		MARSResultsTable table = buildTestXYTable();
		assertEquals(Double.NaN, table.median("Not a Column", "col0", 2, 4));
	}
	
	/*
	 * TEST std()
	 */

	@Test
	void std() {
		MARSResultsTable table = buildTestArrayTable();
		assertEquals(2785.974502807221, table.std("col0"));
	}
	
	@Test
	void stdNaNs() {
		MARSResultsTable table = buildTestArrayNaNsTable();
		assertEquals(2785.974502807221, table.std("col0"));
	}
	
	@Test
	void stdAllNaNs() {
		MARSResultsTable table = buildTestArrayAllNaNsTable();
		assertEquals(Double.NaN, table.std("col0"));
	}
	
	@Test
	void stdNoColumn() {
		MARSResultsTable table = buildTestArrayTable();
		assertEquals(Double.NaN, table.std("not here"));
	}
	
	/*
	 * TEST std() for selected rows
	 */

	@Test
	void stdSelectedRows() {
		MARSResultsTable table = buildTestXYTable();
		assertEquals(2617.7803706062314, table.std("col1", "col0", 2, 4));
	}
	
	@Test
	void stdSelectedRowsNaNs() {
		MARSResultsTable table = buildTestXYNaNsTable();
		assertEquals(2617.7803706062314, table.std("col1", "col0", 2, 4));
	}
	
	@Test
	void stdSelectedRowsAllNaNs() {
		MARSResultsTable table = buildTestXYAllNaNsTable();
		assertEquals(Double.NaN, table.std("col1", "col0", 2, 4));
	}
	
	@Test
	void stdSelectedRowsNoColumn() {
		MARSResultsTable table = buildTestXYTable();
		assertEquals(Double.NaN, table.std("Not a column", "col0", 2, 4));
	}
	
	/*
	 * CONSTANTS AND UTILITY METHODS
	 */

	private MARSResultsTable buildTestArrayTable() {
		MARSResultsTable table = new MARSResultsTable();
		DoubleColumn col0 = new DoubleColumn("col0");
		for (double value : testArray)
			col0.add(value);
		
		table.add(col0);
		
		return table;
	}
	
	private MARSResultsTable buildTestArrayOddTable() {
		MARSResultsTable table = new MARSResultsTable();
		DoubleColumn col0 = new DoubleColumn("col0");
		for (double value : testArrayOdd)
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
	
	private MARSResultsTable buildTestXYTable() {
		MARSResultsTable table = new MARSResultsTable();
		DoubleColumn col0 = new DoubleColumn("col0");
		DoubleColumn col1 = new DoubleColumn("col1");
		for (double[] value : XY) {
			col0.add(value[0]);
			col1.add(value[1]);
		}
		
		table.add(col0);
		table.add(col1);
		
		return table;
	}
	
	private MARSResultsTable buildTestXYNaNsTable() {
		MARSResultsTable table = new MARSResultsTable();
		DoubleColumn col0 = new DoubleColumn("col0");
		DoubleColumn col1 = new DoubleColumn("col1");
		for (double[] value : XYNaNs) {
			col0.add(value[0]);
			col1.add(value[1]);
		}
		
		table.add(col0);
		table.add(col1);
		
		return table;
	}
	
	private MARSResultsTable buildTestXYAllNaNsTable() {
		MARSResultsTable table = new MARSResultsTable();
		DoubleColumn col0 = new DoubleColumn("col0");
		DoubleColumn col1 = new DoubleColumn("col1");
		for (double[] value : XYAllNaNs) {
			col0.add(value[0]);
			col1.add(value[1]);
		}
		
		table.add(col0);
		table.add(col1);
		
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
	
	private final double[] testArrayOdd = {721.4053492, -2340.864487, -2694.189541, 4995.646673, 1643.211239, 3860.546144, 
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
			299.5609289, -4882.151576, 3537.51919, 832.2358674, -3007.967915};
	
	private final double[] testArrayNaNs = {721.4053492, -2340.864487, -2694.189541, 4995.646673, 1643.211239, 3860.546144, 
			815.7884318, -1007.139018, -1404.076795, 195.7610837, -1785.972797, 190.3236654, -4411.370252, -4743.992124, 
			-1015.504958, -2371.327814, -1253.05302, 1880.42598, 2403.053577, 3225.70651, 3197.5338, 1430.499206, 
			-1569.147553, -833.1068533, 4869.277227, -401.6722113, -263.3618484, 3597.692509, -478.9343676, 2446.011308, 
			-2355.304877, -3960.300897, -2194.073013, -538.2221627, 1589.697328, -591.7635183, -3285.584816, 3609.117657, 
			-4393.357614, -742.773464, Double.NaN, -1938.162097, -932.6219098, Double.NaN, -1763.380138, -4545.133429, 
			-4441.702275, -3118.083929, 
			-1619.414716, -3854.88565, 2525.164109, -1827.672271, -434.4189716, 3742.582315, -4434.650675, 2300.292346, 
			618.3404286, 2054.073997, 1650.418286, 4640.574408, -437.0977777, 353.1586727, -1850.484199, -2027.520054, 
			-3559.296065, 2620.35719, 1800.380686, -112.9199202, -1755.952663, 3584.271061, 4995.583324, -1773.42819, 
			1769.444419, -3867.960143, -577.9060965, -4377.209655, 4941.997092, 4578.256938, -1646.14796, -2699.880246, 
			1246.094114, -4005.604619, 1967.859112, 2195.915889, -2539.674577, -2181.147607, -849.8377472, -4810.946999, 
			2239.147404, -4658.640019, 2253.321032, -2392.739537, -2466.804294, -4724.617614, -4700.818179, -829.9974383, 
			299.5609289, -4882.151576, 3537.51919, 832.2358674, -3007.967915, -2885.338512, Double.NaN};
	
	private final double[] testArrayAllNaNs = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
	
	private final double[][] XY = {{1,721.4053492}, {1.1,-2340.864487}, {1.2,-2694.189541}, {1.3,4995.646673}, 
			{1.4,1643.211239}, {1.5,3860.546144}, {1.6,815.7884318}, {1.7,-1007.139018}, {1.8,-1404.076795}, 
			{1.9,195.7610837}, {2,-1785.972797}, {2.1,190.3236654}, {2.2,-4411.370252}, {2.3,-4743.992124}, 
			{2.4,-1015.504958}, {2.5,-2371.327814}, {2.6,-1253.05302}, {2.7,1880.42598}, {2.8,2403.053577}, 
			{2.9,3225.70651}, {3,3197.5338}, {3.1,1430.499206}, {3.2,-1569.147553}, {3.3,-833.1068533}, {3.4,4869.277227}, 
			{3.5,-401.6722113}, {3.6,-263.3618484}, {3.7,3597.692509}, {3.8,-478.9343676}, {3.9,2446.011308}, 
			{4,-2355.304877}, {4.1,-3960.300897}, {4.2,-2194.073013}, {4.3,-538.2221627}, {4.4,1589.697328}, 
			{4.5,-591.7635183}, {4.6,-3285.584816}, {4.7,3609.117657}, {4.8,-4393.357614}, {4.9,-742.773464}, 
			{5,-1938.162097}, {5.1,-932.6219098}, {5.2,-1763.380138}, {5.3,-4545.133429}, {5.4,-4441.702275}, 
			{5.5,-3118.083929}, {5.6,-1619.414716}, {5.7,-3854.88565}, {5.8,2525.164109}, {5.9,-1827.672271}, 
			{6,-434.4189716}, {6.1,3742.582315}, {6.2,-4434.650675}, {6.3,2300.292346}, {6.4,618.3404286}, 
			{6.5,2054.073997}, {6.6,1650.418286}, {6.7,4640.574408}, {6.8,-437.0977777}, {6.9,353.1586727}, 
			{7,-1850.484199}, {7.1,-2027.520054}, {7.2,-3559.296065}, {7.3,2620.35719}, {7.4,1800.380686}, 
			{7.5,-112.9199202}, {7.6,-1755.952663}, {7.7,3584.271061}, {7.8,4995.583324}, {7.9,-1773.42819}, 
			{8,1769.444419}, {8.1,-3867.960143}, {8.2,-577.9060965}, {8.3,-4377.209655}, {8.4,4941.997092}, 
			{8.5,4578.256938}, {8.6,-1646.14796}, {8.7,-2699.880246}, {8.8,1246.094114}, {8.9,-4005.604619}, 
			{9,1967.859112}, {9.1,2195.915889}, {9.2,-2539.674577}, {9.3,-2181.147607}, {9.4,-849.8377472}, 
			{9.5,-4810.946999}, {9.6,2239.147404}, {9.7,-4658.640019}, {9.8,2253.321032}, {9.9,-2392.739537}, 
			{10,-2466.804294}, {10.1,-4724.617614}, {10.2,-4700.818179}, {10.3,-829.9974383}, {10.4,299.5609289}, 
			{10.5,-4882.151576}, {10.6,3537.51919}, {10.7,832.2358674}, {10.8,-3007.967915}, {10.9,-2885.338512}};
	
	private final double[][] XYNaNs = {{1,721.4053492}, {1.1,-2340.864487}, {1.2,-2694.189541}, {1.3,4995.646673}, 
			{1.4,1643.211239}, {1.5,3860.546144}, {1.6,815.7884318}, {1.7,-1007.139018}, {1.8,-1404.076795}, 
			{1.9,195.7610837}, {2,-1785.972797}, {2.1,190.3236654}, {2.2,-4411.370252}, {2.3,-4743.992124}, 
			{2.4,-1015.504958}, {2.45, Double.NaN}, {2.5,-2371.327814}, {2.6,-1253.05302}, {2.7,1880.42598}, {2.8,2403.053577}, 
			{2.9,3225.70651}, {3,3197.5338}, {3.1,1430.499206}, {3.2,-1569.147553}, {3.3,-833.1068533}, {3.4,4869.277227}, 
			{3.5,-401.6722113}, {3.6,-263.3618484}, {3.7,3597.692509}, {3.8,-478.9343676}, {3.9,2446.011308}, 
			{4,-2355.304877}, {4.1,-3960.300897}, {4.2,-2194.073013}, {4.3,-538.2221627}, {4.4,1589.697328}, 
			{4.5,-591.7635183}, {4.6,-3285.584816}, {4.7,3609.117657}, {4.8,-4393.357614}, {4.9,-742.773464}, {4.95, Double.NaN},
			{5,-1938.162097}, {5.1,-932.6219098}, {Double.NaN, 1453.6758}, {5.2,-1763.380138}, {5.3,-4545.133429}, {5.4,-4441.702275}, 
			{5.5,-3118.083929}, {5.6,-1619.414716}, {5.7,-3854.88565}, {5.8,2525.164109}, {5.9,-1827.672271}, 
			{6,-434.4189716}, {6.1,3742.582315}, {6.2,-4434.650675}, {6.3,2300.292346}, {6.4,618.3404286}, 
			{6.5,2054.073997}, {6.6,1650.418286}, {6.7,4640.574408}, {6.8,-437.0977777}, {6.9,353.1586727}, 
			{7,-1850.484199}, {7.1,-2027.520054}, {7.2,-3559.296065}, {7.3,2620.35719}, {7.4,1800.380686}, 
			{7.5,-112.9199202}, {7.6,-1755.952663}, {7.7,3584.271061}, {7.8,4995.583324}, {7.9,-1773.42819}, 
			{8,1769.444419}, {8.1,-3867.960143}, {8.2,-577.9060965}, {8.3,-4377.209655}, {8.4,4941.997092}, 
			{8.5,4578.256938}, {8.6,-1646.14796}, {8.7,-2699.880246}, {8.8,1246.094114}, {8.9,-4005.604619}, 
			{9,1967.859112}, {9.1,2195.915889}, {9.2,-2539.674577}, {9.3,-2181.147607}, {9.4,-849.8377472}, 
			{9.5,-4810.946999}, {9.6,2239.147404}, {9.7,-4658.640019}, {9.8,2253.321032}, {9.9,-2392.739537}, 
			{10,-2466.804294}, {10.1,-4724.617614}, {10.2,-4700.818179}, {10.3,-829.9974383}, {10.4,299.5609289}, 
			{10.5,-4882.151576}, {10.6,3537.51919}, {10.7,832.2358674}, {10.8,-3007.967915}, {10.9,-2885.338512}, {Double.NaN, Double.NaN}};

	private final double[][] XYAllNaNs = {{Double.NaN, Double.NaN}, {Double.NaN, Double.NaN}, {Double.NaN, Double.NaN},
			{Double.NaN, Double.NaN}, {Double.NaN, Double.NaN}, {Double.NaN, Double.NaN}, {Double.NaN, Double.NaN}};
}
