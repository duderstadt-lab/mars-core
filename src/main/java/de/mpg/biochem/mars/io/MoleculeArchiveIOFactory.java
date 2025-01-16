/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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
    private static AmazonS3 createS3SourceWithEndpoint(final String endpoint) {
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
     * Open an {@link MoleculeArchiveSource} for AWS S3.
     *
     * @param s3Url url to the amazon s3 object
     * @param endpointUrl endpoint url for the server
     * @return the MoleculeArchiveAmazonS3Reader
     * @throws IOException the io exception
     */
    public MoleculeArchiveAmazonS3Source openAWSS3SourceWithEndpoint(final String s3Url, final String endpointUrl) throws IOException {
        return new MoleculeArchiveAmazonS3Source(
                createS3SourceWithEndpoint(endpointUrl),
                new AmazonS3URI(s3Url));
    }

    /**
     * Open an {@link MoleculeArchiveSource} for MoleculeArchive filesystem.
     *
     * @param file archive file
     * @return the MoleculeArchiveFSSource
     * @throws IOException the io exception
     */
    public MoleculeArchiveFSSource openFSSource(final File file) throws IOException {
        return new MoleculeArchiveFSSource(file);
    }

    /**
     * Open an {@link MoleculeArchiveSource} for MoleculeArchive filesystem.
     *
     * @param uri archive location
     * @return the MoleculeArchiveFSSource
     * @throws IOException the io exception
     */
    public MoleculeArchiveSource openSource(final URI uri) throws IOException {
        return openSource(uri.toString());
    }

    /**
     * Open an {@link MoleculeArchiveSource} based on some educated guessing from the url.
     *
     * @param url archive location
     * @return the N5Reader
     * @throws IOException the io exception
     */
    public MoleculeArchiveSource openSource(final String url) throws IOException {
        try {
            final URI uri = new URI(url);
            final String scheme = uri.getScheme();
            if (scheme == null);
            else if (uri.getHost() != null && scheme.equals("https") || scheme.equals("http")) {
                if (uri.getHost().matches(".*s3\\..*")) {
                    String[] parts = uri.getHost().split("\\.",3);
                    String bucket = parts[0];
                    String path = uri.getPath();
                    //ensures a single slash remains when no path is provided when opened by N5AmazonS3Reader.
                    if (path.equals("/")) path = "//";
                    String s3Url = "s3://" + bucket + path;
                    String endpointUrl = uri.getScheme() + "://" + parts[2] + ":" + uri.getPort();
                    return openAWSS3SourceWithEndpoint(s3Url, endpointUrl);
                }
            }
        } catch (final URISyntaxException e) {}

        return openFSSource(new File(url));
    }
}
