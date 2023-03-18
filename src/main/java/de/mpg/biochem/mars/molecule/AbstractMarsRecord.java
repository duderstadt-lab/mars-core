/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.metadata.AbstractMarsMetadata;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsPosition;
import de.mpg.biochem.mars.util.MarsRegion;

/**
 * Abstract superclass for all {@link MarsRecord} types: {@link Molecule} and
 * {@link MarsMetadata}. All {@link MarsRecord}s have a basic set of properties
 * including a UID, notes, tags, parameters, a {@link MarsTable},
 * {@link MarsRegion}s, and {@link MarsPosition}s. {@link MarsRecord}s can be
 * serialized to and from Json.
 * <p>
 * This basic set of properties is extended for storage of molecule information
 * and metadata information in {@link Molecule}, {@link AbstractMolecule},
 * {@link MarsMetadata}, {@link AbstractMarsMetadata}.
 * </p>
 * 
 * @author Karl Duderstadt
 */
public abstract class AbstractMarsRecord extends AbstractJsonConvertibleRecord
	implements MarsRecord
{

	/**
	 * Unique ID for storage in maps and universal identification.
	 */
	private String uid;

	/**
	 * Reference to MoleculeArchive containing the record.
	 */
	protected MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties<?, ?>, ? extends MoleculeArchiveIndex<?, ?>> parent;

	private String notes;
	private final LinkedHashSet<String> tags;
	private final LinkedHashMap<String, Object> parameters;
	private final LinkedHashMap<String, MarsRegion> regionsOfInterest;
	private final LinkedHashMap<String, MarsPosition> positionsOfInterest;

	/**
	 * Constructor for creating an empty MarsRecord.
	 */
	public AbstractMarsRecord() {
		super();
		parameters = new LinkedHashMap<>();
		tags = new LinkedHashSet<>();
		regionsOfInterest = new LinkedHashMap<>();
		positionsOfInterest = new LinkedHashMap<>();
	}

	/**
	 * Constructor for creating an empty MarsRecord with the specified UID.
	 * 
	 * @param UID The unique identifier for this MarsRecord.
	 */
	public AbstractMarsRecord(String UID) {
		super();
		parameters = new LinkedHashMap<>();
		tags = new LinkedHashSet<>();
		regionsOfInterest = new LinkedHashMap<>();
		positionsOfInterest = new LinkedHashMap<>();
		this.uid = UID;

	}

	@Override
	protected void createIOMaps() {

		setJsonField("uid", jGenerator -> jGenerator.writeStringField("uid", uid),
			jParser -> uid = jParser.getText());

		setJsonField("type", jGenerator -> jGenerator.writeStringField("type", this
			.getClass().getName()), null);

		setJsonField("notes", jGenerator -> {
			if (notes != null) jGenerator.writeStringField("notes", notes);
		}, jParser -> notes = jParser.getText());

		setJsonField("tags", jGenerator -> {
			if (tags.size() > 0) {
				jGenerator.writeFieldName("tags");
				jGenerator.writeStartArray();
				Iterator<String> iterator = tags.iterator();
				while (iterator.hasNext())
					jGenerator.writeString(iterator.next());
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				tags.add(jParser.getText());
			}
		});

		setJsonField("parameters", jGenerator -> {
			if (parameters.size() > 0) {
				jGenerator.writeArrayFieldStart("parameters");
				for (String name : parameters.keySet()) {
					jGenerator.writeStartObject();
					jGenerator.writeStringField("name", name);
					if (parameters.get(name) instanceof Double) {
						jGenerator.writeStringField("type", "number");
						jGenerator.writeNumberField("value", ((Double) parameters.get(name))
							.doubleValue());
					}
					else if (parameters.get(name) instanceof String) {
						jGenerator.writeStringField("type", "string");
						jGenerator.writeStringField("value", (String) parameters.get(name));
					}
					else if (parameters.get(name) instanceof Boolean) {
						jGenerator.writeStringField("type", "boolean");
						jGenerator.writeBooleanField("value", (Boolean) parameters.get(
							name));
					}
					jGenerator.writeEndObject();
				}
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			if (jParser.currentToken().equals(JsonToken.START_ARRAY)) {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					String name = "";
					String type = "";
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
						String field = jParser.getCurrentName();
						jParser.nextToken();
						if (field.equals("name")) {
							name = jParser.getValueAsString();
						}
						else if (field.equals("type")) {
							type = jParser.getValueAsString();
						}
						else if (field.equals("value")) {
							if (type.equals("number")) {
								if (jParser.getCurrentToken().equals(JsonToken.VALUE_STRING)) {
									String str = jParser.getValueAsString();
									if (Objects.equals(str, "Infinity")) {
										parameters.put(name, Double.POSITIVE_INFINITY);
									}
									else if (Objects.equals(str, "-Infinity")) {
										parameters.put(name, Double.NEGATIVE_INFINITY);
									}
									else if (Objects.equals(str, "NaN")) {
										parameters.put(name, Double.NaN);
									}
								}
								else {
									parameters.put(name, jParser.getDoubleValue());
								}
							}
							else if (type.equals("string")) {
								parameters.put(name, jParser.getValueAsString());
							}
							else if (type.equals("boolean")) {
								parameters.put(name, jParser.getBooleanValue());
							}
						}
					}
				}
			}
			else {
				// Must be old format... so just read in all as numbers
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					String subfieldname = jParser.getCurrentName();
					jParser.nextToken();
					if (jParser.getCurrentToken().equals(JsonToken.VALUE_STRING)) {
						String str = jParser.getValueAsString();
						if (Objects.equals(str, "Infinity")) {
							parameters.put(subfieldname, Double.POSITIVE_INFINITY);
						}
						else if (Objects.equals(str, "-Infinity")) {
							parameters.put(subfieldname, Double.NEGATIVE_INFINITY);
						}
						else if (Objects.equals(str, "NaN")) {
							parameters.put(subfieldname, Double.NaN);
						}
					}
					else {
						parameters.put(subfieldname, jParser.getDoubleValue());
					}
				}
			}

		});

		setJsonField("regionsOfInterest", jGenerator -> {
			if (regionsOfInterest.size() > 0) {
				jGenerator.writeArrayFieldStart("regionsOfInterest");
				for (String region : regionsOfInterest.keySet())
					regionsOfInterest.get(region).toJSON(jGenerator);
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsRegion regionOfInterest = new MarsRegion(jParser);
				regionsOfInterest.put(regionOfInterest.getName(), regionOfInterest);
			}
		});

		setJsonField("positionsOfInterest", jGenerator -> {
			if (positionsOfInterest.size() > 0) {
				jGenerator.writeArrayFieldStart("positionsOfInterest");
				for (String position : positionsOfInterest.keySet())
					positionsOfInterest.get(position).toJSON(jGenerator);
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsPosition positionOfInterest = new MarsPosition(jParser);
				positionsOfInterest.put(positionOfInterest.getName(),
					positionOfInterest);
			}
		});

		/*
		 * 
		 * The fields below are needed for backwards compatibility.
		 * 
		 * Please remove for a future release.
		 * 
		 */

		setJsonField("UID", null, jParser -> uid = jParser.getText());

		setJsonField("Notes", null, jParser -> notes = jParser.getText());

		setJsonField("Tags", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				tags.add(jParser.getText());
			}
		});

		setJsonField("Parameters", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String subfieldname = jParser.getCurrentName();
				jParser.nextToken();
				if (jParser.getCurrentToken().equals(JsonToken.VALUE_STRING)) {
					String str = jParser.getValueAsString();
					if (Objects.equals(str, "Infinity")) {
						parameters.put(subfieldname, Double.POSITIVE_INFINITY);
					}
					else if (Objects.equals(str, "-Infinity")) {
						parameters.put(subfieldname, Double.NEGATIVE_INFINITY);
					}
					else if (Objects.equals(str, "NaN")) {
						parameters.put(subfieldname, Double.NaN);
					}
				}
				else {
					parameters.put(subfieldname, jParser.getDoubleValue());
				}
			}
		});

		setJsonField("stringParameters", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String field = jParser.getCurrentName();
				jParser.nextToken();
				parameters.put(field, jParser.getValueAsString());
			}
		});

		setJsonField("RegionsOfInterest", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsRegion regionOfInterest = new MarsRegion(jParser);
				regionsOfInterest.put(regionOfInterest.getName(), regionOfInterest);
			}
		});

		setJsonField("PositionsOfInterest", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsPosition positionOfInterest = new MarsPosition(jParser);
				positionsOfInterest.put(positionOfInterest.getName(),
					positionOfInterest);
			}
		});
	}

	/**
	 * Get the UID for this record.
	 * 
	 * @return Returns the UID.
	 */
	@Override
	public String getUID() {
		return uid;
	}

	/**
	 * Get notes for this record. Notes can be added during manual sorting to
	 * point out a feature or important detail about the current record.
	 * 
	 * @return Returns a string containing any notes associated with this record.
	 */
	@Override
	public String getNotes() {
		return notes;
	}

	/**
	 * Sets the notes for this record. Notes can be added during manual sorting to
	 * point out a feature or important detail about the current record.
	 * 
	 * @param notes Any notes about this molecule.
	 */
	@Override
	public void setNotes(String notes) {
		this.notes = notes;
	}

	/**
	 * Add to any notes already in the record.
	 * 
	 * @param note String with the note to add to the record.
	 */
	@Override
	public void addNote(String note) {
		this.notes += note;
	}

	/**
	 * Add a string tag to the record. Tags are used for marking individual record
	 * to sorting and processing with subsets of molecules.
	 * 
	 * @param tag The string tag to be added.
	 */
	@Override
	public void addTag(String tag) {
		tags.add(tag);
	}

	/**
	 * Check if the record has a tag.
	 * 
	 * @param tag The string tag to check for.
	 * @return Returns true if the record has the tag and false if not.
	 */
	@Override
	public boolean hasTag(String tag) {
		return tags.contains(tag);
	}

	/**
	 * Check if the record has no tags.
	 * 
	 * @return Returns true if the record has no tags.
	 */
	@Override
	public boolean hasNoTags() {
		return tags.size() == 0;
	}

	/**
	 * Get the set of all tags.
	 * 
	 * @return Returns the set of tags for this record.
	 */
	@Override
	public LinkedHashSet<String> getTags() {
		return tags;
	}

	/**
	 * Get an array list of all tags.
	 * 
	 * @return Returns the set of tags for this record as an array.
	 */
	@Override
	public String[] getTagsArray() {
		String[] tagArray = new String[tags.size()];
		return tags.toArray(tagArray);
	}

	/**
	 * Remove a string tag from the record.
	 * 
	 * @param tag The string tag to remove.
	 */
	@Override
	public void removeTag(String tag) {
		tags.remove(tag);
	}

	/**
	 * Remove all tags from the record.
	 */
	@Override
	public void removeAllTags() {
		tags.clear();
	}

	/**
	 * Add or update a parameter value. Parameters are used to store single values
	 * associated with the record.
	 * 
	 * @param parameter The string parameter name.
	 * @param value The double value to set for the parameter name.
	 */
	@Override
	public void setParameter(String parameter, double value) {
		parameters.put(parameter, value);
	}

	/**
	 * Add or update a parameter value.
	 * 
	 * @param parameter The string parameter name.
	 * @param value The string value to set for the parameter name.
	 */
	@Override
	public void setParameter(String parameter, String value) {
		parameters.put(parameter, value);
	}

	/**
	 * Add or update a parameter value.
	 * 
	 * @param parameter The string parameter name.
	 * @param value The boolean value to set for the parameter name.
	 */
	@Override
	public void setParameter(String parameter, boolean value) {
		parameters.put(parameter, value);
	}

	/**
	 * Remove all parameter values from the record.
	 */
	@Override
	public void removeAllParameters() {
		parameters.clear();
	}

	/**
	 * Remove parameter. Removes the name and value pair.
	 * 
	 * @param parameter The parameter name to remove.
	 */
	@Override
	public void removeParameter(String parameter) {
		parameters.remove(parameter);
	}

	/**
	 * Get the value of a parameter.
	 * 
	 * @param parameter The string parameter name to retrieve the value for.
	 * @return Returns the double value for the parameter name given.
	 */
	@Override
	public double getParameter(String parameter) {
		if (parameters.containsKey(parameter) && parameters.get(
			parameter) instanceof Double)
		{
			return ((Double) parameters.get(parameter)).doubleValue();
		}
		else {
			return Double.NaN;
		}
	}

	/**
	 * Get the value of a string parameter.
	 * 
	 * @param parameter The string parameter name to retrieve the value for.
	 * @return Returns the string value for the parameter name given.
	 */
	@Override
	public String getStringParameter(String parameter) {
		if (parameters.containsKey(parameter) && parameters.get(
			parameter) instanceof String)
		{
			return (String) parameters.get(parameter);
		}
		else {
			return "";
		}
	}

	/**
	 * Get the value of a boolean parameter.
	 * 
	 * @param parameter The parameter name to retrieve.
	 * @return Returns the boolean value for the parameter name given.
	 */
	@Override
	public boolean getBooleanParameter(String parameter) {
		if (parameters.containsKey(parameter) && parameters.get(
			parameter) instanceof Boolean)
		{
			return (boolean) parameters.get(parameter);
		}
		throw new NullPointerException();
	}

	/**
	 * Returns true if any type of parameters has the name give.
	 * 
	 * @param parameter The parameter name to check for.
	 * @return Returns true if a parameter with the name exists.
	 */
	@Override
	public boolean hasParameter(String parameter) {
		return parameters.containsKey(parameter);
	}

	/**
	 * Returns true if the double parameter exists.
	 * 
	 * @param parameter The double parameter name to check for.
	 * @return Returns true if a parameter with this name exists.
	 */
	@Override
	public boolean hasDoubleParameter(String parameter) {
		return parameters.containsKey(parameter) && parameters.get(
				parameter) instanceof Double;
	}

	/**
	 * Returns true if the string parameter exists.
	 * 
	 * @param parameter The string parameter name to check for.
	 * @return Returns true if a parameter with this name exists.
	 */
	@Override
	public boolean hasStringParameter(String parameter) {
		return parameters.containsKey(parameter) && parameters.get(
				parameter) instanceof String;
	}

	/**
	 * Returns true if the boolean parameter exists.
	 * 
	 * @param parameter The boolean parameter name to check for.
	 * @return Returns true if a parameter with this name exists.
	 */
	@Override
	public boolean hasBooleanParameter(String parameter) {
		return parameters.containsKey(parameter) && parameters.get(
				parameter) instanceof Boolean;
	}

	/**
	 * Get the map for all parameters.
	 * 
	 * @return Returns the map of parameter names to values.
	 */
	@Override
	public LinkedHashMap<String, Object> getParameters() {
		return parameters;
	}

	/**
	 * Add or update a {@link MarsRegion}. This can be a region of interest for
	 * further analysis steps: slope calculations or KCP calculations
	 * {@link KCPCommand}. Region names are unique. If a region that has this name
	 * already exists in the record it will be overwritten by this method.
	 * 
	 * @param regionOfInterest The region to add to the record.
	 */
	@Override
	public void putRegion(MarsRegion regionOfInterest) {
		regionsOfInterest.put(regionOfInterest.getName(), regionOfInterest);
	}

	/**
	 * Get a {@link MarsRegion}. Region names are unique. Only one copy of each
	 * region can be stored in the record.
	 * 
	 * @param name The name of the region to retrieve.
	 */
	@Override
	public MarsRegion getRegion(String name) {
		return regionsOfInterest.get(name);
	}

	/**
	 * Check if the record contains a {@link MarsRegion} using the name.
	 * 
	 * @param name The name of the region to check for.
	 */
	@Override
	public boolean hasRegion(String name) {
		return regionsOfInterest.containsKey(name);
	}

	/**
	 * Remove a {@link MarsRegion} from the record using the name.
	 * 
	 * @param name The name of the region to remove.
	 */
	@Override
	public void removeRegion(String name) {
		regionsOfInterest.remove(name);
	}

	/**
	 * Remove all regions from the record.
	 */
	@Override
	public void removeAllRegions() {
		regionsOfInterest.clear();
	}

	/**
	 * Get the set of region names contained in this record.
	 */
	@Override
	public Set<String> getRegionNames() {
		return regionsOfInterest.keySet();
	}

	/**
	 * Get the map for all regions.
	 */
	@Override
	public LinkedHashMap<String, MarsRegion> getRegions() {
		return regionsOfInterest;
	}

	/**
	 * Add or update a {@link MarsPosition}. This can be a position of interest
	 * for further analysis steps. Position names are unique. If a position with
	 * has this name already exists in the record it will be overwritten by this
	 * method.
	 * 
	 * @param positionOfInterest The position to add to the record.
	 */
	@Override
	public void putPosition(MarsPosition positionOfInterest) {
		positionsOfInterest.put(positionOfInterest.getName(), positionOfInterest);
	}

	/**
	 * Get a {@link MarsPosition}. Position names are unique. Only one copy of
	 * each region can be stored in the record.
	 * 
	 * @param name The name of the position to retrieve.
	 */
	@Override
	public MarsPosition getPosition(String name) {
		return positionsOfInterest.get(name);
	}

	/**
	 * Check if the record contains a {@link MarsPosition} using the name.
	 * 
	 * @param name The name of the position to check for.
	 */
	@Override
	public boolean hasPosition(String name) {
		return positionsOfInterest.containsKey(name);
	}

	/**
	 * Remove a {@link MarsPosition} from the record using the name.
	 * 
	 * @param name The name of the position to remove.
	 */
	@Override
	public void removePosition(String name) {
		positionsOfInterest.remove(name);
	}

	/**
	 * Remove all positions from the record.
	 */
	@Override
	public void removeAllPositions() {
		positionsOfInterest.clear();
	}

	/**
	 * Get the set of position names contained in this record.
	 */
	@Override
	public Set<String> getPositionNames() {
		return positionsOfInterest.keySet();
	}

	/**
	 * Get the map for all positions.
	 */
	@Override
	public LinkedHashMap<String, MarsPosition> getPositions() {
		return positionsOfInterest;
	}

	/**
	 * Merge another MarsRecord into this one.
	 * 
	 * @param record The record to merge.
	 */
	@Override
	public void merge(MarsRecord record) {
		setNotes(getNotes() + record.getNotes());
		getTags().addAll(record.getTags());
		getParameters().putAll(record.getParameters());
		getRegions().putAll(record.getRegions());
		getPositions().putAll(record.getPositions());
	}

	/**
	 * Set the parent {@link MoleculeArchive} that this record is stored in.
	 * 
	 * @param archive The {@link MoleculeArchive} holding this record.
	 */
	@Override
	public void setParent(
		MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties<?, ?>, ? extends MoleculeArchiveIndex<?, ?>> archive)
	{
		parent = archive;
	}
}
