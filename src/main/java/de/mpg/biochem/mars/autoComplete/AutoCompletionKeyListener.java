/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
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
