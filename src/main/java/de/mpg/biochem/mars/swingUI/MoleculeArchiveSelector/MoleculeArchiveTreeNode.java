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

package de.mpg.biochem.mars.swingUI.MoleculeArchiveSelector;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import de.mpg.biochem.mars.io.*;

/**
 * A node representing a dataset or group in a container
 * and child nodes if any exist.
 *
 * @author Caleb Hulbert
 * @author John Bogovic
 *
 */
public class MoleculeArchiveTreeNode {

    private final String path;

    private final ArrayList<MoleculeArchiveTreeNode> children;

    public MoleculeArchiveTreeNode(final String path) {

        this.path = path.trim();
        children = new ArrayList<>();
    }

    public static Stream<MoleculeArchiveTreeNode> flattenTree(MoleculeArchiveTreeNode root) {

        return Stream.concat(
                Stream.of(root),
                root.childrenList().stream().flatMap(MoleculeArchiveTreeNode::flattenTree));
    }

    public String getNodeName() {

        return Paths.get(removeLeadingSlash(path)).getFileName().toString();
    }

    public String getParentPath() {

        return Paths.get(removeLeadingSlash(path)).getParent().toString();
    }

    /**
     * Adds a node as a child of this node.
     *
     * @param child the child node
     */
    public void add(final MoleculeArchiveTreeNode child) {

        children.add(child);
    }

    public void remove(final MoleculeArchiveTreeNode child) {

        children.remove(child);
    }

    public void removeAllChildren() {

        children.clear();
    }

    public List<MoleculeArchiveTreeNode> childrenList() {

        return children;
    }

    public Optional<MoleculeArchiveTreeNode> getDescendant( String path ) {

        return getDescendants( x -> x.getPath().endsWith(path)).findFirst();
    }

    /**
     * Adds a node at the specified full path and any parent nodes along the path,
     * if they do not already exist. Returns the node at the specified path.
     *
     * @param path the full path to node
     * @return the node
     */
    public MoleculeArchiveTreeNode addPath( final String path ) {
        return addPath( path, x -> new MoleculeArchiveTreeNode( x ));
    }

    /**
     * Adds a node at the specified full path and any parent nodes along the path,
     * if they do not already exist. Returns the node at the specified path.
     *
     * @param path the full path to node
     * @param constructor function creating a node from a path
     * @return the node
     */
    public MoleculeArchiveTreeNode addPath( final String path, Function<String, MoleculeArchiveTreeNode> constructor ) {
        final String normPath = removeLeadingSlash(path);

        if( !getPath().isEmpty() && !normPath.startsWith(getPath()))
            return null;

        if( this.path.equals(normPath))
            return this;

        final String relativePath = removeLeadingSlash( normPath.replaceAll(this.path, ""));
        final int sepIdx = relativePath.indexOf("/");
        final String childName;
        if( sepIdx < 0 )
            childName = relativePath;
        else
            childName = relativePath.substring(0, sepIdx);

        // get the appropriate child along the path if it exists, otherwise add it
        MoleculeArchiveTreeNode child = null;
        Stream<MoleculeArchiveTreeNode> cs = children.stream().filter( n -> n.getNodeName().equals(childName));
        Optional<MoleculeArchiveTreeNode> copt = cs.findFirst();
        if( copt.isPresent() )
            child = copt.get();
        else {
            child = constructor.apply( this.path.isEmpty() ? childName : this.path + "/" + childName );
            add( child );
        }
        return child.addPath(normPath);
    }

    public Stream<MoleculeArchiveTreeNode> getDescendants( Predicate<MoleculeArchiveTreeNode> filter ) {

        return MoleculeArchiveTreeNode.flattenTree(this).filter( filter );
    }

    public String getPath() {

        return path;
    }

    @Override
    public String toString() {

        final String nodeName = getNodeName();
        return nodeName.isEmpty() ? "/" : nodeName;
    }

    public boolean structureEquals( MoleculeArchiveTreeNode other )
    {
        final boolean samePath = getPath().equals(other.getPath());
        if( !samePath )
            return false;

        boolean childrenEqual = true;
        for( MoleculeArchiveTreeNode c : childrenList()) {
            Optional<MoleculeArchiveTreeNode> otherChildOpt = other.childrenList().stream()
                    .filter( x -> x.getNodeName().equals( c.getNodeName()))
                    .findFirst();

            childrenEqual = childrenEqual &&
                    otherChildOpt.map( x -> x.structureEquals(c))
                            .orElse(false);

            if( !childrenEqual )
                break;
        }
        return childrenEqual;
    }

    public String printRecursive() {

        return printRecursiveHelper(this, "");
    }

    private static String printRecursiveHelper(MoleculeArchiveTreeNode node, String prefix) {

        StringBuffer out = new StringBuffer();
        out.append(prefix + node.path + "\n");
        for (MoleculeArchiveTreeNode c : node.childrenList()) {
            System.out.println(c.path);
            out.append(printRecursiveHelper(c, prefix + " "));
        }

        return out.toString();
    }

    /**
     * Generates a tree based on the output of {@link MoleculeArchiveSource#deepList}, returning the root node.
     *
     * @param base           the path used to call deepList
     * @param pathList       the output of deepList
     * @param groupSeparator the n5 group separator
     * @return the root node
     */
    public static MoleculeArchiveTreeNode fromFlatList(final String base, final String[] pathList, final String groupSeparator) {

        final MoleculeArchiveTreeNode root = new MoleculeArchiveTreeNode(base);
        fromFlatList( root, pathList, groupSeparator );
        return root;
    }

    /**
     * Generates a tree based on the output of {@link MoleculeArchiveSource#deepList}, returning the root node.
     *
     * @param root           the root node corresponding to the base
     * @param pathList       the output of deepList
     * @param groupSeparator the n5 group separator
     */
    public static void fromFlatList(final MoleculeArchiveTreeNode root, final String[] pathList, final String groupSeparator) {

        final HashMap<String, MoleculeArchiveTreeNode> pathToNode = new HashMap<>();

        final String normalizedBase = normalDatasetName(root.getPath(), groupSeparator);
        pathToNode.put(normalizedBase, root);

        // sort the paths by length such that parent nodes always have smaller
        // indexes than their children
        Arrays.sort(pathList);

        final String prefix = normalizedBase == groupSeparator ? "" : normalizedBase;
        for (final String datasetPath : pathList) {

            final String fullPath = prefix + groupSeparator + datasetPath;
            final MoleculeArchiveTreeNode node = new MoleculeArchiveTreeNode(fullPath);
            pathToNode.put(fullPath, node);

            final String parentPath = fullPath.substring(0, fullPath.lastIndexOf(groupSeparator));

            MoleculeArchiveTreeNode parent = pathToNode.get(parentPath);
            if (parent == null) {
                // possible for the parent to not appear in the list
                // if deepList is called with a filter
                parent = new MoleculeArchiveTreeNode(parentPath);
                pathToNode.put(parentPath, parent);
            }
            parent.add(node);
        }
    }

    private static String normalDatasetName(final String fullPath, final String groupSeparator) {

        return fullPath.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
    }

    /**
     * Removes the leading slash from a given path and returns the corrected path.
     * It ensures correctness on both Unix and Windows, otherwise {@code pathName} is treated
     * as UNC path on Windows, and {@code Paths.get(pathName, ...)} fails with {@code InvalidPathException}.
     *
     * @param pathName the path
     * @return the corrected path
     */
    protected static String removeLeadingSlash(final String pathName) {

        return pathName.startsWith("/") || pathName.startsWith("\\") ? pathName.substring(1) : pathName;
    }

}
