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

        recentURLs = prefService.getList(MoleculeArchiveOpenDialog.class, "recentOpenURLs");
        recentList.setListData(recentURLs.toArray(new String[0]));

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
        this.recentURLs = new ArrayList<>();
        recentList.setListData(new String[0]);
        recentList.repaint();
        prefService.remove(MoleculeArchiveOpenDialog.class, "recentOpenURLs");
    }

    public void ok() {
        String url;
        // check if we can skip explicit dataset detection
        if (containerTree.getSelectionCount() == 0) {
            containerPathUpdateCallback.accept(getPath());
            url = getPath();
        } else {
            // archive was selected by the user
            String fullPath = ((MoleculeArchiveSwingTreeNode)containerTree.getLastSelectedPathComponent()).getPath();
            if (fullPath.startsWith("//")) fullPath = fullPath.substring(1, fullPath.length());

            if (fullPath.startsWith(getPath())) url = fullPath;
            else {
                String uri = (getPath().endsWith(source.getGroupSeparator())) ? getPath().substring(0, getPath().length()-1) : getPath();
                if (uri.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_ENDING) || uri.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_STORE_ENDING))
                    url = uri;
                else
                    url = uri + fullPath;
            }
        }
        if (url.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_ENDING) || url.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_STORE_ENDING)) {
            okCallback.accept(new MoleculeArchiveSelection(url));

            if (recentURLs.contains(url))
                recentURLs.remove(recentURLs.indexOf(url));
            recentURLs.add(0, url);
            prefService.put(MoleculeArchiveOpenDialog.class, "recentOpenURLs", recentURLs);
            close();
        }
    }
}
