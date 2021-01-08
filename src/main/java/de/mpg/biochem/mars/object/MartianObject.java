/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
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

package de.mpg.biochem.mars.object;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractMolecule;
import de.mpg.biochem.mars.table.MarsTable;

public class MartianObject extends AbstractMolecule {

	private ConcurrentMap<Integer, MarsTable> archTables;

	public MartianObject() {
		super();
		archTables = new ConcurrentHashMap<>();
	}

	public MartianObject(JsonParser jParser) throws IOException {
		super();
		archTables = new ConcurrentHashMap<>();
		fromJSON(jParser);
	}

	public MartianObject(String UID) {
		super(UID);
		archTables = new ConcurrentHashMap<>();
	}

	public MartianObject(String UID, MarsTable dataTable) {
		super(UID, dataTable);
		archTables = new ConcurrentHashMap<>();
	}

	public void putArchTable(int key, MarsTable table) {
		archTables.put(key, table);
	}

	public boolean hasArchTable(int key) {
		return archTables.containsKey(key);
	}

	public MarsTable getArchTable(int key) {
		return archTables.get(key);
	}

	public void removeArchTable(int key) {
		archTables.remove(key);
	}

	public Set<Integer> getArchTableKeys() {
		return archTables.keySet();
	}

	@Override
	protected void createIOMaps() {
		super.createIOMaps();

		setJsonField("archTables", jGenerator -> {
			if (archTables.keySet().size() > 0) {
				jGenerator.writeArrayFieldStart("archTables");
				for (int slice : archTables.keySet()) {
					jGenerator.writeStartObject();

					// FIXME this is pre OME

					jGenerator.writeNumberField("slice", slice);

					jGenerator.writeFieldName("table");
					archTables.get(slice).toJSON(jGenerator);

					jGenerator.writeEndObject();
				}
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					int slice = -1;

					// FIXME this is pre OME
					if ("slice".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						slice = jParser.getNumberValue().intValue();
					}

					// Move to next field
					jParser.nextToken();

					MarsTable archTable = new MarsTable("archTable " + slice);

					archTable.fromJSON(jParser);

					if (slice != -1) archTables.put(slice, archTable);
				}
			}
		});
	}
}
