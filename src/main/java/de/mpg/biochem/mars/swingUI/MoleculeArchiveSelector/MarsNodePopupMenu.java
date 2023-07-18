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