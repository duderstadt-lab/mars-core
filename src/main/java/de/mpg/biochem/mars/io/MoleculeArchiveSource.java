/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2024 Karl Duderstadt
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

package de.mpg.biochem.mars.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Basic interface for
 * 
 * @author Karl Duderstadt
 */
public interface MoleculeArchiveSource extends MoleculeArchiveStorage {
    public static final String PROPERTIES_FILE_NAME = "MoleculeArchiveProperties";
    public static final String INDEXES_FILE_NAME = "indexes";
    public static final String MOLECULES_SUBDIRECTORY_NAME = "Molecules";
    public static final String METADATA_SUBDIRECTORY_NAME = "Metadata";

    public static final String ROVER_FILE_EXTENSION = ".rover";
    void setPath(String path);

    String getPath();

    String getName();

    void initializeLocation() throws IOException;

    boolean isVirtual();

    boolean isReachable();

    String getArchiveType() throws IOException;

    InputStream getInputStream() throws IOException;

    OutputStream getOutputStream() throws IOException;

    InputStream getRoverInputStream() throws IOException;

    OutputStream getRoverOutputStream() throws IOException;

    InputStream getPropertiesInputStream() throws IOException;

    OutputStream getPropertiesOutputStream() throws IOException;

    InputStream getIndexesInputStream() throws IOException;

    OutputStream getIndexesOutputStream() throws IOException;

    InputStream getMoleculeInputStream(String UID) throws IOException;

    OutputStream getMoleculeOutputStream(String UID) throws IOException;

    void removeMolecule(String UID) throws IOException;

    InputStream getMetadataInputStream(String metaUID) throws IOException;

    OutputStream getMetadataOutputStream(String metaUID) throws IOException;

    public List<String> getMoleculeUIDs();

    public List<String> getMetadataUIDs();

    void removeMetadata(String metaUID) throws IOException;
}
