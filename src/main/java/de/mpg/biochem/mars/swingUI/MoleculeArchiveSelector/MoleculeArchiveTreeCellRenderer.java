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
            if (name.endsWith(".yama") || name.endsWith(".yama.store"))
                setText( String.join( "", new String[]{
                        "<html>",
                        String.format( nameFormat, name ),
                        "</html>"
                }));
            else
                setText( String.join( "", new String[]{
                        "<html>", name, "</html>"
                }));
        }
        return this;
    }

}
