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

package org.scilab.modules.helptools.XML;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link HTMLXMLCodeHandler} — the XML-source syntax highlighter.
 *
 * <p>Pins the pure static {@link HTMLXMLCodeHandler#replaceEntity} plus the many
 * {@code handle*} span shapes. Note the asymmetry (mirrored from the source): only
 * attribute-value / comment / cdata content is run through {@code replaceEntity};
 * everything else — including {@code handleDefault}, unlike the C handler — is emitted
 * verbatim.
 */
public class HTMLXMLCodeHandlerTest {

    // ---- replaceEntity (pure) ------------------------------------------

    @Test
    public void replaceEntityEncodesTheFiveSpecialsNumerically() {
        assertEquals("&#0060;&#0062;&amp;&#0034;&#0039;",
                     HTMLXMLCodeHandler.replaceEntity("<>&\"'"));
    }

    @Test
    public void replaceEntityDoubleEncodesExistingEntities() {
        assertEquals("&amp;lt;", HTMLXMLCodeHandler.replaceEntity("&lt;"));
    }

    // ---- verbatim (non-encoding) spans ---------------------------------

    @Test
    public void handleDefaultIsVerbatim() {
        // Unlike the C handler, XML handleDefault does NOT run replaceEntity.
        AbstractXMLCodeHandler h = HTMLXMLCodeHandler.getInstance();
        assertDoesNotThrow(() -> h.handleDefault("a<b"));
        assertEquals("<span class=\"xmldefault\">a<b</span>", h.toString());
    }

    @Test
    public void structuralTagSpans() throws IOException {
        assertSpan("xmlentity", HTMLXMLCodeHandler.getInstance(), "&amp;", (h) -> h.handleEntity("&amp;"));
        assertSpan("xmlopeninstr", HTMLXMLCodeHandler.getInstance(), "<?", (h) -> h.handleOpenInstr("<?"));
        assertSpan("xmlcloseinstr", HTMLXMLCodeHandler.getInstance(), "?>", (h) -> h.handleCloseInstr("?>"));
        assertSpan("xmlinstrname", HTMLXMLCodeHandler.getInstance(), "xml", (h) -> h.handleInstrName("xml"));
        assertSpan("xmllowtag", HTMLXMLCodeHandler.getInstance(), "<", (h) -> h.handleLow("<"));
        assertSpan("xmltagname", HTMLXMLCodeHandler.getInstance(), "para", (h) -> h.handleOpenTagName("para"));
        assertSpan("xmllowclose", HTMLXMLCodeHandler.getInstance(), "</", (h) -> h.handleLowClose("</"));
        assertSpan("xmlgreattag", HTMLXMLCodeHandler.getInstance(), ">", (h) -> h.handleGreat(">"));
        assertSpan("xmlautoclose", HTMLXMLCodeHandler.getInstance(), "/>", (h) -> h.handleAutoClose("/>"));
    }

    @Test
    public void attributeSpans() throws IOException {
        assertSpan("xmlattributename", HTMLXMLCodeHandler.getInstance(), "id", (h) -> h.handleAttributeName("id"));
        assertSpan("xmlequal", HTMLXMLCodeHandler.getInstance(), "=", (h) -> h.handleEqual("="));
    }

    @Test
    public void commentAndCdataDelimiterSpans() throws IOException {
        assertSpan("xmlopencomment", HTMLXMLCodeHandler.getInstance(), "<!--", (h) -> h.handleOpenComment("<!--"));
        assertSpan("xmlcommentend", HTMLXMLCodeHandler.getInstance(), "-->", (h) -> h.handleCommentEnd("-->"));
        assertSpan("xmlopencdata", HTMLXMLCodeHandler.getInstance(), "<![CDATA[", (h) -> h.handleOpenCdata("<![CDATA["));
        assertSpan("xmlcdataend", HTMLXMLCodeHandler.getInstance(), "]]>", (h) -> h.handleCdataEnd("]]>"));
    }

    // ---- encoding spans -------------------------------------------------

    @Test
    public void attributeValueContentIsEncoded() throws IOException {
        AbstractXMLCodeHandler h = HTMLXMLCodeHandler.getInstance();
        h.handleAttributeValue("a<b&c");
        assertEquals("<span class=\"xmlattributevalue\">a&#0060;b&amp;c</span>", h.toString());
    }

    @Test
    public void commentContentIsEncoded() throws IOException {
        AbstractXMLCodeHandler h = HTMLXMLCodeHandler.getInstance();
        h.handleComment("x < y");
        assertEquals("<span class=\"xmlcomment\">x &#0060; y</span>", h.toString());
    }

    @Test
    public void cdataContentIsEncoded() throws IOException {
        AbstractXMLCodeHandler h = HTMLXMLCodeHandler.getInstance();
        h.handleCdata("1 < 2 & 3");
        assertEquals("<span class=\"xmlcdata\">1 &#0060; 2 &amp; 3</span>", h.toString());
    }

    @Test
    public void handleNothingEmitsRawSequence() throws IOException {
        AbstractXMLCodeHandler h = HTMLXMLCodeHandler.getInstance();
        h.handleNothing("  <raw>  ");
        assertEquals("  <raw>  ", h.toString());
    }

    // ---- helper ---------------------------------------------------------

    @FunctionalInterface
    private interface HandlerCall {
        void apply(AbstractXMLCodeHandler h) throws IOException;
    }

    private static void assertSpan(String cssClass, AbstractXMLCodeHandler h, String seq, HandlerCall call)
    throws IOException {
        call.apply(h);
        assertEquals("<span class=\"" + cssClass + "\">" + seq + "</span>", h.toString());
    }
}
