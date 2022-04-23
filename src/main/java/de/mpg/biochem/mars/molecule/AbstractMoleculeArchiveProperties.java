/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2022 Karl Duderstadt
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

package de.mpg.biochem.mars.molecule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.util.MarsDocument;
import de.mpg.biochem.mars.util.MarsUtil;

/**
 * Abstract superclass for MoleculeArchiveProperties objects that contain global
 * properties of the MoleculeArchive, including indexing, comments, and many
 * other global properties. Any information you would like to know without
 * having to read the entire archive record-by-record.
 * 
 * @author Karl Duderstadt
 */
public abstract class AbstractMoleculeArchiveProperties<M extends Molecule, I extends MarsMetadata>
	extends AbstractJsonConvertibleRecord implements
	MoleculeArchiveProperties<M, I>
{

	protected AtomicInteger numberOfMolecules;
	protected AtomicInteger numMetadata;
	protected String inputSchema;
	
	//Additional documents
	protected Map<String, MarsDocument> documents;
	
	//Format YYYY-MM-DD
	public static final String SCHEMA = "2022-04-11";
	private static final String COMMENTS = "Comments";

	// Sets containing global indexes for various molecule properties.
	protected Set<String> tagSet;
	protected Set<String> positionSet;
	protected Set<String> regionSet;
	protected Set<String> parameterSet;
	protected Set<String> moleculeDataTableColumnSet;
	protected Set<Integer> channelSet;
	protected Set<List<String>> moleculeSegmentTableNames;

	protected MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties<?, ?>, ? extends MoleculeArchiveIndex<?, ?>> parent;

	/**
	 * Creates an empty MoleculeArchiveProperties record.
	 */
	public AbstractMoleculeArchiveProperties() {
		super();
		numberOfMolecules = new AtomicInteger(0);
		numMetadata = new AtomicInteger(0);
		
		//Additional documents
		documents = new LinkedHashMap<String, MarsDocument>();
		
		//Initialize default Comments
		documents.put(COMMENTS, new MarsDocument(COMMENTS));
		
		tagSet = ConcurrentHashMap.newKeySet();
		positionSet = ConcurrentHashMap.newKeySet();
		regionSet = ConcurrentHashMap.newKeySet();
		channelSet = ConcurrentHashMap.newKeySet();
		parameterSet = ConcurrentHashMap.newKeySet();
		moleculeDataTableColumnSet = ConcurrentHashMap.newKeySet();
		moleculeSegmentTableNames = ConcurrentHashMap.newKeySet();
	}

	/**
	 * Constructor for loading MoleculeArchiveProperties record from a JsonParser
	 * stream. Used when archives are initially opened.
	 * 
	 * @param jParser A JsonParser at the start of the MoleculeArchiveProperties
	 *          record.
	 * @throws IOException Thrown if unable to parse Json from the JsonParser
	 *           stream.
	 */
	public AbstractMoleculeArchiveProperties(JsonParser jParser)
		throws IOException
	{
		this();
		fromJSON(jParser);
	}

	@Override
	protected void createIOMaps() {

		setJsonField("archiveType", jGenerator -> {
			if (parent != null) jGenerator.writeStringField("archiveType", parent
				.getClass().getName());
		}, null);

		setJsonField("type", jGenerator -> jGenerator.writeStringField("type", this
			.getClass().getName()), null);

		setJsonField("schema", jGenerator -> jGenerator.writeStringField("schema",
			SCHEMA), jParser -> inputSchema = jParser.getText());

		setJsonField("numberOfMolecules", jGenerator -> jGenerator.writeNumberField(
			"numberOfMolecules", numberOfMolecules.get()),
			jParser -> numberOfMolecules = new AtomicInteger(jParser
				.getValueAsInt()));

		setJsonField("numberOfMetadata", jGenerator -> jGenerator.writeNumberField(
			"numberOfMetadata", numMetadata.get()), jParser -> numMetadata =
				new AtomicInteger(jParser.getIntValue()));

		setJsonField("moleculeTableColumnSet", jGenerator -> {
			if (moleculeDataTableColumnSet.size() > 0) {
				jGenerator.writeFieldName("moleculeTableColumnSet");
				jGenerator.writeStartArray();
				Iterator<String> iterator = moleculeDataTableColumnSet.iterator();
				while (iterator.hasNext())
					jGenerator.writeString(iterator.next());
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				moleculeDataTableColumnSet.add(jParser.getText());
		});

		setJsonField("moleculeSegmentTableNames", jGenerator -> {
			if (moleculeSegmentTableNames.size() > 0) {
				jGenerator.writeFieldName("moleculeSegmentTableNames");
				jGenerator.writeStartArray();
				Iterator<List<String>> iterator = moleculeSegmentTableNames
					.iterator();
				while (iterator.hasNext()) {
					jGenerator.writeStartObject();

					List<String> SegmentTableName = iterator.next();

					jGenerator.writeStringField("yColumnName", SegmentTableName.get(0));
					jGenerator.writeStringField("xColumnName", SegmentTableName.get(1));

					jGenerator.writeEndObject();
				}
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				List<String> segemntTableName = new ArrayList<String>();
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					// Then move past field Name - yColumnName...
					jParser.nextToken();

					segemntTableName.add(jParser.getText());

					// Then move past the field and next field Name - xColumnName...
					jParser.nextToken();
					jParser.nextToken();
					segemntTableName.add(jParser.getText());
				}
				moleculeSegmentTableNames.add(segemntTableName);
			}
		});

		setJsonField("moleculeTagSet", jGenerator -> {
			if (tagSet.size() > 0) {
				jGenerator.writeFieldName("moleculeTagSet");
				jGenerator.writeStartArray();
				Iterator<String> iterator = tagSet.iterator();
				while (iterator.hasNext())
					jGenerator.writeString(iterator.next());
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				tagSet.add(jParser.getText());
		});

		setJsonField("moleculeChannelSet", jGenerator -> {
			if (channelSet.size() > 0) {
				jGenerator.writeFieldName("moleculeChannelSet");
				jGenerator.writeStartArray();
				Iterator<Integer> iterator = channelSet.iterator();
				while (iterator.hasNext())
					jGenerator.writeNumber(iterator.next());
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				channelSet.add(jParser.getIntValue());
		});

		setJsonField("moleculeParameterSet", jGenerator -> {
			if (parameterSet.size() > 0) {
				jGenerator.writeFieldName("moleculeParameterSet");
				jGenerator.writeStartArray();
				Iterator<String> iterator = parameterSet.iterator();
				while (iterator.hasNext())
					jGenerator.writeString(iterator.next());
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				parameterSet.add(jParser.getText());
		});

		setJsonField("moleculeRegionSet", jGenerator -> {
			if (regionSet.size() > 0) {
				jGenerator.writeFieldName("moleculeRegionSet");
				jGenerator.writeStartArray();
				Iterator<String> iterator = regionSet.iterator();
				while (iterator.hasNext())
					jGenerator.writeString(iterator.next());
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				regionSet.add(jParser.getText());
		});

		setJsonField("moleculePositionSet", jGenerator -> {
			if (positionSet.size() > 0) {
				jGenerator.writeFieldName("moleculePositionSet");
				jGenerator.writeStartArray();
				Iterator<String> iterator = positionSet.iterator();
				while (iterator.hasNext())
					jGenerator.writeString(iterator.next());
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				positionSet.add(jParser.getText());
		});
		
		setJsonField("documents", jGenerator -> {
			if (documents.size() > 0) {
				jGenerator.writeArrayFieldStart("documents");
				for (String name : documents.keySet())
					documents.get(name).toJSON(jGenerator);
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsDocument document = new MarsDocument(jParser);
				documents.put(document.getName(),
					document);
			}
		});

		/*
		 * 
		 * The fields below are needed for backwards compatibility.
		 * 
		 * Please remove for a future release.
		 * 
		 */

		setJsonField("Schema", null, jParser -> inputSchema = jParser.getText());

		setJsonField("MoleculeDataTableColumnSet", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				moleculeDataTableColumnSet.add(jParser.getText());
		});

		setJsonField("MoleculeSegmentTableNames", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				List<String> segemntTableName = new ArrayList<String>();
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					// Then move past field Name - yColumnName...
					jParser.nextToken();

					segemntTableName.add(jParser.getText());

					// Then move past the field and next field Name - xColumnName...
					jParser.nextToken();
					jParser.nextToken();
					segemntTableName.add(jParser.getText());
				}
				moleculeSegmentTableNames.add(segemntTableName);
			}
		});

		setJsonField("MoleculeTagSet", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				tagSet.add(jParser.getText());
		});

		setJsonField("MoleculeParameterSet", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY)
				parameterSet.add(jParser.getText());
		});

		setJsonField("numImageMetaData", null, jParser -> numMetadata =
			new AtomicInteger(jParser.getIntValue()));

		setJsonField("numImageMetadata", null, jParser -> numMetadata =
			new AtomicInteger(jParser.getIntValue()));

		setJsonField("moleculeSegmentTableNames", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				List<String> segemntTableName = new ArrayList<String>();
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					// Then move past field Name - yColumnName...
					jParser.nextToken();

					segemntTableName.add(jParser.getText());

					// Then move past the field and next field Name - xColumnName...
					jParser.nextToken();
					jParser.nextToken();
					segemntTableName.add(jParser.getText());
				}
				moleculeSegmentTableNames.add(segemntTableName);
			}
		});
		setJsonField("comments", null, jParser -> documents.put(COMMENTS, new MarsDocument(COMMENTS, jParser.getText())));
		setJsonField("Comments", null, jParser -> documents.put(COMMENTS, new MarsDocument(COMMENTS, jParser.getText())));
	}

	/**
	 * Get the Json input schema for the archive. Returns a string with the date
	 * for the schema definition using to save the archive that has just been
	 * opened. Or null if for archives created with early versions of mars-core.
	 */
	public String getInputSchema() {
		return inputSchema;
	}

	/**
	 * Used to during merge MoleculeArchive merge events to merge the properties
	 * of another archive into this one.
	 * 
	 * @param properties MoleculeArchiveProperties record to merge into this one.
	 * @param archiveName Name of the archive that is being merged with this one.
	 */
	public void merge(MoleculeArchiveProperties<M, I> properties,
		String archiveName)
	{
		this.numberOfMolecules.addAndGet(properties.getNumberOfMolecules());
		this.numMetadata.addAndGet(properties.getNumberOfMetadatas());
		this.addComment("Comments from Merged Archive " + archiveName + ":\n" +
			properties.getComments() + "\n");

		addAllTags(properties.getTagSet());
		addAllChannels(properties.getChannelSet());
		addAllParameters(properties.getParameterSet());
		addAllRegions(properties.getRegionSet());
		addAllPositions(properties.getPositionSet());
		addAllColumns(properties.getColumnSet());
		addAllSegmentsTableNames(properties.getSegmentsTableNames());
	}

	/**
	 * Add a molecule tag to the global set that contains a record of all unique
	 * tag names that are being used.
	 */
	public void addTag(String tag) {
		tagSet.add(tag);
	}

	/**
	 * Add molecule tags to the global set that contains a record of all unique
	 * tag names that are being used.
	 */
	public void addAllTags(Set<String> tags) {
		tagSet.addAll(tags);
	}

	/**
	 * Get the set of molecule tag names in use.
	 */
	public Set<String> getTagSet() {
		return tagSet;
	}

	/**
	 * Redefine the set of molecule tags in use.
	 */
	public void setTagSet(Set<String> tagSet) {
		this.tagSet = tagSet;
	}

	/**
	 * Add a channel index to the global set that contains a record of all unique
	 * indexes that are represented in the archive.
	 */
	public void addChannel(int channel) {
		channelSet.add(channel);
	}

	/**
	 * Add molecule channel indexes to the global set that contains a record of
	 * all unique channel indexes that are represented in the archive.
	 */
	public void addAllChannels(Set<Integer> channels) {
		channelSet.addAll(channels);
	}

	/**
	 * Get the set of channel indexes for molecules in the archive.
	 */
	public Set<Integer> getChannelSet() {
		return channelSet;
	}

	/**
	 * Redefine the set of molecule channels in use. Integers with an index
	 * starting at 0.
	 */
	public void setChannelSet(Set<Integer> channelSet) {
		this.channelSet = channelSet;
	}

	/**
	 * Add a molecule parameter name to the global set that contains a record of
	 * all unique parameter names that are being used.
	 */
	public void addParameter(String parameterName) {
		parameterSet.add(parameterName);
	}

	/**
	 * Add molecule parameter names to the global set that contains a record of
	 * all unique parameter names that are being used.
	 */
	public void addAllParameters(Set<String> parameters) {
		parameterSet.addAll(parameterSet);
	}

	/**
	 * Get the set of molecule parameter names in use.
	 */
	public Set<String> getParameterSet() {
		return parameterSet;
	}

	/**
	 * Redefine the set of parameter names in use.
	 */
	public void setParameterSet(Set<String> parameterSet) {
		this.parameterSet = parameterSet;
	}

	/**
	 * Add a molecule position name to the global set that contains a record of
	 * all unique position names that are being used.
	 */
	public void addPosition(String position) {
		positionSet.add(position);
	}

	/**
	 * Add molecule position names to the global set that contains a record of all
	 * unique position names that are being used.
	 */
	public void addAllPositions(Set<String> positions) {
		positionSet.addAll(positions);
	}

	/**
	 * Get the set of molecule position names in use.
	 */
	public Set<String> getPositionSet() {
		return positionSet;
	}

	/**
	 * Redefine the set of molecule position names in use.
	 */
	public void setPositionSet(Set<String> positionSet) {
		this.positionSet = positionSet;
	}

	/**
	 * Add a molecule region name to the global set that contains a record of all
	 * unique region names that are being used.
	 */
	public void addRegion(String region) {
		regionSet.add(region);
	}

	/**
	 * Add molecule region names to the global set that contains a record of all
	 * unique region names that are being used.
	 */
	public void addAllRegions(Set<String> regions) {
		regionSet.addAll(regions);
	}

	/**
	 * Get the set of molecule region names in use.
	 */
	public Set<String> getRegionSet() {
		return regionSet;
	}

	/**
	 * Redefine the set of molecule region names in use.
	 */
	public void setRegionSet(Set<String> regionSet) {
		this.regionSet = regionSet;
	}

	/**
	 * Set the number of molecule in the archive.
	 */
	public void setNumberOfMolecules(int numMolecules) {
		this.numberOfMolecules.set(numMolecules);
	}

	/**
	 * Get the number of molecule in the archive.
	 */
	public int getNumberOfMolecules() {
		return numberOfMolecules.get();
	}

	/**
	 * Set the number of MarsMetadata records in the archive.
	 */
	public void setNumberOfMetadatas(int numMetadata) {
		this.numMetadata.set(numMetadata);
	}

	/**
	 * Get the number of MarsMetadata records in the archive.
	 */
	public int getNumberOfMetadatas() {
		return numMetadata.get();
	}

	/**
	 * Add a column name to the unique set of column names in use in molecule
	 * DataTables.
	 */
	public void addColumn(String column) {
		this.moleculeDataTableColumnSet.add(column);
	}

	/**
	 * Add column names to the unique set of column names in use in molecule
	 * DataTables.
	 */
	public void addAllColumns(Set<String> columns) {
		this.moleculeDataTableColumnSet.addAll(columns);
	}

	/**
	 * Add column names to the unique set of column names in use in molecule
	 * DataTables.
	 */
	public void addAllColumns(List<String> columns) {
		this.moleculeDataTableColumnSet.addAll(columns);
	}

	/**
	 * Redefine the unique set of column names in use in molecule DataTables.
	 */
	public void setColumnSet(Set<String> moleculeDataTableColumnSet) {
		this.moleculeDataTableColumnSet = moleculeDataTableColumnSet;
	}

	/**
	 * Get the unique set of column names in use in molecule DataTables.
	 */
	public Set<String> getColumnSet() {
		return moleculeDataTableColumnSet;
	}

	/**
	 * Add a segment table name to the unique set of segment table names found in
	 * molecule records.
	 */
	public void addSegmentsTableNames(List<String> segmentTableName) {
		this.moleculeSegmentTableNames.add(segmentTableName);
	}

	/**
	 * Add segment table names to the unique set of segment table names found in
	 * molecule records.
	 */
	public void addAllSegmentsTableNames(
		Set<List<String>> segmentTableNames)
	{
		this.moleculeSegmentTableNames.addAll(segmentTableNames);
	}

	/**
	 * Redefine the unique set of segment table names found in molecule records.
	 */
	public void setSegmentsTableNames(
		Set<List<String>> moleculeSegmentTableNames)
	{
		this.moleculeSegmentTableNames = moleculeSegmentTableNames;
	}

	/**
	 * Get the unique set of segment table names found in molecule records.
	 */
	public Set<List<String>> getSegmentsTableNames() {
		return moleculeSegmentTableNames;
	}

	/**
	 * Get archive comments.
	 */
	public String getComments() {
		return (documents.containsKey(COMMENTS)) ? documents.get(COMMENTS).getContent() : "";
	}

	/**
	 * Add to archive comments.
	 */
	public void addComment(String comment) {
		synchronized (this.documents) {
			String str = (documents.containsKey(COMMENTS)) ? documents.get(COMMENTS).getContent() : "";
			documents.get(COMMENTS).setContent(str + comment);
		}
	}

	/**
	 * Overwrite archive comments with new set of comments.
	 */
	public void setComments(String comments) {
		synchronized (this.documents) {
			documents.put(COMMENTS, new MarsDocument(COMMENTS, comments));
		}
	}
	
	public void putDocument(String name, String content) {
		synchronized (documents) {
			documents.put(name, new MarsDocument(name, content));
		}
	}
	
	public void putDocument(MarsDocument document) {
		synchronized (documents) {
			documents.put(document.getName(), document);
		}
	}
	
	public MarsDocument getDocument(String name) {
		return documents.get(name);
	}
	
	public void removeDocument(String name) {
		documents.remove(name);
	}
	
	public void removeAllDocuments() {
		documents.clear();
	}

	public Set<String> getDocumentNames() {
		return documents.keySet();
	}

	public void save(File directory, JsonFactory jfactory, String fileExtension)
		throws IOException
	{
		File propertiesFile = new File(directory.getAbsolutePath() +
			"/MoleculeArchiveProperties" + fileExtension);
		MarsUtil.writeJsonRecord(this, propertiesFile, jfactory);
	}

	public void clear() {
		tagSet.clear();
		positionSet.clear();
		regionSet.clear();
		parameterSet.clear();
		moleculeDataTableColumnSet.clear();
		channelSet.clear();
		moleculeSegmentTableNames.clear();
	}

	public void addMoleculeProperties(M molecule) {
		parameterSet.addAll(molecule.getParameters().keySet());
		tagSet.addAll(molecule.getTags());
		regionSet.addAll(molecule.getRegionNames());
		positionSet.addAll(molecule.getPositionNames());
		if (molecule.getChannel() > -1) channelSet.add(molecule.getChannel());
		moleculeDataTableColumnSet.addAll(molecule.getTable()
			.getColumnHeadingList());
		moleculeSegmentTableNames.addAll(molecule.getSegmentsTableNames());
	}

	public void addMetadataProperties(I metadata) {
		// Currently nothing is indexed..
	}

	/**
	 * Set the parent MoleculeArchive that these MoleculeArchiveProperties belong
	 * to.
	 */
	public void setParent(
		MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties<?, ?>, ? extends MoleculeArchiveIndex<?, ?>> archive)
	{
		this.parent = archive;
	}
}
