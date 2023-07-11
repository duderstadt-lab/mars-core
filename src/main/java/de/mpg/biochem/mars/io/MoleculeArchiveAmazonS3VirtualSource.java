package de.mpg.biochem.mars.io;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchiveIndex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MoleculeArchiveAmazonS3VirtualSource implements MoleculeArchiveVirtualSource {
    protected final AmazonS3 s3;
    protected final String bucketName;
    protected final String containerPath;

    protected final String fileExtension;

    //Change to interface in the future...
    protected final MoleculeArchiveAmazonS3KeyValueAccess keyValueAccess;

    /**
     * Opens an
     *
     * If the bucket does not exist, it will not be created and
     * all subsequent attempts to read attributes, groups, or datasets will fail.
     *
     * @param s3
     * @param bucketName
     * @throws IOException
     */
    public MoleculeArchiveAmazonS3VirtualSource(final AmazonS3 s3, final String bucketName) throws IOException {

        this(s3, bucketName, "/");
    }

    /**
     * Opens an
     *
     * If the bucket and/or container does not exist, it will not be created and
     * all subsequent attempts to read attributes, groups, or datasets will fail.
     *
     * @param s3
     * @param containerURI
     * @throws IOException
     */
    public MoleculeArchiveAmazonS3VirtualSource(final AmazonS3 s3, final AmazonS3URI containerURI) throws IOException {

        this(s3, containerURI.getBucket(), containerURI.getKey());
    }

    /**
     * Opens an
     *
     * If the bucket and/or container does not exist, it will not be created and
     * all subsequent attempts to read attributes, groups, or datasets will fail.
     *
     * @param s3
     * @param bucketName
     * @param containerPath
     * @throws IOException
     */
    public MoleculeArchiveAmazonS3VirtualSource(
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
    public String getName() {
        String[] parts = containerPath.split("/");
        return parts[parts.length - 1];
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
}
