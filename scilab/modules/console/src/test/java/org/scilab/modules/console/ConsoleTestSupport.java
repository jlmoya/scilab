/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.console;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

/**
 * Small shared helpers for the console action tests. A {@link DefaultStyledDocument}
 * is a plain, headless-safe Swing model with the same {@code getLength()}/{@code
 * remove()} contract the console's input document exposes, so the actions can be
 * exercised against it directly.
 */
final class ConsoleTestSupport {

    private ConsoleTestSupport() {
    }

    static StyledDocument docOf(String content) {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        try {
            doc.insertString(0, content, null);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        return doc;
    }

    static String textOf(StyledDocument doc) {
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }
}
