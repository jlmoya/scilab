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

package org.scilab.modules.helptools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for {@link TemplateHandler}.
 *
 * <p>A template is a text file whose substitution points are wrapped in
 * {@code <!--<token>-->} markers; {@code TemplateHandler} splits on those markers
 * and, for each token, splices in either the page contents or a string produced by
 * a {@link TemplateFiller}. These tests drive the whole read-split-substitute-write
 * pipeline against real (temp) files and a stub filler — no Scilab, no network.
 */
public class TemplateHandlerTest {

    /** Records the id it is asked about and returns a distinctive marker per hook. */
    private static final class StubFiller implements TemplateFiller {
        String lastId;
        public String makePrevious(String id)       {
            lastId = id;
            return "PREV";
        }
        public String makePath(String id)           {
            return "PATH";
        }
        public String makeTop(String id)            {
            return "TOP";
        }
        public String makeNext(String id)           {
            return "NEXT";
        }
        public String makeTocList(String id)        {
            return "TOC";
        }
        public String makeLastModified(String id)   {
            return "LM";
        }
        public String makeSubtitle(String id)       {
            return "SUB";
        }
        public String makeTitle(String id)          {
            lastId = id;
            return "TITLE(" + id + ")";
        }
        public String makeOrigin(String id)         {
            return "ORIGIN";
        }
        public String makeGenerationDate(String id) {
            return "GEN";
        }
        public String makeVersion(String id)        {
            return "VER";
        }
        public String makeStart(String id)          {
            return "START";
        }
    }

    private static File writeTemplate(Path dir, String name, String body) throws IOException {
        File f = new File(dir.toFile(), name);
        Files.writeString(f.toPath(), body, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    public void substitutesContentAndTitleTokens(@TempDir Path dir) throws IOException {
        File tpl = writeTemplate(dir, "tpl.html", "HEAD<!--<content>-->MID<!--<title>-->TAIL");
        StubFiller filler = new StubFiller();
        TemplateHandler handler = new TemplateHandler(filler, tpl, "en_US");

        File out = new File(dir.toFile(), "out.html");
        handler.generateFileFromTemplate(out.getAbsolutePath(), "funcId", "BODY");

        // parseFile appends a trailing newline to the (single) source line.
        String expected = "HEAD" + "BODY" + "MID" + "TITLE(funcId)" + "TAIL" + "\n";
        assertEquals(expected, Files.readString(out.toPath(), StandardCharsets.UTF_8));
        assertEquals("funcId", filler.lastId, "the id must be threaded to the filler");
    }

    @Test
    public void translateTokenIsRoutedThroughTemplateLocalization(@TempDir Path dir) throws IOException {
        File tpl = writeTemplate(dir, "tr.html", "A<!--<start>-->B<!--<translate=Report an issue>-->C");
        TemplateHandler handler = new TemplateHandler(new StubFiller(), tpl, "fr_FR");

        File out = new File(dir.toFile(), "tr-out.html");
        handler.generateFileFromTemplate(out.getAbsolutePath(), "id0", "ignored-content");

        String localized = TemplateLocalization.getLocalized("fr_FR", "Report an issue");
        String content = Files.readString(out.toPath(), StandardCharsets.UTF_8);
        assertEquals("A" + "START" + "B" + localized + "C" + "\n", content);
    }

    @Test
    public void templateWithNoTokensIsCopiedVerbatimPlusNewline(@TempDir Path dir) throws IOException {
        File tpl = writeTemplate(dir, "plain.html", "just some text");
        TemplateHandler handler = new TemplateHandler(new StubFiller(), tpl, "en_US");

        File out = new File(dir.toFile(), "plain-out.html");
        handler.generateFileFromTemplate(out.getAbsolutePath(), "id", "C");
        assertEquals("just some text\n", Files.readString(out.toPath(), StandardCharsets.UTF_8));
    }
}
