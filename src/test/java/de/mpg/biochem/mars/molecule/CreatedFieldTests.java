/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2026 Karl Duderstadt
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.scijava.Context;

import de.mpg.biochem.mars.util.MarsMath;

public class CreatedFieldTests {

	@TempDir
	File tempDir;

	@Test
	void newRecordIsStampedWithCreated() {
		Instant before = Instant.now();
		SingleMolecule molecule = new SingleMolecule(MarsMath.getUUID58());
		Instant after = Instant.now();

		assertNotNull(molecule.getCreated());
		assertTrue(!molecule.getCreated().isBefore(before));
		assertTrue(!molecule.getCreated().isAfter(after));
	}

	@Test
	void createdSurvivesJsonRoundTrip() throws IOException {
		SingleMolecule molecule = new SingleMolecule(MarsMath.getUUID58());
		molecule.setTable(MoleculeArchiveTests.generateRandomTable(5));

		SingleMolecule reloaded = roundTrip(molecule);

		assertEquals(molecule.getCreated(), reloaded.getCreated());
	}

	@Test
	void legacyRecordWithoutCreatedStaysNull() throws IOException {
		//A record written before the created field existed has no such field.
		String legacyJson = "{\"uid\":\"" + MarsMath.getUUID58() +
			"\",\"type\":\"de.mpg.biochem.mars.molecule.SingleMolecule\"}";

		JsonParser jParser = new JsonFactory().createParser(legacyJson);
		SingleMolecule reloaded = new SingleMolecule(jParser);
		jParser.close();

		assertNull(reloaded.getCreated());
	}

	@Test
	void createdIsServedFromIndexForVirtualArchives() throws IOException {
		Context context = new Context();
		try {
			SingleMoleculeArchive archive = new SingleMoleculeArchive("test");
			SingleMolecule molecule = new SingleMolecule(MarsMath.getUUID58());
			molecule.setTable(MoleculeArchiveTests.generateRandomTable(5));
			archive.put(molecule);

			assertEquals(molecule.getCreated(), archive.getMoleculeCreated(molecule
				.getUID()));

			File store = new File(tempDir, "created.yama.store");
			archive.saveAsVirtualStore(store);

			MoleculeArchiveIOPlugin ioPlugin = new MoleculeArchiveIOPlugin();
			context.inject(ioPlugin);
			MoleculeArchive<?, ?, ?, ?> reloaded = ioPlugin.open(store
				.getAbsolutePath());

			assertEquals(molecule.getCreated(), reloaded.getMoleculeCreated(molecule
				.getUID()));
			assertEquals(molecule.getCreated(), reloaded.get(molecule.getUID())
				.getCreated());
		}
		finally {
			context.dispose();
		}
	}

	private static SingleMolecule roundTrip(SingleMolecule molecule)
		throws IOException
	{
		JsonFactory jFactory = new JsonFactory();
		StringWriter writer = new StringWriter();
		JsonGenerator jGenerator = jFactory.createGenerator(writer);
		molecule.toJSON(jGenerator);
		jGenerator.close();

		JsonParser jParser = jFactory.createParser(writer.toString());
		SingleMolecule reloaded = new SingleMolecule(jParser);
		jParser.close();
		return reloaded;
	}
}
