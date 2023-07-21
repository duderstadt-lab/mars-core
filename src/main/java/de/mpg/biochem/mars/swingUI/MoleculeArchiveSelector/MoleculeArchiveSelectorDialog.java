/**
 * Copyright (c) 2018--2020, Saalfeld lab
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package de.mpg.biochem.mars.swingUI.MoleculeArchiveSelector;

import de.mpg.biochem.mars.io.MoleculeArchiveIOFactory;
import de.mpg.biochem.mars.io.MoleculeArchiveSource;
import ij.IJ;
import se.sawano.java.text.AlphanumericComparator;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MoleculeArchiveSelectorDialog {

    private Consumer<MoleculeArchiveSelection> okCallback;

    private JFrame dialog;

    private JTextField containerPathText;

    private JCheckBox virtualBox;

    private JCheckBox cropBox;

    private JTree containerTree;

    private JButton browseBtn;

    private JButton detectBtn;

    private JLabel messageLabel;

    private JButton okBtn;

    private JButton cancelBtn;

    private DefaultTreeModel treeModel;

    private String lastBrowsePath;

    private ExecutorService loaderExecutor;

    private final String initialContainerPath;

    private Consumer<String> containerPathUpdateCallback;

    private Consumer<Void> cancelCallback;

    private TreeCellRenderer treeRenderer;

    private MoleculeArchiveSwingTreeNode rootNode;

    private ExecutorService parseExec;

    private MoleculeArchiveSource source;

    private final AlphanumericComparator comp = new AlphanumericComparator(Collator.getInstance());

    public MoleculeArchiveSelectorDialog(String url) {
        this.initialContainerPath = url;
    }

    public void setTreeRenderer(final TreeCellRenderer treeRenderer) {

        this.treeRenderer = treeRenderer;
    }

    public void setCancelCallback(final Consumer<Void> cancelCallback) {

        this.cancelCallback = cancelCallback;
    }

    public void setContainerPathUpdateCallback(final Consumer<String> containerPathUpdateCallback) {

        this.containerPathUpdateCallback = containerPathUpdateCallback;
    }
    public void setMessage(final String message) {

        messageLabel.setText(message);
    }

    public String getPath() {

        return containerPathText.getText().trim();
    }

    public void run(final Consumer<MoleculeArchiveSelection> okCallback) {

        this.okCallback = okCallback;
        dialog = buildDialog();

        browseBtn.addActionListener(e -> openContainer(this::openBrowseDialog));
        detectBtn.addActionListener(e -> openContainer(() -> getPath()));

        // ok and cancel buttons
        okBtn.addActionListener(e -> ok());
        cancelBtn.addActionListener(e -> cancel());
        dialog.setVisible(true);
    }

    private static final int DEFAULT_OUTER_PAD = 8;
    private static final int DEFAULT_BUTTON_PAD = 3;
    private static final int DEFAULT_MID_PAD = 5;

    private JFrame buildDialog() {

        final int OUTER_PAD = DEFAULT_OUTER_PAD;
        final int BUTTON_PAD = DEFAULT_BUTTON_PAD;
        final int MID_PAD = DEFAULT_MID_PAD;

        final int frameSizeX = UIScale.scale( 600 );
        final int frameSizeY = UIScale.scale( 400 );

        dialog = new JFrame("Open Molecule Archive");
        dialog.setPreferredSize(new Dimension(frameSizeX, frameSizeY));
        dialog.setMinimumSize(dialog.getPreferredSize());

        final Container pane = dialog.getContentPane();
        final JTabbedPane tabs = new JTabbedPane();
        pane.add( tabs );

        final JPanel panel = new JPanel(false);
        panel.setLayout(new GridBagLayout());
        //tabs.addTab("Recent", spatialMetaSpec.buildPanel() );
        tabs.addTab("Browse", panel);

        //Then we could add only a right panel with property summary for either Recent or Browse.

        containerPathText = new JTextField();
        containerPathText.setText(initialContainerPath);
        containerPathText.setPreferredSize(new Dimension(frameSizeX / 3, containerPathText.getPreferredSize().height));
        containerPathText.addActionListener(e -> openContainer(() -> getPath()));

        final GridBagConstraints ctxt = new GridBagConstraints();
        ctxt.gridx = 0;
        ctxt.gridy = 0;
        ctxt.gridwidth = 3;
        ctxt.gridheight = 1;
        ctxt.weightx = 1.0;
        ctxt.weighty = 0.0;
        ctxt.fill = GridBagConstraints.HORIZONTAL;
        ctxt.insets = new Insets(OUTER_PAD, OUTER_PAD, MID_PAD, BUTTON_PAD);
        panel.add(containerPathText, ctxt);

        browseBtn = new JButton("Browse");
        final GridBagConstraints cbrowse = new GridBagConstraints();
        cbrowse.gridx = 3;
        cbrowse.gridy = 0;
        cbrowse.gridwidth = 1;
        cbrowse.gridheight = 1;
        cbrowse.weightx = 0.0;
        cbrowse.weighty = 0.0;
        cbrowse.fill = GridBagConstraints.HORIZONTAL;
        cbrowse.insets = new Insets(OUTER_PAD, BUTTON_PAD, MID_PAD, BUTTON_PAD);
        panel.add(browseBtn, cbrowse);

        detectBtn = new JButton("Detect paths");
        final GridBagConstraints cdetect = new GridBagConstraints();
        cdetect.gridx = 4;
        cdetect.gridy = 0;
        cdetect.gridwidth = 2;
        cdetect.gridheight = 1;
        cdetect.weightx = 0.0;
        cdetect.weighty = 0.0;
        cdetect.fill = GridBagConstraints.HORIZONTAL;
        cdetect.insets = new Insets(OUTER_PAD, BUTTON_PAD, MID_PAD, OUTER_PAD);
        panel.add(detectBtn, cdetect);

        final GridBagConstraints ctree = new GridBagConstraints();
        ctree.gridx = 0;
        ctree.gridy = 1;
        ctree.gridwidth = 6;
        ctree.gridheight = 3;
        ctree.weightx = 1.0;
        ctree.weighty = 1.0;
        ctree.ipadx = 0;
        ctree.ipady = 0;
        ctree.insets = new Insets(0, OUTER_PAD, 0, OUTER_PAD);
        ctree.fill = GridBagConstraints.BOTH;

        treeModel = new DefaultTreeModel(null);
        containerTree = new JTree(treeModel);
        containerTree.setMinimumSize(new Dimension(550, 230));

        containerTree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

        // disable selection of nodes that are not open-able
        //containerTree.addTreeSelectionListener(
        //        new N5IjTreeSelectionListener(containerTree.getSelectionModel()));

        // By default leaf nodes (datasets) are displayed as files. This changes the default behavior to display them as folders
        //        final DefaultTreeCellRenderer treeCellRenderer = (DefaultTreeCellRenderer) containerTree.getCellRenderer();
        if (treeRenderer != null)
            containerTree.setCellRenderer(treeRenderer);

        final JScrollPane treeScroller = new JScrollPane(containerTree);
        treeScroller.setViewportView(containerTree);
        treeScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(treeScroller, ctree);

        // bottom button
        final GridBagConstraints cbot = new GridBagConstraints();
        cbot.gridx = 0;
        cbot.gridy = 4;
        cbot.gridwidth = 1;
        cbot.gridheight = 1;
        cbot.weightx = 0.0;
        cbot.weighty = 0.0;
        cbot.insets = new Insets(OUTER_PAD, OUTER_PAD, OUTER_PAD, OUTER_PAD);
        cbot.anchor = GridBagConstraints.CENTER;

        messageLabel = new JLabel("");
        messageLabel.setVisible(false);
        cbot.gridx = 2;
        cbot.anchor = GridBagConstraints.CENTER;
        panel.add(messageLabel, cbot);

        okBtn = new JButton("OK");
        cbot.gridx = 4;
        cbot.ipadx = (int)(20);
        cbot.anchor = GridBagConstraints.EAST;
        cbot.fill = GridBagConstraints.HORIZONTAL;
        cbot.insets = new Insets(MID_PAD, OUTER_PAD, OUTER_PAD, BUTTON_PAD);
        panel.add(okBtn, cbot);

        cancelBtn = new JButton("Cancel");
        cbot.gridx = 5;
        cbot.ipadx = 0;
        cbot.anchor = GridBagConstraints.EAST;
        cbot.fill = GridBagConstraints.HORIZONTAL;
        cbot.insets = new Insets(MID_PAD, BUTTON_PAD, OUTER_PAD, OUTER_PAD);
        panel.add(cancelBtn, cbot);

        containerTree.addMouseListener( new MarsNodePopupMenu(this).getPopupListener() );

        dialog.pack();
        return dialog;
    }

    public JTree getJTree() {
        return containerTree;
    }

    private String openBrowseDialog() {

        final JFileChooser fileChooser = new JFileChooser();
        /*
         *  Need to allow files so h5 containers can be opened,
         *  and directories so that filesystem n5's and zarrs can be opened.
         */
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        if (lastBrowsePath != null && !lastBrowsePath.isEmpty())
            fileChooser.setCurrentDirectory(new File(lastBrowsePath));
        else if (initialContainerPath != null && !initialContainerPath.isEmpty())
            fileChooser.setCurrentDirectory(new File(initialContainerPath));
        else if (IJ.getInstance() != null) {
            File f = null;

            final String currDir = IJ.getDirectory("current");
            final String homeDir = IJ.getDirectory("home");
            if( currDir != null )
                f = new File( currDir );
            else if( homeDir != null )
                f = new File( homeDir );

            fileChooser.setCurrentDirectory(f);
        }

        final int ret = fileChooser.showOpenDialog(dialog);
        if (ret != JFileChooser.APPROVE_OPTION)
            return null;

        final String path = fileChooser.getSelectedFile().getAbsolutePath();
        containerPathText.setText(path);
        lastBrowsePath = path;

        // callback after browse as well
        containerPathUpdateCallback.accept(path);

        return path;
    }

    private void openContainer(final Supplier<String> opener) {

        SwingUtilities.invokeLater(() -> {
            messageLabel.setText("Building tree...");
            messageLabel.setVisible(true);
            messageLabel.repaint();
        });

        final String path = opener.get();
        containerPathUpdateCallback.accept(path);

        if (path == null) {
            messageLabel.setVisible(false);
            dialog.repaint();
            return;
        }

        source = null;
        try {
            source = new MoleculeArchiveIOFactory().openSource(path);
        } catch (IOException e) { e.printStackTrace(); }

        if (source == null) {
            messageLabel.setVisible(false);
            dialog.repaint();
            return;
        }

        final String rootPath = source.getPath();

        if (loaderExecutor == null) {
            loaderExecutor = Executors.newCachedThreadPool();
        }

        final String[] pathParts = path.split( source.getGroupSeparator() );

        final String rootName = pathParts[ pathParts.length - 1 ];
        if( treeRenderer != null  && treeRenderer instanceof MoleculeArchiveTreeCellRenderer )
            ((MoleculeArchiveTreeCellRenderer)treeRenderer ).setRootName(rootName);

        rootNode = new MoleculeArchiveSwingTreeNode( rootPath, treeModel );
        treeModel.setRoot(rootNode);

        containerTree.setEnabled(true);
        containerTree.repaint();

        final MoleculeArchiveSource finalSource = source;

        parseExec = Executors.newSingleThreadExecutor();
        parseExec.submit(() -> {
            try {

                SwingUtilities.invokeLater(() -> {
                    messageLabel.setText("Listing...");
                    messageLabel.repaint();
                });

                String[] sourcePaths = finalSource.deepList(rootPath, loaderExecutor);
                MoleculeArchiveSwingTreeNode.fromFlatList(rootNode, sourcePaths, finalSource.getGroupSeparator() );

                sortRecursive( rootNode );
                containerTree.expandRow( 0 );

                SwingUtilities.invokeLater(() -> {
                    messageLabel.setText("Done");
                    messageLabel.repaint();
                });

                Thread.sleep(1000);
                SwingUtilities.invokeLater(() -> {
                    messageLabel.setText("");
                    messageLabel.setVisible(false);
                    messageLabel.repaint();
                });
            }
            catch (InterruptedException e) { }
            catch (ExecutionException e) { }
            catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void ok() {
        // stop parsing things
        if( parseExec != null )
            parseExec.shutdownNow();

        // check if we can skip explicit dataset detection
        if (containerTree.getSelectionCount() == 0) {
            containerPathUpdateCallback.accept(getPath());
            okCallback.accept(new MoleculeArchiveSelection(getPath()));
        } else {
            // archive was selected by the user
            String treePath = ((MoleculeArchiveSwingTreeNode)containerTree.getLastSelectedPathComponent()).getPath();
            String fullPath = (treePath.startsWith("/")) ? treePath : source.getGroupSeparator() + treePath;
            if (fullPath.startsWith(getPath())) okCallback.accept(new MoleculeArchiveSelection(fullPath));
            else {
                String uri = (getPath().endsWith(source.getGroupSeparator())) ? getPath().substring(0, getPath().length()-1) : getPath();
                okCallback.accept(new MoleculeArchiveSelection( uri + fullPath));
            }
        }
        source.close();
        dialog.setVisible(false);
        dialog.dispose();
    }

    public void cancel() {
        dialog.setVisible(false);
        dialog.dispose();

        if (cancelCallback != null)
            cancelCallback.accept(null);
    }

    private void sortRecursive( final MoleculeArchiveSwingTreeNode node )
    {
        if( node != null ) {
            final List<MoleculeArchiveTreeNode> children = node.childrenList();
            if( !children.isEmpty())
            {
                children.sort(Comparator.comparing(MoleculeArchiveTreeNode::toString, comp));
            }
            treeModel.nodeStructureChanged(node);
            for( MoleculeArchiveTreeNode child : children )
                sortRecursive( (MoleculeArchiveSwingTreeNode)child );
        }
    }

    private static String normalDatasetName(final String fullPath, final String groupSeparator) {

        return fullPath.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
    }

    private static boolean pathsEqual( final String a, final String b )
    {
        return normalDatasetName( a, "/" ).equals( normalDatasetName( b, "/" ) );
    }

}