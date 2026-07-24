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
package org.scilab.modules.xcos.port;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.scilab.modules.xcos.port.command.CommandPort;
import org.scilab.modules.xcos.port.control.ControlPort;
import org.scilab.modules.xcos.port.input.InputPort;
import org.scilab.modules.xcos.port.output.OutputPort;

import com.mxgraph.util.mxConstants;

/**
 * Hermetic unit tests for {@link Orientation}.
 *
 * <p>{@code Orientation} is a pure-Java enum ({@code WEST, NORTH, EAST, SOUTH}).
 * The angle helpers ({@link Orientation#getAbsoluteAngle} /
 * {@link Orientation#getRelativeAngle}) take a {@code Class<? extends BasicPort>}
 * but only feed it to {@code Class.isAssignableFrom} to decide whether the port
 * is an output (or command) port. No {@code BasicPort} is ever instantiated and
 * none of those classes are <em>initialized</em> (a {@code .class} literal loads
 * and links a class but does not run its static initializer), so these tests
 * never cross the JNI boundary and require no native runtime.</p>
 *
 * <p>The ordinal order is a semantic contract: {@code getOrientationAngle()} is
 * defined as {@code ordinal() * 90}, so the whole angle algebra depends on
 * {@code WEST=0, NORTH=1, EAST=2, SOUTH=3}. The assertions below pin that order
 * and the derived angles.</p>
 */
public class OrientationTest {

    /** Every orientation, in declared order. */
    private static final Orientation[] ALL = {
        Orientation.WEST, Orientation.NORTH, Orientation.EAST, Orientation.SOUTH
    };

    // ---------------------------------------------------------------------
    // enum identity / ordering
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("exactly four orientations declared, in W-N-E-S order")
    public void valuesInExpectedOrder() {
        assertArrayEquals(ALL, Orientation.values());
        assertEquals(4, Orientation.values().length);
    }

    @Test
    @DisplayName("ordinals drive the *90 orientation angle (W=0,N=1,E=2,S=3)")
    public void ordinalsPinTheAngleContract() {
        assertEquals(0, Orientation.WEST.ordinal());
        assertEquals(1, Orientation.NORTH.ordinal());
        assertEquals(2, Orientation.EAST.ordinal());
        assertEquals(3, Orientation.SOUTH.ordinal());
    }

    @Test
    @DisplayName("valueOf round-trips with name() for every constant")
    public void valueOfRoundTrips() {
        for (Orientation o : Orientation.values()) {
            assertSame(o, Orientation.valueOf(o.name()));
        }
    }

    // ---------------------------------------------------------------------
    // getLabelPosition()
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("getLabelPosition maps horizontal sides, center otherwise")
    public void labelPositionMapping() {
        assertEquals(mxConstants.ALIGN_RIGHT, Orientation.EAST.getLabelPosition());
        assertEquals(mxConstants.ALIGN_LEFT, Orientation.WEST.getLabelPosition());
        assertEquals(mxConstants.ALIGN_CENTER, Orientation.NORTH.getLabelPosition());
        assertEquals(mxConstants.ALIGN_CENTER, Orientation.SOUTH.getLabelPosition());
    }

    @Test
    @DisplayName("getLabelPosition returns the documented jgraphx literals")
    public void labelPositionLiterals() {
        // Characterization of the concrete strings handed to jgraphx.
        assertEquals("right", Orientation.EAST.getLabelPosition());
        assertEquals("left", Orientation.WEST.getLabelPosition());
        assertEquals("center", Orientation.NORTH.getLabelPosition());
        assertEquals("center", Orientation.SOUTH.getLabelPosition());
    }

    // ---------------------------------------------------------------------
    // getVerticalLabelPosition()
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("getVerticalLabelPosition maps vertical sides, middle otherwise")
    public void verticalLabelPositionMapping() {
        assertEquals(mxConstants.ALIGN_TOP, Orientation.NORTH.getVerticalLabelPosition());
        assertEquals(mxConstants.ALIGN_BOTTOM, Orientation.SOUTH.getVerticalLabelPosition());
        assertEquals(mxConstants.ALIGN_MIDDLE, Orientation.EAST.getVerticalLabelPosition());
        assertEquals(mxConstants.ALIGN_MIDDLE, Orientation.WEST.getVerticalLabelPosition());
    }

    @Test
    @DisplayName("getVerticalLabelPosition returns the documented jgraphx literals")
    public void verticalLabelPositionLiterals() {
        assertEquals("top", Orientation.NORTH.getVerticalLabelPosition());
        assertEquals("bottom", Orientation.SOUTH.getVerticalLabelPosition());
        assertEquals("middle", Orientation.EAST.getVerticalLabelPosition());
        assertEquals("middle", Orientation.WEST.getVerticalLabelPosition());
    }

    // ---------------------------------------------------------------------
    // getSpacingSide()
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("getSpacingSide maps each side to its jgraphx spacing style")
    public void spacingSideMapping() {
        // STYLE_SPACING_* are non-final in jgraphx; assert against the fields
        // themselves rather than hard-coded literals so the mapping (not the
        // library's spelling) is what is under test.
        assertEquals(mxConstants.STYLE_SPACING_TOP, Orientation.NORTH.getSpacingSide());
        assertEquals(mxConstants.STYLE_SPACING_BOTTOM, Orientation.SOUTH.getSpacingSide());
        assertEquals(mxConstants.STYLE_SPACING_RIGHT, Orientation.EAST.getSpacingSide());
        assertEquals(mxConstants.STYLE_SPACING_LEFT, Orientation.WEST.getSpacingSide());
    }

    @Test
    @DisplayName("every side yields a distinct, non-empty spacing style")
    public void spacingSidesAreDistinct() {
        String n = Orientation.NORTH.getSpacingSide();
        String s = Orientation.SOUTH.getSpacingSide();
        String e = Orientation.EAST.getSpacingSide();
        String w = Orientation.WEST.getSpacingSide();
        for (String v : new String[] { n, s, e, w }) {
            assertTrue(v != null && !v.isEmpty(), "spacing style must be non-empty");
        }
        assertEquals(4, new java.util.HashSet<>(java.util.Arrays.asList(n, s, e, w)).size(),
                     "the four sides must map to four different styles");
    }

    // ---------------------------------------------------------------------
    // getAbsoluteAngle() — input-like ports (neither OutputPort nor CommandPort)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("absolute angle of an input port is ordinal*90 when un-flipped/un-mirrored")
    public void absoluteAngleInputNeutral() {
        assertEquals(0, Orientation.WEST.getAbsoluteAngle(InputPort.class, false, false));
        assertEquals(90, Orientation.NORTH.getAbsoluteAngle(InputPort.class, false, false));
        assertEquals(180, Orientation.EAST.getAbsoluteAngle(InputPort.class, false, false));
        assertEquals(270, Orientation.SOUTH.getAbsoluteAngle(InputPort.class, false, false));
    }

    @Test
    @DisplayName("output ports add 180 to the base angle (mod 360)")
    public void absoluteAngleOutputNeutral() {
        assertEquals(180, Orientation.WEST.getAbsoluteAngle(OutputPort.class, false, false));
        assertEquals(270, Orientation.NORTH.getAbsoluteAngle(OutputPort.class, false, false));
        assertEquals(0, Orientation.EAST.getAbsoluteAngle(OutputPort.class, false, false)); // 360 % 360
        assertEquals(90, Orientation.SOUTH.getAbsoluteAngle(OutputPort.class, false, false)); // 450 % 360
    }

    @Test
    @DisplayName("CommandPort is classified as an output port (same +180 offset)")
    public void commandPortActsAsOutput() {
        for (Orientation o : ALL) {
            assertEquals(o.getAbsoluteAngle(OutputPort.class, false, false),
                         o.getAbsoluteAngle(CommandPort.class, false, false),
                         "CommandPort must share the OutputPort offset for " + o);
        }
    }

    @Test
    @DisplayName("ControlPort is classified as an input port (no +180 offset)")
    public void controlPortActsAsInput() {
        for (Orientation o : ALL) {
            assertEquals(o.getAbsoluteAngle(InputPort.class, false, false),
                         o.getAbsoluteAngle(ControlPort.class, false, false),
                         "ControlPort must share the InputPort offset for " + o);
        }
    }

    @Test
    @DisplayName("flip only rotates NORTH/SOUTH; mirror only rotates WEST/EAST")
    public void flipAndMirrorAreAxisSelective() {
        // flip is a no-op on the horizontal (WEST/EAST) axis...
        assertEquals(Orientation.WEST.getAbsoluteAngle(InputPort.class, false, false),
                     Orientation.WEST.getAbsoluteAngle(InputPort.class, true, false));
        assertEquals(Orientation.EAST.getAbsoluteAngle(InputPort.class, false, false),
                     Orientation.EAST.getAbsoluteAngle(InputPort.class, true, false));
        // ...but flips the vertical (NORTH/SOUTH) axis by 180.
        assertEquals(270, Orientation.NORTH.getAbsoluteAngle(InputPort.class, true, false)); // 90+180
        assertEquals(90, Orientation.SOUTH.getAbsoluteAngle(InputPort.class, true, false)); // 270+180-360

        // mirror is a no-op on the vertical axis...
        assertEquals(Orientation.NORTH.getAbsoluteAngle(InputPort.class, false, false),
                     Orientation.NORTH.getAbsoluteAngle(InputPort.class, false, true));
        assertEquals(Orientation.SOUTH.getAbsoluteAngle(InputPort.class, false, false),
                     Orientation.SOUTH.getAbsoluteAngle(InputPort.class, false, true));
        // ...but flips the horizontal axis by 180.
        assertEquals(180, Orientation.WEST.getAbsoluteAngle(InputPort.class, false, true)); // 0+180
        assertEquals(0, Orientation.EAST.getAbsoluteAngle(InputPort.class, false, true)); // 180+180-360
    }

    @Test
    @DisplayName("output + flip + mirror cancels back to the neutral input angles")
    public void outputFlippedAndMirroredCancels() {
        // +180 (output) plus +180 on the active axis (flip/mirror) == +360 == 0.
        assertEquals(0, Orientation.WEST.getAbsoluteAngle(OutputPort.class, true, true));
        assertEquals(90, Orientation.NORTH.getAbsoluteAngle(OutputPort.class, true, true));
        assertEquals(180, Orientation.EAST.getAbsoluteAngle(OutputPort.class, true, true));
        assertEquals(270, Orientation.SOUTH.getAbsoluteAngle(OutputPort.class, true, true));
    }

    @Test
    @DisplayName("absolute angle is always a normalized [0,360) multiple of 90")
    public void absoluteAngleAlwaysNormalized() {
        // Non-negative inputs -> the final `% 360` always lands in [0,360).
        for (Orientation o : ALL) {
            for (Class<? extends BasicPort> k : kinds()) {
                for (boolean flip : new boolean[] { false, true }) {
                    for (boolean mirror : new boolean[] { false, true }) {
                        assertNormalized(o.getAbsoluteAngle(k, flip, mirror));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // getRelativeAngle()
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("getRelativeAngle(0,...) == getAbsoluteAngle(...) for every combination")
    public void relativeAtZeroEqualsAbsolute() {
        Class<? extends BasicPort>[] kinds = kinds();
        for (Orientation o : ALL) {
            for (Class<? extends BasicPort> k : kinds) {
                for (boolean flip : new boolean[] { false, true }) {
                    for (boolean mirror : new boolean[] { false, true }) {
                        assertEquals(o.getAbsoluteAngle(k, flip, mirror),
                                     o.getRelativeAngle(0, k, flip, mirror),
                                     "blockAngle=0 must be the absolute angle for "
                                     + o + "/" + k.getSimpleName() + "/f=" + flip + "/m=" + mirror);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("relative angle rotates an input port by the block angle (90 deg)")
    public void relativeAngleInput90() {
        assertEquals(90, Orientation.WEST.getRelativeAngle(90, InputPort.class, false, false));
        assertEquals(180, Orientation.NORTH.getRelativeAngle(90, InputPort.class, false, false));
        assertEquals(270, Orientation.EAST.getRelativeAngle(90, InputPort.class, false, false));
        assertEquals(0, Orientation.SOUTH.getRelativeAngle(90, InputPort.class, false, false)); // 360 % 360
    }

    @Test
    @DisplayName("relative angle rotates an output port by the block angle (90 deg)")
    public void relativeAngleOutput90() {
        assertEquals(270, Orientation.WEST.getRelativeAngle(90, OutputPort.class, false, false));
        assertEquals(0, Orientation.NORTH.getRelativeAngle(90, OutputPort.class, false, false)); // 360 % 360
        assertEquals(90, Orientation.EAST.getRelativeAngle(90, OutputPort.class, false, false)); // 450 % 360
        assertEquals(180, Orientation.SOUTH.getRelativeAngle(90, OutputPort.class, false, false)); // 540 % 360
    }

    @Test
    @DisplayName("a full-turn block angle (360) is equivalent to 0")
    public void relativeAngleFullTurnIsIdentity() {
        for (Orientation o : ALL) {
            for (Class<? extends BasicPort> k : kinds()) {
                assertEquals(o.getRelativeAngle(0, k, false, false),
                             o.getRelativeAngle(360, k, false, false),
                             "360 deg must equal 0 deg for " + o + "/" + k.getSimpleName());
            }
        }
    }

    @Test
    @DisplayName("CHARACTERIZATION: a negative block angle yields a negative, non-normalized result")
    public void relativeAngleNegativeBlockAngleIsNotNormalized() {
        // getFlippedAndMirroredAngle finishes with a plain `% 360`, and Java's %
        // keeps the sign of the dividend. WEST's base is 0, so 0 + (-90) = -90
        // and (-90 % 360) == -90 -- NOT 270. This documents current behavior.
        assertEquals(-90, Orientation.WEST.getRelativeAngle(-90, InputPort.class, false, false));
        // Orientations whose base absorbs the -90 into a non-negative value stay non-negative.
        assertEquals(0, Orientation.NORTH.getRelativeAngle(-90, InputPort.class, false, false)); // 90-90
        assertEquals(90, Orientation.EAST.getRelativeAngle(-90, InputPort.class, false, false)); // 180-90
        assertEquals(180, Orientation.SOUTH.getRelativeAngle(-90, InputPort.class, false, false)); // 270-90
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Class<? extends BasicPort>[] kinds() {
        return new Class[] {
            InputPort.class, OutputPort.class, CommandPort.class, ControlPort.class
        };
    }

    private static void assertNormalized(int angle) {
        assertTrue(angle >= 0 && angle < 360, "angle out of [0,360): " + angle);
        assertEquals(0, angle % 90, "angle must be a multiple of 90: " + angle);
    }
}
