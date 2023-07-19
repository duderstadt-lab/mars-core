package de.mpg.biochem.mars.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 * Location where Molecule Archives are stored.
 * Follows structure of N5Source.
 *
 * @author Karl Duderstadt
 */
public interface MoleculeArchiveStorage extends AutoCloseable {

    /**
     * Directory ending for n5 datasets.
     */
    public static final String N5_DATASET_DIRECTORY_ENDING = "n5";
    public static final String MOLECULE_ARCHIVE_ENDING = "yama";
    public static final String MOLECULE_ARCHIVE_STORE_ENDING = "yama.store";

    String getURI();

    /**
     * Test whether a group or dataset exists at a given path.
     *
     * @param pathName
     *            group path
     * @return true if the path exists
     * @throws IOException
     *             an exception is thrown if the source is not accessible
     */
    boolean exists(final String pathName) throws IOException;

    /**
     * Test whether a dataset exists at a given path.
     *
     * @param pathName
     *            dataset path
     * @return true if a dataset exists
     * @throws IOException
     *             an exception is thrown if the existence of a dataset at the given path cannot be determined.
     */
    default boolean datasetExists(final String pathName) throws IOException {

        return exists(pathName) && (pathName.endsWith(MOLECULE_ARCHIVE_ENDING) || pathName.endsWith(MOLECULE_ARCHIVE_STORE_ENDING));
    }

    /**
     * List all groups (including datasets) in a group.
     *
     * @param pathName
     *            group path
     * @return list of children
     * @throws IOException
     *             an exception is thrown if pathName is not a valid group
     */
    String[] list(final String pathName) throws IOException;

    /**
     * Recursively list all groups and datasets in the given path.
     * Only paths that satisfy the provided filter will be included, but the
     * children of paths that were excluded may be included (filter does not
     * apply to the subtree).
     *
     * @param pathName
     *            base group path
     * @param filter
     *            filter for children to be included
     * @return list of child groups and datasets
     * @throws IOException
     * 		an exception is thrown if pathName is not a valid group
     */
    default String[] deepList(
            final String pathName,
            final Predicate<String> filter) throws IOException {

        final String groupSeparator = getGroupSeparator();
        final String normalPathName = pathName
                .replaceAll(
                        "(^" + groupSeparator + "*)|(" + groupSeparator + "*$)",
                        "");

        final List<String> absolutePaths = deepList(this, normalPathName, false, filter);
        return absolutePaths
                .stream()
                .map(a -> a.replaceFirst(normalPathName + "(" + groupSeparator + "?)", ""))
                .filter(a -> !a.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Recursively list all groups and datasets in the given path.
     *
     * @param pathName
     *            base group path
     * @return list of groups and datasets
     * @throws IOException
     * 				an exception is thrown if pathName is not a valid group
     */
    default String[] deepList(final String pathName) throws IOException {

        return deepList(pathName, a -> true);
    }

    /**
     * Recursively list all datasets in the given path. Only paths that satisfy the
     * provided filter will be included, but the children of paths that were
     * excluded may be included (filter does not apply to the subtree).
     *
     * <p>
     * This method delivers the same results as
     * </p>
     *
     * <pre>
     * {@code
     * n5.deepList(prefix, a -> {
     * 	try {
     * 		return n5.datasetExists(a) && filter.test(a);
     * 	} catch (final IOException e) {
     * 		return false;
     * 	}
     * });
     * }
     * </pre>
     * <p>
     * but will execute {@link #datasetExists(String)} only once per node. This can
     * be relevant for performance on high latency backends such as cloud stores.
     * </p>
     *
     * @param pathName base group path
     * @param filter   filter for datasets to be included
     * @return list of groups
     * @throws IOException
     * 				an exception is thrown if pathName is not a valid group
     */
    default String[] deepListDatasets(
            final String pathName,
            final Predicate<String> filter) throws IOException {

        final String groupSeparator = getGroupSeparator();
        final String normalPathName = pathName
                .replaceAll(
                        "(^" + groupSeparator + "*)|(" + groupSeparator + "*$)",
                        "");

        final List<String> absolutePaths = deepList(this, normalPathName, true, filter);
        return absolutePaths
                .stream()
                .map(a -> a.replaceFirst(normalPathName + "(" + groupSeparator + "?)", ""))
                .filter(a -> !a.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Recursively list all including datasets in the given path.
     *
     * <p>
     * This method delivers the same results as
     * </p>
     *
     * <pre>
     * {@code
     * n5.deepList(prefix, a -> {
     * 	try {
     * 		return n5.datasetExists(a);
     * 	} catch (final IOException e) {
     * 		return false;
     * 	}
     * });
     * }
     * </pre>
     * <p>
     * but will execute {@link #datasetExists(String)} only once per node. This
     * can
     * be relevant for performance on high latency backends such as cloud
     * stores.
     * </p>
     *
     * @param pathName
     *            base group path
     * @return list of groups
     * @throws IOException
     * 				an exception is thrown if pathName is not a valid group
     */
    default String[] deepListDatasets(final String pathName) throws IOException {

        return deepListDatasets(pathName, a -> true);
    }

    /**
     * Helper method to recursively list all groups and datasets. This method is not part of the
     * public API and is accessible only because Java 8 does not support private
     * interface methods yet.
     *
     * TODO make private when committing to Java versions newer than 8
     *
     * @param store        the MoleculeArchiveStore reader
     * @param pathName     the base group path
     * @param datasetsOnly true if only dataset paths should be returned
     * @param filter       a dataset filter
     * @return the list of all children
     * @throws IOException
     * 				an exception is thrown if pathName is not a valid group
     */
    static ArrayList<String> deepList(
            final MoleculeArchiveStorage store,
            final String pathName,
            final boolean datasetsOnly,
            final Predicate<String> filter) throws IOException {

        final ArrayList<String> children = new ArrayList<>();
        final boolean isDataset = store.datasetExists(pathName);

        final boolean passDatasetTest = datasetsOnly && !isDataset;
        if (!passDatasetTest && filter.test(pathName))
            children.add(pathName);

        if (!isDataset) {
            final String groupSeparator = store.getGroupSeparator();
            final String[] baseChildren = store.list(pathName);
            for (final String child : baseChildren)
                children.addAll(deepList(store, pathName + groupSeparator + child, datasetsOnly, filter));
        }

        return children;
    }

    /**
     * Recursively list all groups (including datasets) in the given group, in
     * parallel, using the given {@link ExecutorService}. Only paths that
     * satisfy
     * the provided filter will be included, but the children of paths that were
     * excluded may be included (filter does not apply to the subtree).
     *
     * @param pathName
     *            base group path
     * @param filter
     *            filter for children to be included
     * @param executor
     *            executor service
     * @return list of datasets
     * @throws IOException
     * 				an exception is thrown if pathName is not a valid group
     * @throws ExecutionException
     *             the execution exception
     * @throws InterruptedException
     *             the interrupted exception
     */
    default String[] deepList(
            final String pathName,
            final Predicate<String> filter,
            final ExecutorService executor) throws IOException, InterruptedException, ExecutionException {

        final String groupSeparator = getGroupSeparator();
        final String normalPathName = pathName.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
        final ArrayList<String> results = new ArrayList<String>();
        final LinkedBlockingQueue<Future<String>> datasetFutures = new LinkedBlockingQueue<>();
        deepListHelper(this, normalPathName, false, filter, executor, datasetFutures);

        datasetFutures.poll().get(); // skip self
        while (!datasetFutures.isEmpty()) {
            final String result = datasetFutures.poll().get();
            if (result != null)
                results.add(result.substring(normalPathName.length() + groupSeparator.length()));
        }

        return results.stream().toArray(String[]::new);
    }

    /**
     * Recursively list all groups (including datasets) in the given group, in
     * parallel, using the given {@link ExecutorService}.
     *
     * @param pathName
     *            base group path
     * @param executor
     *            executor service
     * @return list of groups
     * @throws IOException
     * 				an exception is thrown if pathName is not a valid group
     * @throws ExecutionException
     *             the execution exception
     * @throws InterruptedException
     *             this exception is thrown if execution is interrupted
     */
    default String[] deepList(
            final String pathName,
            final ExecutorService executor) throws IOException, InterruptedException, ExecutionException {

        return deepList(pathName, a -> true, executor);
    }

    /**
     * Recursively list all datasets in the given group, in parallel, using the
     * given {@link ExecutorService}. Only paths that satisfy the provided
     * filter
     * will be included, but the children of paths that were excluded may be
     * included (filter does not apply to the subtree).
     *
     * <p>
     * This method delivers the same results as
     * </p>
     *
     * <pre>
     * {@code
     * n5.deepList(prefix, a -> {
     * 	try {
     * 		return store.datasetExists(a) && filter.test(a);
     * 	} catch (final IOException e) {
     * 		return false;
     * 	}
     * }, exec);
     * }
     * </pre>
     * <p>
     * but will execute {@link #exists(String)} only once per node. This
     * can
     * be relevant for performance on high latency backends such as cloud
     * stores.
     * </p>
     *
     * @param pathName
     *            base group path
     * @param filter
     *            filter for datasets to be included
     * @param executor
     *            executor service
     * @return list of datasets
     * @throws IOException
     * 				an exception is thrown if pathName is not a valid group
     * @throws ExecutionException
     *             the execution exception
     * @throws InterruptedException
     *             this exception is thrown if execution is interrupted
     */
    default String[] deepListDatasets(
            final String pathName,
            final Predicate<String> filter,
            final ExecutorService executor) throws IOException, InterruptedException, ExecutionException {

        final String groupSeparator = getGroupSeparator();
        final String normalPathName = pathName.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
        final ArrayList<String> results = new ArrayList<String>();
        final LinkedBlockingQueue<Future<String>> datasetFutures = new LinkedBlockingQueue<>();
        deepListHelper(this, normalPathName, true, filter, executor, datasetFutures);

        datasetFutures.poll().get(); // skip self
        while (!datasetFutures.isEmpty()) {
            final String result = datasetFutures.poll().get();
            if (result != null)
                results.add(result.substring(normalPathName.length() + groupSeparator.length()));
        }

        return results.stream().toArray(String[]::new);
    }

    /**
     * Recursively list all datasets in the given group, in parallel, using the
     * given {@link ExecutorService}.
     *
     * <p>
     * This method delivers the same results as
     * </p>
     *
     * <pre>
     * {@code
     * n5.deepList(prefix, a -> {
     * 	try {
     * 		return store.datasetExists(a);
     * 	} catch (final IOException e) {
     * 		return false;
     * 	}
     * }, exec);
     * }
     * </pre>
     * <p>
     * but will execute {@link #exists(String)} only once per node. This
     * can
     * be relevant for performance on high latency backends such as cloud
     * stores.
     * </p>
     *
     * @param pathName
     *            base group path
     * @param executor
     *            executor service
     * @return list of groups
     * @throws IOException
     * 				an exception is thrown if pathName is not a valid group
     * @throws ExecutionException
     *             the execution exception
     * @throws InterruptedException
     *             this exception is thrown if execution is interrupted
     */
    default String[] deepListDatasets(
            final String pathName,
            final ExecutorService executor) throws IOException, InterruptedException, ExecutionException {

        return deepListDatasets(pathName, a -> true, executor);
    }

    /**
     * Helper method for parallel deep listing. This method is not part of the
     * public API and is accessible only because Java 8 does not support private
     * interface methods yet.
     *
     * TODO make private when committing to Java versions newer than 8
     *
     * @param store
     *            the MoleculeArchiveStore reader
     * @param path
     *            the base path
     * @param datasetsOnly
     *            true if only dataset paths should be returned
     * @param filter
     *            filter for datasets to be included
     * @param executor
     *            the executor service
     * @param datasetFutures
     *            result futures
     */
    static void deepListHelper(
            final MoleculeArchiveStorage store,
            final String path,
            final boolean datasetsOnly,
            final Predicate<String> filter,
            final ExecutorService executor,
            final LinkedBlockingQueue<Future<String>> datasetFutures) {

        final String groupSeparator = store.getGroupSeparator();

        datasetFutures.add(executor.submit(() -> {

            boolean isDataset = false;
            try {
                isDataset = store.datasetExists(path);
            } catch (final IOException e) {}

            if (!isDataset) {
                String[] children = null;
                try {
                    children = store.list(path);
                    for (final String child : children) {
                        final String fullChildPath = path + groupSeparator + child;
                        deepListHelper(store, fullChildPath, datasetsOnly, filter, executor, datasetFutures);
                    }
                } catch (final IOException e) {}
            }
            final boolean passDatasetTest = datasetsOnly && !isDataset;
            return !passDatasetTest && filter.test(path) ? path : null;
        }));
    }

    /**
     * Returns the symbol that is used to separate nodes in a group path.
     *
     * @return the group separator
     */
    default String getGroupSeparator() {

        return "/";
    }

    /**
     * Creates a group path by concatenating all nodes with the node separator
     * defined by {@link #getGroupSeparator()}. The string will not have a
     * leading or trailing node separator symbol.
     *
     * @param nodes a collection of child node names
     * @return the full group path
     */
    default String groupPath(final String... nodes) {

        if (nodes == null || nodes.length == 0)
            return "";

        final String groupSeparator = getGroupSeparator();
        final StringBuilder builder = new StringBuilder(nodes[0]);

        for (int i = 1; i < nodes.length; ++i) {

            builder.append(groupSeparator);
            builder.append(nodes[i]);
        }

        return builder.toString();
    }

    /**
     * Default implementation of {@link AutoCloseable#close()} for all
     * implementations that do not hold any closable resources.
     */
    @Override
    default void close() {}
}
