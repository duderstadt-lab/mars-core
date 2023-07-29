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

package de.mpg.biochem.mars.io;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;

public class MoleculeArchiveAmazonS3Source implements MoleculeArchiveSource {
    protected final AmazonS3 s3;
    protected final String bucketName;
    protected String containerPath;

    protected String fileExtension;

    //Change to interface in the future...
    protected final MoleculeArchiveAmazonS3KeyValueAccess keyValueAccess;

    /**
     * Opens an
     *
     * If the bucket does not exist, it will not be created and
     * all subsequent attempts to read attributes, groups, or datasets will fail.
     *
     * @param s3 AmazonS3 location.
     * @param bucketName the name of the bucket.
     * @throws IOException thrown when reading or writing to the location fails.
     */
    public MoleculeArchiveAmazonS3Source(final AmazonS3 s3, final String bucketName) throws IOException {

        this(s3, bucketName, "/");
    }

    /**
     * Opens an
     *
     * If the bucket and/or container does not exist, it will not be created and
     * all subsequent attempts to read attributes, groups, or datasets will fail.
     *
     * @param s3 AmazonS3 location.
     * @param containerURI the container uri.
     * @throws IOException thrown when reading or writing to the location fails.
     */
    public MoleculeArchiveAmazonS3Source(final AmazonS3 s3, final AmazonS3URI containerURI) throws IOException {

        this(s3, containerURI.getBucket(), containerURI.getKey());
    }

    /**
     * Opens an
     *
     * If the bucket and/or container does not exist, it will not be created and
     * all subsequent attempts to read attributes, groups, or datasets will fail.
     *
     * @param s3 AmazonS3 location.
     * @param bucketName the name of the bucket.
     * @param containerPath the object path within the bucket.
     * @throws IOException thrown when reading or writing to the location fails.
     */
    public MoleculeArchiveAmazonS3Source(
            final AmazonS3 s3,
            final String bucketName,
            final String containerPath) throws IOException {

        this.s3 = s3;
        this.bucketName = bucketName;
        this.containerPath = containerPath;
        this.keyValueAccess = new MoleculeArchiveAmazonS3KeyValueAccess(s3, bucketName);
        this.fileExtension = detectFileExtension();
    }

    private String detectFileExtension() {
        List<String> keys = this.keyValueAccess.listObjectKeys(containerPath);
        for (final String key : keys)
            if (key.startsWith(PROPERTIES_FILE_NAME)) return key.substring(PROPERTIES_FILE_NAME.length());
        return ".sml";
    }

    @Override
    public void setPath(String path) {
        this.containerPath = path;
    }

    @Override
    public String getPath() {
        return containerPath;
    }

    @Override
    public String getName() {
        String[] parts = containerPath.split("/");
        return parts[parts.length - 1];
    }

    @Override
    public void initializeLocation() throws IOException {
        //Create directories if they do not exist.
        if (!keyValueAccess.exists(this.containerPath)) keyValueAccess.createDirectories(this.containerPath);

        String metadataDirPath = this.containerPath + "/" + METADATA_SUBDIRECTORY_NAME;
        if (!keyValueAccess.exists(metadataDirPath)) keyValueAccess.createDirectories(metadataDirPath);

        String moleculesDirPath = this.containerPath + "/" + MOLECULES_SUBDIRECTORY_NAME;
        if (!keyValueAccess.exists(moleculesDirPath)) keyValueAccess.createDirectories(moleculesDirPath);
    }

    @Override
    public boolean isVirtual() {
        return this.keyValueAccess.isDirectory(this.containerPath);
    }

    public boolean isReachable() {
        try{
            URL url = s3.getUrl(bucketName,"/");
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("OPTIONS");
            connection.connect();
            //int respCode = connection.getResponseCode();
            return true;
        } catch(UnknownHostException e) {
            return false;
        } catch(IOException e) {
            return false;
        }
    }

    @Override
    public String getArchiveType() throws IOException {
        if (isVirtual()) {
            InputStream propertiesInputStream = new BufferedInputStream(getPropertiesInputStream());

            // Here we automatically detect the format of the JSON file
            // Can be JSON text or Smile encoded binary file...
            JsonFactory jsonF = new JsonFactory();
            SmileFactory smileF = new SmileFactory();
            DataFormatDetector det = new DataFormatDetector(jsonF,
                    smileF);
            DataFormatMatcher match = det.findFormat(propertiesInputStream);
            JsonParser jParser = match.createParserWithMatch();

            String archiveType = "de.mpg.biochem.mars.molecule.SingleMoleculeArchive";

            while (jParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jParser.getCurrentName();

                if ("archiveType".equals(fieldName) || "ArchiveType".equals(fieldName)) {
                    jParser.nextToken();
                    archiveType = jParser.getText();
                    break;
                }
            }

            jParser.close();
            propertiesInputStream.close();

            return archiveType;
        } else {
            InputStream inputStream = new BufferedInputStream(getInputStream());

            // Here we automatically detect the format of the JSON file
            // Can be JSON text or Smile encoded binary file...
            JsonFactory jsonF = new JsonFactory();
            SmileFactory smileF = new SmileFactory();
            DataFormatDetector det = new DataFormatDetector(jsonF,
                    smileF);
            DataFormatMatcher match = det.findFormat(inputStream);
            JsonParser jParser = match.createParserWithMatch();

            String archiveType = "de.mpg.biochem.mars.molecule.SingleMoleculeArchive";

            jParser.nextToken();
            jParser.nextToken();
            if ("properties".equals(jParser.getCurrentName()) ||
                    "MoleculeArchiveProperties".equals(jParser.getCurrentName()))
            {
                jParser.nextToken();
                while (jParser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = jParser.getCurrentName();

                    if ("archiveType".equals(fieldName) || "ArchiveType".equals(
                            fieldName))
                    {
                        jParser.nextToken();
                        archiveType = jParser.getText();
                        break;
                    }
                }
            }

            jParser.close();
            inputStream.close();

            return archiveType;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForReading(containerPath);
        return lockedChannel.newInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForWriting(containerPath);
        return lockedChannel.newOutputStream();
    }

    public InputStream getRoverInputStream() throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForReading(containerPath + ROVER_FILE_EXTENSION);
        return lockedChannel.newInputStream();
    }

    @Override
    public OutputStream getRoverOutputStream() throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForWriting(containerPath + ROVER_FILE_EXTENSION);
        return lockedChannel.newOutputStream();
    }

    @Override
    public InputStream getPropertiesInputStream() throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForReading(containerPath + "/" + PROPERTIES_FILE_NAME + fileExtension);
        return lockedChannel.newInputStream();
    }

    @Override
    public OutputStream getPropertiesOutputStream() throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForWriting(containerPath + "/" + PROPERTIES_FILE_NAME + fileExtension);
        return lockedChannel.newOutputStream();
    }

    @Override
    public InputStream getIndexesInputStream() throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForReading(containerPath + "/" + INDEXES_FILE_NAME + fileExtension);
        return lockedChannel.newInputStream();
    }

    @Override
    public OutputStream getIndexesOutputStream() throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForWriting(containerPath + "/" + INDEXES_FILE_NAME + fileExtension);
        return lockedChannel.newOutputStream();
    }

    @Override
    public InputStream getMoleculeInputStream(String UID) throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForReading(containerPath + "/" + MOLECULES_SUBDIRECTORY_NAME + "/" + UID + fileExtension);
        return lockedChannel.newInputStream();
    }

    @Override
    public OutputStream getMoleculeOutputStream(String UID) throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForWriting(containerPath + "/" + MOLECULES_SUBDIRECTORY_NAME + "/" + UID + fileExtension);
        return lockedChannel.newOutputStream();
    }

    @Override
    public void removeMolecule(String UID) throws IOException {
        keyValueAccess.delete(containerPath + "/" + MOLECULES_SUBDIRECTORY_NAME + "/" + UID + fileExtension);
    }

    @Override
    public InputStream getMetadataInputStream(String metaUID) throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForReading(containerPath + "/" + METADATA_SUBDIRECTORY_NAME + "/" + metaUID + fileExtension);
        return lockedChannel.newInputStream();
    }

    @Override
    public OutputStream getMetadataOutputStream(String metaUID) throws IOException {
        final LockedChannel lockedChannel = keyValueAccess.lockForWriting(containerPath + "/" + METADATA_SUBDIRECTORY_NAME + "/" + metaUID + fileExtension);
        return lockedChannel.newOutputStream();
    }

    @Override
    public List<String> getMoleculeUIDs() {
        return this.keyValueAccess.listObjectKeys(containerPath + "/" + MOLECULES_SUBDIRECTORY_NAME);
    }

    @Override
    public List<String> getMetadataUIDs() {
        return this.keyValueAccess.listObjectKeys(containerPath + "/" + METADATA_SUBDIRECTORY_NAME);
    }

    @Override
    public void removeMetadata(String metaUID) throws IOException {
        keyValueAccess.delete(containerPath + "/" + METADATA_SUBDIRECTORY_NAME + "/" + metaUID + fileExtension);
    }

    @Override
    public String getURI() {
        return null;
    }

    @Override
    public boolean exists(String pathName) throws IOException {
        return this.keyValueAccess.exists(pathName);
    }

    @Override
    public String[] list(String pathName) throws IOException {
        if (!keyValueAccess.isDirectory(pathName)
                || pathName.endsWith("." + MOLECULE_ARCHIVE_STORE_ENDING)
                || pathName.endsWith("." + N5_DATASET_DIRECTORY_ENDING))
            return new String[0];

        return keyValueAccess.list(pathName);
    }

    @Override
    public String[] listDirectories(String pathName) throws IOException {
        if (!keyValueAccess.isDirectory(pathName)
                || pathName.endsWith("." + MOLECULE_ARCHIVE_STORE_ENDING)
                || pathName.endsWith("." + N5_DATASET_DIRECTORY_ENDING))
            return new String[0];

        return keyValueAccess.listDirectories(pathName);
    }

    @Override
    public String[] listFiles(String pathName) throws IOException {
        if (!keyValueAccess.isDirectory(pathName)
                || pathName.endsWith("." + MOLECULE_ARCHIVE_STORE_ENDING)
                || pathName.endsWith("." + N5_DATASET_DIRECTORY_ENDING))
            return new String[0];

        return keyValueAccess.listObjectKeys(pathName).toArray(new String[0]);
    }
}
