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

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.commands.MergeVirtualStoresCommand;

public class MergeVirtualStoresCommandTest {

	@TempDir
	File tempDir;

	@Test
	void mergedStoreKeepsMetadataRecords() throws IOException {
		buildStore("a", "metaA", "scopeA");
		buildStore("b", "metaB", "scopeB");

		try (Context context = new Context(MoleculeArchiveService.class,
			StatusService.class, UIService.class))
		{
			MergeVirtualStoresCommand merge = new MergeVirtualStoresCommand();
			merge.setContext(context);
			merge.setDirectory(tempDir);
			merge.setThreads(2);
			merge.run();
		}

		SingleMoleculeArchive merged = new SingleMoleculeArchive(new File(tempDir,
			"merged.yama.store"));

		assertEquals(Set.of("metaA", "metaB"), new TreeSet<>(merged
			.getMetadataUIDs()));
		assertEquals(4, merged.getNumberOfMolecules());

		//Each metadata file must hold its own record, not the merged index.
		for (String uid : new String[] { "metaA", "metaB" }) {
			MarsOMEMetadata metadata = merged.getMetadata(uid);
			assertNotNull(metadata);
			assertEquals(uid, metadata.getUID());
		}
		assertEquals("scopeA", merged.getMetadata("metaA").getMicroscopeName());
		assertEquals("scopeB", merged.getMetadata("metaB").getMicroscopeName());
	}

	private void buildStore(String name, String metaUID, String microscope)
		throws IOException
	{
		SingleMoleculeArchive archive = new SingleMoleculeArchive(name);
		MarsOMEMetadata metadata = new MarsOMEMetadata(metaUID);
		metadata.setMicroscopeName(microscope);
		archive.putMetadata(metadata);
		for (int i = 0; i < 2; i++) {
			SingleMolecule molecule = new SingleMolecule(name + i,
				MoleculeArchiveTests.generateRandomTable(5));
			molecule.setMetadataUID(metaUID);
			archive.put(molecule);
		}
		archive.saveAsVirtualStore(new File(tempDir, name + ".yama.store"));
	}
}
