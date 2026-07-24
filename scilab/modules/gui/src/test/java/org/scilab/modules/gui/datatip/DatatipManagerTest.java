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

package org.scilab.modules.gui.datatip;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link DatatipManager}, the static registry that maps a
 * figure uid to its {@link DatatipManagerMode}.
 *
 * <h2>What is exercised, and why not everything</h2>
 *
 * <p>{@code DatatipManager} is a thin façade over a {@code static
 * Map<Integer, DatatipManagerMode>}. Its own class initializer only allocates
 * that {@code HashMap} and references no native type, so loading and using the
 * façade against an <em>empty</em> map stays pure Java: each accessor's guarded
 * {@code if (mode != null)} branch is skipped and a safe default is returned.
 * That absent-entry contract &mdash; what the manager answers for a figure that
 * has no datatip mode &mdash; is the real, constantly-used public behavior tested
 * here.
 *
 * <p>The <em>present-entry</em> half cannot be reached hermetically:
 * <ul>
 *   <li>{@link DatatipManager#newDatatipManagerMode(int)} constructs a
 *       {@link DatatipManagerMode}, and that class's <b>static initializer</b>
 *       eagerly evaluates {@code datatipMessage = Messages.gettext(...)}, a JNI
 *       call into native localization. Merely instantiating the mode (directly or
 *       via reflection) therefore requires the native runtime.</li>
 *   <li>{@link DatatipManager#start(int)} / {@link DatatipManager#stop(int)} call
 *       the SWIG-generated native {@code ScilabNativeView}.</li>
 *   <li>{@code setEnabled(uid, b)} and {@code setSelected(uid, tip)} on a
 *       <em>present</em> mode reach the native {@code GraphicController}.</li>
 * </ul>
 * Those paths are intentionally not invoked. A notable consequence documented in
 * a comment below: because the only hermetic value of {@code selectedTip} is its
 * initial {@code null}, {@code getSelected(uid)} for a <em>registered</em> mode
 * would auto-unbox {@code null} to {@code int} and throw &mdash; but reaching that
 * state needs the native constructor, so it is out of scope here.
 *
 * <p>Every test first {@code deleteDatatipManager(uid)} on a distinctive uid to
 * guarantee absence, so the assertions hold regardless of test ordering or of
 * whatever else shares the process-wide static map.
 */
public class DatatipManagerTest {

    /**
     * Distinctive uids no production code or sibling test would register, keeping
     * these tests independent of the shared static {@code allDatatipManagers} map.
     */
    private static final int UID_A = 0x0DA70001;
    private static final int UID_B = 0x0DA70002;

    // --- isEnabled: absent-entry default ------------------------------------

    @Test
    public void isEnabledReturnsFalseForAFigureWithNoDatatipManager() {
        DatatipManager.deleteDatatipManager(UID_A);
        assertFalse(DatatipManager.isEnabled(UID_A),
                    "a figure without a registered datatip mode is never enabled");
    }

    // --- getSelected: absent-entry default (primitive 0, not null) ----------

    @Test
    public void getSelectedReturnsZeroForAFigureWithNoDatatipManager() {
        DatatipManager.deleteDatatipManager(UID_A);
        // The absent branch returns the literal 0 (int), sidestepping the
        // null-unboxing hazard that the present branch would hit.
        assertEquals(0, DatatipManager.getSelected(UID_A),
                     "no selected tip is reported as 0 for an unregistered figure");
    }

    // --- getFromUid: absent-entry default -----------------------------------

    @Test
    public void getFromUidReturnsNullForAFigureWithNoDatatipManager() {
        DatatipManager.deleteDatatipManager(UID_A);
        assertNull(DatatipManager.getFromUid(UID_A),
                   "no datatip mode instance exists for an unregistered figure uid");
    }

    // --- setEnabled / setSelected: defensive no-ops -------------------------

    @Test
    public void setEnabledOnAFigureWithNoDatatipManagerIsASilentNoOp() {
        DatatipManager.deleteDatatipManager(UID_A);

        // Guarded by the null check, so it never reaches the native GraphicController.
        assertDoesNotThrow(() -> DatatipManager.setEnabled(UID_A, true));
        assertDoesNotThrow(() -> DatatipManager.setEnabled(UID_A, false));

        // Enabling cannot conjure a mode into existence.
        assertNull(DatatipManager.getFromUid(UID_A));
        assertFalse(DatatipManager.isEnabled(UID_A),
                    "setEnabled must not enable a figure that has no datatip mode");
    }

    @Test
    public void setSelectedOnAFigureWithNoDatatipManagerIsASilentNoOp() {
        DatatipManager.deleteDatatipManager(UID_A);

        assertDoesNotThrow(() -> DatatipManager.setSelected(UID_A, 42));

        assertNull(DatatipManager.getFromUid(UID_A));
        assertEquals(0, DatatipManager.getSelected(UID_A),
                     "setSelected on an absent figure leaves the selection unchanged");
    }

    // --- deleteDatatipManager: harmless + idempotent ------------------------

    @Test
    public void deleteDatatipManagerOnAFigureWithNoneIsAHarmlessNoOp() {
        DatatipManager.deleteDatatipManager(UID_A);
        assertDoesNotThrow(() -> DatatipManager.deleteDatatipManager(UID_A));
        assertNull(DatatipManager.getFromUid(UID_A));
    }

    @Test
    public void deleteDatatipManagerIsIdempotentAcrossRepeatedCalls() {
        assertDoesNotThrow(() -> {
            DatatipManager.deleteDatatipManager(UID_A);
            DatatipManager.deleteDatatipManager(UID_A);
            DatatipManager.deleteDatatipManager(UID_A);
        });
        assertNull(DatatipManager.getFromUid(UID_A));
        assertFalse(DatatipManager.isEnabled(UID_A));
    }

    // --- keying: distinct figures are independent ---------------------------

    @Test
    public void distinctFigureUidsAreTrackedIndependentlyInTheAbsentContract() {
        DatatipManager.deleteDatatipManager(UID_A);
        DatatipManager.deleteDatatipManager(UID_B);

        assertNull(DatatipManager.getFromUid(UID_A));
        assertNull(DatatipManager.getFromUid(UID_B));
        assertFalse(DatatipManager.isEnabled(UID_A));
        assertFalse(DatatipManager.isEnabled(UID_B));
        assertEquals(0, DatatipManager.getSelected(UID_A));
        assertEquals(0, DatatipManager.getSelected(UID_B));

        DatatipManager.setEnabled(UID_A, true);
        assertFalse(DatatipManager.isEnabled(UID_B),
                    "touching one uid must not affect another");
    }

    // --- full absent-entry contract, end to end -----------------------------

    @Test
    public void theWholeAbsentDatatipManagerContractHoldsTogether() {
        DatatipManager.deleteDatatipManager(UID_A);

        assertNull(DatatipManager.getFromUid(UID_A));
        assertFalse(DatatipManager.isEnabled(UID_A));
        assertEquals(0, DatatipManager.getSelected(UID_A));

        // Every mutator is a no-op while no mode is registered...
        assertDoesNotThrow(() -> DatatipManager.setEnabled(UID_A, true));
        assertDoesNotThrow(() -> DatatipManager.setSelected(UID_A, 7));
        assertDoesNotThrow(() -> DatatipManager.deleteDatatipManager(UID_A));

        // ...and the observable state is unchanged afterwards.
        assertNull(DatatipManager.getFromUid(UID_A));
        assertFalse(DatatipManager.isEnabled(UID_A));
        assertEquals(0, DatatipManager.getSelected(UID_A));
    }
}
