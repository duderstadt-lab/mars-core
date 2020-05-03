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
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.scijava.Context;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.table.MarsTable;

/**
 * MoleculeArchives are the primary storage structure of Mars datasets. MoleculeArchives provides an optimal structure 
 * for storing single-molecule time-series data. Time-series data for each molecule in a dataset are 
 * stored in the form of {@link Molecule} records, which may also contain calculated parameters, tags, 
 * notes, and kinetic change point segments. These records are assigned a UID string at the time of creation.
 * This string provides univeral molecule uniqueness throughout all datasets. MoleculeArchives 
 * contain a collection of molecule records associated with a given experimental condition or analysis 
 * pipeline.
 * <p>
 * {@link MarsMetadata} records containing data collection information are also stored 
 * in MoleculeArchives. They are identified using metaUID strings. {@link Molecule} records 
 * associated with a given data collection have a metaUID string linking them
 * to the correct {@link MarsMetadata} record within the same MoleculeArchive. 
 * 
 * Global properties of the MoleculeArchive, including indexing, comments, etc.., are stored 
 * in a {@link MoleculeArchiveProperties} record also contained within the MoleculeArchive. 
 * <p>
 * See {@link AbstractMoleculeArchive} for further information.
 * </p>
 * @author Karl Duderstadt
 * @param <M> Molecule type.
 * @param <I> MarsMetadata type.
 * @param <P> MoleculeArchiveProperties type.
 */
public interface MoleculeArchive<M extends Molecule, I extends MarsMetadata, P extends MoleculeArchiveProperties> extends JsonConvertibleRecord {
	
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
	 * Adds an MarsMetadata record to the archive. If an MarsMetadata record with 
	 * the same UID is already in the archive, the record is updated.
	 * 
	 * All indexes are updated with the properties of the MarsMetadata record added.
	 * 
	 * @param metadata an metadata record to add or update.
	 */
	void putMetadata(I metadata);
	
	/**
	 * The metadata record with the UID given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param metaUID the UID of the metadata record to remove.
	 */
	void removeMetadata(String metaUID);

	/**
	 * The metadata record given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param meta metadata record to remove.
	 */
	void removeMetadata(I meta);
	
	/**
	 * Retrieves an metadata record.
	 * 
	 * @param index The index of the metadata record to retrieve.
	 * @return A metadata record.
	 */
	I getMetadata(int index);
	
	/**
	 * Retrieves a metadata record.
	 * 
	 * @param metaUID The UID of the metadata record to retrieve.
	 * @return A metadata record.
	 */
	I getMetadata(String metaUID);
	
	/**
	 * Retrieves the list of UIDs of all metadata records.
	 * Useful for stream().forEach(...) operations.
	 * 
	 * @return The list of all metadata UIDs.
	 */
	ArrayList<String> getMetadataUIDs();
	
	/**
	 * Number of molecule records in the MoleculeArchive.
	 * 
	 * @return The integer number of molecule records.
	 */
	int getNumberOfMolecules();
	
	/**
	 * Number of metadata records in the MoleculeArchive.
	 * 
	 * @return The integer number of metadata records.
	 */
	int getNumberOfMetadatas();
	
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
	 * Comma separated list of tags for the metadata record with the given UID.
	 * 
	 * @param UID The UID of the metadata record to retrieve the tag list for.
	 * @return A String containing a comma separated list of tags.
	 */
	String getMetadataTagList(String UID);
	
	/**
	 * Tags for the metadata record with the given UID.
	 * 
	 * @param UID The UID of the metadata record to retrieve the tag list for.
	 * @return The set of tags for the given metadata record.
	 */
	LinkedHashSet<String> getMetadataTagSet(String UID);
	
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
	 * Retrieve the list of tags for a molecule. Will retrieve
	 * the list from the index if working in virtual memory.
	 * 
	 * @param UID The UID of the molecule to retrieve the tags of.
	 * @return Returns the set of for the molecule with UID.
	 */
	public LinkedHashSet<String> moleculeTags(String UID);
	
	/**
	 * Check if a MARSImageMetadata record has a tag. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the MARSImageMetadata record to check for the tag.
	 * @param tag The tag to check for.
	 * @return Returns true if the MARSImageMetadata record has the tag and false if not.
	 */
	boolean metadataHasTag(String UID, String tag);

	/**
	 * Removes all molecule records with the tag provided.
	 * 
	 * @param tag Molecule records with this tag will be removed.
	 */
	void deleteMoleculesWithTag(String tag);
	
	/**
	 * Removes all metadata records with the tag provided.
	 * 
	 * @param tag metadata records with this tag will be removed.
	 */
	void deleteMetadataRecordsWithTag(String tag);
	
	/**
	 * Used to check if there is a molecule record with the UID given.
	 * 
	 * @param UID Check for a molecule record with this UID.
	 * @return True if the archive contains the molecule record 
	 * with the provided UID and false if not.
	 */
	boolean contains(String UID);
	
	/**
	 * Used to check if there is a metadata record with the UID given.
	 * 
	 * @param UID Check for a metadata record with this UID.
	 * @return True if the archive contains a metadata record 
	 * with the provided UID and false if not.
	 */
	boolean containsMetadata(String UID);

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
	 * Convenience method to retrieve a Molecule stream. Can be used to 
	 * iterate over all molecules using forEach.
	 * 
	 * @return Molecule stream.
	 */
	public Stream<M> molecules();
	
	/**
	 * Convenience method to retrieve a metadata stream. Can be used to 
	 * iterate over all metadata using forEach.
	 * 
	 * @return Metadata stream.
	 */
	public Stream<I> metadata();
	
	/**
	 * Convenience method to retrieve a multithreated Molecule stream. Can be used to 
	 * iterate over all molecules using forEach in a multithreaded manner. 
	 * 
	 * @return Molecule stream.
	 */
	public Stream<I> parallelMetadata();
	
	/**
	 * Convenience method to retrieve a multithreated Molecule stream. Can be used to 
	 * iterate over all molecules using forEach in a multithreaded manner. 
	 * 
	 * @return Molecule stream.
	 */
	public Stream<M> parallelMolecules();
	
	/**
	 * Convenience method to execute an action on all molecules using a Consumer.
	 * 
	 * @param action Action to perform on all molecules.
	 */
	public void forEach(Consumer<? super Molecule> action);
	
	/**
	 * Get the UID of the metadata for a molecule record. If 
	 * working from a virtual store, this will use an index providing
	 * optimal performance. If working in memory this is the same as
	 * retrieving the molecule record and the metadata UID from 
	 * it directly.
	 * 
	 * @param UID The UID of the molecule to get the metadata UID for.
	 * @return The UID string of the metadata record corresponding to the
	 * molecule record whose UID was provided.
	 */
	String getMetadataUIDforMolecule(String UID);
	
	/**
	 * Get the UID at the provided index location.
	 * 
	 * @param index Retrieve the UID at this index location.
	 * @return The UID at the index location provided.
	 */
	String getUIDAtIndex(int index);
	
	/**
	 * Get the metadata UID at the provided index location.
	 * 
	 * @param index Retrieve the metadata UID at this index location.
	 * @return The metadata UID at the index location provided.
	 */
	String getMetadataUIDAtIndex(int index);
	
	/**
	 * Returns the File from which the archive was opened.
	 * 
	 * @return The File the archive was opened from.
	 */
	File getFile();
	
	/**
	 * Set the file the archive should save to.
	 * 
	 * @param file The File the archive was opened from.
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
	@Deprecated
	void addLogMessage(String message);
	
	/**
	 * Add a log message to all MarsImageMetadata records. Used by all processing plugins 
	 * so there is a record of the sequence of processing steps during analysis. Do not
	 * start a new line after adding the message.
	 * 
	 * @param message The String message to add to all MarsImageMetadata logs.
	 */
	void log(String str);
	
	/**
	 * Add a log message to all MarsImageMetadata records. Used by all processing plugins 
	 * so there is a record of the sequence of processing steps during analysis. Start
	 * a new line after adding the message.
	 * 
	 * @param message The String message to add to all MarsImageMetadata logs.
	 */
	void logln(String str);
	
	/**
	 * Get the {@link MoleculeArchiveProperties} which contain general information about the archive.
	 * This includes numbers of records, comments, and global lists of table columns, tags, and parameters. 
	 * Deprecated - use properties() instead. 
	 * 
	 * @return The {@link MoleculeArchiveProperties} for this {@link AbstractMoleculeArchive}.
	 */
	@Deprecated
	MoleculeArchiveProperties getProperties();
	
	/**
	 * Get the {@link MoleculeArchiveProperties} which contain general information about the archive.
	 * This includes numbers of records, comments, and global lists of table columns, tags, and parameters. 
	 * 
	 * @return The {@link MoleculeArchiveProperties} for this {@link AbstractMoleculeArchive}.
	 */
	MoleculeArchiveProperties properties();
	
	/**
	 * Update the {@link MoleculeArchiveProperties}. Updates the global tag 
	 * list using the tagIndex and updates the record numbers. 
	 * If in virtual mode, this saves the properties to the virtual store.
	 * 
	 * The parameter list and MarsTable column names are not updated 
	 * because in virtual mode this would require reading all records in the
	 * archive, since indexes for these items are not maintained. Therefore,
	 * the accuracy of these elements rely entirely on updates when adding
	 * and changing records.
	 * 
	 * If a complete update is required then use the {@link #rebuildIndexes()} method 
	 * or corresponding menu item in the MoleculeArchiveWindow.
	 */
	void updateProperties();
	
	/**
	 * Create empty MoleculeArchiveProperties record.
	 * 
	 * @return Empty MoleculeArchiveProperties.
	 */
	public P createProperties();
	
	/**
	 * Create MoleculeArchiveProperties record using JsonParser stream.
	 * 
	 * @param jParser JsonParser to use to create archive properties.
	 * @throws IOException Thrown if unable to read Json from JsonParser stream.
	 * @return MoleculeArchiveProperties created.
	 */
	public P createProperties(JsonParser jParser) throws IOException;
	
	/**
	 * Create MarsMetadata record using JsonParser stream.
	 * 
	 * @param jParser JsonParser to use to create metadata.
	 * @throws IOException Thrown if unable to read Json from JsonParser stream.
	 * @return MarsMetadata record created using JsonParser stream.
	 */
	public I createMetadata(final Context context, JsonParser jParser) throws IOException;
	
	/**
	 * Create empty MarsMetadata record with the metaUID specified.
	 * 
	 * @param metaUID The metaUID to use during creation of the empty MarsMetadata record.
	 * @return MarsMetadata record.
	 */
	public I createMetadata(final Context context, String metaUID);
	
	/**
	 * Create empty Molecule record.
	 * 
	 * @return Empty molecule record.
	 */
	public M createMolecule();
	
	/**
	 * Create Molecule record using the JsonParser stream given.
	 * 
	 * @param jParser JsonParser to use to create the molecule.
	 * @throws IOException Thrown if unable to read Json from JsonParser stream.
	 * @return Molecule created using the JsonParser stream.
	 */
	public M createMolecule(JsonParser jParser) throws IOException;
	
	/**
	 * Create empty Molecule record with the UID specified.
	 * 
	 * @param UID The UID to use during creation.
	 * @return Empty molecule with the UID given.
	 */
	public M createMolecule(String UID);
	
	/**
	 * Create Molecule record using the UID and {@link MarsTable} specified.
	 * 
	 * @param UID The UID to use during molecule creation.
	 * @param table The MarsTable set as the DataTable during creation.
	 * @return Molecule created.
	 */
	public M createMolecule(String UID, MarsTable table);
}
