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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.util.MarsUtil;

public abstract class AbstractMoleculeArchiveIndex<M extends Molecule, I extends MarsMetadata>
	extends AbstractJsonConvertibleRecord implements MoleculeArchiveIndex<M, I>
{

	/*
	 * The set of molecule UIDs.
	 */
	private ConcurrentSkipListSet<String> moleculeUIDs;

	/*
	 * The set of metadata UIDs.
	 */
	private ConcurrentSkipListSet<String> metadataUIDs;

	/*
	 * Map from molecule UID to tag set.
	 */
	private ConcurrentMap<String, Set<String>> moleculeUIDtoTagList;

	/*
	 * Map from metadata UID to tag set.
	 */
	private ConcurrentMap<String, Set<String>> metadataUIDtoTagList;

	/*
	 * Map from molecule UID to channel index.
	 */
	private ConcurrentMap<String, Integer> moleculeUIDtoChannel;

	/*
	 * Map from molecule UID to channel index.
	 */
	private ConcurrentMap<String, Integer> moleculeUIDtoImage;

	/*
	 * Map from molecule UID to metadata UID.
	 */
	private ConcurrentMap<String, String> moleculeUIDtoMetadataUID;

	public AbstractMoleculeArchiveIndex() {
		super();
		initializeVariables();
	}

	public AbstractMoleculeArchiveIndex(JsonParser jParser) throws IOException {
		super();
		initializeVariables();
		fromJSON(jParser);
	}

	private void initializeVariables() {
		moleculeUIDtoTagList = new ConcurrentHashMap<>();
		moleculeUIDtoChannel = new ConcurrentHashMap<>();
		moleculeUIDtoImage = new ConcurrentHashMap<>();
		metadataUIDtoTagList = new ConcurrentHashMap<>();
		moleculeUIDtoMetadataUID = new ConcurrentHashMap<>();

		moleculeUIDs = new ConcurrentSkipListSet<String>();//ConcurrentHashMap.newKeySet();
		metadataUIDs = new ConcurrentSkipListSet<String>();//ConcurrentHashMap.newKeySet();
	}

	@Override
	protected void createIOMaps() {

		setJsonField("metadata", jGenerator -> {
			jGenerator.writeFieldName("metadata");
			jGenerator.writeStartArray();
			for (String metaUID : metadataUIDs) {
				jGenerator.writeStartObject();
				jGenerator.writeStringField("uid", metaUID);

				if (metadataUIDtoTagList.containsKey(metaUID)) {
					jGenerator.writeArrayFieldStart("tags");
					for (String tag : metadataUIDtoTagList.get(metaUID)) {
						jGenerator.writeString(tag);
					}
					jGenerator.writeEndArray();
				}

				jGenerator.writeEndObject();
			}
			jGenerator.writeEndArray();
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				String metaUID = "NULL";
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("uid".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						metaUID = jParser.getText();
						metadataUIDs.add(metaUID);
					}

					if ("tags".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						LinkedHashSet<String> tags = new LinkedHashSet<String>();
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							tags.add(jParser.getText());
						}
						if (!metaUID.equals("NULL")) metadataUIDtoTagList.put(metaUID,
							tags);
					}
				}

			}
		});

		setJsonField("molecules", jGenerator -> {
			jGenerator.writeArrayFieldStart("molecules");
			for (String UID : moleculeUIDs) {
				jGenerator.writeStartObject();
				jGenerator.writeStringField("uid", UID);
				jGenerator.writeStringField("metadataUID", moleculeUIDtoMetadataUID.get(
					UID));

				if (moleculeUIDtoTagList.containsKey(UID)) {
					jGenerator.writeArrayFieldStart("tags");
					for (String tag : moleculeUIDtoTagList.get(UID)) {
						jGenerator.writeString(tag);
					}
					jGenerator.writeEndArray();
				}

				if (moleculeUIDtoChannel.containsKey(UID)) {
					jGenerator.writeNumberField("channel", moleculeUIDtoChannel.get(UID));
				}

				if (moleculeUIDtoImage.containsKey(UID)) {
					jGenerator.writeNumberField("image", moleculeUIDtoImage.get(UID));
				}

				jGenerator.writeEndObject();
			}
			jGenerator.writeEndArray();
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				String UID = "NULL";
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("uid".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						UID = jParser.getText();
						moleculeUIDs.add(UID);
					}

					if ("metadataUID".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						moleculeUIDtoMetadataUID.put(UID, jParser.getText());
					}

					if ("tags".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						LinkedHashSet<String> tags = new LinkedHashSet<String>();
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							tags.add(jParser.getText());
						}
						moleculeUIDtoTagList.put(UID, tags);
					}

					if ("channel".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						moleculeUIDtoChannel.put(UID, jParser.getIntValue());
					}

					if ("image".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						moleculeUIDtoImage.put(UID, jParser.getIntValue());
					}
				}
			}
		});

		/*
		 * 
		 * The fields below are needed for backwards compatibility.
		 * 
		 * Please remove for a future release.
		 * 
		 */

		setJsonField("MetadataIndex", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				String metaUID = "NULL";
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("UID".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						metaUID = jParser.getText();
						metadataUIDs.add(metaUID);
					}

					if ("Tags".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						LinkedHashSet<String> tags = new LinkedHashSet<String>();
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							tags.add(jParser.getText());
						}
						if (!metaUID.equals("NULL")) metadataUIDtoTagList.put(metaUID,
							tags);
					}
				}

			}
		});

		setJsonField("imageMetaDataIndex", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				String metaUID = "NULL";
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("UID".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						metaUID = jParser.getText();
						metadataUIDs.add(metaUID);
					}

					if ("Tags".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						LinkedHashSet<String> tags = new LinkedHashSet<String>();
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							tags.add(jParser.getText());
						}
						if (!metaUID.equals("NULL")) metadataUIDtoTagList.put(metaUID,
							tags);
					}
				}

			}
		});

		setJsonField("ImageMetadataIndex", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				String metaUID = "NULL";
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("UID".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						metaUID = jParser.getText();
						metadataUIDs.add(metaUID);
					}

					if ("Tags".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						LinkedHashSet<String> tags = new LinkedHashSet<String>();
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							tags.add(jParser.getText());
						}
						if (!metaUID.equals("NULL")) metadataUIDtoTagList.put(metaUID,
							tags);
					}
				}

			}
		});

		setJsonField("MoleculeIndex", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				String UID = "NULL";
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("UID".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						UID = jParser.getText();
						moleculeUIDs.add(UID);
					}

					if ("ImageMetaDataUID".equals(jParser.getCurrentName()) ||
						"ImageMetadataUID".equals(jParser.getCurrentName()) || "MetadataUID"
							.equals(jParser.getCurrentName()))
					{
						jParser.nextToken();
						moleculeUIDtoMetadataUID.put(UID, jParser.getText());
					}

					if ("Tags".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						LinkedHashSet<String> tags = new LinkedHashSet<String>();
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							tags.add(jParser.getText());
						}
						moleculeUIDtoTagList.put(UID, tags);
					}
				}
			}
		});

		setJsonField("moleculeIndex", null, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				String UID = "NULL";
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("UID".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						UID = jParser.getText();
						moleculeUIDs.add(UID);
					}

					if ("ImageMetaDataUID".equals(jParser.getCurrentName()) ||
						"ImageMetadataUID".equals(jParser.getCurrentName()) || "MetadataUID"
							.equals(jParser.getCurrentName()))
					{
						jParser.nextToken();
						moleculeUIDtoMetadataUID.put(UID, jParser.getText());
					}

					if ("Tags".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						LinkedHashSet<String> tags = new LinkedHashSet<String>();
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							tags.add(jParser.getText());
						}
						moleculeUIDtoTagList.put(UID, tags);
					}
				}
			}
		});
	}

	/**
	 * Save the archive properties to a file.
	 * 
	 * @param directory Folder to save to.
	 * @param jfactory JsonFactory to use when saving.
	 * @param fileExtension The file extension (.json or .sml).
	 */
	public void save(File directory, JsonFactory jfactory, String fileExtension)
		throws IOException
	{
		File indexFile = new File(directory.getAbsolutePath() + "/indexes" +
			fileExtension);
		MarsUtil.writeJsonRecord(this, indexFile, jfactory);
	}

	@Override
	public void addMolecule(M molecule) {
		moleculeUIDs.add(molecule.getUID());
		moleculeUIDtoTagList.put(molecule.getUID(), molecule.getTags());
		moleculeUIDtoChannel.put(molecule.getUID(), molecule.getChannel());
		moleculeUIDtoImage.put(molecule.getUID(), molecule.getImage());
		moleculeUIDtoMetadataUID.put(molecule.getUID(), molecule.getMetadataUID());
	}

	@Override
	public void removeMolecule(Molecule molecule) {
		removeMolecule(molecule.getUID());
	}

	@Override
	public void removeMolecule(String UID) {
		moleculeUIDs.remove(UID);
		moleculeUIDtoTagList.remove(UID);
		moleculeUIDtoChannel.remove(UID);
		moleculeUIDtoImage.remove(UID);
		moleculeUIDtoMetadataUID.remove(UID);
	}

	@Override
	public void addMetadata(I metadata) {
		metadataUIDs.add(metadata.getUID());
		metadataUIDtoTagList.put(metadata.getUID(), metadata.getTags());
	}

	@Override
	public void removeMetadata(I metadata) {
		removeMetadata(metadata.getUID());
	}

	@Override
	public void removeMetadata(String metadataUID) {
		metadataUIDs.remove(metadataUID);
		metadataUIDtoTagList.remove(metadataUID);
	}

	@Override
	public boolean containsMoleculeUID(String UID) {
		return moleculeUIDs.contains(UID);
	}

	@Override
	public boolean containsMetadataUID(String metadataUID) {
		return metadataUIDs.contains(metadataUID);
	}

	@Override
	public ConcurrentSkipListSet<String> getMoleculeUIDSet() {
		return moleculeUIDs;
	}

	@Override
	public ConcurrentSkipListSet<String> getMetadataUIDSet() {
		return metadataUIDs;
	}

	@Override
	public Map<String, Set<String>> getMetadataUIDtoTagListMap() {
		return metadataUIDtoTagList;
	}

	@Override
	public Map<String, Set<String>> getMoleculeUIDtoTagListMap() {
		return moleculeUIDtoTagList;
	}

	@Override
	public Map<String, Integer> getMoleculeUIDtoImageMap() {
		return moleculeUIDtoImage;
	}

	@Override
	public Map<String, Integer> getMoleculeUIDtoChannelMap() {
		return moleculeUIDtoChannel;
	}

	@Override
	public String getMetadataUIDforMolecule(String UID) {
		return moleculeUIDtoMetadataUID.get(UID);
	}

	public Map<String, String> getMoleculeUIDtoMetadataUIDMap() {
		return moleculeUIDtoMetadataUID;
	}
}
