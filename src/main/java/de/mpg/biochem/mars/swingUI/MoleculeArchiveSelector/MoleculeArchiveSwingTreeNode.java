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

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

public class MoleculeArchiveSwingTreeNode extends MoleculeArchiveTreeNode implements MutableTreeNode {

    private MoleculeArchiveSwingTreeNode parent;

    private DefaultTreeModel treeModel;

    private boolean leaf = true;

    public MoleculeArchiveSwingTreeNode( final String path ) {
        super( path );
    }

    public MoleculeArchiveSwingTreeNode( final String path, final DefaultTreeModel model ) {
        super( path );
        this.treeModel = model;
    }

    public MoleculeArchiveSwingTreeNode( final String path, final MoleculeArchiveSwingTreeNode parent ) {
        super( path );
        this.parent = parent;
    }

    public MoleculeArchiveSwingTreeNode( final String path, final MoleculeArchiveSwingTreeNode parent, final DefaultTreeModel model ) {
        super( path );
        this.parent = parent;
        this.treeModel = model;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration children() {
        return Collections.enumeration(childrenList());
    }

    public void add(final MoleculeArchiveSwingTreeNode child) {

        childrenList().add(child);
    }

    @Override
    public MoleculeArchiveSwingTreeNode addPath( final String path ) {
        final String normPath = removeLeadingSlash(path);
        if( removeLeadingSlash(getPath()).equals(normPath))
            return this;

        final String relativePath = removeLeadingSlash( normPath.replaceAll( "^"+getPath(), "" ));
        final int sepIdx = relativePath.indexOf("/");
        final String childName;
        if( sepIdx < 0 )
            childName = relativePath;
        else
            childName = relativePath.substring(0, sepIdx);

        // get the appropriate child along the path if it exists, otherwise add it
        MoleculeArchiveTreeNode child = null;
        Stream<MoleculeArchiveTreeNode> cs = childrenList().stream().filter( n -> n.getNodeName().equals(childName));;
        Optional<MoleculeArchiveTreeNode> copt = cs.findFirst();
        if( copt.isPresent() )
            child = copt.get();
        else {
            child = new MoleculeArchiveSwingTreeNode(
                    getPath().isEmpty() ? childName : getPath() + "/" + childName,
                    this, treeModel );

            add( child );

            if( treeModel != null)
                treeModel.nodesWereInserted( this, new int[]{childrenList().size() - 1 });
        }
        return (MoleculeArchiveSwingTreeNode) child.addPath(normPath);
    }

    public MoleculeArchiveSwingTreeNode addChildPath( final String path ) {
        final String normPath = removeLeadingSlash(path);
        if( removeLeadingSlash(getPath()).equals(normPath))
            return this;

        final String relativePath = removeLeadingSlash( normPath.replaceAll( "^"+getPath(), "" ));
        final int sepIdx = relativePath.indexOf("/");
        final String childName;
        if( sepIdx < 0 )
            childName = relativePath;
        else
            childName = relativePath.substring(0, sepIdx);

        // get the appropriate child along the path if it exists, otherwise add it
        MoleculeArchiveTreeNode child = null;
        Stream<MoleculeArchiveTreeNode> cs = childrenList().stream().filter( n -> n.getNodeName().equals(childName));;
        Optional<MoleculeArchiveTreeNode> copt = cs.findFirst();
        if( copt.isPresent() )
            child = copt.get();
        else {
            child = new MoleculeArchiveSwingTreeNode(
                    getPath().isEmpty() ? childName : getPath() + "/" + childName,
                    this, treeModel );

            add( child );

            //if( treeModel != null)
            //    treeModel.nodesWereInserted( this, new int[]{childrenList().size() - 1 });
        }
        return (MoleculeArchiveSwingTreeNode) child;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public MoleculeArchiveSwingTreeNode getChildAt(int i) {
        return (MoleculeArchiveSwingTreeNode) childrenList().get(i);
    }

    @Override
    public int getChildCount() {
        return childrenList().size();
    }

    @SuppressWarnings("unlikely-arg-type")
    @Override
    public int getIndex(TreeNode n) {
        return childrenList().indexOf(n);
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public boolean isLeaf() {
        return leaf;
    }

    public void setLeaf(boolean leaf) {
        this.leaf = leaf;
    }

    public static void fromFlatList(final MoleculeArchiveSwingTreeNode root, final String[] pathList, final String groupSeparator) {

        final HashMap<String, MoleculeArchiveSwingTreeNode> pathToNode = new HashMap<>();

        final String normalizedBase = normalDatasetName(root.getPath(), groupSeparator);
        pathToNode.put(normalizedBase, root);

        // sort the paths by length such that parent nodes always have smaller
        // indexes than their children
        Arrays.sort(pathList);

        final String prefix = normalizedBase == groupSeparator ? "" : normalizedBase;
        for (final String datasetPath : pathList) {

            final String fullPath = prefix + groupSeparator + datasetPath;
            final String parentPath = fullPath.substring(0, fullPath.lastIndexOf(groupSeparator));

            MoleculeArchiveSwingTreeNode parent = pathToNode.get(parentPath);
            if (parent == null) {
                // possible for the parent to not appear in the list
                // if deepList is called with a filter
                parent = new MoleculeArchiveSwingTreeNode(parentPath);
                pathToNode.put(parentPath, parent);
            }
            final MoleculeArchiveSwingTreeNode node = new MoleculeArchiveSwingTreeNode(fullPath, parent );
            pathToNode.put(fullPath, node);

            parent.add(node);
        }
    }

    private static String normalDatasetName(final String fullPath, final String groupSeparator) {

        return fullPath.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
    }

    @Override
    public void insert(MutableTreeNode child, int index) {
        if( child instanceof MoleculeArchiveSwingTreeNode )
            childrenList().add(index, (MoleculeArchiveSwingTreeNode)child);
    }

    @Override
    public void remove(int index) {
        childrenList().remove(index);
    }

    @SuppressWarnings("unlikely-arg-type")
    @Override
    public void remove(MutableTreeNode node) {
        childrenList().remove(node);
    }

    @Override
    public void removeFromParent() {
        parent.childrenList().remove(this);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        if( newParent instanceof MoleculeArchiveSwingTreeNode )
            this.parent = (MoleculeArchiveSwingTreeNode)newParent;
    }

    @Override
    public void setUserObject(Object object) {
        // does nothing
    }

}
