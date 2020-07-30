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
package de.mpg.biochem.mars.molecule;

import java.util.ArrayList;
import java.util.Set;

public interface MoleculeArchiveProperties extends JsonConvertibleRecord {
	
	void addTag(String tag);
	
	void addAllTags(Set<String> tags);
	
	Set<String> getTagSet();
	
	void setTagSet(Set<String> tagSet);
	
	void addParameter(String parameterName);
	
	void addAllParameters(Set<String> parameters);
	
	void removeParameter(String parameter);
	
	Set<String> getParameterSet();
	
	void setParameterSet(Set<String> parameterSet);
	
	void setNumberOfMolecules(int numMolecules);
	
	int getNumberOfMolecules();
	
	void setNumberOfMetadatas(int numMetadata);
	
	int getNumberOfMetadatas();
	
	void addColumn(String column);
	
	void addAllColumns(Set<String> columns);
	
	void addAllColumns(ArrayList<String> columns);
	
	void setColumnSet(Set<String> moleculeDataTableColumnSet);
	
	Set<String> getColumnSet();
	
	void addSegmentTableName(ArrayList<String> segmentTableName);
	
	void addAllSegmentTableNames(Set<ArrayList<String>> segmentTableNames);
	
	void setSegmentTableNames(Set<ArrayList<String>> moleculeSegmentTableNames);
	
	Set<ArrayList<String>> getSegmentTableNames();
	
	String getComments();
	
	void addComment(String comment);
	
	void setComments(String comments);
	
	void merge(MoleculeArchiveProperties properties, String archiveName);
	
	/**
	 * Set the parent {@link MoleculeArchive} that this record is stored in.
	 * 
	 * @param archive The {@link MoleculeArchive} holding this record.
	 */
	void setParent(MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties> archive);
}
