package de.mpg.biochem.mars.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

//import org.janelia.saalfeldlab.n5.s3.AmazonS3KeyValueAccess;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

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
    public String getArchiveType() throws IOException {
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
        //logService.warn("The file " + file.getName() +
        //        " doesn't have a MoleculeArchiveProperties field. Is this a proper yama file?");

        jParser.close();
        inputStream.close();

        return archiveType;
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
