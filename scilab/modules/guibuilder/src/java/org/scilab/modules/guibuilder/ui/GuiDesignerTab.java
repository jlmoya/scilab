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

import java.nio.file.Files;
import java.nio.file.Paths;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;

/**
 * PLACEHOLDER for Task 7: parses the given file and prints a one-line
 * summary to stdout instead of opening a real tab. This is deliberate --
 * it proves the whole path (macro to gateway to giws to Java to parser)
 * before any UI exists, so a failure in Task 8 (which replaces this with a
 * real Swing tab) is a UI failure and nothing else. Do not add UI here.
 */
final class GuiDesignerTab {

    private GuiDesignerTab() {
    }

    static boolean openOn(String path) {
        try {
            if (path == null || path.isEmpty()) {
                System.out.println("[guidesigner] no file given");
                return true;
            }
            String src = new String(Files.readAllBytes(Paths.get(path)));
            Design d = ScilabGuiParser.parse(src);
            System.out.println("[guidesigner] " + path + ": " + d.allNodes().size()
                               + " widget(s), " + d.unmodelled().size() + " unmodelled region(s)");
            return true;
        } catch (Exception e) {
            System.out.println("[guidesigner] could not open " + path + ": " + e.getMessage());
            return false;
        }
    }
}
