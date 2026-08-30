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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Preview hands a path to the Scilab interpreter as a string inside a command.
 * That is a quoting boundary, and quoting boundaries fail silently: a path the
 * command mangles produces "file not found" rather than anything that points at
 * the real cause.
 */
public class PreviewCommandTest {

    @Test
    public void theCommandExecsTheFileSilently() {
        String cmd = GuiDesignerTab.execCommand(Paths.get("/tmp/preview.sce"));
        assertTrue(cmd.startsWith("exec("), cmd);
        // -1 so the console shows the GUI appearing, not a transcript of the
        // code that built it.
        assertTrue(cmd.contains(", -1)"), cmd);
        assertTrue(cmd.contains("/tmp/preview.sce"), cmd);
    }

    @Test
    public void aDoubleQuoteInThePathIsDoubledAsScilabExpects() {
        // Scilab escapes a double quote inside a double-quoted string by
        // doubling it. Left alone, the string would terminate early and the
        // remainder of the path would be parsed as code.
        String cmd = GuiDesignerTab.execCommand(Paths.get("/tmp/od\"d/preview.sce"));
        assertTrue(cmd.contains("od\"\"d"), cmd);
    }

    @Test
    public void aSingleQuoteInThePathIsLeftAloneAndStillParses() {
        // A single quote needs no escaping inside a double-quoted Scilab string,
        // and doubling it would corrupt the path instead of protecting it.
        String cmd = GuiDesignerTab.execCommand(Paths.get("/tmp/it's/preview.sce"));
        assertTrue(cmd.contains("it's"), cmd);
        assertFalse(cmd.contains("it''s"), cmd);
    }

    @Test
    public void thePreviewFileHoldsTheSourceAsUtf8() throws IOException {
        // Written as UTF-8 whatever the original charset was, because Scilab
        // reads scripts as UTF-8 and this copy exists only to be executed.
        String source = "disp(\"café\");\n";
        Path tmp = GuiDesignerTab.writePreviewFile(source);
        try {
            assertEquals(source, new String(Files.readAllBytes(tmp), StandardCharsets.UTF_8));
            assertTrue(tmp.getFileName().toString().endsWith(".sce"),
                       "Scilab decides how to read a file by its extension: " + tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void theOriginalFileIsNotWhatGetsExecuted() throws IOException {
        // Preview must never run the file on disk: once editing exists, the
        // design and the file will differ, and previewing the stale one would
        // quietly show the wrong GUI.
        Path tmp = GuiDesignerTab.writePreviewFile("disp(1);\n");
        try {
            assertTrue(tmp.toAbsolutePath().toString().contains("guidesigner-preview-"), tmp.toString());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
