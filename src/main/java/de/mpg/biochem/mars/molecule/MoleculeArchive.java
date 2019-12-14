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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.table.MarsTable;

public interface MoleculeArchive<M extends Molecule, I extends MarsImageMetadata, P extends MoleculeArchiveProperties> extends JsonConvertibleRecord {
	
	/**
	 * Rebuild all indexes by inspecting the contents of store directories. 
	 * Then save the new indexes to the indexes.json file in the store. 
	 * 
	 * @throws IOException if something goes wrong saving the indexes.
	 */
	void rebuildIndexes() throws IOException;
	
	/**
	 * Saves the MoleculeAchive to the file from which it was opened.
	 * 
	 * @throws IOException if something goes wrong saving the data.
	 */
	void save() throws IOException;
	
	/**
	 * Saves MoleculeAchive to the given file destination. 
	 * 
	 * @param file a yama file destination. If the .yama is not present it will be added.
	 * @throws IOException if something goes wrong saving the data.
	 */
	void saveAs(File file) throws IOException;
	
	/**
	 * Creates the directory given and a virtual store inside. 
	 * Rebuilds indexes in the process if the archive was loaded
	 * from a virtual store.
	 * 
	 * @param virtualDirectory a directory destination for the virtual store.
	 * @throws IOException if something goes wrong creating the virtual store.
	 */
	void saveAsVirtualStore(File virtualDirectory) throws IOException;
	
	/**
	 * Adds a molecule to the archive. If a molecule with the same UID 
	 * is already in the archive, the record is updated.
	 * 
	 * All indexes are updated with the properties of the molecule added.
	 * 
	 * @param molecule a record to add or update.
	 */
	void put(M molecule);
	
	/**
	 * Adds an ImageMetadata record to the archive. If an ImageMetadata record with 
	 * the same UID is already in the archive, the record is updated.
	 * 
	 * All indexes are updated with the properties of the ImageMetadata record added.
	 * 
	 * @param metadata an ImageMetadata record to add or update.
	 */
	void putImageMetadata(I metadata);
	
	/**
	 * The ImageMetadata record with the UID given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param metaUID the UID of the ImageMetadata record to remove.
	 */
	void removeImageMetadata(String metaUID);

	/**
	 * The ImageMetadata record given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param meta ImageMetadata record to remove.
	 */
	void removeImageMetadata(I meta);
	
	/**
	 * Retrieves an MARSImageMetadata record.
	 * 
	 * @param index The index of the MARSImageMetadata record to retrieve.
	 * @return A MARSImageMetadata record.
	 */
	I getImageMetadata(int index);
	
	/**
	 * Retrieves a MARSImageMetadata record.
	 * 
	 * @param metaUID The UID of the MARSImageMetadata record to retrieve.
	 * @return A MARSImageMetadata record.
	 */
	I getImageMetadata(String metaUID);
	
	/**
	 * Retrieves the list of UIDs of all MARSImageMetadata records.
	 * Useful for stream().forEach(...) operations.
	 * 
	 * @return The list of all MARImageMetadata UIDs.
	 */
	ArrayList<String> getImageMetadataUIDs();
	
	/**
	 * Number of molecule records in the MoleculeArchive.
	 * 
	 * @return The integer number of molecule records.
	 */
	int getNumberOfMolecules();
	
	/**
	 * Number of MARSImageMetadata records in the MoleculeArchive.
	 * 
	 * @return The integer number of MARSImageMetadata records.
	 */
	int getNumberOfImageMetadataRecords();
	
	/**
	 * Location of the virtual store.
	 * 
	 * @return The String absolute path of the open virtual store.
	 */
	String getStoreLocation();
	
	/**
	 * Global comments.
	 * 
	 * @return The global comments String.
	 */
	String getComments();
	
	/**
	 * Sets the global comments. This replaces all current 
	 * comments with those given.
	 * 
	 * @param comments A string of global comments to set.
	 */
	void setComments(String comments);
	
	/**
	 * True if the archive is virtual, false if not.
	 * 
	 * @return A boolean which is true if working from a virtual store.
	 */
	boolean isVirtual();

	/**
	 * Retrieves the molecule record at the provided index.
	 * 
	 * @param index The integer index position of the molecule record.
	 * @return A Molecule record.
	 */
	M get(int index);
	
	/**
	 * Removes the molecule record with the given UID.
	 * 
	 * @param UID The UID of the molecule record to remove.
	 */
	void remove(String UID);
	
	/**
	 * Removes the molecule record provided.
	 * 
	 * @param molecule The molecule record to remove.
	 */
	void remove(M molecule);
	
	/**
	 * Retrieves the list of UIDs for all Molecule records. 
	 * Useful for stream().forEach(...) operations.
	 * 
	 * @return The list with all Molecule UIDs.
	 */
	ArrayList<String> getMoleculeUIDs();
	
	/**
	 * Comma separated list of tags for the molecule with the given UID.
	 * 
	 * @param UID The UID of the molecule to retrieve the tag list for.
	 * @return A String containing a comma separated list of tags.
	 */
	String getTagList(String UID);
	
	/**
	 * Tags for the molecule with the given UID.
	 * 
	 * @param UID The UID of the molecule to retrieve the tag set for.
	 * @return A set containing all tags for the given molecule.
	 */
	LinkedHashSet<String> getTagSet(String UID);
	
	/**
	 * Comma separated list of tags for the MARSImageMetadata record with the given UID.
	 * 
	 * @param UID The UID of the MARSImageMetadata record to retrieve the tag list for.
	 * @return A String containing a comma separated list of tags.
	 */
	String getImageMetadataTagList(String UID);
	
	/**
	 * Tags for the MARSImageMetadata record with the given UID.
	 * 
	 * @param UID The UID of the MARSImageMetadata record to retrieve the tag list for.
	 * @return The set of tags for the given MARSImageMetadata record.
	 */
	LinkedHashSet<String> getImageMetadataTagSet(String UID);
	
	/**
	 * Saves a molecule record as a json file.
	 * 
	 * @param directory The directory to save the file in.
	 * @param molecule The molecule record to save.
	 * @param jfactory the JsonFactory to use when saving. 
	 * Determines if smile or text encoding is used.
	 * 
	 * @throws IOException if the molecule can't be saved to the file given.
	 */
	void saveMoleculeToFile(File directory, M molecule, JsonFactory jfactory) throws IOException;
	
	/**
	 * Saves a MARSImageMetadata record as a json file.
	 * 
	 * @param directory The directory to save the file in.
	 * @param imageMetadata The MARSImageMetadata record to save.
	 * @param jfactory the JsonFactory to use when saving. 
	 * Determines if smile or text encoding is used.
	 * 
	 * @throws IOException if the MARSImageMetadata can't be saved to the file given.
	 */
	void saveImageMetadataToFile(File directory, I imageMetadata, JsonFactory jfactory) throws IOException;
	
	/**
	 * Check if a molecule record has a tag. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the molecule to check for the tag.
	 * @param tag The tag to check for.
	 * @return Returns true if the molecule has the tag and false if not.
	 */
	boolean moleculeHasTag(String UID, String tag);
	
	/**
	 * Check if a molecule record has tags. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the molecule to check.
	 * @return Returns true if the molecule has tags and false if not.
	 */
	boolean moleculeHasTags(String UID);
	
	/**
	 * Check if a MARSImageMetadata record has a tag. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the MARSImageMetadata record to check for the tag.
	 * @param tag The tag to check for.
	 * @return Returns true if the MARSImageMetadata record has the tag and false if not.
	 */
	boolean imageMetadataHasTag(String UID, String tag);

	/**
	 * Removes all molecule records with the tag provided.
	 * 
	 * @param tag Molecule records with this tag will be removed.
	 */
	void deleteMoleculesWithTag(String tag);
	
	/**
	 * Removes all MARSImageMetadata records with the tag provided.
	 * 
	 * @param tag MARSImageMetadata records with this tag will be removed.
	 */
	void deleteImageMetadataRecordsWithTag(String tag);
	
	/**
	 * Used to check if there is a molecule record with the UID given.
	 * 
	 * @param UID Check for a molecule record with this UID.
	 * @return True if the archive contains the molecule record 
	 * with the provided UID and false if not.
	 */
	boolean contains(String UID);
	
	/**
	 * Used to check if there is a MARSImageMetadata record with the UID given.
	 * 
	 * @param UID Check for a MARSImageMetadata record with this UID.
	 * @return True if the archive contains a MARSImageMetadata record 
	 * with the provided UID and false if not.
	 */
	boolean containsImageMetadataRecord(String UID);

	/**
	 * Get the molecule record with the given UID.
	 * 
	 * @param UID The UID of the record to retrieve.
	 * @return The Molecule record with the UID given 
	 * or null if none is located.
	 */
	M get(String UID);
	
	/**
	 * Get the index position of the UID given.
	 * 
	 * @param UID The UID to find the index location for.
	 * @return The Integer location in the index of
	 * the UID provided.
	 */
	int getIndex(String UID);
	
	/**
	 * Get the UID of the MARSImageMetadata for a molecule record. If 
	 * working from a virtual store, this will use an index providing
	 * optimal performance. If working in memory this is the same as
	 * retrieving the molecule record and the ImageMetadata UID from 
	 * it directly.
	 * 
	 * @param UID The UID of the molecule to get the MARSImageMetadata UID for.
	 * @return The UID string of the MARSImageMetadata record corresponding to the
	 * molecule record whose UID was provided.
	 */
	String getImageMetadataUIDforMolecule(String UID);
	
	/**
	 * Get the UID at the provided index location.
	 * 
	 * @param index Retrieve the UID at this index location.
	 * @return The UID at the index location provided.
	 */
	String getUIDAtIndex(int index);
	
	/**
	 * Get the ImageMetadata UID at the provided index location.
	 * 
	 * @param index Retrieve the ImageMetadata UID at this index location.
	 * @return The ImageMetadata UID at the index location provided.
	 */
	String getImageMetadataUIDAtIndex(int index);
	
	/**
	 * Returns the File from which the archive was opened.
	 * 
	 * @return The File the archive was opened from.
	 */
	File getFile();
	
	/**
	 * Set the file the archive should save to.
	 * 
	 * @return The File the archive was opened from.
	 */
	void setFile(File file);

	/**
	 * Set the name of the archive.
	 * 
	 * @param name The new name of the archive.
	 */
	void setName(String name);
	
	/**
	 * Get the name of the archive.
	 * 
	 * @return The String name of the archive.
	 */
	String getName();

	/**
	 * Returns the MoleculeArchiveWindow holding this archive, if one exists.
	 * Otherwise, null is returned.
	 * 
	 * @return The MoleculeArchiveWindow containing this archive.
	 */
	MoleculeArchiveWindow getWindow();
	
	/**
	 * Set the window containing this archive.
	 * 
	 * @param win Set the MoleculeArchiveWindow that contains this archive.
	 */
	void setWindow(MoleculeArchiveWindow win);
	
	/**
	 * Lock the archive window during processing, if one exists.
	 */
	void lock();
	
	/**
	 * Unlock the archive window after processing is done, if one exists.
	 */
	void unlock();
	
	/**
	 * Set json output format to SMILE. See Jackson JSON for further details.
	 */
	 void setSMILEOutputEncoding();
	
	/**
	 * Set json output format to text.
	 */
	void unsetSMILEOutputEncoding();
	
	/**
	 * Check if SMILE is the output encoding.
	 * 
	 * @return True if SMILE is the output encoding, false if not.
	 */
	boolean isSMILEOutputEncoding();
	
	/**
	 * Check if SMILE is the input encoding when the archive was opened.
	 * 
	 * @return True if SMILE was the input encoding, false if not.
	 */
	boolean isSMILEInputEncoding();
	
	/**
	 * Natural Order Sort all Molecule UIDs in the index. Run after adding new
	 * records or after recovery to ensure the molecule records preserve an order.
	 */
	void naturalOrderSortMoleculeIndex();
	
	/**
	 * Add a Log message to all MARSImageMetadata records. Used by all processing plugins 
	 * so there is a record of the sequence of processing steps during analysis.
	 * 
	 * @param message The String message to add to all MARSImageMetadata logs.
	 */
	void addLogMessage(String message);
	
	MoleculeArchiveProperties getProperties();
	
	MoleculeArchiveService getMoleculeArchiveService();
	
	void updateProperties();
	
	P createProperties();
	
	P createProperties(JsonParser jParser) throws IOException;
	
	I createImageMetadata(JsonParser jParser) throws IOException;
	
	I createImageMetadata(String metaUID);
	
	M createMolecule();
	
	M createMolecule(JsonParser jParser) throws IOException;
	
	M createMolecule(String UID);
	
	M createMolecule(String UID, MarsTable table);
}
