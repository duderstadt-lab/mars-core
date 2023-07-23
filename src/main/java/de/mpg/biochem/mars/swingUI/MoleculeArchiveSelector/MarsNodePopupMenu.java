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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Type;
import java.util.HashMap;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

public class MarsNodePopupMenu extends JPopupMenu {

    private static final long serialVersionUID = 1431304870893536697L;

    protected final MoleculeArchiveSelectorDialog dialog;

    protected final JMenuItem showMetadata;

    protected final PopupListener popupListener;

    private Point clickPt;

    private JTree tree;

    private JFrame metadataFrame;

    private JTextArea metadataTextArea;

    public MarsNodePopupMenu(final MoleculeArchiveSelectorDialog dialog) {

        this.dialog = dialog;
        this.tree = dialog.getJTree();

        popupListener = new PopupListener();

        showMetadata = new JMenuItem("Show Properties");
        showMetadata.addActionListener(e -> showDialog());
        add(showMetadata);

        buildMetadataFrame();
    }

    public PopupListener getPopupListener() {
        return popupListener;
    }

    public void setupListeners() {
        dialog.getJTree().addMouseListener(popupListener);
    }

    public void showDialog() {
        /*
        if (popupListener.selPath != null) {
            Object o = popupListener.selPath.getLastPathComponent();
            if (o instanceof N5TreeNode) {
                final N5TreeNode node = (N5TreeNode) o;
                setText(node);
            } else if (o instanceof N5TreeNodeWrapper) {
                final N5TreeNodeWrapper wrapper = (N5TreeNodeWrapper) o;
                setText(wrapper.getNode());
            } else
                System.out.println(o.getClass());
        }
         */
        metadataFrame.setVisible(true);
    }

    public class PopupListener extends MouseAdapter {

        TreePath selPath;

        public void mousePressed(MouseEvent e) {

            if( SwingUtilities.isRightMouseButton(e)) {
                clickPt = e.getPoint();
                Component c = e.getComponent();

                selPath = tree.getPathForLocation(e.getX(), e.getY());
                MarsNodePopupMenu.this.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    public JFrame buildMetadataFrame()
    {
        metadataFrame = new JFrame("Metadata");
        metadataFrame.setPreferredSize(new Dimension( 400, 400 ));
        metadataFrame.setMinimumSize(new Dimension( 200, 200 ));

        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add( new JLabel("Metadata"));

        metadataTextArea = new JTextArea();
        metadataTextArea.setEditable( false );

        final JScrollPane textView = new JScrollPane( metadataTextArea );
        panel.add( textView, BorderLayout.CENTER );

        metadataFrame.add(panel);
        return metadataFrame;
    }
}