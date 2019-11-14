/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
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
package de.mpg.biochem.mars.molecule;

import java.util.ArrayList;
import java.util.Set;

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.PositionOfInterest;
import de.mpg.biochem.mars.util.RegionOfInterest;

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
