/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab test coverage
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.xcos.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.modules.xcos.port.command.CommandPort;
import org.scilab.modules.xcos.port.control.ControlPort;
import org.scilab.modules.xcos.port.input.InputPort;
import org.scilab.modules.xcos.port.output.OutputPort;

/**
 * Hermetic unit tests for {@link LinkPortMap}.
 *
 * All assertions rely on class-literal comparisons and the pure static mapping
 * helpers. No {@link org.scilab.modules.xcos.port.BasicPort} or
 * {@link BasicLink} instances are constructed, so the native-backed
 * JavaController runtime is never touched. (Referencing a {@code Class} literal
 * loads but does not <em>initialize</em> the class, so no static initializer of
 * the link/port hierarchy runs.)
 */
public class LinkPortMapTest {

    /* ---- enum shape ---- */

    @Test
    public void enumHasSixValues() {
        assertEquals(6, LinkPortMap.values().length);
    }

    @Test
    public void valueOfRoundTrips() {
        for (LinkPortMap v : LinkPortMap.values()) {
            assertSame(v, LinkPortMap.valueOf(v.name()));
        }
    }

    /* ---- per-constant mapping (link class, port class, isStart) ---- */

    @Test
    public void exInputMapping() {
        assertSame(ExplicitLink.class, LinkPortMap.EX_INPUT.getLinkKlass());
        assertSame(InputPort.class, LinkPortMap.EX_INPUT.getPortKlass());
        assertFalse(LinkPortMap.EX_INPUT.isStart());
    }

    @Test
    public void imInputMapping() {
        assertSame(ImplicitLink.class, LinkPortMap.IM_INPUT.getLinkKlass());
        assertSame(InputPort.class, LinkPortMap.IM_INPUT.getPortKlass());
        assertFalse(LinkPortMap.IM_INPUT.isStart());
    }

    @Test
    public void exOutputMapping() {
        assertSame(ExplicitLink.class, LinkPortMap.EX_OUTPUT.getLinkKlass());
        assertSame(OutputPort.class, LinkPortMap.EX_OUTPUT.getPortKlass());
        assertTrue(LinkPortMap.EX_OUTPUT.isStart());
    }

    @Test
    public void imOutputMapping() {
        assertSame(ImplicitLink.class, LinkPortMap.IM_OUTPUT.getLinkKlass());
        assertSame(OutputPort.class, LinkPortMap.IM_OUTPUT.getPortKlass());
        assertTrue(LinkPortMap.IM_OUTPUT.isStart());
    }

    @Test
    public void controlMapping() {
        assertSame(CommandControlLink.class, LinkPortMap.CONTROL.getLinkKlass());
        assertSame(ControlPort.class, LinkPortMap.CONTROL.getPortKlass());
        assertFalse(LinkPortMap.CONTROL.isStart());
    }

    @Test
    public void commandMapping() {
        assertSame(CommandControlLink.class, LinkPortMap.COMMAND.getLinkKlass());
        assertSame(CommandPort.class, LinkPortMap.COMMAND.getPortKlass());
        assertTrue(LinkPortMap.COMMAND.isStart());
    }

    /* ---- getLinkPortMap(type, isStart) ---- */

    @Test
    public void getLinkPortMapExplicit() {
        assertSame(LinkPortMap.EX_INPUT, LinkPortMap.getLinkPortMap(1, false));
        assertSame(LinkPortMap.EX_OUTPUT, LinkPortMap.getLinkPortMap(1, true));
    }

    @Test
    public void getLinkPortMapImplicit() {
        assertSame(LinkPortMap.IM_INPUT, LinkPortMap.getLinkPortMap(2, false));
        assertSame(LinkPortMap.IM_OUTPUT, LinkPortMap.getLinkPortMap(2, true));
    }

    @Test
    public void getLinkPortMapControlCommand() {
        assertSame(LinkPortMap.CONTROL, LinkPortMap.getLinkPortMap(-1, false));
        assertSame(LinkPortMap.COMMAND, LinkPortMap.getLinkPortMap(-1, true));
    }

    @Test
    public void getLinkPortMapUnknownTypeReturnsNull() {
        assertNull(LinkPortMap.getLinkPortMap(0, false));
        assertNull(LinkPortMap.getLinkPortMap(0, true));
        assertNull(LinkPortMap.getLinkPortMap(3, false));
        assertNull(LinkPortMap.getLinkPortMap(-2, true));
        assertNull(LinkPortMap.getLinkPortMap(Integer.MAX_VALUE, false));
        assertNull(LinkPortMap.getLinkPortMap(Integer.MIN_VALUE, true));
    }

    /* ---- getLinkClass(type) ---- */

    @Test
    public void getLinkClassByType() {
        assertSame(ExplicitLink.class, LinkPortMap.getLinkClass(1));
        assertSame(ImplicitLink.class, LinkPortMap.getLinkClass(2));
        assertSame(CommandControlLink.class, LinkPortMap.getLinkClass(-1));
    }

    /**
     * Documents the contract stated in the javadoc: for a given type the link
     * class is independent of the start flag (the two directions of a type share
     * one link class).
     */
    @Test
    public void linkClassIsStartAgnostic() {
        assertSame(LinkPortMap.EX_INPUT.getLinkKlass(), LinkPortMap.EX_OUTPUT.getLinkKlass());
        assertSame(LinkPortMap.IM_INPUT.getLinkKlass(), LinkPortMap.IM_OUTPUT.getLinkKlass());
        assertSame(LinkPortMap.CONTROL.getLinkKlass(), LinkPortMap.COMMAND.getLinkKlass());
    }

    /**
     * Defect-characterization: {@code getLinkClass} does not guard against an
     * unmapped type. {@code getLinkPortMap} returns {@code null} and the
     * subsequent {@code getLinkKlass()} dereference throws NPE rather than
     * returning {@code null}.
     */
    @Test
    public void getLinkClassWithUnknownTypeThrowsNPE() {
        assertThrows(NullPointerException.class, () -> LinkPortMap.getLinkClass(0));
        assertThrows(NullPointerException.class, () -> LinkPortMap.getLinkClass(99));
    }

    /* ---- getPortClass(linkClass, isStart) ---- */

    @Test
    public void getPortClassExplicit() {
        assertSame(InputPort.class, LinkPortMap.getPortClass(ExplicitLink.class, false));
        assertSame(OutputPort.class, LinkPortMap.getPortClass(ExplicitLink.class, true));
    }

    @Test
    public void getPortClassImplicit() {
        assertSame(InputPort.class, LinkPortMap.getPortClass(ImplicitLink.class, false));
        assertSame(OutputPort.class, LinkPortMap.getPortClass(ImplicitLink.class, true));
    }

    @Test
    public void getPortClassCommandControl() {
        assertSame(ControlPort.class, LinkPortMap.getPortClass(CommandControlLink.class, false));
        assertSame(CommandPort.class, LinkPortMap.getPortClass(CommandControlLink.class, true));
    }

    /**
     * A link class not present in the mapping (the abstract base itself) yields
     * {@code null} for either direction.
     */
    @Test
    public void getPortClassUnknownLinkReturnsNull() {
        assertNull(LinkPortMap.getPortClass(BasicLink.class, false));
        assertNull(LinkPortMap.getPortClass(BasicLink.class, true));
    }

    /* ---- isStart(BasicPort) ---- */

    /**
     * A {@code null} port is neither an InputPort nor a ControlPort, so the
     * else-branch is taken and {@code 0.0} is returned. This exercises real
     * behavior without constructing a native-backed port instance.
     */
    @Test
    public void isStartNullPortIsNotAStart() {
        assertEquals(0.0, LinkPortMap.isStart(null), 0.0);
    }
}
