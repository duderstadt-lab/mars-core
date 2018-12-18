/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.mars.autoComplete;

import org.fife.rsta.ac.AbstractLanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.LanguageAwareCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.swing.script.LanguageSupportPlugin;

import java.awt.event.KeyListener;
import java.util.ArrayList;

/**
 * AbstractLanguageSupportPlugin
 * <p>
 * <p>
 * <p>
 * @author Robert Haase
 * October 2018
 */
abstract class AbstractLanguageSupportPlugin  extends AbstractLanguageSupport
        implements LanguageSupportPlugin
{
    @Parameter
    ModuleService moduleService;

    private final static int MINIMUM_WORD_LENGTH_TO_OPEN_PULLDOWN = 1;

    @Override
    public void install(final RSyntaxTextArea rSyntaxTextArea) {
        final AutoCompletion ac = createAutoCompletion(new LanguageAwareCompletionProvider(getCompletionProvider()));
        ac.setAutoActivationDelay(100);
        ac.setAutoActivationEnabled(true);
        ac.setShowDescWindow(true);
        ac.install(rSyntaxTextArea);
        installImpl(rSyntaxTextArea, ac);

        rSyntaxTextArea.addKeyListener(new AutoCompletionKeyListener(ac,
                rSyntaxTextArea));
    }

    abstract CompletionProvider getCompletionProvider();

    @Override
    public void uninstall(final RSyntaxTextArea rSyntaxTextArea) {
        uninstallImpl(rSyntaxTextArea);

        final ArrayList<KeyListener> toRemove = new ArrayList<>();
        for (final KeyListener keyListener : rSyntaxTextArea.getKeyListeners()) {
            if (keyListener instanceof AutoCompletionKeyListener) {
                toRemove.add(keyListener);
            }
        }
        for (final KeyListener keyListener : toRemove) {
            rSyntaxTextArea.removeKeyListener(keyListener);
        }

    }
}
