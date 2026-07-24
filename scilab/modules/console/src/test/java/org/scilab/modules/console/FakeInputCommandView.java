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

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.StyledDocument;

import com.artenum.rosetta.interfaces.ui.InputCommandView;

/**
 * Hermetic test double for the rosetta {@link InputCommandView} interface.
 *
 * <p>Not a {@code *Test} class, so surefire never runs it. It backs the console
 * input with a plain mutable {@code String} and records the mutating calls the
 * console actions make, so a test can drive an action and then assert on what it
 * did (text content, caret moves, resets, backspaces) without any Swing widget,
 * event loop, or running Scilab.
 */
class FakeInputCommandView implements InputCommandView {

    String text = "";
    int caretPosition = 0;
    Point caretLocation = new Point(0, 0);

    final List<String> appended = new ArrayList<String>();
    int resetCount = 0;
    int backspaceCount = 0;
    int toBeginningCount = 0;
    int toEndCount = 0;
    boolean setCaretPositionCalled = false;
    int lastSetCaretPosition = -1;
    String lastSetText = null;

    // --- InputCommandView ---------------------------------------------------

    public int getCaretPosition() {
        return caretPosition;
    }

    public void setCaretPosition(int p) {
        setCaretPositionCalled = true;
        lastSetCaretPosition = p;
        caretPosition = p;
    }

    public Point getCaretLocation() {
        return caretLocation;
    }

    public void backspace() {
        backspaceCount++;
    }

    public void requestFocus() {
    }

    // --- OutputView ---------------------------------------------------------

    public void setEditable(boolean b) {
    }

    public void append(String s) {
        appended.add(s);
        text = text + s;
    }

    public void append(String s, String style) {
        appended.add(s);
        text = text + s;
    }

    public void setStyleName(String s) {
    }

    public String getText() {
        return text;
    }

    public void reset() {
        resetCount++;
        text = "";
    }

    public void setText(String s) {
        lastSetText = s;
        text = s;
    }

    public void setStyledDocument(StyledDocument doc) {
    }

    public Writer getWriter() {
        return null;
    }

    public Writer getErrorWriter() {
        return null;
    }

    public void setCaretPositionToBeginning() {
        toBeginningCount++;
        caretPosition = 0;
    }

    public void setCaretPositionToEnd() {
        toEndCount++;
        caretPosition = text.length();
    }

    // --- GuiComponent -------------------------------------------------------

    public void setBackground(Color c) {
    }

    public void setForeground(Color c) {
    }

    public void setVisible(boolean b) {
    }

    public void setFont(Font f) {
    }
}
