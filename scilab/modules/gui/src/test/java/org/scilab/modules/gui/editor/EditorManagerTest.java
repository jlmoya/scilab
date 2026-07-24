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

package org.scilab.modules.gui.editor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link EditorManager}, the static registry that maps a
 * figure uid to its point-and-click {@link Editor}.
 *
 * <h2>What is exercised, and why not everything</h2>
 *
 * <p>{@code EditorManager} is a thin façade over a {@code static
 * Map<Integer, Editor>}. Its class initializer only allocates that {@code
 * HashMap}, so loading the class touches no native code and no Swing peer. The
 * <em>absent-entry</em> half of every accessor is likewise pure Java: when no
 * editor is registered for a uid the guarded {@code if (editor != null)} branch
 * is skipped and a safe default is returned. That path is the contract tested
 * here, and it is the one the running application relies on constantly &mdash;
 * callbacks routinely ask {@code isModifyEnabled(fig)} for a figure that never
 * had an editor created.
 *
 * <p>The <em>present-entry</em> half cannot be reached hermetically:
 * <ul>
 *   <li>{@link EditorManager#newEditor(Integer)} constructs a real
 *       {@link Editor}, whose constructor builds a Swing popup menu via
 *       {@code Messages.gettext(...)} &mdash; a JNI call into native
 *       localization &mdash; and then reaches the native {@code GraphicController}
 *       through {@code CommonHandler.objectExists(...)}.</li>
 *   <li>{@link EditorManager#start(int)} / {@link EditorManager#stop(int)} call
 *       the SWIG-generated native {@code ScilabNativeView}.</li>
 * </ul>
 * Those four methods require the native Scilab runtime and are intentionally not
 * invoked. Every test below first {@code deleteEditor(uid)} on a distinctive uid
 * to guarantee absence, so the assertions hold regardless of test ordering or of
 * whatever else shares the process-wide static map.
 */
public class EditorManagerTest {

    /**
     * Distinctive uids no production code or sibling test would register, keeping
     * these tests independent of the shared static {@code allEditors} map.
     */
    private static final int UID_A = 0x5E1D0001;
    private static final int UID_B = 0x5E1D0002;

    // --- isModifyEnabled: absent-entry default ------------------------------

    @Test
    public void isModifyEnabledReturnsFalseForAFigureWithNoEditor() {
        EditorManager.deleteEditor(UID_A);
        assertFalse(EditorManager.isModifyEnabled(UID_A),
                    "a figure without a registered editor is never modify-enabled");
    }

    // --- getFromUid: absent-entry default -----------------------------------

    @Test
    public void getFromUidReturnsNullForAFigureWithNoEditor() {
        EditorManager.deleteEditor(UID_A);
        assertNull(EditorManager.getFromUid(UID_A),
                   "no editor instance exists for an unregistered figure uid");
    }

    // --- enableModify / disableModify: defensive no-ops ---------------------

    @Test
    public void enableModifyOnAFigureWithNoEditorIsASilentNoOp() {
        EditorManager.deleteEditor(UID_A);

        // The null guard swallows the call: no editor is created and nothing throws.
        assertDoesNotThrow(() -> EditorManager.enableModify(UID_A));

        // Crucially, enabling cannot conjure an editor into existence.
        assertNull(EditorManager.getFromUid(UID_A));
        assertFalse(EditorManager.isModifyEnabled(UID_A),
                    "enableModify must not enable a figure that has no editor");
    }

    @Test
    public void disableModifyOnAFigureWithNoEditorIsASilentNoOp() {
        EditorManager.deleteEditor(UID_A);

        assertDoesNotThrow(() -> EditorManager.disableModify(UID_A));

        assertNull(EditorManager.getFromUid(UID_A));
        assertFalse(EditorManager.isModifyEnabled(UID_A));
    }

    // --- deleteEditor: harmless + idempotent --------------------------------

    @Test
    public void deleteEditorOnAFigureWithNoEditorIsAHarmlessNoOp() {
        // Ensure absence, then delete again: HashMap.remove of a missing key is a no-op.
        EditorManager.deleteEditor(UID_A);
        assertDoesNotThrow(() -> EditorManager.deleteEditor(UID_A));
        assertNull(EditorManager.getFromUid(UID_A));
    }

    @Test
    public void deleteEditorIsIdempotentAcrossRepeatedCalls() {
        assertDoesNotThrow(() -> {
            EditorManager.deleteEditor(UID_A);
            EditorManager.deleteEditor(UID_A);
            EditorManager.deleteEditor(UID_A);
        });
        assertNull(EditorManager.getFromUid(UID_A));
        assertFalse(EditorManager.isModifyEnabled(UID_A));
    }

    // --- keying: distinct figures are independent ---------------------------

    @Test
    public void distinctFigureUidsAreTrackedIndependentlyInTheAbsentContract() {
        EditorManager.deleteEditor(UID_A);
        EditorManager.deleteEditor(UID_B);

        // Two different uids each resolve to the same "no editor" answers, and
        // operating on one never leaks state onto the other.
        assertNull(EditorManager.getFromUid(UID_A));
        assertNull(EditorManager.getFromUid(UID_B));
        assertFalse(EditorManager.isModifyEnabled(UID_A));
        assertFalse(EditorManager.isModifyEnabled(UID_B));

        EditorManager.enableModify(UID_A);
        assertFalse(EditorManager.isModifyEnabled(UID_B),
                    "touching one uid must not affect another");
    }

    // --- full absent-entry contract, end to end -----------------------------

    @Test
    public void theWholeAbsentEditorContractHoldsTogether() {
        EditorManager.deleteEditor(UID_A);

        assertNull(EditorManager.getFromUid(UID_A));
        assertFalse(EditorManager.isModifyEnabled(UID_A));

        // Every mutator is a no-op while no editor is registered...
        assertDoesNotThrow(() -> EditorManager.enableModify(UID_A));
        assertDoesNotThrow(() -> EditorManager.disableModify(UID_A));
        assertDoesNotThrow(() -> EditorManager.deleteEditor(UID_A));

        // ...and the observable state is unchanged afterwards.
        assertNull(EditorManager.getFromUid(UID_A));
        assertFalse(EditorManager.isModifyEnabled(UID_A));
    }
}
