package de.mpg.biochem.mars.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

//import org.janelia.saalfeldlab.n5.s3.AmazonS3KeyValueAccess;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;

//Placed here for testing purposes but will ultimately go into mars-minio..

public class MoleculeArchiveAmazonS3Source implements MoleculeArchiveSource {
    protected final AmazonS3 s3;
    protected final String bucketName;
    protected final String containerPath;

    //Change to interface in the future...
    protected final MoleculeArchiveAmazonS3KeyValueAccess keyValueAccess;

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
    public MoleculeArchiveAmazonS3Source(final AmazonS3 s3, final AmazonS3URI containerURI) throws IOException {
        this(s3, containerURI.getBucket(), containerURI.getKey());
    }

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
    public MoleculeArchiveAmazonS3Source(final AmazonS3 s3, final String bucketName) throws IOException {
        this(s3, bucketName, "/");
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
    public MoleculeArchiveAmazonS3Source(
            final AmazonS3 s3,
            final String bucketName,
            final String containerPath) throws IOException {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.containerPath = containerPath;
        this.keyValueAccess = new MoleculeArchiveAmazonS3KeyValueAccess(s3, bucketName);
    }

    @Override
    public String getName() {
        String[] parts = containerPath.split("/");
        return parts[parts.length - 1];
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
}
