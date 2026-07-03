/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab / macOS 2027 fork
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.modules.gui.bridge.canvas;

import java.awt.LayoutManager;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JPanel;

import org.scilab.forge.scirenderer.Canvas;
import org.scilab.modules.graphic_objects.axes.AxesContainer;
import org.scilab.modules.gui.canvas.SimpleCanvas;

/**
 * The rendering-backend seam for a figure's drawing area. Container code (frames, panels, scroll
 * panes) is typed against this class, so a figure can be backed by any renderer implementation —
 * the JOGL {@link SwingScilabCanvas} or the Vulkan {@link SwingScilabVulkanCanvas} — chosen by
 * {@link ScilabCanvasFactory}.
 */
public abstract class AbstractScilabCanvas extends JPanel implements SimpleCanvas {

    private static final long serialVersionUID = 1L;

    protected AbstractScilabCanvas(LayoutManager layout) {
        super(layout);
    }

    /** @return the scirenderer canvas doing the actual drawing. */
    public abstract Canvas getRendererCanvas();

    /** @return the MVC figure (or axes container) rendered by this canvas. */
    public abstract AxesContainer getFigure();

    /** @return the Scilab id of the rendered figure. */
    public abstract int getFigureIndex();

    /** @return whether the figure has auto-resize set. */
    public abstract boolean isAutoResize();

    public abstract void setId(Integer id);

    public abstract Integer getId();

    /** Adds the listener handling key events to the canvas. */
    public abstract void addEventHandlerKeyListener(KeyListener listener);

    /** Removes the listener handling key events from the canvas. */
    public abstract void removeEventHandlerKeyListener(KeyListener listener);

    /** Adds the listener handling mouse events to the drawable component. */
    public abstract void addEventHandlerMouseListener(MouseListener listener);

    /** Removes the listener handling mouse events from the drawable component. */
    public abstract void removeEventHandlerMouseListener(MouseListener listener);

    /** Adds the listener handling mouse motion events to the drawable component. */
    public abstract void addEventHandlerMouseMotionListener(MouseMotionListener listener);

    /** Removes the listener handling mouse motion events from the drawable component. */
    public abstract void removeEventHandlerMouseMotionListener(MouseMotionListener listener);
}
