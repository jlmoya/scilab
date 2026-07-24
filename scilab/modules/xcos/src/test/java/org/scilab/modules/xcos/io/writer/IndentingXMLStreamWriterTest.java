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

package org.scilab.modules.xcos.io.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link IndentingXMLStreamWriter}.
 *
 * <p>The writer wraps a real JDK StAX {@link XMLStreamWriter} backed by a
 * {@link StringWriter}; the tests assert the pretty-printed output structurally
 * (newline + indentation before nested elements) rather than byte-for-byte, so
 * they stay robust across StAX implementations while still proving the
 * indentation contract. The indent-step accessors are exercised directly.
 * Only JDK XML APIs are used — no Scilab native runtime.
 */
public class IndentingXMLStreamWriterTest {

    private IndentingXMLStreamWriter newWriter(StringWriter sw) throws XMLStreamException {
        XMLStreamWriter base = XMLOutputFactory.newFactory().createXMLStreamWriter(sw);
        return new IndentingXMLStreamWriter(base);
    }

    // ---- indent-step accessors -------------------------------------------

    @Test
    public void defaultIndentStepIsTwoSpaces() throws XMLStreamException {
        IndentingXMLStreamWriter w = newWriter(new StringWriter());
        assertEquals(2, w.getIndentStep(), "default indent step is the two-space string \"  \"");
    }

    @Test
    public void setIndentStepIntStoresThatManySpaces() throws XMLStreamException {
        IndentingXMLStreamWriter w = newWriter(new StringWriter());
        w.setIndentStep(4);
        assertEquals(4, w.getIndentStep());
    }

    @Test
    public void setIndentStepIntZeroYieldsNoIndent() throws XMLStreamException {
        IndentingXMLStreamWriter w = newWriter(new StringWriter());
        w.setIndentStep(0);
        assertEquals(0, w.getIndentStep());
    }

    @Test
    public void setIndentStepIntNegativeYieldsNoIndent() throws XMLStreamException {
        // The build loop `for (; indentStep > 0; ...)` never runs for a negative
        // argument, so the step collapses to the empty string (length 0).
        IndentingXMLStreamWriter w = newWriter(new StringWriter());
        w.setIndentStep(-7);
        assertEquals(0, w.getIndentStep());
    }

    @Test
    public void setIndentStepStringReportsItsLength() throws XMLStreamException {
        IndentingXMLStreamWriter w = newWriter(new StringWriter());
        w.setIndentStep("abcd");
        assertEquals(4, w.getIndentStep());
        w.setIndentStep("");
        assertEquals(0, w.getIndentStep());
        w.setIndentStep("\t\t");
        assertEquals(2, w.getIndentStep(), "getIndentStep returns the string length, not a visual width");
    }

    // ---- serialized indentation behaviour --------------------------------

    @Test
    public void nestedElementsAreIndentedByTwoSpacesPerLevel() throws XMLStreamException {
        StringWriter sw = new StringWriter();
        IndentingXMLStreamWriter w = newWriter(sw);
        w.writeStartDocument();
        w.writeStartElement("root");
        w.writeStartElement("child");
        w.writeCharacters("text");
        w.writeEndElement();   // child
        w.writeEndElement();   // root
        w.writeEndDocument();
        w.flush();

        String out = sw.toString();
        assertTrue(out.contains("<root>"), out);
        // child sits on its own line, indented one step (two spaces) under root.
        assertTrue(out.contains("\n  <child>"), out);
        assertTrue(out.contains("text"), out);
        assertTrue(out.contains("</child>"), out);
        // the closing root tag is pushed onto a fresh, unindented line.
        assertTrue(out.contains("\n</root>"), out);
    }

    @Test
    public void writeStartDocumentIsFollowedByANewline() throws XMLStreamException {
        StringWriter sw = new StringWriter();
        IndentingXMLStreamWriter w = newWriter(sw);
        w.writeStartDocument();
        w.writeStartElement("root");
        w.writeEndElement();
        w.writeEndDocument();
        w.flush();

        String out = sw.toString();
        // The XML declaration is terminated by "?>" and the writer appends "\n".
        assertTrue(out.contains("?>\n"), "expected a newline right after the XML declaration: " + out);
    }

    @Test
    public void threeLevelsGiveMultiplicativeIndentation() throws XMLStreamException {
        StringWriter sw = new StringWriter();
        IndentingXMLStreamWriter w = newWriter(sw);
        w.writeStartDocument();
        w.writeStartElement("root");
        w.writeStartElement("a");
        w.writeStartElement("b");
        w.writeCharacters("v");
        w.writeEndElement();   // b
        w.writeEndElement();   // a
        w.writeEndElement();   // root
        w.writeEndDocument();
        w.flush();

        String out = sw.toString();
        assertTrue(out.contains("\n  <a>"), out);   // depth 1 -> 2 spaces
        assertTrue(out.contains("\n    <b>"), out); // depth 2 -> 4 spaces
    }

    @Test
    public void customIndentStepIsHonoured() throws XMLStreamException {
        StringWriter sw = new StringWriter();
        IndentingXMLStreamWriter w = newWriter(sw);
        w.setIndentStep("--");    // distinctive, non-space step
        w.writeStartDocument();
        w.writeStartElement("root");
        w.writeStartElement("child");
        w.writeCharacters("t");
        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
        w.flush();

        String out = sw.toString();
        assertTrue(out.contains("\n--<child>"), "custom indent step must prefix nested elements: " + out);
    }

    @Test
    public void emptyElementIsIndentedOnItsOwnLine() throws XMLStreamException {
        StringWriter sw = new StringWriter();
        IndentingXMLStreamWriter w = newWriter(sw);
        w.writeStartDocument();
        w.writeStartElement("root");
        w.writeEmptyElement("e");
        w.writeEndElement();   // root
        w.writeEndDocument();
        w.flush();

        String out = sw.toString();
        // Robust to "<e/>" vs "<e></e>" serialization: only the indented prefix is asserted.
        assertTrue(out.contains("\n  <e"), out);
    }

    @Test
    public void indentStepZeroProducesNewlinesButNoSpaces() throws XMLStreamException {
        StringWriter sw = new StringWriter();
        IndentingXMLStreamWriter w = newWriter(sw);
        w.setIndentStep(0);
        w.writeStartDocument();
        w.writeStartElement("root");
        w.writeStartElement("child");
        w.writeCharacters("t");
        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
        w.flush();

        String out = sw.toString();
        // Line breaks still separate elements, but there is no leading indentation.
        assertTrue(out.contains("\n<child>"), out);
        assertFalse(out.contains("\n  <child>"), "no two-space indent expected with a zero-width step: " + out);
    }

    // ---- type shape ------------------------------------------------------

    @Test
    public void isADelegatingXmlStreamWriter() throws XMLStreamException {
        IndentingXMLStreamWriter w = newWriter(new StringWriter());
        assertTrue(w instanceof DelegatingXMLStreamWriter);
        assertTrue(w instanceof XMLStreamWriter);
    }
}
