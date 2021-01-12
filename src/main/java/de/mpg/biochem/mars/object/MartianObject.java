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

import de.mpg.biochem.mars.image.PeakShape;
import de.mpg.biochem.mars.molecule.AbstractMolecule;
import de.mpg.biochem.mars.table.MarsTable;

public class MartianObject extends AbstractMolecule {

	private ConcurrentMap<Integer, PeakShape> shapes;

	public MartianObject() {
		super();
		shapes = new ConcurrentHashMap<>();
	}

	public MartianObject(JsonParser jParser) throws IOException {
		super();
		shapes = new ConcurrentHashMap<>();
		fromJSON(jParser);
	}

	public MartianObject(String UID) {
		super(UID);
		shapes = new ConcurrentHashMap<>();
	}

	public MartianObject(String UID, MarsTable dataTable) {
		super(UID, dataTable);
		shapes = new ConcurrentHashMap<>();
	}

	public void putShape(int t, PeakShape shape) {
		shapes.put(t, shape);
	}

	public boolean hasShape(int t) {
		return shapes.containsKey(t);
	}

	public PeakShape getShape(int t) {
		return shapes.get(t);
	}

	public void removeShape(int t) {
		shapes.remove(t);
	}

	public Set<Integer> getShapeKeys() {
		return shapes.keySet();
	}

	@Override
	protected void createIOMaps() {
		super.createIOMaps();

		setJsonField("shapes", jGenerator -> {
			if (shapes.keySet().size() > 0) {
				jGenerator.writeArrayFieldStart("shapes");
				for (int t : shapes.keySet()) {
					jGenerator.writeStartObject();
					jGenerator.writeNumberField("t", t);

					jGenerator.writeFieldName("shape");
					shapes.get(t).toJSON(jGenerator);

					jGenerator.writeEndObject();
				}
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				int t = -1;
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("t".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						t = jParser.getNumberValue().intValue();
					}

					if ("shape".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						PeakShape shape = new PeakShape(jParser);
						if (t != -1) shapes.put(t, shape);
					}
				}
			}
		});
	}
}
