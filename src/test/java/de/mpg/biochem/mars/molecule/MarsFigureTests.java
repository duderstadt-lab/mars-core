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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import org.junit.jupiter.api.Test;

import de.mpg.biochem.mars.util.MarsFigure;
import de.mpg.biochem.mars.util.MarsMath;

public class MarsFigureTests {

	@Test
	void figureSurvivesJsonRoundTrip() throws IOException {
		SingleMolecule molecule = new SingleMolecule(MarsMath.getUUID58());
		molecule.setTable(MoleculeArchiveTests.generateRandomTable(5));
		MarsFigure figure = new MarsFigure("kymograph", "{\"projectionWidth\":10}",
			"data:image/png;base64,AAAA");
		figure.setGenerator("test 1.0");
		molecule.setFigure(figure);

		SingleMolecule reloaded = roundTrip(molecule);

		MarsFigure reloadedFigure = reloaded.getFigure();
		assertNotNull(reloadedFigure);
		assertEquals("kymograph", reloadedFigure.getType());
		assertEquals("{\"projectionWidth\":10}", reloadedFigure.getSettings());
		assertEquals("data:image/png;base64,AAAA", reloadedFigure.getImage());
		assertEquals("test 1.0", reloadedFigure.getGenerator());
		assertEquals(figure.getCreated(), reloadedFigure.getCreated());
		assertEquals(5, reloaded.getTable().getRowCount());
	}

	@Test
	void legacyRecordHasNoFigure() throws IOException {
		String legacyJson = "{\"uid\":\"" + MarsMath.getUUID58() +
			"\",\"type\":\"de.mpg.biochem.mars.molecule.SingleMolecule\"}";
		JsonParser jParser = new JsonFactory().createParser(legacyJson);
		SingleMolecule reloaded = new SingleMolecule(jParser);
		jParser.close();

		assertFalse(reloaded.hasFigure());
		assertNull(reloaded.getFigure());
	}

	@Test
	void settingFigureMarksRecordModified() throws IOException {
		SingleMolecule molecule = roundTrip(new SingleMolecule(MarsMath
			.getUUID58()));
		assertFalse(molecule.isModified());

		molecule.setFigure(new MarsFigure("montage", "{}", ""));
		assertTrue(molecule.isModified());
		assertTrue(molecule.hasFigure());
	}

	@Test
	void mergeKeepsExistingFigure() {
		MarsFigure first = new MarsFigure("kymograph", "{}", "first");
		MarsFigure second = new MarsFigure("kymograph", "{}", "second");

		SingleMolecule withFigure = new SingleMolecule(MarsMath.getUUID58(),
			MoleculeArchiveTests.generateRandomTable(5));
		withFigure.setFigure(first);
		SingleMolecule other = new SingleMolecule(MarsMath.getUUID58(),
			MoleculeArchiveTests.generateRandomTable(5));
		other.setFigure(second);
		withFigure.merge(other);
		assertSame(first, withFigure.getFigure());

		SingleMolecule withoutFigure = new SingleMolecule(MarsMath.getUUID58(),
			MoleculeArchiveTests.generateRandomTable(5));
		withoutFigure.merge(other);
		assertSame(second, withoutFigure.getFigure());
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
