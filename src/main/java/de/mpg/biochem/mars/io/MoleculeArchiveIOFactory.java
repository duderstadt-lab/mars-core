package de.mpg.biochem.mars.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.client.builder.AwsClientBuilder;

public class MoleculeArchiveIOFactory {

    /**
     * Helper method.
     *
     * @param endpoint
     * @return
     */
    private static AmazonS3 createS3WithEndpoint(final String endpoint) {
        AmazonS3 s3;
        AWSCredentials credentials = null;
        try {
            credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
        } catch(final Exception e) {
            System.out.println( "Could not load AWS credentials, falling back to anonymous." );
        }
        final AWSStaticCredentialsProvider credentialsProvider =
                new AWSStaticCredentialsProvider(credentials == null ? new AnonymousAWSCredentials() : credentials);

        //US_EAST_2 is used as a dummy region.
        s3 = AmazonS3ClientBuilder.standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, Regions.US_EAST_2.getName()))
                .withCredentials(credentialsProvider)
                .build();

        return s3;
    }

    /**
     * Open an {@link MoleculeArchiveWriter} for AWS S3.
     *
     * @param s3Url url to the s3 object
     * @param endpointUrl endpoint url to the server
     * @return the N5AmazonS3Writer
     * @throws IOException the io exception
     */
    public MoleculeArchiveAmazonS3Writer openAWSS3WriterWithEndpoint(final String s3Url, final String endpointUrl) throws IOException {

        return new MoleculeArchiveAmazonS3Writer(
                createS3WithEndpoint(endpointUrl),
                new AmazonS3URI(s3Url));
    }

    /**
     * Open an {@link MoleculeArchiveSource} for AWS S3.
     *
     * @param s3Url url to the amazon s3 object
     * @param endpointUrl endpoint url for the server
     * @return the MoleculeArchiveAmazonS3Reader
     * @throws IOException the io exception
     */
    public MoleculeArchiveAmazonS3Source openAWSS3ReaderWithEndpoint(final String s3Url, final String endpointUrl) throws IOException {
        return new MoleculeArchiveAmazonS3Source(
                createS3WithEndpoint(endpointUrl),
                new AmazonS3URI(s3Url));
    }

    /**
     * Open an {@link MoleculeArchiveSource} for MoleculeArchive filesystem.
     *
     * @param file archive file
     * @return the MoleculeArchiveFSSource
     * @throws IOException the io exception
     */
    public MoleculeArchiveSource openFSSource(final File file) {
        return new MoleculeArchiveFSSource(file);
    }

    /**
     * Open an {@link MoleculeArchiveSource} for MoleculeArchive filesystem.
     *
     * @param file archive file
     * @return the MoleculeArchiveFSSource
     * @throws IOException the io exception
     */
    public MoleculeArchiveVirtualSource openFSVirtualSource(final File file) {
        return new MoleculeArchiveFSVirtualSource(file);
    }

    /**
     * Open an {@link MoleculeArchiveSource} for MoleculeArchive filesystem.
     *
     * @param file archive file
     * @return the MoleculeArchiveFSSource
     * @throws IOException the io exception
     */
    public MoleculeArchiveSource openSource(final URI uri) {
        //Figure out what type of source this is...

        //return new MoleculeArchiveFSSource(file);
    }

    /**
     * Open an {@link MoleculeArchiveSource} for MoleculeArchive filesystem.
     *
     * @param file archive file
     * @return the MoleculeArchiveFSSource
     * @throws IOException the io exception
     */
    public MoleculeArchiveVirtualSource openVirtualSource(final URI uri) {
        //Figure out what type of source this is...

        virtualDirectory.mkdirs();
        File metadataDir = new File(virtualDirectory.getAbsolutePath() +
                "/Metadata");
        File moleculesDir = new File(virtualDirectory.getAbsolutePath() +
                "/Molecules");

        metadataDir.mkdirs();
        moleculesDir.mkdirs();

        //return new MoleculeArchiveFSSource(file);
    }

    /**
     * Open an {@link MoleculeArchiveSource} based on some educated guessing from the url.
     *
     * @param url the location of the root location of the store
     * @return the N5Reader
     * @throws IOException the io exception
     */
    public MoleculeArchiveSource openReader(final String url) throws IOException {

        try {
            final URI uri = new URI(url);
            final String scheme = uri.getScheme();
            if (scheme == null);
            if (uri.getHost()!= null && scheme.equals("https") || scheme.equals("http")) {
                if (uri.getHost().matches(".*s3\\..*")) {
                    String[] parts = uri.getHost().split("\\.",3);
                    String bucket = parts[0];
                    String path = uri.getPath();
                    //ensures a single slash remains when no path is provided when opened by N5AmazonS3Reader.
                    if (path.equals("/")) path = "//";
                    String s3Url = "s3://" + bucket + path;
                    String endpointUrl = uri.getScheme() + "://" + parts[2] + ":" + uri.getPort();
                    return openAWSS3ReaderWithEndpoint(s3Url, endpointUrl);
                }
            }
        } catch (final URISyntaxException e) {}
        return openFSReader(url);
    }

    /**
     * Open an {@link MoleculeArchiveWriter} based on some educated guessing from the url.
     *
     * @param url the location of the root location of the store
     * @return the ArchiveWriter
     * @throws IOException the io exception
     */
    public MoleculeArchiveWriter openWriter(final String url) throws IOException {
        try {
            final URI uri = new URI(url);
            final String scheme = uri.getScheme();
            if (scheme == null);
            if (uri.getHost()!= null && scheme.equals("https") || scheme.equals("http")) {
                if (uri.getHost().matches(".*s3\\..*")) {
                    String[] parts = uri.getHost().split("\\.",3);
                    String bucket = parts[0];
                    String path = uri.getPath();
                    //ensures a single slash remains when no path is provided when opened by N5AmazonS3Reader.
                    if (path.equals("/")) path = "//";
                    String s3Url = "s3://" + bucket + path;
                    String endpointUrl = uri.getScheme() + "://" + parts[2] + ":" + uri.getPort();
                    return openAWSS3WriterWithEndpoint(s3Url, endpointUrl);
                }
            }
        } catch (final URISyntaxException e) {}
        return openFSWriter(url);
    }
}
