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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link DelegatingXMLStreamWriter}.
 *
 * <p>{@code DelegatingXMLStreamWriter} is a package-private, abstract pass-through
 * over an {@link XMLStreamWriter} — every method simply forwards to the wrapped
 * writer. The tests live in the same package so they can subclass it, and they
 * forward into a hand-rolled recording {@link XMLStreamWriter} (no mocking
 * framework is on the test classpath) to prove each call is delegated verbatim,
 * arguments included, and that the return-valued methods propagate the wrapped
 * writer's result. Pure Java + JDK StAX types: no native runtime is involved.
 */
public class DelegatingXMLStreamWriterTest {

    /** A minimal concrete subclass: the abstract class implements every method itself. */
    private static final class ConcreteDelegating extends DelegatingXMLStreamWriter {
        ConcreteDelegating(XMLStreamWriter writer) {
            super(writer);
        }
    }

    /** Records every call (name + args) and returns sentinels from the getters. */
    private static final class RecordingXMLStreamWriter implements XMLStreamWriter {
        final List<String> calls = new ArrayList<>();
        NamespaceContext lastSetContext;
        final NamespaceContext cannedContext = new DummyNamespaceContext();

        private void rec(String s) {
            calls.add(s);
        }

        String last() {
            return calls.get(calls.size() - 1);
        }

        @Override public void writeStartElement(String localName) {
            rec("writeStartElement(" + localName + ")");
        }
        @Override public void writeStartElement(String namespaceURI, String localName) {
            rec("writeStartElement(" + namespaceURI + "," + localName + ")");
        }
        @Override public void writeStartElement(String prefix, String localName, String namespaceURI) {
            rec("writeStartElement(" + prefix + "," + localName + "," + namespaceURI + ")");
        }
        @Override public void writeEmptyElement(String namespaceURI, String localName) {
            rec("writeEmptyElement(" + namespaceURI + "," + localName + ")");
        }
        @Override public void writeEmptyElement(String prefix, String localName, String namespaceURI) {
            rec("writeEmptyElement(" + prefix + "," + localName + "," + namespaceURI + ")");
        }
        @Override public void writeEmptyElement(String localName) {
            rec("writeEmptyElement(" + localName + ")");
        }
        @Override public void writeEndElement() {
            rec("writeEndElement()");
        }
        @Override public void writeEndDocument() {
            rec("writeEndDocument()");
        }
        @Override public void close() {
            rec("close()");
        }
        @Override public void flush() {
            rec("flush()");
        }
        @Override public void writeAttribute(String localName, String value) {
            rec("writeAttribute(" + localName + "," + value + ")");
        }
        @Override public void writeAttribute(String prefix, String namespaceURI, String localName, String value) {
            rec("writeAttribute(" + prefix + "," + namespaceURI + "," + localName + "," + value + ")");
        }
        @Override public void writeAttribute(String namespaceURI, String localName, String value) {
            rec("writeAttribute(" + namespaceURI + "," + localName + "," + value + ")");
        }
        @Override public void writeNamespace(String prefix, String namespaceURI) {
            rec("writeNamespace(" + prefix + "," + namespaceURI + ")");
        }
        @Override public void writeDefaultNamespace(String namespaceURI) {
            rec("writeDefaultNamespace(" + namespaceURI + ")");
        }
        @Override public void writeComment(String data) {
            rec("writeComment(" + data + ")");
        }
        @Override public void writeProcessingInstruction(String target) {
            rec("writeProcessingInstruction(" + target + ")");
        }
        @Override public void writeProcessingInstruction(String target, String data) {
            rec("writeProcessingInstruction(" + target + "," + data + ")");
        }
        @Override public void writeCData(String data) {
            rec("writeCData(" + data + ")");
        }
        @Override public void writeDTD(String dtd) {
            rec("writeDTD(" + dtd + ")");
        }
        @Override public void writeEntityRef(String name) {
            rec("writeEntityRef(" + name + ")");
        }
        @Override public void writeStartDocument() {
            rec("writeStartDocument()");
        }
        @Override public void writeStartDocument(String version) {
            rec("writeStartDocument(" + version + ")");
        }
        @Override public void writeStartDocument(String encoding, String version) {
            rec("writeStartDocument(" + encoding + "," + version + ")");
        }
        @Override public void writeCharacters(String text) {
            rec("writeCharacters(" + text + ")");
        }
        @Override public void writeCharacters(char[] text, int start, int len) {
            rec("writeCharactersArr(" + new String(text) + "," + start + "," + len + ")");
        }
        @Override public String getPrefix(String uri) {
            rec("getPrefix(" + uri + ")");
            return "P:" + uri;
        }
        @Override public void setPrefix(String prefix, String uri) {
            rec("setPrefix(" + prefix + "," + uri + ")");
        }
        @Override public void setDefaultNamespace(String uri) {
            rec("setDefaultNamespace(" + uri + ")");
        }
        @Override public void setNamespaceContext(NamespaceContext context) {
            rec("setNamespaceContext()");
            lastSetContext = context;
        }
        @Override public NamespaceContext getNamespaceContext() {
            rec("getNamespaceContext()");
            return cannedContext;
        }
        @Override public Object getProperty(String name) {
            rec("getProperty(" + name + ")");
            return "V:" + name;
        }
    }

    /** Inert NamespaceContext so we can assert reference identity through the delegate. */
    private static final class DummyNamespaceContext implements NamespaceContext {
        @Override public String getNamespaceURI(String prefix) {
            return null;
        }
        @Override public String getPrefix(String namespaceURI) {
            return null;
        }
        @Override public Iterator<String> getPrefixes(String namespaceURI) {
            return new ArrayList<String>().iterator();
        }
    }

    private RecordingXMLStreamWriter rec;
    private XMLStreamWriter delegate;

    @BeforeEach
    public void setUp() {
        rec = new RecordingXMLStreamWriter();
        delegate = new ConcreteDelegating(rec);
    }

    @Test
    public void isAnXMLStreamWriter() {
        assertTrue(delegate instanceof XMLStreamWriter);
    }

    @Test
    public void writeStartElementOverloadsForwardAllArguments() throws XMLStreamException {
        delegate.writeStartElement("a");
        assertEquals("writeStartElement(a)", rec.last());
        delegate.writeStartElement("ns", "b");
        assertEquals("writeStartElement(ns,b)", rec.last());
        delegate.writeStartElement("pre", "c", "ns2");
        assertEquals("writeStartElement(pre,c,ns2)", rec.last());
    }

    @Test
    public void writeEmptyElementOverloadsForwardAllArguments() throws XMLStreamException {
        delegate.writeEmptyElement("a");
        assertEquals("writeEmptyElement(a)", rec.last());
        delegate.writeEmptyElement("ns", "b");
        assertEquals("writeEmptyElement(ns,b)", rec.last());
        delegate.writeEmptyElement("pre", "c", "ns2");
        assertEquals("writeEmptyElement(pre,c,ns2)", rec.last());
    }

    @Test
    public void writeEndAndDocumentLifecycleForward() throws XMLStreamException {
        delegate.writeEndElement();
        assertEquals("writeEndElement()", rec.last());
        delegate.writeEndDocument();
        assertEquals("writeEndDocument()", rec.last());
    }

    @Test
    public void closeAndFlushForward() throws XMLStreamException {
        delegate.flush();
        assertEquals("flush()", rec.last());
        delegate.close();
        assertEquals("close()", rec.last());
    }

    @Test
    public void writeAttributeOverloadsForwardAllArguments() throws XMLStreamException {
        delegate.writeAttribute("k", "v");
        assertEquals("writeAttribute(k,v)", rec.last());
        delegate.writeAttribute("ns", "k2", "v2");
        assertEquals("writeAttribute(ns,k2,v2)", rec.last());
        delegate.writeAttribute("pre", "ns", "k3", "v3");
        assertEquals("writeAttribute(pre,ns,k3,v3)", rec.last());
    }

    @Test
    public void namespaceWritersForward() throws XMLStreamException {
        delegate.writeNamespace("pre", "urn:ns");
        assertEquals("writeNamespace(pre,urn:ns)", rec.last());
        delegate.writeDefaultNamespace("urn:def");
        assertEquals("writeDefaultNamespace(urn:def)", rec.last());
    }

    @Test
    public void commentAndProcessingInstructionsForward() throws XMLStreamException {
        delegate.writeComment("hello");
        assertEquals("writeComment(hello)", rec.last());
        delegate.writeProcessingInstruction("target");
        assertEquals("writeProcessingInstruction(target)", rec.last());
        delegate.writeProcessingInstruction("target", "data");
        assertEquals("writeProcessingInstruction(target,data)", rec.last());
    }

    @Test
    public void cdataDtdAndEntityRefForward() throws XMLStreamException {
        delegate.writeCData("<raw>");
        assertEquals("writeCData(<raw>)", rec.last());
        delegate.writeDTD("<!DOCTYPE x>");
        assertEquals("writeDTD(<!DOCTYPE x>)", rec.last());
        delegate.writeEntityRef("amp");
        assertEquals("writeEntityRef(amp)", rec.last());
    }

    @Test
    public void writeStartDocumentOverloadsForward() throws XMLStreamException {
        delegate.writeStartDocument();
        assertEquals("writeStartDocument()", rec.last());
        delegate.writeStartDocument("1.1");
        assertEquals("writeStartDocument(1.1)", rec.last());
        delegate.writeStartDocument("UTF-8", "1.0");
        assertEquals("writeStartDocument(UTF-8,1.0)", rec.last());
    }

    @Test
    public void writeCharactersStringForwards() throws XMLStreamException {
        delegate.writeCharacters("some text");
        assertEquals("writeCharacters(some text)", rec.last());
    }

    @Test
    public void writeCharactersArrayForwardsSliceBounds() throws XMLStreamException {
        char[] buf = {'a', 'b', 'c', 'd'};
        delegate.writeCharacters(buf, 1, 2);
        // The exact char[]/start/len triple is forwarded unchanged.
        assertEquals("writeCharactersArr(abcd,1,2)", rec.last());
    }

    @Test
    public void setPrefixAndDefaultNamespaceForward() throws XMLStreamException {
        delegate.setPrefix("p", "urn:x");
        assertEquals("setPrefix(p,urn:x)", rec.last());
        delegate.setDefaultNamespace("urn:d");
        assertEquals("setDefaultNamespace(urn:d)", rec.last());
    }

    @Test
    public void getPrefixReturnsWrappedResult() throws XMLStreamException {
        String prefix = delegate.getPrefix("urn:z");
        assertEquals("getPrefix(urn:z)", rec.last());
        assertEquals("P:urn:z", prefix);
    }

    @Test
    public void setAndGetNamespaceContextForwardAndReturnWrappedInstance() throws XMLStreamException {
        NamespaceContext ctx = new DummyNamespaceContext();
        delegate.setNamespaceContext(ctx);
        assertEquals("setNamespaceContext()", rec.last());
        assertSame(ctx, rec.lastSetContext, "the exact context instance must be forwarded");

        NamespaceContext got = delegate.getNamespaceContext();
        assertEquals("getNamespaceContext()", rec.last());
        assertSame(rec.cannedContext, got, "getNamespaceContext must return the wrapped writer's value");
    }

    @Test
    public void getPropertyReturnsWrappedResult() {
        Object v = delegate.getProperty("prop.name");
        assertEquals("getProperty(prop.name)", rec.last());
        assertEquals("V:prop.name", v);
    }

    @Test
    public void nullArgumentsAreForwardedUnchanged() throws XMLStreamException {
        // The delegate performs no validation of its own; nulls pass straight through.
        delegate.writeStartElement((String) null);
        assertEquals("writeStartElement(null)", rec.last());
        delegate.writeCharacters((String) null);
        assertEquals("writeCharacters(null)", rec.last());
    }

    @Test
    public void callsAreForwardedInOrderExactlyOncePerInvocation() throws XMLStreamException {
        delegate.writeStartDocument();
        delegate.writeStartElement("root");
        delegate.writeCharacters("x");
        delegate.writeEndElement();
        delegate.writeEndDocument();

        assertEquals(5, rec.calls.size(), "each call forwards exactly once");
        assertEquals("writeStartDocument()", rec.calls.get(0));
        assertEquals("writeStartElement(root)", rec.calls.get(1));
        assertEquals("writeCharacters(x)", rec.calls.get(2));
        assertEquals("writeEndElement()", rec.calls.get(3));
        assertEquals("writeEndDocument()", rec.calls.get(4));
    }
}
