package de.mpg.biochem.mars.molecule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.scijava.Typed;

import com.fasterxml.jackson.core.JsonFactory;
import de.mpg.biochem.mars.util.JsonConvertable;

public interface MoleculeArchive<M extends Molecule, I extends MarsImageMetadata, P extends MoleculeArchiveProperties> extends JsonConvertable {
	
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
	 * Adds an ImageMetaData record to the archive. If an ImageMetaData record with 
	 * the same UID is already in the archive, the record is updated.
	 * 
	 * All indexes are updated with the properties of the ImageMetaData record added.
	 * 
	 * @param metaData an ImageMetaData record to add or update.
	 */
	void putImageMetaData(I metaData);
	
	/**
	 * The ImageMetaData record with the UID given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param metaUID the UID of the ImageMetaData record to remove.
	 */
	void removeImageMetaData(String metaUID);

	/**
	 * The ImageMetaData record given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param meta ImageMetaData record to remove.
	 */
	void removeImageMetaData(I meta);
	
	/**
	 * Retrieves an MARSImageMetaData record.
	 * 
	 * @param index The index of the MARSImageMetaData record to retrieve.
	 * @return A MARSImageMetaData record.
	 */
	I getImageMetaData(int index);
	
	/**
	 * Retrieves a MARSImageMetaData record.
	 * 
	 * @param metaUID The UID of the MARSImageMetaData record to retrieve.
	 * @return A MARSImageMetaData record.
	 */
	I getImageMetaData(String metaUID);
	
	/**
	 * Retrieves the list of UIDs of all MARSImageMetaData records.
	 * Useful for stream().forEach(...) operations.
	 * 
	 * @return The list of all MARImageMetaData UIDs.
	 */
	ArrayList<String> getImageMetaDataUIDs();
	
	/**
	 * Number of molecule records in the MoleculeArchive.
	 * 
	 * @return The integer number of molecule records.
	 */
	int getNumberOfMolecules();
	
	/**
	 * Number of MARSImageMetaData records in the MoleculeArchive.
	 * 
	 * @return The integer number of MARSImageMetaData records.
	 */
	int getNumberOfImageMetaDataRecords();
	
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
	 * Comma separated list of tags for the MARSImageMetaData record with the given UID.
	 * 
	 * @param UID The UID of the MARSImageMetaData record to retrieve the tag list for.
	 * @return A String containing a comma separated list of tags.
	 */
	String getImageMetaDataTagList(String UID);
	
	/**
	 * Tags for the MARSImageMetaData record with the given UID.
	 * 
	 * @param UID The UID of the MARSImageMetaData record to retrieve the tag list for.
	 * @return The set of tags for the given MARSImageMetaData record.
	 */
	LinkedHashSet<String> getImageMetaDataTagSet(String UID);
	
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
	 * Saves a MARSImageMetaData record as a json file.
	 * 
	 * @param directory The directory to save the file in.
	 * @param imageMetaData The MARSImageMetaData record to save.
	 * @param jfactory the JsonFactory to use when saving. 
	 * Determines if smile or text encoding is used.
	 * 
	 * @throws IOException if the MARSImageMetaData can't be saved to the file given.
	 */
	void saveImageMetaDataToFile(File directory, I imageMetaData, JsonFactory jfactory) throws IOException;
	
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
	 * Check if a MARSImageMetaData record has a tag. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the MARSImageMetaData record to check for the tag.
	 * @param tag The tag to check for.
	 * @return Returns true if the MARSImageMetaData record has the tag and false if not.
	 */
	boolean imageMetaDataHasTag(String UID, String tag);

	/**
	 * Removes all molecule records with the tag provided.
	 * 
	 * @param tag Molecule records with this tag will be removed.
	 */
	void deleteMoleculesWithTag(String tag);
	
	/**
	 * Removes all MARSImageMetaData records with the tag provided.
	 * 
	 * @param tag MARSImageMetaData records with this tag will be removed.
	 */
	void deleteImageMetaDataRecordsWithTag(String tag);
	
	/**
	 * Used to check if there is a molecule record with the UID given.
	 * 
	 * @param UID Check for a molecule record with this UID.
	 * @return True if the archive contains the molecule record 
	 * with the provided UID and false if not.
	 */
	boolean contains(String UID);
	
	/**
	 * Used to check if there is a MARSImageMetaData record with the UID given.
	 * 
	 * @param UID Check for a MARSImageMetaData record with this UID.
	 * @return True if the archive contains a MARSImageMetaData record 
	 * with the provided UID and false if not.
	 */
	boolean containsImageMetaDataRecord(String UID);

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
	 * Get the UID of the MARSImageMetaData for a molecule record. If 
	 * working from a virtual store, this will use an index providing
	 * optimal performance. If working in memory this is the same as
	 * retrieving the molecule record and the ImageMetaData UID from 
	 * it directly.
	 * 
	 * @param UID The UID of the molecule to get the MARSImageMetaData UID for.
	 * @return The UID string of the MARSImageMetaData record corresponding to the
	 * molecule record whose UID was provided.
	 */
	String getImageMetaDataUIDforMolecule(String UID);
	
	/**
	 * Get the UID at the provided index location.
	 * 
	 * @param index Retrieve the UID at this index location.
	 * @return The UID at the index location provided.
	 */
	String getUIDAtIndex(int index);
	
	/**
	 * Get the ImageMetaData UID at the provided index location.
	 * 
	 * @param index Retrieve the ImageMetaData UID at this index location.
	 * @return The ImageMetaData UID at the index location provided.
	 */
	String getImageMetaDataUIDAtIndex(int index);
	
	/**
	 * Returns the File from which the archive was opened.
	 * 
	 * @return The File the archive was opened from.
	 */
	File getFile();

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
	 * Add a Log message to all MARSImageMetaData records. Used by all processing plugins 
	 * so there is a record of the sequence of processing steps during analysis.
	 * 
	 * @param message The String message to add to all MARSImageMetaData logs.
	 */
	void addLogMessage(String message);
	
	MoleculeArchiveProperties getProperties();
	
	void updateProperties();
}
