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

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

/**
 * AutoCompletionKeyListener
 * <p>
 * <p>
 * <p>
 * @author Robert Haase
 * October 2018
 */
class AutoCompletionKeyListener implements KeyListener {

    private final static int MINIMUM_WORD_LENGTH_TO_OPEN_PULLDOWN = 1;

    AutoCompletion ac;
    RSyntaxTextArea textArea;
    ArrayList<Character> disabledChars;

    public AutoCompletionKeyListener(final AutoCompletion ac,
                                     final RSyntaxTextArea textArea)
    {
        this.ac = ac;
        this.textArea = textArea;

        disabledChars = new ArrayList<>();
        disabledChars.add(' ');
        disabledChars.add('\n');
        disabledChars.add('\t');
        disabledChars.add(';');
    }

    @Override
    public void keyTyped(final KeyEvent e) {

    }

    @Override
    public void keyPressed(final KeyEvent e) {

    }

    @Override
    public void keyReleased(final KeyEvent e) {
        SwingUtilities.invokeLater(() -> {
            if (disabledChars.contains(e.getKeyChar())) {
                if (!e.isControlDown()) {
                    // the pulldown should not be hidden if CTRL+SPACE are pressed
                    ac.hideChildWindows();
                }
            }
            else if (e.getKeyCode() >= 65 // a
                    && e.getKeyCode() <= 90 // z
            ) {
                if (ScriptingAutoCompleteProvider.getInstance().getAlreadyEnteredText(
                        textArea).length() >= MINIMUM_WORD_LENGTH_TO_OPEN_PULLDOWN &&
                        ScriptingAutoCompleteProvider.getInstance()
                                .getCompletions(textArea).size() > 1)
                {
                    ac.doCompletion();
                }
            }
        });
    }
}
