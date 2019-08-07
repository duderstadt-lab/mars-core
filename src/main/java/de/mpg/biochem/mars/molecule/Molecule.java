package de.mpg.biochem.mars.molecule;

import java.util.ArrayList;
import java.util.Set;

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.table.MarsTable;

public interface Molecule extends JsonConvertibleRecord, MarsRecord {
	
	void setImageMetadataUID(String imageMetadataUID);
	
	/**
	 * Get the UID of the {@link MarsImageMetadata} record associated with
	 * this molecule. The {@link MarsImageMetadata} contains information about
	 * the data collection (Timing of frames, colors, collection date, etc...)
	 * 
	 * @return Return a JSON string representation of the molecule.
	 */
	String getImageMetadataUID();
		
	/**
	 * Add or update a Segments table ({@link MarsTable}) generated 
	 * using the yColumnName and xColumnName. The {@link KCPCommand} performs
	 * kinetic change point analysis generating segments to fit regions
	 * of a trace. The information about these segments is added using
	 * this method.
	 * 
	 * @param xColumnName The name of the column used for x for KCP analysis.
	 * @param yColumnName The name of the column used for y for KCP analysis.
	 * @param segs The {@link MarsTable} to add that contains the 
	 * segments.
	 */
	void putSegmentsTable(String xColumnName, String yColumnName, MarsTable segs);
	
	/**
	 * Retrieve a Segments table ({@link MarsTable}) generated 
	 * using xColumnName and yColumnName.
	 * 
	 * @param xColumnName The name of the x column used for analysis.
	 * @param yColumnName The name of the y column used for analysis.
	 * @return The MARSResultsTable generated using the columns specified.
	 */	
	MarsTable getSegmentsTable(String xColumnName, String yColumnName);
	
	/**
	 * Check if record has a Segments table ({@link MarsTable}) generated 
	 * using xColumnName and yColumnName.
	 * 
	 * @param xColumnName The name of the x column used for analysis.
	 * @param yColumnName The name of the y column used for analysis.
	 * @return Boolean whether the segment table exists.
	 */	
	boolean hasSegmentsTable(String xColumnName, String yColumnName);
	
	/**
	 * Retrieve a Segments table ({@link MarsTable}) generated 
	 * using yColumnName and xColumnName provided in index positions 0
	 * and 1 of an ArrayList, respectively.
	 * 
	 * @param tableColumnNames The xColumnName and yColumnName used when
	 * generating the table, provided in index positions 0 and 1 of an 
	 * ArrayList, respectively.
	 * @return The MARSResultsTable generated using the columns specified.
	 */	
	MarsTable getSegmentsTable(ArrayList<String> tableColumnNames);
	
	/**
	 * Remove the Segments table ({@link MarsTable}) generated 
	 * using yColumnName and xColumnName.
	 * 
	 * @param xColumnName The name of the x column used for analysis.
	 * @param yColumnName The name of the y column used for analysis.
	 */
	void removeSegmentsTable(String xColumnName, String yColumnName);
	
	/**
	 * Retrieve a Segments table ({@link MarsTable}) generated 
	 * using yColumnName and xColumnName.
	 * 
	 * @return The Set of ArrayLists holding the x and y column names at
	 * index positions 0 and 1, respectively.
	 */
	Set<ArrayList<String>> getSegmentTableNames();
}
