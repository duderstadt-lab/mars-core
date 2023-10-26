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

import de.mpg.biochem.mars.io.MoleculeArchiveSource;
import de.mpg.biochem.mars.molecule.commands.ImportCloudArchiveCommand;
import org.scijava.Context;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class MoleculeArchiveOpenDialog extends AbstractMoleculeArchiveDialog {

    public MoleculeArchiveOpenDialog(String url, Context context) {
        super(url, context);
    }

    public MoleculeArchiveOpenDialog(Context context) {
        super(context);
    }

    protected JFrame buildDialog() {
        super.buildDialog();
        dialog.setTitle("Open Molecule Archive");

        savedRecentPaths = prefService.getList(MoleculeArchiveOpenDialog.class, "recentOpenURLs");
        recentPathList.setListData(savedRecentPaths.toArray(new String[0]));

        okBtn.setText("Open");

        containerTree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                ok();
            }
            }
        });

        return dialog;
    }

    public void clearRecent() {
        this.savedRecentPaths = new ArrayList<>();
        recentPathList.setListData(new String[0]);
        recentPathList.repaint();
        prefService.remove(MoleculeArchiveOpenDialog.class, "recentOpenURLs");
    }

    public void ok() {
        String url = getPath();
        containerPathUpdateCallback.accept(getPath());

        if (!url.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_ENDING)
                && !url.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_STORE_ENDING) && containerTree.getSelectionCount() != 0) {
            String nodeName = ((MoleculeArchiveSwingTreeNode)containerTree.getLastSelectedPathComponent()).getNodeName();

            String nodePath = "";
            MoleculeArchiveSwingTreeNode node = (MoleculeArchiveSwingTreeNode) containerTree.getLastSelectedPathComponent();

            //If the parent of the parent is null than the parent is the root of the tree
            //and should already be included in the path
            while (node.getParent() != null && node.getParent().getParent() != null) {
                node = (MoleculeArchiveSwingTreeNode) node.getParent();
                nodePath += node.getNodeName() + "/";
            }

            if (!url.endsWith("/")) url = url + "/";
            url = url + nodePath + nodeName;
        }

        if (url.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_ENDING) || url.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_STORE_ENDING)) {
            okCallback.accept(new MoleculeArchiveSelection(url));

            url = url.substring(0, url.lastIndexOf("/") + 1);
            if (savedRecentPaths.contains(url))
                savedRecentPaths.remove(savedRecentPaths.indexOf(url));
            savedRecentPaths.add(0, url);
            prefService.put(MoleculeArchiveOpenDialog.class, "recentOpenURLs", savedRecentPaths);
            close();
        }
    }
}
