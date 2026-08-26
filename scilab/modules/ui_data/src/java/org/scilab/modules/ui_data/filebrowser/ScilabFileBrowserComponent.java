/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2011 - DIGITEO - Calixte DENIZET
 *
 * Copyright (C) 2012 - 2016 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.ui_data.filebrowser;

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.UIManager;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * A JPanel containing the different elements composing the File Browser
 * @author Calixte DENIZET
 */
@SuppressWarnings(value = { "serial" })
public class ScilabFileBrowserComponent extends JPanel {

    private static final int GAP = 3;

    private SwingScilabFileBrowser filebrowser;
    private SwingScilabTreeTable stt;
    private JScrollPane scrollPane;

    /**
     * Default constructor
     */
    public ScilabFileBrowserComponent() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
        ScilabFileSelectorComboBox combobox = new ScilabFileSelectorComboBox();
        stt = new SwingScilabTreeTable(new ScilabFileBrowserModel(), combobox);
        // Was Color.WHITE, which kept the file browser a white rectangle under a dark
        // look and feel. Table.background is what the tree-table would use anyway.
        Object c = UIManager.get("Table.background");
        Color bg = (c instanceof Color) ? (Color) c : Color.WHITE;
        stt.setBackground(bg);

        add(new ScilabFileSelectorPanel(stt), BorderLayout.PAGE_START);
        add(new ScilabFileSelectorFilter(stt), BorderLayout.PAGE_END);

        JScrollPane jsp = new JScrollPane(stt);
        jsp.getViewport().setBackground(bg);
        this.scrollPane = jsp;
        add(jsp, BorderLayout.CENTER);
    }

    /**
     * Re-read the background whenever the look and feel changes.
     *
     * Reading it once in the constructor was not enough: on a live theme switch the
     * panel kept the previous theme's colour while the rest of the window changed,
     * leaving a white file browser inside a dark window. A JViewport in particular
     * never picks the new value up on its own, because nothing installs a UI-managed
     * background on it.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        Object c = UIManager.get("Table.background");
        if (c instanceof Color) {
            Color bg = (Color) c;
            if (stt != null) {
                stt.setBackground(bg);
            }
            if (scrollPane != null) {
                scrollPane.getViewport().setBackground(bg);
            }
        }
    }

    /**
     * Set the base directory
     * @param baseDir the base directory
     */
    public void setBaseDir(final String baseDir) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                stt.setBaseDir(baseDir);
            }
        });
    }

    /**
     * @return the file browser (as Tab) instance
     */
    public SwingScilabFileBrowser getFileBrowser() {
        return filebrowser;
    }

    /**
     * @return the next button in the history
     */
    public JButton getNextButton() {
        return stt.getNextButton();
    }

    /**
     * @return the previous button in the history
     */
    public JButton getPreviousButton() {
        return stt.getPreviousButton();
    }
}
