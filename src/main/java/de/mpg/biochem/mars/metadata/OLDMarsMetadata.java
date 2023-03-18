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

package de.mpg.biochem.mars.metadata;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractMarsRecord;
import de.mpg.biochem.mars.table.MarsTable;
import ome.xml.meta.OMEXMLMetadata;

public class OLDMarsMetadata extends AbstractMarsRecord implements
	MarsMetadata
{

	// Processing log for the record
	protected String log = "";

	protected String Microscope = "unknown";

	// Directory where the images are stored..
	protected String SourceDirectory = "unknown";

	// Date and time when the data was collected...
	protected String CollectionDate = "unknown";

	// Table housing main record data.
	protected MarsTable dataTable = new MarsTable("DataTable");

	// BDV views
	protected LinkedHashMap<String, MarsBdvSource> bdvSources =
		new LinkedHashMap<String, MarsBdvSource>();

	/**
	 * Constructor for loading a MarsMetadata record from a file. Typically, used
	 * when streaming records into memory when loading a or when a record is
	 * retrieved from the virtual store.
	 * 
	 * @param jParser A JsonParser at the start of the record.
	 * @throws IOException Thrown if unable to parse Json from JsonParser stream.
	 */
	public OLDMarsMetadata(JsonParser jParser) throws IOException {
		super();
		fromJSON(jParser);
	}

	@Override
	protected void createIOMaps() {
		super.createIOMaps();

		setJsonField("Microscope", jGenerator -> {
			if (Microscope != null) jGenerator.writeStringField("Microscope",
				Microscope);
		}, jParser -> Microscope = jParser.getText());

		setJsonField("SourceDirectory", jGenerator -> {
			if (SourceDirectory != null) jGenerator.writeStringField(
				"SourceDirectory", SourceDirectory);
		}, jParser -> SourceDirectory = jParser.getText());

		setJsonField("Log", jGenerator -> {
			if (!log.equals("")) {
				jGenerator.writeStringField("Log", log);
			}
		}, jParser -> log = jParser.getText());

		setJsonField("BdvSources", jGenerator -> {
			if (bdvSources.size() > 0) {
				jGenerator.writeArrayFieldStart("BdvSources");
				for (MarsBdvSource source : bdvSources.values()) {
					source.toJSON(jGenerator);
				}
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsBdvSource source = new MarsBdvSource(jParser);
				bdvSources.put(source.getName(), source);
			}
		});

		setJsonField("CollectionDate", jGenerator -> {
			if (CollectionDate != null) jGenerator.writeStringField("CollectionDate",
				CollectionDate);
		}, jParser -> CollectionDate = jParser.getText());

		setJsonField("DataTable", jGenerator -> {
			if (dataTable.getColumnCount() > 0) {
				jGenerator.writeFieldName("DataTable");
				dataTable.toJSON(jGenerator);
			}
		}, jParser -> dataTable.fromJSON(jParser));

	}

	public MarsTable getDataTable() {
		return dataTable;
	}

	public MarsBdvSource getBdvSource(String name) {
		return bdvSources.get(name);
	}

	/**
	 * Get the Collection of BigDataViewer sources with each in
	 * {@link MarsBdvSource} format.
	 */
	public Collection<MarsBdvSource> getBdvSources() {
		return bdvSources.values();
	}

	/**
	 * Get the set of BigDataViewer source names.
	 */
	public Set<String> getBdvSourceNames() {
		return bdvSources.keySet();
	}

	/**
	 * Get the name of the microscope used for data collection. This is just for
	 * record keeping. There are no predefined setting based on microscope names.
	 */
	public String getMicroscopeName() {
		return Microscope;
	}

	/**
	 * Get the Date when these data were collected.
	 */
	public String getCollectionDate() {
		return CollectionDate;
	}

	/**
	 * Get the Source Directory where the images are stored.
	 */
	public String getSourceDirectory() {
		return SourceDirectory;
	}

	/**
	 * Get the log that contains the history of processing steps conducted on this
	 * dataset and the associated molecule records contained in the same.
	 */
	public String getLog() {
		return log;
	}

	// Should never be used are required to have the MarsMetadata interface, which
	// is required
	// for opening in the archive...

	@Override
	public void populateMetadata(OMEXMLMetadata md) {
		// TODO Auto-generated method stub

	}

	@Override
	public MarsOMEImage getImage(int imageIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MarsOMEPlane getPlane(int imageIndex, int planeIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasPlane(int imageIndex, int planeIndex) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public MarsOMEPlane getPlane(int imageIndex, int Z, int C, int T) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getImageCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setMicroscopeName(String Microscope) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setSourceDirectory(String path) {
		// TODO Auto-generated method stub

	}

	@Override
	public void log(String str) {
		// TODO Auto-generated method stub

	}

	@Override
	public void logln(String str) {
		// TODO Auto-generated method stub

	}

	@Override
	public void putBdvSource(MarsBdvSource source) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeBdvSource(String name) {
		// TODO Auto-generated method stub

	}

	/**
	 * Remove all {@link MarsBdvSource}s.
	 */
	@Override
	public void removeAllBdvSources() {
		bdvSources.clear();
	}

	@Override
	public boolean hasBdvSource(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setImage(MarsOMEImage image, int imageIndex) {
		// TODO Auto-generated method stub

	}

	@Override
	public Stream<MarsOMEImage> images() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void merge(MarsMetadata metadata) {
		// TODO Auto-generated method stub

	}
}
