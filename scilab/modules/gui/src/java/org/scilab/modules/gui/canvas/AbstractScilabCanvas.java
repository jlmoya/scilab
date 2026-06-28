/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab macOS/2027 modernization
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.gui.canvas;

import java.awt.LayoutManager;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JPanel;

import org.scilab.modules.graphic_objects.axes.AxesContainer;

/**
 * Common base for a Scilab figure rendering canvas.
 *
 * <p>A figure canvas is both a Swing {@link JPanel} (so it drops into the figure's component
 * tree) and a {@link SimpleCanvas} (the GUI bridge contract), and it routes the figure editor's
 * key/mouse handlers to the underlying drawable. This base names that shared surface so the figure
 * machinery (the frame and the dockable/static panels) can hold either the default JOGL canvas
 * ({@code SwingScilabCanvas}) or an alternative backend such as the flag-gated bgfx/Metal canvas
 * ({@code SwingScilabBgfxCanvas}) through one type, with no {@code instanceof} juggling.
 *
 * <p>It deliberately adds no behavior: {@code SwingScilabCanvas} already provides every method
 * below, so making it extend this base is behavior-preserving.
 *
 * @author Scilab macOS/2027 modernization
 */
public abstract class AbstractScilabCanvas extends JPanel implements SimpleCanvas {

    private static final long serialVersionUID = 1L;

    protected AbstractScilabCanvas() {
        super();
    }

    protected AbstractScilabCanvas(LayoutManager layout) {
        super(layout);
    }

    /**
     * @return the MVC figure (axes container) drawn by this canvas.
     */
    public abstract AxesContainer getFigure();

    /* Editor event-handler routing (to the underlying drawable component). */

    public abstract void addEventHandlerKeyListener(KeyListener listener);

    public abstract void removeEventHandlerKeyListener(KeyListener listener);

    public abstract void addEventHandlerMouseListener(MouseListener listener);

    public abstract void removeEventHandlerMouseListener(MouseListener listener);

    public abstract void addEventHandlerMouseMotionListener(MouseMotionListener listener);

    public abstract void removeEventHandlerMouseMotionListener(MouseMotionListener listener);
}
