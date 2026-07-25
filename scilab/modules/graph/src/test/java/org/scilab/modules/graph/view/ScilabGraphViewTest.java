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

package org.scilab.modules.graph.view;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mxgraph.model.mxCell;
import com.mxgraph.util.mxPoint;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;

/**
 * Hermetic unit tests for {@link ScilabGraphView}.
 *
 * The constructor is trivial, and {@code updateLabelBounds} is exercised only
 * along its plain-text (default) branch: a vertex whose label is ordinary text
 * routes to {@code getDefaultBounds}, which computes text bounds with AWT font
 * metrics (available under headless AWT). The LaTeX / MathML branches pop up a
 * modal dialog on error and load rendering back-ends, so they are out of scope
 * for a hermetic test.
 */
public class ScilabGraphViewTest {

    @Test
    public void constructorRetainsTheGivenGraph() {
        mxGraph graph = new mxGraph();
        ScilabGraphView view = new ScilabGraphView(graph);
        assertSame(graph, view.getGraph());
    }

    /** Build a resolved state for a plain-text vertex label. */
    private static mxCellState plainTextVertexState(mxGraph graph, String label) {
        mxCell cell = new mxCell(label);
        cell.setVertex(true);

        mxCellState state = new mxCellState(graph.getView(), cell, new HashMap<String, Object>());
        state.setCell(cell);
        state.setX(0);
        state.setY(0);
        state.setWidth(80);
        state.setHeight(30);
        state.setAbsoluteOffset(new mxPoint(0, 0));

        Map<String, Object> style = new HashMap<String, Object>();
        state.setStyle(style);
        return state;
    }

    @Test
    public void updateLabelBoundsComputesNonNullBoundsForPlainText() {
        mxGraph graph = new mxGraph();
        ScilabGraphView view = new ScilabGraphView(graph);

        mxCellState state = plainTextVertexState(graph, "hello");
        // Before the call there is no label bounds.
        assertNull(state.getLabelBounds());

        view.updateLabelBounds(state);

        assertNotNull(state.getLabelBounds());
    }

    @Test
    public void updateLabelBoundsHandlesAnEmptyLabel() {
        mxGraph graph = new mxGraph();
        ScilabGraphView view = new ScilabGraphView(graph);

        mxCellState state = plainTextVertexState(graph, "");
        view.updateLabelBounds(state);

        // An empty label still yields a (possibly zero-sized) bounds object.
        assertNotNull(state.getLabelBounds());
    }
}
