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
package org.scilab.modules.xcos.explorer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;

import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.scilab.modules.xcos.Kind;

/**
 * Hermetic unit tests for {@link BrowserTreeNodeData}.
 *
 * <p>{@code BrowserTreeNodeData} is the data payload of a node in the Xcos
 * diagram-browser tree. A node identified by {@code uid == 0} is the synthetic
 * <em>root</em>; every other node wraps a native model object addressed by its
 * {@code uid}/{@link Kind} pair.</p>
 *
 * <p><b>Hermetic surface.</b> {@link Kind} is a pure SWIG-generated enum (no
 * {@code System.loadLibrary}), so construction, the {@code uid}/{@code kind}
 * getters, the reference counter, {@code hashCode}/{@code equals}, and the
 * {@code uid == 0} short-circuits of {@code toString()} and
 * {@code fillOrUpdateContent(...)} are all pure Java. The <em>non-root</em>
 * paths of {@code toString()} and {@code fillOrUpdateContent(...)} instantiate
 * {@code JavaController}/{@code Controller} (SWIG/JNI) to read object
 * properties across the native boundary; those require the Scilab native
 * runtime and are intentionally not exercised here. The tests below pin the
 * pure-Java behaviour, including two characterization facts that could surprise
 * a caller: {@code equals}/{@code hashCode} depend on {@code uid} alone (kind
 * is ignored), and {@code toString()} returns {@code "Root"} for <em>any</em>
 * node whose {@code uid} is 0, regardless of its declared kind.</p>
 */
public class BrowserTreeNodeDataTest {

    /* ------------------------------------------------------------------ */
    /* Construction & getters                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("default constructor builds the root node (uid 0, kind DIAGRAM)")
    public void defaultConstructorIsRoot() {
        BrowserTreeNodeData node = new BrowserTreeNodeData();
        assertEquals(0L, node.getId());
        assertSame(Kind.DIAGRAM, node.getKind());
    }

    @Test
    @DisplayName("parameterized constructor stores the id and kind verbatim")
    public void parameterizedConstructorStoresIdAndKind() {
        BrowserTreeNodeData block = new BrowserTreeNodeData(42L, Kind.BLOCK);
        assertEquals(42L, block.getId());
        assertSame(Kind.BLOCK, block.getKind());

        BrowserTreeNodeData link = new BrowserTreeNodeData(-7L, Kind.LINK);
        assertEquals(-7L, link.getId());
        assertSame(Kind.LINK, link.getKind());

        BrowserTreeNodeData port = new BrowserTreeNodeData(Long.MAX_VALUE, Kind.PORT);
        assertEquals(Long.MAX_VALUE, port.getId());
        assertSame(Kind.PORT, port.getKind());
    }

    @Test
    @DisplayName("kind may be any declared Kind constant")
    public void everyKindIsAccepted() {
        for (Kind k : Kind.values()) {
            assertSame(k, new BrowserTreeNodeData(1L, k).getKind());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Reference counter                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("incRefCount pre-increments from an initial count of 0")
    public void incRefCountFromFresh() {
        BrowserTreeNodeData node = new BrowserTreeNodeData(1L, Kind.BLOCK);
        assertEquals(1, node.incRefCount());
        assertEquals(2, node.incRefCount());
        assertEquals(3, node.incRefCount());
    }

    @Test
    @DisplayName("decRefCount pre-decrements and can go negative")
    public void decRefCountFromFresh() {
        BrowserTreeNodeData node = new BrowserTreeNodeData(1L, Kind.BLOCK);
        assertEquals(-1, node.decRefCount());
        assertEquals(-2, node.decRefCount());
    }

    @Test
    @DisplayName("inc/dec are symmetric around the running count")
    public void incThenDecInterplay() {
        BrowserTreeNodeData node = new BrowserTreeNodeData(1L, Kind.BLOCK);
        assertEquals(1, node.incRefCount());
        assertEquals(2, node.incRefCount());
        assertEquals(1, node.decRefCount());
        assertEquals(0, node.decRefCount());
        assertEquals(-1, node.decRefCount());
    }

    @Test
    @DisplayName("the root node's reference counter also starts at 0")
    public void defaultConstructorRefCountStartsAtZero() {
        // The no-arg constructor never assigns refCount explicitly; it relies on
        // the int field defaulting to 0 (characterization).
        BrowserTreeNodeData root = new BrowserTreeNodeData();
        assertEquals(1, root.incRefCount());
    }

    /* ------------------------------------------------------------------ */
    /* hashCode                                                           */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("hashCode pins the uid-folding formula for known ids")
    public void hashCodeKnownValues() {
        // hashCode == 31 + (int)(uid ^ (uid >>> 32))
        assertEquals(31, new BrowserTreeNodeData().hashCode());               // uid 0
        assertEquals(31, new BrowserTreeNodeData(0L, Kind.BLOCK).hashCode()); // uid 0
        assertEquals(32, new BrowserTreeNodeData(1L, Kind.BLOCK).hashCode()); // uid 1
        assertEquals(36, new BrowserTreeNodeData(5L, Kind.BLOCK).hashCode()); // uid 5
    }

    @Test
    @DisplayName("hashCode folds the high 32 bits into the low 32 bits")
    public void hashCodeFoldsHighBits() {
        // uid = 2^32 folds to 1, colliding with uid = 1.
        assertEquals(32, new BrowserTreeNodeData(1L << 32, Kind.BLOCK).hashCode());
        assertEquals(new BrowserTreeNodeData(1L, Kind.BLOCK).hashCode(),
                     new BrowserTreeNodeData(1L << 32, Kind.BLOCK).hashCode());
        // uid = -1 folds to 0, colliding with uid = 0.
        assertEquals(31, new BrowserTreeNodeData(-1L, Kind.BLOCK).hashCode());
    }

    @Test
    @DisplayName("hashCode is stable across repeated calls")
    public void hashCodeIsStable() {
        BrowserTreeNodeData node = new BrowserTreeNodeData(123456789L, Kind.DIAGRAM);
        int first = node.hashCode();
        assertEquals(first, node.hashCode());
        assertEquals(first, node.hashCode());
    }

    @Test
    @DisplayName("hashCode ignores kind (only uid participates)")
    public void hashCodeIgnoresKind() {
        assertEquals(new BrowserTreeNodeData(9L, Kind.BLOCK).hashCode(),
                     new BrowserTreeNodeData(9L, Kind.LINK).hashCode());
    }

    /* ------------------------------------------------------------------ */
    /* equals                                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("equals is reflexive")
    public void equalsReflexive() {
        BrowserTreeNodeData node = new BrowserTreeNodeData(5L, Kind.BLOCK);
        assertEquals(node, node);
    }

    @Test
    @DisplayName("nodes with the same uid and kind are equal and symmetric")
    public void equalsSameUidSameKind() {
        BrowserTreeNodeData a = new BrowserTreeNodeData(5L, Kind.BLOCK);
        BrowserTreeNodeData b = new BrowserTreeNodeData(5L, Kind.BLOCK);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("equals depends on uid alone — different kinds still compare equal")
    public void equalsIgnoresKind() {
        // Characterization: kind is deliberately excluded from equality, so two
        // nodes addressing the same uid with *different* kinds are 'equal'.
        BrowserTreeNodeData block = new BrowserTreeNodeData(5L, Kind.BLOCK);
        BrowserTreeNodeData link = new BrowserTreeNodeData(5L, Kind.LINK);
        assertEquals(block, link);
        assertEquals(link, block);
        assertEquals(block.hashCode(), link.hashCode());
    }

    @Test
    @DisplayName("nodes with different uids are not equal")
    public void equalsDifferentUid() {
        BrowserTreeNodeData a = new BrowserTreeNodeData(5L, Kind.BLOCK);
        BrowserTreeNodeData b = new BrowserTreeNodeData(6L, Kind.BLOCK);
        assertNotEquals(a, b);
        assertNotEquals(b, a);
    }

    @Test
    @DisplayName("equals(null) is false")
    public void equalsNull() {
        assertNotEquals(new BrowserTreeNodeData(5L, Kind.BLOCK), null);
    }

    @Test
    @DisplayName("equals against a foreign type is false")
    public void equalsDifferentType() {
        assertNotEquals(new BrowserTreeNodeData(5L, Kind.BLOCK), "not a node");
        assertNotEquals(new BrowserTreeNodeData(5L, Kind.BLOCK), Long.valueOf(5L));
    }

    @Test
    @DisplayName("equality uses getClass(), so a subclass instance is never equal")
    public void equalsRejectsSubclass() {
        BrowserTreeNodeData base = new BrowserTreeNodeData(5L, Kind.BLOCK);
        BrowserTreeNodeData sub = new BrowserTreeNodeData(5L, Kind.BLOCK) { };
        // getClass() != obj.getClass() -> not equal, in both directions.
        assertNotEquals(base, sub);
        assertNotEquals(sub, base);
    }

    /* ------------------------------------------------------------------ */
    /* toString (root path only)                                          */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("the default (root) node stringifies to \"Root\"")
    public void toStringRootDefault() {
        assertEquals("Root", new BrowserTreeNodeData().toString());
    }

    @Test
    @DisplayName("any uid==0 node stringifies to \"Root\", regardless of kind")
    public void toStringUidZeroRegardlessOfKind() {
        // Characterization: the uid==0 short-circuit wins even when the node was
        // built with a non-DIAGRAM kind, so this never crosses the JNI boundary.
        assertEquals("Root", new BrowserTreeNodeData(0L, Kind.BLOCK).toString());
        assertEquals("Root", new BrowserTreeNodeData(0L, Kind.PORT).toString());
    }

    /* ------------------------------------------------------------------ */
    /* fillOrUpdateContent (root path only)                               */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("fillOrUpdateContent on the root node wipes the body content")
    public void fillOrUpdateContentRootClearsBody() throws Exception {
        HTMLDocument doc = htmlDocument(
            "<html><head></head><body>XCOS_MARKER_CONTENT</body></html>");
        assertTrue(documentText(doc).contains("XCOS_MARKER_CONTENT"),
                   "precondition: the marker is present before the call");

        BrowserTreeNodeData root = new BrowserTreeNodeData(); // uid == 0
        assertDoesNotThrow(() -> root.fillOrUpdateContent(doc));

        assertFalse(documentText(doc).contains("XCOS_MARKER_CONTENT"),
                    "the root render path must clean up all body content");
    }

    @Test
    @DisplayName("fillOrUpdateContent takes the cleanup path for any uid==0 node")
    public void fillOrUpdateContentUidZeroParamCtor() throws Exception {
        HTMLDocument doc = htmlDocument(
            "<html><head></head><body>XCOS_MARKER_CONTENT</body></html>");

        // A node built with (0, BLOCK) is still uid==0, so it takes the pure-Swing
        // cleanup branch and never constructs a native Controller.
        BrowserTreeNodeData node = new BrowserTreeNodeData(0L, Kind.BLOCK);
        assertDoesNotThrow(() -> node.fillOrUpdateContent(doc));
        assertFalse(documentText(doc).contains("XCOS_MARKER_CONTENT"));
    }

    /* ------------------------------------------------------------------ */
    /* helpers                                                            */
    /* ------------------------------------------------------------------ */

    private static HTMLDocument htmlDocument(String html) throws Exception {
        HTMLEditorKit kit = new HTMLEditorKit();
        HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
        kit.read(new StringReader(html), doc, 0);
        return doc;
    }

    private static String documentText(HTMLDocument doc) throws BadLocationException {
        return doc.getText(0, doc.getLength());
    }
}
