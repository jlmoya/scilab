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

package org.scilab.modules.preferences.Component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Grid} preference component (a {@link Panel}
 * with a {@code GridBagLayout}). The heart of the class is {@code setConstraints},
 * which translates a child's DOM attributes into a {@link GridBagConstraints}. That
 * translation is pure — the numeric reads route through {@code getInt}/
 * {@code getDouble} (declared on {@code XCommonManager}) and the anchor/insets/fill
 * lookups are plain string switches — so no native code or display is required.
 * The test lives in the component package to reach the package-private
 * {@code setConstraints}.
 */
public class GridTest {

    private static Document doc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Element cell(Document d, String... kv) {
        Element e = d.createElement("cell");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void setConstraintsTranslatesEveryAttribute() throws Exception {
        Document d = doc();
        Grid g = new Grid(d.createElement("Grid"));
        GridBagConstraints gbc = new GridBagConstraints();
        g.setConstraints(gbc, cell(d, "gridx", "3", "gridy", "2", "gridwidth", "2", "gridheight", "4",
                                    "weightx", "0.5", "weighty", "0.25", "ipadx", "4", "ipady", "6",
                                    "anchor", "north", "insets", "large", "fill", "both"));
        // gridx/gridy are 1-based in the DOM and stored 0-based.
        assertEquals(2, gbc.gridx);
        assertEquals(1, gbc.gridy);
        assertEquals(2, gbc.gridwidth);
        assertEquals(4, gbc.gridheight);
        assertEquals(0.5, gbc.weightx, 0.0);
        assertEquals(0.25, gbc.weighty, 0.0);
        assertEquals(4, gbc.ipadx);
        assertEquals(6, gbc.ipady);
        assertEquals(GridBagConstraints.NORTH, gbc.anchor);
        assertEquals(new Insets(5, 5, 5, 5), gbc.insets);
        assertEquals(GridBagConstraints.BOTH, gbc.fill);
    }

    @Test
    public void setConstraintsUsesTheDocumentedDefaults() throws Exception {
        Document d = doc();
        Grid g = new Grid(d.createElement("Grid"));
        GridBagConstraints gbc = new GridBagConstraints();
        int anchorBefore = gbc.anchor;
        int fillBefore = gbc.fill;
        g.setConstraints(gbc, cell(d));
        assertEquals(0, gbc.gridx, "gridx defaults to 1 => stored 0");
        assertEquals(0, gbc.gridy);
        assertEquals(1, gbc.gridwidth);
        assertEquals(1, gbc.gridheight);
        assertEquals(1.0, gbc.weightx, 0.0);
        assertEquals(1.0, gbc.weighty, 0.0);
        assertEquals(0, gbc.ipadx);
        assertEquals(0, gbc.ipady);
        assertEquals(anchorBefore, gbc.anchor, "an unmatched anchor leaves the field untouched");
        assertEquals(fillBefore, gbc.fill, "an unmatched fill leaves the field untouched");
    }

    @Test
    public void anchorNamesMapToConstants() throws Exception {
        Document d = doc();
        Grid g = new Grid(d.createElement("Grid"));
        assertEquals(GridBagConstraints.CENTER, anchorOf(g, d, "center"));
        assertEquals(GridBagConstraints.SOUTHEAST, anchorOf(g, d, "southeast"));
        assertEquals(GridBagConstraints.PAGE_END, anchorOf(g, d, "page_end"));
    }

    private static int anchorOf(Grid g, Document d, String anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        g.setConstraints(gbc, cell(d, "anchor", anchor));
        return gbc.anchor;
    }

    @Test
    public void insetsHugeUsesTheLargerMargin() throws Exception {
        Document d = doc();
        Grid g = new Grid(d.createElement("Grid"));
        GridBagConstraints gbc = new GridBagConstraints();
        g.setConstraints(gbc, cell(d, "insets", "huge"));
        assertEquals(new Insets(15, 15, 15, 15), gbc.insets);
    }

    @Test
    public void fillNamesMapToConstants() throws Exception {
        Document d = doc();
        Grid g = new Grid(d.createElement("Grid"));
        assertEquals(GridBagConstraints.NONE, fillOf(g, d, "none"));
        assertEquals(GridBagConstraints.HORIZONTAL, fillOf(g, d, "horizontal"));
        assertEquals(GridBagConstraints.VERTICAL, fillOf(g, d, "vertical"));
        assertEquals(GridBagConstraints.BOTH, fillOf(g, d, "both"));
    }

    private static int fillOf(Grid g, Document d, String fill) {
        GridBagConstraints gbc = new GridBagConstraints();
        g.setConstraints(gbc, cell(d, "fill", fill));
        return gbc.fill;
    }

    @Test
    public void addRegistersTheChild() throws Exception {
        Document d = doc();
        Grid g = new Grid(d.createElement("Grid"));
        g.add(new JLabel("x"), cell(d, "gridx", "1", "gridy", "1"));
        assertEquals(1, g.getComponentCount());
    }

    /**
     * Defect characterization: {@code Grid.toString()} returns the copy/paste
     * literal {@code "VBox"} rather than {@code "Grid"}.
     */
    @Test
    public void toStringIsTheCopyPastedVBoxLabel() throws Exception {
        assertEquals("VBox", new Grid(doc().createElement("Grid")).toString());
    }

    @Test
    public void actuatorsAreEmpty() throws Exception {
        assertArrayEquals(new String[] {}, new Grid(doc().createElement("Grid")).actuators());
    }
}
