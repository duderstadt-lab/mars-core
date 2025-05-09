/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import de.mpg.biochem.mars.util.MarsUtil.ThrowingConsumer;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.table.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.Deflater;

/**
 * Mars implementation of a scijava results table. All numbers are stored as
 * doubles (as {@link org.scijava.table.DoubleColumn}). Convenience methods and
 * constructors are provided for common operations (min, max, mean, std,
 * variance, linearRegression, sorting, filtering, etc), for saving and opening
 * tables in csv or json format, and retrieval of values in many formats.
 * Throughout ({@link org.apache.commons.math3}) is used for common operations
 * where possible.
 * <p>
 * GenericColumns containing Strings can also be added to MarsTables. Their
 * primary intended use is for generating output tables that need to combine
 * numbers and strings or static data storage of tables composed entirely of
 * strings (for example, with frame metadata information for time points as
 * rows).
 * </p>
 * <p>
 * More complex row filtering operations can be accomplished using the rowStream
 * method and java 8 stream framework. The optimal implementation would return
 * an {@code ArrayList<Integer>} with a set of rows to remove or keep. This list
 * can then be used with the deleteRows or keepRows methods.
 * </p>
 * <p>
 * All sorting and filtering operations are performed in place. This allows for
 * processing of larger tables in memory without requiring enough memory for a
 * copy. If a copy is desired. For example, if several filtering and sorting
 * steps are performed from the same primary table, prior to each operation a
 * copy can be made using the
 * {@link de.mpg.biochem.mars.table.MarsTable#clone()} method.
 * </p>
 * 
 * @author Karl Duderstadt
 */
public class MarsTable extends AbstractTable<Column<?>, Object>
	implements GenericTable, JsonConvertibleRecord
{

	private static final long serialVersionUID = 1L;

	private int decimalPlacePrecision = -1;

	private MarsTableWindow win;

	private StringBuilder sb;

	private String name = "MarsTable";

	private File file;

	@Parameter
	private StatusService statusService;

	/** Creates an empty results table. */
	public MarsTable() {
		super();
	}

	/**
	 * Creates an empty results table with the name given.
	 * 
	 * @param name Table name.
	 */
	public MarsTable(String name) {
		super();
		this.name = name;
	}

	/**
	 * Creates a results table with the name given and column headers.
	 * 
	 * @param name Table name.
	 * @param headers Names of column headers to initialize.
	 */
	public MarsTable(String name, String... headers) {
		super();
		this.name = name;
		for (String header : headers)
			add(createColumn(header));
	}

	/**
	 * Opens a results table from the file provided. The values are loaded in
	 * based on the extension of the file provided, which can be json or csv. If
	 * no file extension is found it is assumed the file has csv format.
	 * 
	 * @param file File to load table from.
	 * @throws JsonParseException Thrown if unable to parse Json.
	 * @throws IOException Thrown if unable to read file.
	 */
	public MarsTable(File file) throws JsonParseException, IOException {
		super();
		this.file = file;
		setName(file.getName());
		if (file.getName().endsWith(".json") || file.getName().endsWith(".yamt"))
			loadJSON(file);
		else loadCSV(file);
	}

	/**
	 * Opens a results table from the file provided. The values are loaded in
	 * based on the extension of the file provided, which can be json or csv. If
	 * no file extension is found it is assumed the file has csv format. If the
	 * StatusService is provided, then a progress bar is shown during loading of
	 * csv files.
	 * 
	 * @param file File to load the table from.
	 * @param statusService StatusService from the current Context to provide the
	 *          user with progress updates.
	 * @throws IOException If unable to load the file.
	 * @throws JsonParseException If a problem is encountered when parsing Json.
	 */
	public MarsTable(File file, StatusService statusService)
		throws JsonParseException, IOException
	{
		super();
		setName(file.getName());
		this.file = file;

		this.statusService = statusService;

		if (file.getName().endsWith(".json") || file.getName().endsWith(".yamt"))
			loadJSON(file);
		else loadCSV(file);
	}

	/**
	 * Creates a results table with the given row and column dimensions.
	 * 
	 * @param columnCount Number of columns.
	 * @param rowCount Number of rows.
	 */
	public MarsTable(final int columnCount, final int rowCount) {
		super(columnCount, rowCount);
	}

	/**
	 * Creates a results table with the given a scijava Table. Creates
	 * DoubleColumns if those exist. Otherwise, generic columns are created.
	 * 
	 * @param table Something implementing the Table interface.
	 */
	public MarsTable(Table<Column<?>, Object> table) {
		for (int col = 0; col < table.getColumnCount(); col++) {
			if (table.get(col) instanceof DoubleColumn) {
				DoubleColumn column = new DoubleColumn(table.get(col).getHeader());
				for (int row = 0; row < table.getRowCount(); row++)
					column.add((double) table.get(col, row));

				add(column);
			}
			else {
				GenericColumn column = new GenericColumn(table.get(col).getHeader());
				for (int row = 0; row < table.getRowCount(); row++)
					column.add(table.get(col, row));

				add(column);
			}
		}
	}

	/**
	 * Creates a results table with the given name and column and row dimensions.
	 * 
	 * @param name Table name.
	 * @param columnCount Number of columns.
	 * @param rowCount Number of rows.
	 */
	public MarsTable(final String name, final int columnCount,
		final int rowCount)
	{
		super(columnCount, rowCount);
		this.name = name;
	}

	/**
	 * Wraps a scijava table or anything implementing the scijava Table interface
	 * inside a MarsTable without copying. Provides fast access to MarsTable
	 * functions without the need to copy all values.
	 * 
	 * @param table Something implementing the Table interface.
	 * @return The wrapped MarsTable.
	 */
	public static MarsTable wrap(Table<Column<?>, Object> table) {
		MarsTable shell = new MarsTable();
		for (int i = 0; i < table.getColumnCount(); i++)
			shell.add(table.get(i));

		return shell;
	}

	/**
	 * Sets the name of the table.
	 * 
	 * @param name Name of the table.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Retrieves the name of the table.
	 * 
	 * @return The name of the table.
	 */
	public String getName() {
		return name;
	}

	public MarsTable setPrecision(int decimalPlacePrecision) {
		if (decimalPlacePrecision > -1 && decimalPlacePrecision < 19)
			this.decimalPlacePrecision = decimalPlacePrecision;
		else this.decimalPlacePrecision = -1;

		return this;
	}

	/**
	 * Returns the column headings as an array of strings.
	 * 
	 * @return String array contain the column headers.
	 */
	public String[] getColumnHeadings() {
		String[] columns = new String[getColumnCount()];
		for (int i = 0; i < columns.length; i++) {
			columns[i] = getColumnHeader(i);
		}
		return columns;
	}

	/**
	 * Returns the column headings as an ArrayList of strings.
	 * 
	 * @return ArrayList containing the column headers.
	 */
	public List<String> getColumnHeadingList() {
		List<String> columns = new ArrayList<>();

		for (int i = 0; i < getColumnCount(); i++) {
			if (!columns.contains(getColumnHeader(i))) columns.add(getColumnHeader(
				i));
		}

		return columns;
	}

	/**
	 * Returns a reference to the window containing the table if there is one.
	 * This could be a swing or javafx window.
	 * 
	 * @return The window containing the table if working with a gui.
	 */
	public MarsTableWindow getWindow() {
		return win;
	}

	/**
	 * Sets a reference to the window holding the table. This could be a swing or
	 * javafx window.
	 * 
	 * @param win The window containing the table if working with a gui.
	 * @return A reference to the MarsTableWindow provided.
	 */
	public MarsTableWindow setWindow(MarsTableWindow win) {
		this.win = win;
		return win;
	}

	/**
	 * Returns an array of double values for the column given. All rows will have
	 * an entry in the array even if they have NaN values.
	 * 
	 * @param column Header of the column to retrieve.
	 * @return Array of double values for the column. With NaN values included.
	 */
	public double[] getColumnAsDoubles(String column) {
		if (hasColumn(column) && get(column) instanceof DoubleColumn) {
			DoubleColumn dCol = (DoubleColumn) get(column);
			return dCol.copyArray();
		}
		return new double[0];
	}

	/**
	 * Returns an array of double values for the column given. NaN values are
	 * ignored.
	 * 
	 * @param column Header of the column to retrieve.
	 * @return Array of double values for the column with NaN values removed.
	 */
	public double[] getColumnAsDoublesNoNaNs(String column) {
		if (hasColumn(column) && get(column) instanceof DoubleColumn) {
			ArrayList<Double> values = new ArrayList<>();
			DoubleColumn dCol = (DoubleColumn) get(column);
			double[] backingArray = dCol.getArray();

			for (int row = 0; row < getRowCount(); row++) {
				if (Double.isNaN(backingArray[row])) continue;

				values.add(backingArray[row]);
			}

			return values.stream().mapToDouble(i -> i).toArray();
		}
		else {
			return new double[0];
		}
	}

	/**
	 * Returns an array of double values for the column within the range given for
	 * a rowSelectionColumn (inclusive of bounds). NaN values are not included in
	 * the list.
	 * 
	 * @param column Header of the column to retrieve.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return Array of double values for the column within the range given. NaN
	 *         values removed.
	 */
	public double[] getColumnAsDoublesNoNaNs(String column,
		String rowSelectionColumn, double lowerBound, double upperBound)
	{
		if (hasColumn(column) && hasColumn(rowSelectionColumn) && get(
			column) instanceof DoubleColumn && get(
				rowSelectionColumn) instanceof DoubleColumn)
		{
			ArrayList<Double> values = new ArrayList<>();
			double[] backingArrayColumn = ((DoubleColumn) get(column)).getArray();
			double[] backingArrayRowSelectionColumn = ((DoubleColumn) get(
				rowSelectionColumn)).getArray();

			for (int row = 0; row < getRowCount(); row++) {
				if (Double.isNaN(backingArrayColumn[row])) continue;

				if (backingArrayRowSelectionColumn[row] >= lowerBound &&
					backingArrayRowSelectionColumn[row] <= upperBound) values.add(
						backingArrayColumn[row]);
			}

			return values.stream().mapToDouble(i -> i).toArray();
		}
		else {
			return new double[0];
		}
	}

	/**
	 * Returns a comma delimited string of the values in the row specified.
	 * 
	 * @param row The row index to retrieve as a string.
	 * @return Comma delimited string containing the row items.
	 */
	public String getRowAsString(int row) {
		if ((row < 0) || (row >= getRowCount())) throw new IllegalArgumentException(
			"Row out of range: " + row);
		if (sb == null) sb = new StringBuilder(200);
		else sb.setLength(0);
		for (int i = 0; i < getColumnCount(); i++) {
			String value = String.valueOf(get(i, row));
			if (value.contains(",")) value = "\"" + value + "\"";
			sb.append(value);
			if (i != getColumnCount() - 1) sb.append(',');
		}
		return new String(sb);
	}

	/**
	 * Returns the row specified as a List of Objects. These could be Doubles or
	 * Strings.
	 * 
	 * @param row The row index to retrieve as a string.
	 * @return Object list of items in each column for the row index provided.
	 */
	public List<Object> getRowAsList(int row) {
		if ((row < 0) || (row >= getRowCount())) throw new IllegalArgumentException(
			"Row out of range: " + row);
		List<Object> rowList = new ArrayList<>();
		rowList.add(row);
		for (int i = 0; i < getColumnCount(); i++)
			rowList.add(get(i, row));
		return rowList;
	}

	/* 
	 * IMPORT AND EXPORT
	 */

	/**
	 * Saves the table to the file path in smile encoded json format.
	 * 
	 * @param file File where the table is written.
	 * @return Return true if no IO error is encountered.
	 */
	public boolean saveAs(File file) {
		try {
			if (!file.getAbsolutePath().toLowerCase().endsWith(".yamt")) file =
				new File(file.getAbsolutePath() + ".yamt");
			saveAsYAMT(file.getAbsolutePath());
			return true;
		}
		catch (IOException e) {
			return false;
		}
	}

	/**
	 * Saves the table to the string file path specified in csv format.
	 * 
	 * @param path The string path for saving.
	 * @throws IOException Thrown if unable to write the file.
	 */
	public void saveAsCSV(String path) throws IOException {
		if (getRowCount() == 0) return;

		if (!path.endsWith(".csv")) {
			path += ".csv";
		}

		PrintWriter pw;
		FileOutputStream fos = new FileOutputStream(path);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		pw = new PrintWriter(bos);

		// Now let's build the header
		StringBuilder sb = new StringBuilder(200);
		String heading;
		for (int i = 0; i < getColumnCount(); i++) {
			heading = getColumnHeader(i);
			sb.append(heading);
			if (i != getColumnCount() - 1) sb.append(',');
		}
		pw.println(new String(sb));

		for (int i = 0; i < getRowCount(); i++)
			pw.println(getRowAsString(i));
		pw.close();
	}

	/**
	 * JSON serialization of table values. Includes schema with column type
	 * definitions of either string or number. values specified in records format,
	 * with each row an object containing column:value pairs.
	 * 
	 * @param jGenerator JsonGenerator stream the table should be serialized to.
	 * @throws IOException Thrown if unable to write to the JsonGenerator stream.
	 */
	@Override
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeStartObject();
		if (getColumnCount() > 0) {
			// First we need to write the table schema
			jGenerator.writeObjectFieldStart("schema");
			jGenerator.writeArrayFieldStart("fields");
			for (int i = 0; i < getColumnCount(); i++) {
				jGenerator.writeStartObject();
				jGenerator.writeStringField("name", getColumnHeader(i));
				if (get(i) instanceof GenericColumn) {
					jGenerator.writeStringField("type", "string");
				}
				else if (get(i) instanceof DoubleColumn) {
					jGenerator.writeStringField("type", "number");
				}
				jGenerator.writeEndObject();
			}
			jGenerator.writeEndArray();
			jGenerator.writeEndObject();

			// writeDataAsRowObjectArray(jGenerator);

			// Actual table data
			if (jGenerator instanceof SmileGenerator) writeDataAsGzippedBlocks(
				jGenerator);
			else writeDataAsRowObjectArray(jGenerator);
		}
		jGenerator.writeEndObject();
	}

	private void writeDataAsGzippedBlocks(JsonGenerator jGenerator)
		throws IOException
	{
		jGenerator.writeObjectFieldStart("data");
		String blockName = "DoubleBlock,GZIP,dims=[" + stream().filter(
			c -> c instanceof DoubleColumn).count() + "," + getRowCount() + "]";
		jGenerator.writeBinaryField(blockName, buildDataBlock());

		// Write GenericColumns as arrays of Strings
		for (int i = 0; i < getColumnCount(); i++)
			if (get(i) instanceof GenericColumn) {
				jGenerator.writeArrayFieldStart(getColumnHeader(i));
				GenericColumn col = (GenericColumn) get(i);
				for (int row = 0; row < getRowCount(); row++)
					jGenerator.writeString((String) col.getValue(row));
				jGenerator.writeEndArray();
			}
		jGenerator.writeEndObject();
	}

	private byte[] buildDataBlock() throws IOException {
		long colCount = stream().filter(c -> c instanceof DoubleColumn).count();
		ByteBuffer byteBuffer = ByteBuffer.allocate((int) colCount * getRowCount() *
			8);

		DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
		for (int i = 0; i < getColumnCount(); i++)
			if (get(i) instanceof DoubleColumn) doubleBuffer.put(((DoubleColumn) get(
				i)).getArray(), 0, getRowCount());

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GzipParameters parameters = new GzipParameters();
		parameters.setCompressionLevel(Deflater.DEFAULT_COMPRESSION);
		GzipCompressorOutputStream deflater = new GzipCompressorOutputStream(out,
			parameters);
		deflater.write(byteBuffer.array());
		deflater.close();

		return out.toByteArray();
	}

	private void writeDataAsRowObjectArray(JsonGenerator jGenerator)
		throws IOException
	{
		jGenerator.writeArrayFieldStart("data");
		for (int row = 0; row < getRowCount(); row++) {
			jGenerator.writeStartObject();
			for (int col = 0; col < getColumnCount(); col++) {
				if (get(col) instanceof GenericColumn) jGenerator.writeStringField(
					getColumnHeader(col), (String) get(col, row));
				else if (get(col) instanceof DoubleColumn &&
					decimalPlacePrecision != -1) jGenerator.writeNumberField(
						getColumnHeader(col), MarsMath.round((double) get(col, row),
							decimalPlacePrecision));
				else if (get(col) instanceof DoubleColumn) jGenerator.writeNumberField(
					getColumnHeader(col), (double) get(col, row));
			}
			jGenerator.writeEndObject();
		}
		jGenerator.writeEndArray();
	}

	/**
	 * JSON deserialization of table values. Schema is used to determine column
	 * type of either DoubleColumn or GenericColumn. Record objects are added as
	 * the stream is parsed. The Double value types of NaN, Infinity, -Infinity
	 * are serialized and deserialized.
	 * 
	 * @param jParser JsonParser stream to read objects and fields from.
	 * @throws IOException Thrown if unable to read from the JsonParser stream.
	 */
	@Override
	public void fromJSON(JsonParser jParser) throws IOException {
		// Then we move through fields
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
			String fieldName_L1 = jParser.getCurrentName();

			if ("schema".equals(fieldName_L1)) {
				// First we move past object start
				jParser.nextToken();

				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					String fieldName_L2 = jParser.getCurrentName();
					if ("fields".equals(fieldName_L2)) {
						// Have to move past array start
						jParser.nextToken();

						String columnName = "";
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							while (jParser.nextToken() != JsonToken.END_OBJECT) {
								String fieldName_L3 = jParser.getCurrentName();

								if ("name".equals(fieldName_L3)) {
									jParser.nextToken();
									columnName = jParser.getText();
								}

								if ("type".equals(fieldName_L3)) {
									jParser.nextToken();

									if ("number".equals(jParser.getText())) {
										add(new DoubleColumn(columnName));
									}
									else if ("string".equals(jParser.getText())) {
										add(new GenericColumn(columnName));
									}
								}
							}
						}
					}
				}
			}

			if ("data".equals(fieldName_L1)) {
				jParser.nextToken();

				if (jParser.currentToken() == JsonToken.START_ARRAY)
					readDataAsRowObjectArray(jParser);
				else if (jParser.currentToken() == JsonToken.START_OBJECT)
					readDataBlockAndStringArrays(jParser);
			}
		}
	}

	private void readDataAsRowObjectArray(JsonParser jParser) throws IOException {
		while (jParser.nextToken() != JsonToken.END_ARRAY) {
			appendRow();
			int rowIndex = getRowCount() - 1;
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String colName = jParser.getCurrentName();

				// move to value token
				jParser.nextToken();
				if (get(colName) instanceof DoubleColumn) {
					if (jParser.getCurrentToken().equals(JsonToken.VALUE_STRING)) {
						String str = jParser.getValueAsString();
						if (Objects.equals(str, "Infinity")) {
							setValue(colName, rowIndex, Double.POSITIVE_INFINITY);
						}
						else if (Objects.equals(str, "-Infinity")) {
							setValue(colName, rowIndex, Double.NEGATIVE_INFINITY);
						}
						else if (Objects.equals(str, "NaN")) {
							setValue(colName, rowIndex, Double.NaN);
						}
					}
					else {
						setValue(colName, rowIndex, jParser.getDoubleValue());
					}
				}
				else if (get(colName) instanceof GenericColumn) {
					setValue(colName, rowIndex, jParser.getValueAsString());
				}
			}
		}
	}

	private void readDataBlockAndStringArrays(JsonParser jParser)
		throws IOException
	{
		int rows = -1;
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
			String fieldName = jParser.getCurrentName();

			if (fieldName.startsWith("DoubleBlock,GZIP,dims=[")) {
				String dimensions = fieldName.substring(23, fieldName.length() - 1);
				int cols = Integer.parseInt(dimensions.substring(0, dimensions.indexOf(
					",")));
				rows = Integer.parseInt(dimensions.substring(dimensions.indexOf(",") + 1
				));

				jParser.nextToken();
				byte[] binaryDataBlock = jParser.getBinaryValue();
				ByteArrayInputStream input = new ByteArrayInputStream(binaryDataBlock);
				GzipCompressorInputStream inflater = new GzipCompressorInputStream(
					input);
				DataInputStream dis = new DataInputStream(inflater);

				ByteBuffer buffer = ByteBuffer.allocate(cols * rows * 8);
				dis.readFully(buffer.array());
				DoubleBuffer dubBuf = buffer.asDoubleBuffer();
				List<DoubleColumn> doubleColumnList = new ArrayList<>();
				stream().filter(c -> c instanceof DoubleColumn).forEach(
					col -> doubleColumnList.add((DoubleColumn) col));
				for (int col = 0; col < cols; col++) {
					double[] colData = new double[rows];
					dubBuf.get(colData);
					doubleColumnList.get(col).fill(colData);
				}
				dis.close();
			}

			if (hasColumn(fieldName)) {
				GenericColumn column = (GenericColumn) get(fieldName);
				int rowNum = 0;
				jParser.nextToken();
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					column.add(jParser.getText());
					rowNum++;
				}
				if (rows == -1) rows = rowNum;
			}
		}
		setRowCount(rows);
	}

	/**
	 * Saves the table to the file path specified in json format.
	 * 
	 * @param path String path for writing.
	 * @throws IOException Thrown if unable to write to the path given.
	 */
	public void saveAsJSON(String path) throws IOException {
		if (getRowCount() == 0) return;

		if (!path.endsWith(".json")) {
			path += ".json";
		}

		OutputStream stream = new BufferedOutputStream(Files.newOutputStream(new File(path).toPath()));

		JsonGenerator jGenerator;
		JsonFactory jFactory = new JsonFactory();
		jGenerator = jFactory.createGenerator(stream, JsonEncoding.UTF8);

		toJSON(jGenerator);

		jGenerator.close();

		// flush and close streams...
		stream.flush();
		stream.close();
	}

	/**
	 * Saves the table to the file path specified in yamt format. This is a smile
	 * encoded json file.
	 * 
	 * @param path String path for writing.
	 * @throws IOException Thrown if unable to save to path given.
	 */
	public void saveAsYAMT(String path) throws IOException {
		if (getRowCount() == 0) return;

		if (!path.endsWith(".yamt")) {
			path += ".yamt";
		}

		OutputStream stream = new BufferedOutputStream(Files.newOutputStream(new File(path).toPath()));

		JsonGenerator jGenerator;
		JsonFactory jFactory = new SmileFactory();
		jGenerator = jFactory.createGenerator(stream);

		toJSON(jGenerator);

		jGenerator.close();

		// flush and close streams...
		stream.flush();
		stream.close();
	}

	private MarsTable loadCSV(File file) {
		String absolutePath = file.getAbsolutePath();
		double size_in_bytes = file.length();
		double readPosition = 0;
		final String lineSeparator = "\n";
		int currentPercentDone = 0;
		int currentPercent;

		Path path = Paths.get(absolutePath);
		boolean csv = absolutePath.endsWith(".csv") || absolutePath.endsWith(
			".CSV");
		String cellSeparator = csv ? "," : "\t";

		try (BufferedReader br = Files.newBufferedReader(path,
			StandardCharsets.UTF_8))
		{
			String header = br.readLine();
			readPosition += header.getBytes().length + lineSeparator
				.getBytes().length;
			String[] headings = header.split(cellSeparator);

			int firstColumn = headings.length > 0 && headings[0].equals(" ") ? 1 : 0;

			for (int i = firstColumn; i < headings.length; i++) {
				headings[i] = headings[i].trim();
			}

			boolean[] stringColumn = new boolean[headings.length];

			int row = 0;
			for (String line; (line = br.readLine()) != null;) {
				String[] items = line.split(cellSeparator);

				// During the first cycle we need to build the table with columns that
				// are either DoubleColumns or GenericColumns for numbers or strings
				// We need to detect this by what is in the first row.
				if (row == 0) {
					for (int i = firstColumn; i < headings.length; i++) {
						if (items[i].equals("NaN") || items[i].equals("-Infinity") ||
							items[i].equals("Infinity"))
						{
							// This should be a DoubleColumn
							add(new DoubleColumn(headings[i]));
							stringColumn[i] = false;
						}
						else {
							double value = Double.NaN;
							try {
								value = Double.parseDouble(items[i]);
							}
							catch (NumberFormatException ignored) {}

							if (Double.isNaN(value)) {
								add(new GenericColumn(headings[i]));
								stringColumn[i] = true;
							}
							else {
								add(new DoubleColumn(headings[i]));
								stringColumn[i] = false;
							}
						}
					}
				}

				appendRow();
				for (int i = firstColumn; i < headings.length; i++) {
					if (stringColumn[i]) {
						setValue(i - firstColumn, row, items[i].trim());
					}
					else {
						double value = Double.NaN;
						try {
							value = Double.parseDouble(items[i]);
						}
						catch (NumberFormatException ignored) {}

						setValue(i - firstColumn, row, value);
					}
				}
				if (statusService != null) {
					readPosition += line.getBytes().length + lineSeparator
						.getBytes().length;
					currentPercent = (int) Math.round(readPosition * 1000 /
						size_in_bytes);
					if (currentPercent > currentPercentDone) {
						currentPercentDone = currentPercent;
						statusService.showStatus(currentPercent, 1000, "Opening file " +
							file.getName());
					}
				}
				row++;
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		if (statusService != null) {
			statusService.showProgress(100, 100);
			statusService.showStatus("Opening file " + file.getName() + " - Done!");
		}

		return this;
	}

	private MarsTable loadJSON(File file) throws IOException {
		InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()));

		this.name = file.getName();

		// Here we automatically detect the format of the JSON file
		// Can be JSON text or Smile encoded binary file...
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory();
		DataFormatDetector det = new DataFormatDetector(jsonF,
				smileF);
		DataFormatMatcher match = det.findFormat(inputStream);
		JsonParser jParser = match.createParserWithMatch();

		fromJSON(jParser);

		jParser.close();
		inputStream.close();

		return this;
	}

	@Override
	/*
	  Returns a JSON string representation of the table.
	 */
	public String toString() {
		return name;
	}

	/**
	 * Set the double value for a pair of column and row indices. Sets a double
	 * value for a DoubleColumn and a String value for a GenericColumn.
	 * 
	 * @param col Index of the column that contains the value to set.
	 * @param row Index of the row that contains the value to set.
	 * @param value The new value to set at the position given.
	 */
	public void setValue(int col, int row, double value) {
		setValue(getColumnHeader(col), row, value);
	}

	/**
	 * Set the String value for a pair of column and row indices. Sets a String
	 * value for a GenericColumn and tries to convert to a double value for a
	 * DoubleColumn. If conversion is not possible an NaN value is set.
	 * 
	 * @param col Index of the column that contains the value to set.
	 * @param row Index of the row that contains the value to set.
	 * @param value The new String value to set at the position given.
	 */
	public void setValue(int col, int row, String value) {
		((GenericColumn) get(getColumnHeader(col))).set(row, value);
	}

	/**
	 * Set the double value for the column heading and row index specified. Sets a
	 * double value for a DoubleColumn and a String value for a GenericColumn.
	 * 
	 * @param column Heading of the column that contains the value to set.
	 * @param row Index of the row that contains the value to set.
	 * @param value The new double value to set at the position given.
	 */
	public void setValue(String column, int row, double value) {
		if (!hasColumn(column)) {
			DoubleColumn col = new DoubleColumn(column);
			for (int i = 0; i < getRowCount(); i++) {
				if (i == row) col.add(value);
				else col.add(Double.NaN);
			}
			add(col);
		}
		else if (get(column) instanceof DoubleColumn) ((DoubleColumn) get(column))
			.set(row, value);
		else if (get(column) instanceof GenericColumn) {
			String str = String.valueOf(value);
			((GenericColumn) get(column)).set(row, str);
		}
	}

	/**
	 * Set the String value for the column heading and row index specified. Sets a
	 * String value for a GenericColumn and attempts to convert to a double value
	 * for DoubleColumns. If conversion is not possible an NaN value is set.
	 * 
	 * @param column Heading of the column that contains the value to set.
	 * @param row Index of the row that contains the value to set.
	 * @param value The new double value to set at the position given.
	 */
	public void setValue(String column, int row, String value) {
		if (!hasColumn(column)) {
			GenericColumn col = new GenericColumn(column);
			for (int i = 0; i < getRowCount(); i++) {
				if (i == row) col.add(value);
				else col.add("");
			}
			add(col);
		}
		else if (get(column) instanceof GenericColumn) ((GenericColumn) get(column))
			.set(row, value);
		else if (get(column) instanceof DoubleColumn) {
			double num = Double.NaN;
			try {
				num = Double.parseDouble(value);
			}
			catch (NumberFormatException e) {
				// Do nothing.. set NaN as value...
			}
			((DoubleColumn) get(column)).set(row, num);
		}
	}

	/**
	 * Returns the double value at the column and row indices specified. NaN is
	 * returned if the position given does not exist.
	 * 
	 * @param col Index of the column that contains the value.
	 * @param row Index of the row that contains the value.
	 * @return The value at the specified col and row indices.
	 */
	public double getValue(int col, int row) {
		try {
			return ((DoubleColumn) get(col)).get(row);
		}
		catch (ClassCastException e1) {
			if (get(col) instanceof GenericColumn) return Double.parseDouble((String) get(
				col).get(row));
			return Double.NaN;
		}
	}

	/**
	 * Returns the double value at the column header and row index specified. NaN
	 * is returned if the position given does not exist. If the column is of type
	 * GenericColumn conversion to double is attempted. If conversion is not
	 * possible NaN is returned;
	 * 
	 * @param column Header of the column that contains the value.
	 * @param row Index of the row that contains the value.
	 * @return The double value at the column header and row index specified.
	 */
	public double getValue(String column, int row) {
		try {
			return ((DoubleColumn) get(column)).get(row);
		}
		catch (ClassCastException e1) {
			if (get(column) instanceof GenericColumn) return Double.parseDouble(
				(String) get(column).get(row));
			return Double.NaN;
		}
	}

	/**
	 * Returns the string value for the column header and row index specified. If
	 * the column type is DoubleColumn the double value is converted to a string.
	 * 
	 * @param column Header of the column that contains the value.
	 * @param row Index of the row that contains the value.
	 * @return The String value at the column header and row index specified.
	 */
	public String getStringValue(String column, int row) {
		try {
			return (String) get(column).get(row);
		}
		catch (ClassCastException e1) {
			if (get(column) instanceof DoubleColumn) return String.valueOf(get(column)
				.get(row));
			return null;
		}
	}

	/**
	 * Returns the string value at the column and row indices specified. If the
	 * column type is DoubleColumn the double value is converted to a string.
	 * 
	 * @param col Index of the column that contains the value.
	 * @param row Index of the row that contains the value.
	 * @return The double value at the column and row indices specified.
	 */
	public String getStringValue(int col, int row) {
		try {
			return (String) get(col, row);
		}
		catch (ClassCastException e1) {
			if (get(col) instanceof DoubleColumn) return String.valueOf(get(col,
				row));
			return null;
		}
	}

	/**
	 * Returns true if the table contains a column with the name specified.
	 * 
	 * @param colName String column header to check for.
	 * @return Returns true if the table contains the column.
	 */
	public boolean hasColumn(String colName) {
		for (String column : getColumnHeadingList()) {
			if (column.equals(colName)) return true;
		}
		return false;
	}

	/**
	 * Finds the maximum of the column values. NaN values are ignored.
	 * 
	 * @param column name of the column.
	 * @return maximum value in the column values. NaN is returned if all values
	 *         are NaN or the column does not exist.
	 */
	public double max(String column) {
		if (!hasColumn(column)) return Double.NaN;
		double max = Double.MIN_VALUE;
		for (double value : (DoubleColumn) get(column)) {
			if (Double.isNaN(value)) continue;
			if (max < value) max = value;
		}
		if (max == Double.MIN_VALUE) return Double.NaN;
		return max;
	}

	/**
	 * Finds the max of the values for the maxColumn within the range given for a
	 * rowSelectionColumn (inclusive of bounds). NaN values are ignored. If no
	 * values exist for the bounds provided NaN is returned.
	 * 
	 * @param maxColumn name of the column used for finding the max.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return max of the column values. NaN is returned if all values are NaN or
	 *         one of the columns does not exist.
	 */
	public double max(String maxColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		if (!hasColumn(maxColumn) || !hasColumn(rowSelectionColumn))
			return Double.NaN;
		double max = Double.MIN_VALUE;
		for (int row = 0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(maxColumn, row))) continue;

			if (getValue(rowSelectionColumn, row) >= lowerBound && getValue(
				rowSelectionColumn, row) <= upperBound && max < getValue(maxColumn,
					row)) max = getValue(maxColumn, row);
		}
		if (max == Double.MIN_VALUE) return Double.NaN;
		return max;
	}

	/**
	 * Finds the minimum of the column values. NaN values are ignored.
	 * 
	 * @param column name of the column.
	 * @return minimum value in the column values. NaN is returned if all values
	 *         are NaN or the column does not exist.
	 */
	public double min(String column) {
		if (!hasColumn(column)) return Double.NaN;
		double min = Double.MAX_VALUE;
		for (double value : (DoubleColumn) get(column)) {
			if (Double.isNaN(value)) continue;
			if (min > value) min = value;
		}
		if (min == Double.MAX_VALUE) return Double.NaN;
		return min;
	}

	/**
	 * Finds the min of the values for the maxColumn within the range given for a
	 * rowSelectionColumn (inclusive of bounds). NaN values are ignored. If no
	 * values exist for the bounds provided NaN is returned.
	 * 
	 * @param minColumn name of the column used for finding the min.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return min of the column values. NaN is returned if all values are NaN or
	 *         one of the columns does not exist.
	 */
	public double min(String minColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		if (!hasColumn(minColumn) || !hasColumn(rowSelectionColumn))
			return Double.NaN;
		double min = Double.MAX_VALUE;
		for (int row = 0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(minColumn, row))) continue;

			if (getValue(rowSelectionColumn, row) >= lowerBound && getValue(
				rowSelectionColumn, row) <= upperBound && min > getValue(minColumn,
					row)) min = getValue(minColumn, row);
		}
		if (min == Double.MAX_VALUE) return Double.NaN;
		return min;
	}

	/**
	 * Calculates the mean of the values for the column given. NaN values are
	 * ignored.
	 * 
	 * @param column name of the column.
	 * @return mean value of the column values. NaN is returned if all values are
	 *         NaN or the column does not exist.
	 */
	public double mean(String column) {
		return StatUtils.mean(this.getColumnAsDoublesNoNaNs(column));
	}

	/**
	 * Calculates the mean of the values for the meanColumn within the range given
	 * for a rowSelectionColumn (inclusive of bounds). NaN values are ignored. If
	 * no values exist for the bounds provided NaN is returned.
	 * 
	 * @param meanColumn name of the column used for calculating the mean.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return mean value of the column values. NaN is returned if all values are
	 *         NaN or one of the columns does not exist.
	 */
	public double mean(String meanColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		return StatUtils.mean(this.getColumnAsDoublesNoNaNs(meanColumn,
			rowSelectionColumn, lowerBound, upperBound));
	}

	/**
	 * Calculates the sum of the values for the column given. NaN values are
	 * ignored.
	 * 
	 * @param column name of the column.
	 * @return sum value of the column values. NaN is returned if all values are
	 *         NaN or the column does not exist.
	 */
	public double sum(String column) {
		return StatUtils.sum(this.getColumnAsDoublesNoNaNs(column));
	}

	/**
	 * Calculates the sum of the values for the sumColumn within the range given
	 * for a rowSelectionColumn (inclusive of bounds). NaN values are ignored. If
	 * no values exist for the bounds provided NaN is returned.
	 * 
	 * @param sumColumn name of the column used for calculating the mean.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return sum value of the column values. NaN is returned if all values are
	 *         NaN or one of the columns does not exist.
	 */
	public double sum(String sumColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		return StatUtils.sum(this.getColumnAsDoublesNoNaNs(sumColumn,
			rowSelectionColumn, lowerBound, upperBound));
	}

	/**
	 * Calculates the median of the column values. NaN values are ignored.
	 * 
	 * @param column name of the column.
	 * @return median value of the column values. NaN is returned if all values
	 *         are NaN or the column does not exist.
	 */
	public double median(String column) {
		if (!hasColumn(column)) return Double.NaN;
		List<Double> values = new ArrayList<>();
		for (int i = 0; i < getRowCount(); i++) {
			if (Double.isNaN(getValue(column, i))) continue;
			values.add(getValue(column, i));
		}
		Collections.sort(values);

		if (values.size() == 0) return Double.NaN;

		double median;
		if (values.size() % 2 == 0) median = (values.get(values.size() / 2) + values
			.get(values.size() / 2 - 1)) / 2;
		else median = values.get(values.size() / 2);

		return median;
	}

	/**
	 * Finds the median of the values for the medianColumn within the range given
	 * for a rowSelectionColumn (inclusive of bounds). NaN values are ignored. If
	 * no values exist for the bounds provided NaN is returned.
	 * 
	 * @param medianColumn name of the column used for finding the median.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return median value of the column values. NaN is returned if all values
	 *         are NaN or one of the columns does not exist.
	 */
	public double median(String medianColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		if (!hasColumn(medianColumn) || !hasColumn(rowSelectionColumn))
			return Double.NaN;
		List<Double> values = new ArrayList<>();
		for (int row = 0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(medianColumn, row))) continue;

			if (getValue(rowSelectionColumn, row) >= lowerBound && getValue(
				rowSelectionColumn, row) <= upperBound) values.add(getValue(
					medianColumn, row));
		}
		if (values.size() == 0) return Double.NaN;

		Collections.sort(values);
		double median;
		if (values.size() % 2 == 0) median = (values.get(values.size() / 2) + values
			.get(values.size() / 2 - 1)) / 2;
		else median = values.get(values.size() / 2);

		return median;
	}

	/**
	 * Calculates the standard deviation of the column. NaN values are ignored.
	 * 
	 * @param column name of the column.
	 * @return standard deviation of the column values. NaN is returned if all
	 *         values are NaN or the column does not exist.
	 */
	public double std(String column) {
		StandardDeviation standardDeviation = new StandardDeviation();
		return standardDeviation.evaluate(this.getColumnAsDoublesNoNaNs(column));
	}

	/**
	 * Calculates the standard deviation of the values for the stdColumn within
	 * the range given for a rowSelectionColumn (inclusive of bounds). NaN values
	 * are ignored. If no values exist for the bounds provided NaN is returned.
	 * 
	 * @param stdColumn name of the column to use for the standard deviation
	 *          calculation.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return standard deviation of the stdColumn for the rowSelection. NaN is
	 *         returned if all values are NaN or one of the columns does not
	 *         exist.
	 */
	public double std(String stdColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		StandardDeviation standardDeviation = new StandardDeviation();
		return standardDeviation.evaluate(this.getColumnAsDoublesNoNaNs(stdColumn,
			rowSelectionColumn, lowerBound, upperBound));
	}

	/**
	 * Calculates the median absolute deviation. NaN values are ignored.
	 *
	 * @param column name of the column.
	 * @return median absolute deviation of the column values. NaN is returned if
	 *         all values are NaN or the column does not exist.
	 */
	public double mad(String column) {
		if (!hasColumn(column)) return Double.NaN;
		double median = median(column);

		List<Double> medianDevs = new ArrayList<>();

		for (int row = 0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(column, row))) continue;

			medianDevs.add(Math.abs(median - getValue(column, row)));
		}

		if (medianDevs.size() == 0) return Double.NaN;

		// Now find median of the deviations
		Collections.sort(medianDevs);

		double medianDev;
		if (medianDevs.size() % 2 == 0) medianDev = (medianDevs.get(medianDevs
			.size() / 2) + medianDevs.get(medianDevs.size() / 2 - 1)) / 2;
		else medianDev = medianDevs.get(medianDevs.size() / 2);

		return medianDev;
	}

	/**
	 * Calculates the median absolute deviation of the values for the madColumn
	 * within the range given for a rowSelectionColumn (inclusive of bounds). NaN
	 * values are ignored. If no values exist for the bounds provided NaN is
	 * returned.
	 * 
	 * @param madColumn Name of the column used to calculate the median absolute
	 *          deviation.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return standard deviation of the stdColumn for the rowSelection. NaN is
	 *         returned if all values are NaN or one of the columns does not
	 *         exist.
	 */
	public double mad(String madColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		if (!hasColumn(madColumn) || !hasColumn(rowSelectionColumn))
			return Double.NaN;
		double median = median(madColumn, rowSelectionColumn, lowerBound,
			upperBound);

		ArrayList<Double> medianDevs = new ArrayList<>();
		for (int row = 0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(madColumn, row))) continue;

			if (getValue(rowSelectionColumn, row) >= lowerBound && getValue(
				rowSelectionColumn, row) <= upperBound)
			{
				medianDevs.add(Math.abs(median - getValue(madColumn, row)));
			}
		}

		if (medianDevs.size() == 0) return Double.NaN;

		// Now find median of the deviations
		Collections.sort(medianDevs);

		double medianDev;
		if (medianDevs.size() % 2 == 0) medianDev = (medianDevs.get(medianDevs
			.size() / 2) + medianDevs.get(medianDevs.size() / 2 - 1)) / 2;
		else medianDev = medianDevs.get(medianDevs.size() / 2);

		return medianDev;
	}

	/**
	 * Calculates the standard error of the mean for the column given. NaN values
	 * are ignored.
	 * 
	 * @param column Name of the column used to calculate the median absolute
	 *          deviation.
	 * @return The standard error of the mean for the column given. NaN is
	 *         returned if all values are NaN or one the column does not exist.
	 */
	public double sem(String column) {
		StandardDeviation standardDeviation = new StandardDeviation();
		double[] values = this.getColumnAsDoublesNoNaNs(column);
		return standardDeviation.evaluate(values) / Math.sqrt(values.length);
	}

	/**
	 * Calculates the standard error of the mean for the meanColumn within the
	 * range given for a rowSelectionColumn (inclusive of bounds). NaN values are
	 * ignored. If no values exist for the bounds provided NaN is returned.
	 * 
	 * @param meanColumn Name of the column used to calculate the standard error
	 *          of the mean.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return Standard error of the mean. NaN is returned if all values are NaN
	 *         or one of the columns does not exist.
	 */
	public double sem(String meanColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		StandardDeviation standardDeviation = new StandardDeviation();
		double[] values = this.getColumnAsDoublesNoNaNs(meanColumn,
			rowSelectionColumn, lowerBound, upperBound);
		return standardDeviation.evaluate(values) / Math.sqrt(values.length);
	}

	/**
	 * Calculates the variance for the column given. NaN values are ignored.
	 * 
	 * @param varianceColumn Name of the column used to calculate the variance.
	 * @return The variance for the column given. NaN is returned if all values
	 *         are NaN or the column does not exist.
	 */
	public double variance(String varianceColumn) {
		return StatUtils.populationVariance(this.getColumnAsDoublesNoNaNs(
			varianceColumn));
	}

	/**
	 * Calculates the variance for the varianceColumn within the range given for a
	 * rowSelectionColumn (inclusive of bounds). NaN values are ignored. If no
	 * values exist for the bounds provided NaN is returned.
	 * 
	 * @param varianceColumn Name of the column used to calculate the variance.
	 * @param rowSelectionColumn name of the column used for filtering a range of
	 *          values.
	 * @param lowerBound smallest value included in the row selection range.
	 * @param upperBound largest value included in the row selection range.
	 * @return Variance. NaN is returned if all values are NaN or one of the
	 *         columns does not exist.
	 */
	public double variance(String varianceColumn, String rowSelectionColumn,
		double lowerBound, double upperBound)
	{
		return StatUtils.populationVariance(this.getColumnAsDoublesNoNaNs(
			varianceColumn, rowSelectionColumn, lowerBound, upperBound));
	}

	/**
	 * Calculates the linear fit given an xColumn and yColumn pair. NaN values are
	 * ignored. y = A + Bx A = output[0] +/- output[1] B = output[2] +/- output[3]
	 * Standard error is reported.
	 * 
	 * @param xColumn Name of the column containing the x values.
	 * @param yColumn Name of the column containing the y values.
	 * @return Array with fit result.
	 */
	public double[] linearRegression(String xColumn, String yColumn) {
		SimpleRegression linearFit = new SimpleRegression(true);
		if (!hasColumn(xColumn) || !hasColumn(yColumn)) return new double[] {
			Double.NaN, Double.NaN, Double.NaN, Double.NaN };

		// Is linearFit.evaluate with arrays faster ???
		for (int row = 0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(xColumn, row)) || Double.isNaN(getValue(yColumn,
				row))) continue;
			linearFit.addData(getValue(xColumn, row), getValue(yColumn, row));
		}

		return new double[] { linearFit.getIntercept(), linearFit
			.getInterceptStdErr(), linearFit.getSlope(), linearFit.getSlopeStdErr() };
	}

	/**
	 * Calculates the linear fit given an xColumn and yColumn pair. NaN values are
	 * ignored. y = A + Bx A = output[0] +/- output[1] B = output[2] +/- output[3]
	 * Standard error is reported.
	 * 
	 * @param xColumn Name of the column containing the x values.
	 * @param yColumn Name of the column containing the y values.
	 * @param lowerBound LowerBound of fitting region in x values.
	 * @param upperBound UpperBound of fitting region in x values.
	 * @return Array with fit result.
	 */
	public double[] linearRegression(String xColumn, String yColumn,
		double lowerBound, double upperBound)
	{
		SimpleRegression linearFit = new SimpleRegression(true);
		if (!hasColumn(xColumn) || !hasColumn(yColumn)) return new double[] {
			Double.NaN, Double.NaN, Double.NaN, Double.NaN };

		// Is linearFit.evaluate with arrays faster ???
		for (int row = 0; row < getRowCount(); row++) {
			if (Double.isNaN(getValue(xColumn, row)) || Double.isNaN(getValue(yColumn,
				row))) continue;

			if (getValue(xColumn, row) >= lowerBound && getValue(xColumn,
				row) <= upperBound) linearFit.addData(getValue(xColumn, row), getValue(
					yColumn, row));
		}

		return new double[] { linearFit.getIntercept(), linearFit
			.getInterceptStdErr(), linearFit.getSlope(), linearFit.getSlopeStdErr() };
	}

	// Should additional linearRegression methods that take and return
	// SimpleRegression objects to allow more flexibility be added?

	/**
	 * Sort the table in ascending order on one or more columns.
	 * 
	 * @param columns Comma separated list of columns to sort by.
	 * @return MarsTable for next operation.
	 */
	public MarsTable sort(String... columns) {
		return sort(true, columns);
	}

	/**
	 * Sort the table on one or more columns either in ascending or descending
	 * order.
	 * 
	 * @param ascending Determines sort order.
	 * @param columns Comma separated list of columns to sort by.
	 * @return MarsTable for next operation.
	 */
	public MarsTable sort(final boolean ascending, String... columns) {
		final int[] columnIndexes = new int[columns.length];

		for (int i = 0; i < columns.length; i++)
			columnIndexes[i] = getColumnIndex(columns[i]);

		new ResultsTableList(this).sort((o1, o2) -> {
			for (int columnIndex : columnIndexes) {
				int groupDifference = 0;
				if (get(columnIndex) instanceof DoubleColumn) groupDifference = Double
						.compare(o1.getValue(columnIndex), o2.getValue(columnIndex));
				else if (get(columnIndex) instanceof GenericColumn) groupDifference =
						StringUtils.compare(o1.getStringValue(columnIndex), o2
								.getStringValue(columnIndex));

				if (groupDifference != 0) return ascending ? groupDifference
						: -groupDifference;
			}
			return 0;
		});

		return this;
	}

	/**
	 * Returns a stream of MarsTableRow. This is useful for performing operations
	 * on all rows using Consumers.
	 * 
	 * @return A stream of MarsTableRows.
	 */
	public Stream<MarsTableRow> rows() {
		Iterator<MarsTableRow> iterator = new Iterator<MarsTableRow>() {

			final private MarsTableRow row = new MarsTableRow(MarsTable.this);

			@Override
			public MarsTableRow next() {
				return row.next();
			}

			@Override
			public boolean hasNext() {
				return row.hasNext();
			}
		};

		Iterable<MarsTableRow> iterable = () -> iterator;
		return StreamSupport.stream(iterable.spliterator(), false);
	}

	/**
	 * Add MarsTableRow to the end of the table. Assumes the table has all the
	 * columns listed in the row.
	 * 
	 * @param row A MarsTableRow that should be added to the end of the table.
	 * @return MarsTable for next operation.
	 */
	public MarsTable addRow(MarsTableRow row) {
		appendRow();
		for (String colHeader : getColumnHeadingList()) {
			Column<?> column = this.get(colHeader);

			if (column instanceof DoubleColumn) {
				setValue(colHeader, getRowCount() - 1, row.getValue(colHeader));
			}

			if (column instanceof GenericColumn) {
				setValue(colHeader, getRowCount() - 1, row.getStringValue(colHeader));
			}
		}
		return this;
	}

	/**
	 * Remove rows at the positions specified in the ordered list given.
	 * 
	 * @param rows The list of rows to remove.
	 * @return MarsTable for next operation.
	 */
	public MarsTable deleteRows(int[] rows) {
		if (rows.length == 0) return this;

		int pos = 0;
		int rowsIndex = 0;
		for (int i = 0; i < getRowCount(); i++) {
			if (rowsIndex < rows.length) {
				if (rows[rowsIndex] == i) {
					rowsIndex++;
					continue;
				}
			}

			if (pos != i) {
				// means we need to move row i to position row.
				for (int j = 0; j < getColumnCount(); j++)
					set(j, pos, get(j, i));
			}
			pos++;
		}

		// delete last rows
		for (int row = getRowCount() - 1; row > pos - 1; row--)
			removeRow(row);

		return this;
	}

	/**
	 * Remove rows at the positions specified in the ordered list given.
	 * 
	 * @param rows An ArrayList containing the rows to remove.
	 * @return MarsTable for next operation.
	 */
	public MarsTable deleteRows(List<Integer> rows) {
		if (rows.size() == 0) return this;

		int pos = 0;
		int rowsIndex = 0;
		for (int i = 0; i < getRowCount(); i++) {
			if (rowsIndex < rows.size()) {
				if (rows.get(rowsIndex) == i) {
					rowsIndex++;
					continue;
				}
			}

			if (pos != i) {
				// means we need to move row i to position row.
				for (int j = 0; j < getColumnCount(); j++)
					set(j, pos, get(j, i));
			}
			pos++;
		}

		// delete last rows
		for (int row = getRowCount() - 1; row > pos - 1; row--)
			removeRow(row);

		return this;
	}

	/**
	 * Remove rows at the positions specified in the ordered list given.
	 * 
	 * @param rows The list of rows to remove.
	 * @return MarsTable for next operation.
	 */
	public MarsTable keepRows(int[] rows) {
		if (rows.length == 0) return this;

		int rowsIndex = 0;
		for (int row = 0; row < getRowCount(); row++) {
			if (rowsIndex < rows.length) {
				if (rows[rowsIndex] == row) {
					// means we need to move row i to position row.
					for (int j = 0; j < getColumnCount(); j++)
						set(j, rowsIndex, get(j, row));
					rowsIndex++;
				}
			}
		}

		// delete last rows
		for (int row = getRowCount() - 1; row > rows.length - 1; row--)
			removeRow(row);

		return this;
	}

	/**
	 * Keep rows at the positions specified in the ordered list given. Remove all
	 * other rows.
	 * 
	 * @param rows A List containing the rows to keep.
	 * @return MarsTable for next operation.
	 */
	public MarsTable keepRows(List<Integer> rows) {
		if (rows.size() == 0) {
			// Then we remove all rows...
			// Maybe we just need to change size...
			// This will work for now...
			for (int row = getRowCount() - 1; row > rows.size() - 1; row--)
				removeRow(row);
			return this;
		}

		int rowsIndex = 0;
		for (int row = 0; row < getRowCount(); row++) {
			if (rowsIndex < rows.size()) {
				if (rows.get(rowsIndex) == row) {
					// means we need to move row i to position row.
					for (int j = 0; j < getColumnCount(); j++)
						set(j, rowsIndex, get(j, row));
					rowsIndex++;
				}
			}
		}

		// delete last rows
		for (int row = getRowCount() - 1; row > rows.size() - 1; row--)
			removeRow(row);

		return this;
	}

	@Override
	protected DoubleColumn createColumn(final String header) {
		return new DoubleColumn(header);
	}

	/**
	 * Get the record in Json string format.
	 * 
	 * @return Json string representation of the record.
	 */
	@Override
	public String dumpJSON() {
		return MarsUtil.dumpJSON(this::toJSON);
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	/**
	 * Create a copy of the MarsTable.
	 * 
	 * @return A copy of the MarsTable.
	 */
	@Override
	public MarsTable clone() {
		MarsTable table = new MarsTable(this.getName());
		for (int col = 0; col < getColumnCount(); col++) {
			if (get(col) instanceof DoubleColumn) {
				DoubleColumn column = new DoubleColumn(get(col).getHeader());
				for (int row = 0; row < getRowCount(); row++)
					column.add(getValue(col, row));

				table.add(column);
			}
			else if (get(col) instanceof GenericColumn) {
				GenericColumn column = new GenericColumn(get(col).getHeader());
				for (int row = 0; row < getRowCount(); row++)
					column.add(getStringValue(col, row));

				table.add(column);
			}
		}
		return table;
	}

	// These classes are used for sorting in place. They
	// may be replaced with a different sort implementation in future releases.
	// But the external API will not need to change.
	private static class ResultsTableList extends AbstractList<Row> {

		private final MarsTable table;

		public ResultsTableList(MarsTable table) {
			this.table = table;
		}

		@Override
		public Row get(int row) {
			return new Row(row, table);
		}

		@Override
		public Row set(int row, Row values) {
			Row old = get(row);

			for (int colIndex = 0; colIndex < table.getColumnCount(); colIndex++) {
				Column<?> column = table.get(colIndex);

				if (column instanceof DoubleColumn) {
					table.setValue(column.getHeader(), row, values.getValue(column
						.getHeader()));
				}

				if (column instanceof GenericColumn) {
					table.setValue(column.getHeader(), row, values.getStringValue(column
						.getHeader()));
				}
			}

			return old;
		}

		@Override
		public int size() {
			return table.getRowCount();
		}
	}

	private static class Row {

		private final Map<String, Double> doubleValues = new HashMap<>();
		private final Map<String, String> stringValues = new HashMap<>();

		private final MarsTable table;

		Row(int row, MarsTable table) {
			this.table = table;

			for (int colIndex = 0; colIndex < table.getColumnCount(); colIndex++) {
				Column<?> column = table.get(colIndex);

				if (column instanceof DoubleColumn) {
					doubleValues.put(column.getHeader(), table.getValue(colIndex, row));
				}

				if (column instanceof GenericColumn) {
					stringValues.put(column.getHeader(), table.getStringValue(colIndex,
						row));
				}
			}
		}

		double getValue(String column) {
			return doubleValues.get(column);
		}

		double getValue(int colIndex) {
			return doubleValues.get(table.getColumnHeader(colIndex));
		}

		String getStringValue(String column) {
			return stringValues.get(column);
		}

		String getStringValue(int colIndex) {
			return stringValues.get(table.getColumnHeader(colIndex));
		}
	}

	@Override
	public void setShowWarnings(boolean showWarnings) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setJsonField(String field,
		ThrowingConsumer<JsonGenerator, IOException> output,
		ThrowingConsumer<JsonParser, IOException> input)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public Predicate<JsonGenerator> getJsonGenerator(String field) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Predicate<JsonParser> getJsonParser(String field) {
		// TODO Auto-generated method stub
		return null;
	}
}
