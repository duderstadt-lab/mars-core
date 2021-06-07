/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2021 Karl Duderstadt
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
import java.util.concurrent.ConcurrentSkipListSet;

import com.fasterxml.jackson.core.JsonFactory;

import de.mpg.biochem.mars.metadata.MarsMetadata;

public interface MoleculeArchiveIndex<M extends Molecule, I extends MarsMetadata>
	extends JsonConvertibleRecord
{

	void addMolecule(M molecule);

	void removeMolecule(M molecule);

	void removeMolecule(String UID);

	void addMetadata(I metadata);

	void removeMetadata(I metadata);

	void removeMetadata(String metadataUID);

	boolean containsMoleculeUID(String UID);

	boolean containsMetadataUID(String metadataUID);

	ConcurrentSkipListSet<String> getMoleculeUIDSet();

	ConcurrentSkipListSet<String> getMetadataUIDSet();

	Map<String, Set<String>> getMetadataUIDtoTagListMap();

	Map<String, Set<String>> getMoleculeUIDtoTagListMap();

	Map<String, Integer> getMoleculeUIDtoImageMap();

	Map<String, Integer> getMoleculeUIDtoChannelMap();

	Map<String, String> getMoleculeUIDtoMetadataUIDMap();

	String getMetadataUIDforMolecule(String UID);

	void save(File directory, JsonFactory jfactory, String fileExtension)
		throws IOException;
}
