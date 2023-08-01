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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.NonReadableChannelException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class MoleculeArchiveAmazonS3KeyValueAccess {
    private final AmazonS3 s3;
    private final String bucketName;

    /**
     * Opens an {@link AmazonS3} client and a given bucket name.
     *
     * @param s3 the s3 instance
     * @param bucketName the bucket name
     * @throws IOException if the access could not be created
     */
    public MoleculeArchiveAmazonS3KeyValueAccess(final AmazonS3 s3, final String bucketName) throws IOException {

        this.s3 = s3;
        this.bucketName = bucketName;

        if (!s3.doesBucketExistV2(bucketName)) throw new IOException("Bucket " + bucketName + " does not exist.");
    }

    public String[] components(final String path) {

        return Arrays.stream(path.split("/"))
                .filter(x -> !x.isEmpty())
                .toArray(String[]::new);
    }


    public String compose(final String... components) {

        if (components == null || components.length == 0)
            return null;

        return normalize(
                Arrays.stream(components)
                        .filter(x -> !x.isEmpty())
                        .collect(Collectors.joining("/"))
        );
    }


    public String parent(final String path) {

        final String[] components = components(path);
        final String[] parentComponents =Arrays.copyOf(components, components.length - 1);

        return compose(parentComponents);
    }

    public String normalize(final String path) {

        return normalizeGroupPath(path);
    }

    /**
     * Normalize a group path relative to a container's root, resulting in
     * removal of redundant "/", "./", resolution of relative "../",
     * and removal of leading slashes.
     *
     * @param path
     *            to normalize
     * @return the normalized path
     */
    public static String normalizeGroupPath(final String path) {

        /*
         * Alternatively, could do something like the below in every
         * KeyValueReader implementation
         *
         * return keyValueAccess.relativize( N5URI.normalizeGroupPath(path),
         * basePath);
         *
         * has to be in the implementations, since KeyValueAccess doesn't have a
         * basePath.
         */
        return normalizePath(path.startsWith("/") || path.startsWith("\\") ? path.substring(1) : path);
    }

    /**
     * Normalize a POSIX path, resulting in removal of redundant "/", "./", and
     * resolution of relative "../".
     * <p>
     * NOTE: currently a private helper method only used by normalizeGroupPath(String).
     * 	It's safe to do in that case since relative group paths should always be POSIX compliant.
     * 	A new helper method to understand other path types (e.g. Windows) may be necessary eventually.
     *
     * @param path
     *            to normalize
     * @return the normalized path
     */
    private static String normalizePath(String path) {

        path = path == null ? "" : path;
        final char[] pathChars = path.toCharArray();

        final List<String> tokens = new ArrayList<>();
        final StringBuilder curToken = new StringBuilder();
        boolean escape = false;
        for (final char character : pathChars) {
            /* Skip if we last saw escape */
            if (escape) {
                escape = false;
                curToken.append(character);
                continue;
            }
            /* Check if we are escape character */
            if (character == '\\') {
                escape = true;
            } else if (character == '/') {
                if (tokens.isEmpty() && curToken.length() == 0) {
                    /* If we are root, and the first token, then add the '/' */
                    curToken.append(character);
                }

                /*
                 * The current token is complete, add it to the list, if it
                 * isn't empty
                 */
                final String newToken = curToken.toString();
                if (!newToken.isEmpty()) {
                    /*
                     * If our token is '..' then remove the last token instead
                     * of adding a new one
                     */
                    if (newToken.equals("..")) {
                        tokens.remove(tokens.size() - 1);
                    } else {
                        tokens.add(newToken);
                    }
                }
                /* reset for the next token */
                curToken.setLength(0);
            } else {
                curToken.append(character);
            }
        }
        final String lastToken = curToken.toString();
        if (!lastToken.isEmpty()) {
            if (lastToken.equals("..")) {
                tokens.remove(tokens.size() - 1);
            } else {
                tokens.add(lastToken);
            }
        }
        if (tokens.isEmpty())
            return "";
        String root = "";
        if (tokens.get(0).equals("/")) {
            tokens.remove(0);
            root = "/";
        }
        return root + tokens
                .stream()
                .filter(it -> !it.equals("."))
                .filter(it -> !it.isEmpty())
                .reduce((l, r) -> l + "/" + r)
                .orElse("");
    }

    /**
     * Test whether the {@code normalPath} exists.
     * <p>
     * Removes leading slash from {@code normalPath}, and then checks whether
     * either {@code path} or {@code path + "/"} is a key.
     *
     * @param normalPath is expected to be in normalized form, no further
     * 		efforts are made to normalize it.
     * @return {@code true} if {@code path} exists, {@code false} otherwise
     */
    public boolean exists(final String normalPath) {

        return isDirectory(normalPath) || isFile(normalPath);
    }

    /**
     * Check existence of the given {@code key}.
     *
     * @return {@code true} if {@code key} exists.
     */
    private boolean keyExists(final String key) {
        final ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix(key)
                .withMaxKeys(1);
        final ListObjectsV2Result objectsListing = s3.listObjectsV2(listObjectsRequest);
        return objectsListing.getKeyCount() > 0;
    }

    /**
     * When listing children objects for a group, must append a delimiter to the path (e.g. group/data/).
     * This is necessary for not including wrong objects in the filtered set
     * (e.g. group/data-2/attributes.json when group/data is passed without the last slash).
     *
     * @param path the path
     * @return the path with a trailing slash
     */
    public static String addTrailingSlash(final String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * When absolute paths are passed (e.g. /group/data), AWS S3 service creates an additional root folder with an empty name.
     * This method removes the root slash symbol and returns the corrected path.
     *
     * @param path the path
     * @return the path without the leading slash
     */
    public static String removeLeadingSlash(final String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * Test whether the path is a directory.
     * <p>
     * Appends trailing "/" to {@code normalPath} if there is none, removes
     * leading "/", and then checks whether resulting {@code path} is a key.
     *
     * @param normalPath is expected to be in normalized form, no further
     * 		efforts are made to normalize it.
     * @return {@code true} if {@code path} (with trailing "/") exists as a key, {@code false} otherwise
     */

    public boolean isDirectory(final String normalPath) {
        final String key = removeLeadingSlash(addTrailingSlash(normalPath));
        return key.isEmpty() || keyExists(key);
    }

    /**
     * Test whether the path is a file.
     * <p>
     * Checks whether {@code normalPath} has no trailing "/", then removes
     * leading "/" and checks whether the resulting {@code path} is a key.
     *
     * @param normalPath is expected to be in normalized form, no further
     * 		efforts are made to normalize it.
     * @return {@code true} if {@code path} exists as a key and has no trailing slash, {@code false} otherwise
     */

    public boolean isFile(final String normalPath) {
        return !normalPath.endsWith("/") && keyExists(removeLeadingSlash(normalPath));
    }


    public LockedChannel lockForReading(final String normalPath) throws IOException {
        return new S3ObjectChannel(removeLeadingSlash(normalPath), true);
    }


    public LockedChannel lockForWriting(final String normalPath) throws IOException {
        return new S3ObjectChannel(removeLeadingSlash(normalPath), false);
    }

    public List<String> listObjectKeys(final String normalPath) {
        final List<String> keys = new ArrayList<>();
        final String prefix = removeLeadingSlash(addTrailingSlash(normalPath));
        final ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix(prefix)
                .withDelimiter("/");
        ListObjectsV2Result objectsListing;
        do {
            objectsListing = s3.listObjectsV2(listObjectsRequest);
            for (final S3ObjectSummary objectSummary : objectsListing.getObjectSummaries()) {
                keys.add(objectSummary.getKey());
            }
            listObjectsRequest.setContinuationToken(objectsListing.getNextContinuationToken());
        } while (objectsListing.isTruncated());
        return keys;
    }

    public String[] listDirectories(final String normalPath) {
        return list(normalPath, true);
    }

    private String[] list(final String normalPath, final boolean onlyDirectories) {
        final List<String> items = new ArrayList<>();
        final String prefix = removeLeadingSlash(addTrailingSlash(normalPath));
        final ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix(prefix)
                .withDelimiter("/");
        ListObjectsV2Result objectsListing;
        do {
            objectsListing = s3.listObjectsV2(listObjectsRequest);
            for (final String commonPrefix : objectsListing.getCommonPrefixes()) items.add(lastGroupName(commonPrefix));
            if (!onlyDirectories)
                for (final S3ObjectSummary objectSummary : objectsListing.getObjectSummaries()) items.add(lastGroupName(objectSummary.getKey()));
            listObjectsRequest.setContinuationToken(objectsListing.getNextContinuationToken());
        } while (objectsListing.isTruncated());
        return items.toArray(new String[items.size()]);
    }

    private String lastGroupName(final String pathName) {
        String[] parts = pathName.split("/");
        return parts[parts.length - 1];
    }

    public String[] list(final String normalPath) throws IOException {
        return list(normalPath, false);
    }


    public void createDirectories(final String normalPath) throws IOException {

        String path = "";
        for (final String component : components(removeLeadingSlash(normalPath))) {
            path = addTrailingSlash(compose(path, component));
            if (path.equals("/")) {
                continue;
            }
            final ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(0);
            s3.putObject(
                    bucketName,
                    path,
                    new ByteArrayInputStream(new byte[0]),
                    metadata);
        }
    }

    public void delete(final String normalPath) throws IOException {

        if (!s3.doesBucketExistV2(bucketName))
            return;

        // remove bucket when deleting "/"
        if (normalPath.equals(normalize("/"))) {

            // need to delete all objects before deleting the bucket
            // see: https://docs.aws.amazon.com/AmazonS3/latest/userguide/delete-bucket.html
            ObjectListing objectListing = s3.listObjects(bucketName);
            while (true) {
                final Iterator<S3ObjectSummary> objIter = objectListing.getObjectSummaries().iterator();
                while (objIter.hasNext()) {
                    s3.deleteObject(bucketName, objIter.next().getKey());
                }

                // If the bucket contains many objects, the listObjects() call
                // might not return all of the objects in the first listing. Check to
                // see whether the listing was truncated. If so, retrieve the next page of objects
                // and delete them.
                if (objectListing.isTruncated()) {
                    objectListing = s3.listNextBatchOfObjects(objectListing);
                } else {
                    break;
                }
            }

            s3.deleteBucket(bucketName);
            return;
        }

        final String path = removeLeadingSlash(normalPath);

        if (!path.endsWith("/")) {
            s3.deleteObjects(new DeleteObjectsRequest(bucketName)
                    .withKeys(new String[]{path}));
        }

        final String prefix = addTrailingSlash(path);
        final ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix(prefix);
        ListObjectsV2Result objectsListing;
        do {
            objectsListing = s3.listObjectsV2(listObjectsRequest);
            final List<String> objectsToDelete = new ArrayList<>();
            for (final S3ObjectSummary object : objectsListing.getObjectSummaries())
                objectsToDelete.add(object.getKey());

            if (!objectsToDelete.isEmpty()) {
                s3.deleteObjects(new DeleteObjectsRequest(bucketName)
                        .withKeys(objectsToDelete.toArray(new String[objectsToDelete.size()])));
            }
            listObjectsRequest.setContinuationToken(objectsListing.getNextContinuationToken());
        } while (objectsListing.isTruncated());
    }

    /**
     * Helper class that drains the rest of the {@link S3ObjectInputStream} on {@link #close()}.
     *
     * Without draining the stream AWS S3 SDK sometimes outputs the following warning message:
     * "... Not all bytes were read from the S3ObjectInputStream, aborting HTTP connection ...".
     *
     * Draining the stream helps to avoid this warning and possibly reuse HTTP connections.
     *
     * Calling {@link S3ObjectInputStream#abort()} does not prevent this warning as discussed here:
     * https://github.com/aws/aws-sdk-java/issues/1211
     */
    private static class S3ObjectInputStreamDrain extends InputStream {

        private final S3ObjectInputStream in;
        private boolean closed;

        public S3ObjectInputStreamDrain(final S3ObjectInputStream in) {

            this.in = in;
        }

        @Override
        public int read() throws IOException {

            return in.read();
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {

            return in.read(b, off, len);
        }

        @Override
        public boolean markSupported() {

            return in.markSupported();
        }

        @Override
        public void mark(final int readlimit) {

            in.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {

            in.reset();
        }

        @Override
        public int available() throws IOException {

            return in.available();
        }

        @Override
        public long skip(final long n) throws IOException {

            return in.skip(n);
        }

        @Override
        public void close() throws IOException {

            if (!closed) {
                do {
                    in.skip(in.available());
                } while (read() != -1);
                in.close();
                closed = true;
            }
        }
    }

    private class S3ObjectChannel implements LockedChannel {

        protected final String path;
        final boolean readOnly;
        private final ArrayList<Closeable> resources = new ArrayList<>();

        protected S3ObjectChannel(final String path, final boolean readOnly) throws IOException {

            this.path = path;
            this.readOnly = readOnly;
        }

        private void checkWritable() {

            if (readOnly) {
                throw new NonReadableChannelException();
            }
        }

        @Override
        public InputStream newInputStream() throws IOException {
            final S3ObjectInputStream in = s3.getObject(bucketName, path).getObjectContent();
            final S3ObjectInputStreamDrain s3in = new S3ObjectInputStreamDrain(in);
            synchronized (resources) {
                resources.add(s3in);
            }
            return s3in;
        }

        @Override
        public Reader newReader() throws IOException {

            final InputStreamReader reader = new InputStreamReader(newInputStream(), StandardCharsets.UTF_8);
            synchronized (resources) {
                resources.add(reader);
            }
            return reader;
        }

        @Override
        public OutputStream newOutputStream() throws IOException {

            checkWritable();
            return new S3OutputStream();
        }

        @Override
        public Writer newWriter() throws IOException {

            checkWritable();
            final OutputStreamWriter writer = new OutputStreamWriter(newOutputStream(), StandardCharsets.UTF_8);
            synchronized (resources) {
                resources.add(writer);
            }
            return writer;
        }

        @Override
        public void close() throws IOException {

            synchronized (resources) {
                for (final Closeable resource : resources)
                    resource.close();
                resources.clear();
            }
        }

        final class S3OutputStream extends OutputStream {
            private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

            private boolean closed = false;

            @Override
            public void write(final byte[] b, final int off, final int len) throws IOException {

                buf.write(b, off, len);
            }

            @Override
            public void write(final int b) throws IOException {

                buf.write(b);
            }

            @Override
            public synchronized void close() throws IOException {

                if (!closed) {
                    closed = true;
                    final byte[] bytes = buf.toByteArray();
                    final ObjectMetadata objectMetadata = new ObjectMetadata();
                    objectMetadata.setContentLength(bytes.length);
                    try (final InputStream data = new ByteArrayInputStream(bytes)) {
                        s3.putObject(bucketName, path, data, objectMetadata);
                    }
                    buf.close();
                }
            }
        }
    }
}
