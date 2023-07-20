package de.mpg.biochem.mars.swingUI.MoleculeArchiveSelector;

import ij.ImagePlus;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MoleculeArchiveTreeCellRenderer extends DefaultTreeCellRenderer
{
    private static final long serialVersionUID = -4245251506197982653L;

    protected static final String thinSpace = "&#x2009;";

    //	private static final String times = "&#x2715;";
    protected static final String times = "&#xd7;";

    protected static final String warningFormat = "<font color=\"rgb(179, 58, 58)\">%s</font>";

    protected static final String nameFormat = "<b>%s</b>";

    protected static final String dimDelimeter = thinSpace + times + thinSpace;

    protected final boolean showConversionWarning;

    protected String rootName;

    public MoleculeArchiveTreeCellRenderer( final boolean showConversionWarning )
    {
        this.showConversionWarning = showConversionWarning;
    }

    public void setRootName( String rootName ) {
        this.rootName = rootName;
    }

    @Override
    public Component getTreeCellRendererComponent( final JTree tree, final Object value,
                                                   final boolean sel, final boolean exp, final boolean leaf, final int row, final boolean hasFocus )
    {

        super.getTreeCellRendererComponent( tree, value, sel, exp, leaf, row, hasFocus );

        MoleculeArchiveSwingTreeNode node;
        if ( value instanceof MoleculeArchiveSwingTreeNode )
        {
            node = ( ( MoleculeArchiveSwingTreeNode ) value );
            final String name = node.getParent() == null ? rootName : node.getNodeName();

            setText( String.join( "", new String[]{
                    "<html>",
                    String.format( nameFormat, name ),
                    "</html>"
            }));
        }
        return this;
    }

}