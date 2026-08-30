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

package org.scilab.modules.guibuilder.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.SourceRange;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;

import org.junit.jupiter.api.Test;

public class DesignWriterTest {

    private static final SourceValidator ALWAYS_VALID = source -> true;
    private static final SourceValidator NEVER_VALID = source -> false;

    private static final String SRC = ""
        + "// A GUI somebody wrote by hand.\n"
        + "f  =  figure(\"figure_name\", \"Demo\");\n"
        + "\n"
        + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n"
        + "\n"
        + "function ok_callback()\n"
        + "  disp(\"hi\");   // untouched\n"
        + "endfunction\n";

    @Test
    public void theControllingInvariantOpenSaveIsByteIdentical() throws Exception {
        Design d = ScilabGuiParser.parse(SRC);
        String out = DesignWriter.write(d, new SourceDocument(SRC), ALWAYS_VALID);
        assertEquals(SRC, out, "saving without editing must not disturb a single byte");
    }

    @Test
    public void anEditChangesOnlyTheIntendedSpan() throws Exception {
        Design d = ScilabGuiParser.parse(SRC);
        Node ok = d.byTag("ok");
        SourceRange stringRange = ok.properties().get("string").range();

        SourceDocument doc = new SourceDocument(SRC);
        doc.replace(stringRange, "\"Go\"");
        String out = DesignWriter.write(d, doc, ALWAYS_VALID);

        assertTrue(out.contains("\"Go\""));
        // Everything else survives, including the double space and the comments.
        assertTrue(out.contains("f  =  figure("), "unrelated formatting was disturbed");
        assertTrue(out.contains("// A GUI somebody wrote by hand."));
        assertTrue(out.contains("disp(\"hi\");   // untouched"));
    }

    @Test
    public void aWriteThatWouldNotParseIsRefused() {
        Design d = ScilabGuiParser.parse(SRC);
        SourceDocument doc = new SourceDocument(SRC);
        doc.replace(new SourceRange(0, 1), "@");
        WriteRefusedException e = assertThrows(WriteRefusedException.class,
                                               () -> DesignWriter.write(d, doc, NEVER_VALID));
        assertTrue(e.getMessage().toLowerCase().contains("parse"));
    }

    @Test
    public void anEditOverlappingAnUnmodelledRegionIsRefused() {
        String src = ""
            + "for k = 1:3\n"
            + "  uicontrol(f, \"style\", \"text\", \"tag\", \"t\" + string(k));\n"
            + "end\n";
        Design d = ScilabGuiParser.parse(src);
        assertTrue(!d.unmodelled().isEmpty(), "precondition: the loop is unmodelled");

        SourceDocument doc = new SourceDocument(src);
        SourceRange locked = d.unmodelled().get(0).range();
        doc.replace(new SourceRange(locked.start(), locked.start() + 1), "X");

        WriteRefusedException e = assertThrows(WriteRefusedException.class,
                                               () -> DesignWriter.write(d, doc, ALWAYS_VALID));
        assertTrue(e.getMessage().toLowerCase().contains("locked"));
    }

    // --- Beyond the brief: the controlling invariant through the full
    // pipeline (parser included), not just through SourceDocument in
    // isolation -- and not just for files shaped like the brief's SRC. ---

    @Test
    public void aCrlfFileWithNoEditsRoundTripsByteIdenticalThroughTheFullPipeline() throws Exception {
        String src = ""
            + "f = figure(\"figure_name\", \"Demo\");\r\n"
            + "\r\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\r\n";
        Design d = ScilabGuiParser.parse(src);
        String out = DesignWriter.write(d, new SourceDocument(src), ALWAYS_VALID);
        assertEquals(src, out, "CRLF line endings must survive untouched, not be normalised to LF");
    }

    @Test
    public void aFileWithNoTrailingNewlineAndNoEditsRoundTripsByteIdenticalThroughTheFullPipeline()
            throws Exception {
        String src = "f = figure(\"figure_name\", \"Demo\");";
        Design d = ScilabGuiParser.parse(src);
        String out = DesignWriter.write(d, new SourceDocument(src), ALWAYS_VALID);
        assertEquals(src, out, "a missing trailing newline must not be invented");
    }

    @Test
    public void aDesignWithNoUnmodelledRegionsStillRoundTripsByteIdenticalAfterAnEdit() throws Exception {
        String src = ""
            + "f = figure();\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n";
        Design d = ScilabGuiParser.parse(src);
        assertTrue(d.unmodelled().isEmpty(), "precondition: nothing in this file is unmodelled");

        Node ok = d.byTag("ok");
        SourceRange stringRange = ok.properties().get("string").range();
        SourceDocument doc = new SourceDocument(src);
        doc.replace(stringRange, "\"Go\"");

        String out = DesignWriter.write(d, doc, ALWAYS_VALID);
        String expected = src.substring(0, stringRange.start()) + "\"Go\"" + src.substring(stringRange.end());
        assertEquals(expected, out);
    }

    // --- Beyond the brief: a refusal must be a clean refusal -- nothing
    // reaches the validator, and nothing is left half-applied on the
    // document for a caller to trip over afterwards. ---

    @Test
    public void aWriteRefusedByTheLockedRegionCheckNeverInvokesTheValidator() {
        String src = ""
            + "for k = 1:3\n"
            + "  uicontrol(f, \"style\", \"text\", \"tag\", \"t\" + string(k));\n"
            + "end\n";
        Design d = ScilabGuiParser.parse(src);
        SourceDocument doc = new SourceDocument(src);
        SourceRange locked = d.unmodelled().get(0).range();
        doc.replace(new SourceRange(locked.start(), locked.start() + 1), "X");

        boolean[] called = { false };
        SourceValidator spy = source -> {
            called[0] = true;
            return true;
        };

        assertThrows(WriteRefusedException.class, () -> DesignWriter.write(d, doc, spy));
        assertTrue(!called[0],
            "the locked-region refusal must short-circuit before any rendered text reaches the validator");
    }

    @Test
    public void aWriteRefusedByTheLockedRegionCheckLeavesTheDocumentUnmodified() {
        String src = ""
            + "for k = 1:3\n"
            + "  uicontrol(f, \"style\", \"text\", \"tag\", \"t\" + string(k));\n"
            + "end\n";
        Design d = ScilabGuiParser.parse(src);
        SourceDocument doc = new SourceDocument(src);
        SourceRange locked = d.unmodelled().get(0).range();
        SourceRange editRange = new SourceRange(locked.start(), locked.start() + 1);
        doc.replace(editRange, "X");

        assertThrows(WriteRefusedException.class, () -> DesignWriter.write(d, doc, ALWAYS_VALID));

        // The refusal must leave the caller with the exception and nothing
        // else: the same single edit, the same original text, and a document
        // that still renders exactly as it did before the refused write.
        assertEquals(1, doc.editedRanges().size());
        assertEquals(editRange, doc.editedRanges().get(0));
        assertEquals(src, doc.original());
        assertEquals("X", doc.render().substring(editRange.start(), editRange.start() + 1));
    }

    @Test
    public void aLockedRegionOverlapIsCaughtEvenWhenItIsNotTheFirstEditChecked() {
        String src = ""
            + "f = figure(\"figure_name\", \"Demo\");\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n"
            + "for k = 1:3\n"
            + "  uicontrol(f, \"style\", \"text\", \"tag\", \"t\" + string(k));\n"
            + "end\n";
        Design d = ScilabGuiParser.parse(src);
        assertTrue(!d.unmodelled().isEmpty(), "precondition: the loop is unmodelled");

        Node ok = d.byTag("ok");
        SourceRange legalRange = ok.properties().get("string").range();
        SourceRange locked = d.unmodelled().get(0).range();
        SourceRange illegalRange = new SourceRange(locked.start(), locked.start() + 1);

        SourceDocument doc = new SourceDocument(src);
        // The legal edit is added FIRST and the locked one SECOND: a check
        // that only looked at the first edit, or stopped at the first
        // non-overlapping one, would let this write through.
        doc.replace(legalRange, "\"Go\"");
        doc.replace(illegalRange, "X");

        WriteRefusedException e = assertThrows(WriteRefusedException.class,
                                               () -> DesignWriter.write(d, doc, ALWAYS_VALID));
        assertTrue(e.getMessage().toLowerCase().contains("locked"));
    }
}
