/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.guibuilder.ui;

/**
 * Scilab-facing entry point for the GUI designer.
 *
 * <p>Called from native code through a hand-written JNI bridge
 * (modules/guibuilder/src/jni/GuiDesigner.{hxx,cpp}) invoked by the
 * guidesigner_open gateway primitive (modules/guibuilder/sci_gateway/cpp/
 * sci_guidesigner.cpp), which modules/guibuilder/macros/guidesigner.sci
 * calls. This mirrors the mechanism org.scilab.modules.scinotes.SciNotes
 * uses, rather than {@code @ScilabExported}: that annotation lives in
 * modules/graph, a module neither guibuilder nor its scinotes-shaped
 * dependencies otherwise need.
 */
public final class GuiDesigner {

    private GuiDesigner() {
    }

    /**
     * Open the designer, on a file when one is given.
     *
     * @param path a .sce to open, or the empty string for an empty designer
     * @return true when the tab was opened
     */
    public static boolean open(String path) {
        return GuiDesignerTab.openOn(path);
    }
}
