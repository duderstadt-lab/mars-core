package de.mpg.biochem.mars.swingUI.MoleculeArchiveSelector;

import de.mpg.biochem.mars.io.MoleculeArchiveIOFactory;
import de.mpg.biochem.mars.io.MoleculeArchiveSource;
import de.mpg.biochem.mars.molecule.MoleculeArchiveWindow;
import de.mpg.biochem.mars.molecule.commands.ImportCloudArchiveCommand;
import org.scijava.Context;
import org.scijava.ui.DialogPrompt;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;

public class MoleculeArchiveSaveDialog extends AbstractMoleculeArchiveDialog {

    public MoleculeArchiveSaveDialog(String url, Context context) {
        super(url, context);
    }

    public MoleculeArchiveSaveDialog(Context context) {
        super(context);
    }

    protected JFrame buildDialog() {
        super.buildDialog();
        dialog.setTitle("Save Molecule Archive");

        recentURLs = prefService.getList(MoleculeArchiveSaveDialog.class, "recentSaveURLs");
        recentList.setListData(recentURLs.toArray(new String[0]));

        okBtn.setText("Save");

        containerTree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String fullPath = ((MoleculeArchiveSwingTreeNode)containerTree.getLastSelectedPathComponent()).getPath();
                    if (fullPath.startsWith("//")) fullPath = fullPath.substring(1, fullPath.length());

                    String url = "";
                    if (fullPath.startsWith(getPath())) url = fullPath;
                    else {
                        String uri = (getPath().endsWith(source.getGroupSeparator())) ? getPath().substring(0, getPath().length()-1) : getPath();
                        if (uri.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_ENDING) || uri.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_STORE_ENDING))
                            url = uri;
                        else
                            url = uri + fullPath;
                    }

                    containerPathText.setText(url);
                }
            }
        });

        return dialog;
    }

    public void clearRecent() {
        this.recentURLs = new ArrayList<>();
        recentList.setListData(new String[0]);
        recentList.repaint();
        prefService.remove(MoleculeArchiveSaveDialog.class, "recentSaveURLs");
    }

    public void ok() {
        String url;
        // check if we can skip explicit dataset detection
        containerPathUpdateCallback.accept(getPath());
        url = getPath();

        if (url.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_ENDING) || url.endsWith("." + MoleculeArchiveSource.MOLECULE_ARCHIVE_STORE_ENDING)) {
            try {
                if (source == null)
                    source = new MoleculeArchiveIOFactory().openSource(getPath());

                if (source.exists(url)) {
                    //Show confirmation dialog
                    DialogPrompt.Result result = uiService.showDialog("An archive already exists at the location. Do you want to overwrite it?",
                            "Overwrite?", DialogPrompt.MessageType.QUESTION_MESSAGE, DialogPrompt.OptionType.YES_NO_OPTION);
                    if (result == DialogPrompt.Result.YES_OPTION) {
                        //Continue saving and overwrite..
                    } else return;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            okCallback.accept(new MoleculeArchiveSelection(url));

            if (recentURLs.contains(url))
                recentURLs.remove(recentURLs.indexOf(url));
            recentURLs.add(0, url);
            prefService.put(MoleculeArchiveSaveDialog.class, "recentSaveURLs", recentURLs);
            close();
        }
    }
}
