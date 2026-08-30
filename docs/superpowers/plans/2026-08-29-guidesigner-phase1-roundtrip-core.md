# GUI Designer — Phase 1: round-trip core — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new Scilab core module that can open a real `.sce` GUI file, show exactly what it understood and what it could not, and save it back byte-for-byte unchanged.

**Architecture:** Three headless Java units with one-way dependencies — `model` (pure data with source ranges), `parse` (Scilab source → model, using SciNotes' JFlex lexer), `write` (model + edits → source, position-preserving) — plus a read-only dockable tab that renders the parse result. No canvas, no editing, no layouts: those are phases 2 and 3. The controlling invariant is that opening a file and saving it with no edits produces identical bytes.

**Tech Stack:** Java 25, Maven (reactor at `scilab/pom.xml`), CMake for native wiring, JUnit 6.1.2 (`org.junit.jupiter`) + surefire + JaCoCo, SciNotes' `ScilabLexer` (JFlex), giws for the Scilab↔Java bridge, FlatLaf-themed Swing.

**Spec:** `docs/design/guibuilder-rewrite.md` (revision 2). Read it before starting; this plan implements sections 5–8, 10 and the phase-1 row of section 11.

## Global Constraints

- Repo root is `/Users/josemoya/Projects/CLionProjects/scilab`. The Scilab tree is the nested `scilab/` directory (the doubled path is real). Plans and design docs live at the **repo root** under `docs/`; module code lives under `scilab/modules/`.
- **Commit messages must contain NO AI attribution** — no `Co-Authored-By`, no `Claude-Session`, no "Generated with". This overrides any harness default, in every repo.
- Work directly on `main` and push **both** remotes (`origin` = GitHub, `gitlab` = GitLab). Do not create branches.
- Use `git commit -F <file>`; never inline a multi-line message with `-m`.
- **Never run `sudo`.** Never run `tccutil`.
- Every batch `.sce` ends with `exit(n)`, never `quit(n)` — `quit` ignores its argument.
- A `'` inside a `"…"` Scilab string terminates the string; use `""` for embedded quotes.
- Never filter, summarise or suppress build/test output to make it look clean. Show it verbatim; `tee`, never `grep`, when reporting a build.
- Module command name is **`guidesigner`**, not `guibuilder` — the ATOMS toolbox still owns `guibuilder` until phase 2.
- Java package root is `org.scilab.modules.guibuilder`. Maven artifactId is `guibuilder`. Directory is `scilab/modules/guibuilder`.
- `model`, `parse` and `write` must contain **no Swing imports and no Scilab-runtime imports**, so they stay headless-testable. Referencing `XConfiguration` or the graphic-object model from a hermetic test aborts the JVM with exit 134.
- Java tests use JUnit 5/6 Jupiter: `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.*`.
- Run a module's tests with the full lifecycle: `mvn -pl modules/guibuilder -am test` from `scilab/`. Never `mvn surefire:test` alone — jacoco's `prepare-agent` must substitute `@{argLine}` first or the run fails.
- macOS arm64 is the only verified platform. Do not add Windows/Linux build wiring.

---

## File Structure

All paths relative to `/Users/josemoya/Projects/CLionProjects/scilab/scilab`.

**New module skeleton**
- `modules/guibuilder/pom.xml` — Maven module; parent `scilab-modules-parent`; depends on `scinotes` (lexer + editor pane), `commons`, `gui`, `core`.
- `modules/guibuilder/CMakeLists.txt` — native wiring for the gateway (Task 7).
- `modules/guibuilder/sci_gateway/guibuilder_gateway.xml` — declares `guidesigner` (Task 7).
- `modules/guibuilder/sci_gateway/cpp/sci_guidesigner.cpp` — gateway entry (Task 7).
- `modules/guibuilder/macros/guidesigner.sci` — user-facing macro (Task 7).

**`model` — pure data, no Swing, no Scilab**
- `src/java/org/scilab/modules/guibuilder/model/SourceRange.java` — a half-open `[start, end)` char span.
- `src/java/org/scilab/modules/guibuilder/model/ScilabIdentifier.java` — tag validation.
- `src/java/org/scilab/modules/guibuilder/model/WidgetStyle.java` — the 12 uicontrol styles + `AXES`.
- `src/java/org/scilab/modules/guibuilder/model/PropertyValue.java` — a property with its source text, range, and whether it is literal or computed.
- `src/java/org/scilab/modules/guibuilder/model/Node.java` — a widget: id, tag, style, properties, source range, parent.
- `src/java/org/scilab/modules/guibuilder/model/Frame.java` — a `Node` with ordered children.
- `src/java/org/scilab/modules/guibuilder/model/UnmodelledRegion.java` — a span the parser could not model, with a human-readable reason.
- `src/java/org/scilab/modules/guibuilder/model/Design.java` — the file's text, the root frame, the unmodelled regions, tag index.

**`parse` — source → model**
- `src/java/org/scilab/modules/guibuilder/parse/Token.java` — a positioned token.
- `src/java/org/scilab/modules/guibuilder/parse/ScilabTokenStream.java` — wraps SciNotes' `ScilabLexer` into a positioned token list.
- `src/java/org/scilab/modules/guibuilder/parse/ScilabGuiParser.java` — recognises `figure`/`uicontrol` calls and their property lists; everything else becomes an `UnmodelledRegion`.

**`write` — model + edits → source**
- `src/java/org/scilab/modules/guibuilder/write/SourceDocument.java` — records non-overlapping replacements against the original text and renders the result.
- `src/java/org/scilab/modules/guibuilder/write/SourceValidator.java` — interface; "is this valid Scilab?"
- `src/java/org/scilab/modules/guibuilder/write/WriteRefusedException.java` — thrown rather than writing something unparseable or touching a locked span.
- `src/java/org/scilab/modules/guibuilder/write/DesignWriter.java` — applies edits, refuses on locked spans, validates, returns text.
- `src/java/org/scilab/modules/guibuilder/write/Macr2TreeValidator.java` — the Scilab-backed `SourceValidator` (Task 9).

**`ui` — the read-only tab (Swing; Task 8)**
- `src/java/org/scilab/modules/guibuilder/ui/GuiDesigner.java` — `@ScilabExported` entry point.
- `src/java/org/scilab/modules/guibuilder/ui/GuiDesignerTab.java` — the dockable panel: component tree, property table, locked-region list, Save.
- `src/java/org/scilab/modules/guibuilder/ui/GuiDesignerTabFactory.java` — `extends AbstractScilabTabFactory`.

**Tests** — mirror under `src/test/java/...`; corpus files under `src/test/resources/corpus/`.

---

## Task 1: Module skeleton, `SourceRange`, `ScilabIdentifier`

**Files:**
- Create: `modules/guibuilder/pom.xml`
- Modify: `pom.xml` (add `<module>modules/guibuilder</module>` after line 58, `modules/scinotes`)
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/model/SourceRange.java`
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/model/ScilabIdentifier.java`
- Test: `modules/guibuilder/src/test/java/org/scilab/modules/guibuilder/model/SourceRangeTest.java`
- Test: `modules/guibuilder/src/test/java/org/scilab/modules/guibuilder/model/ScilabIdentifierTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `SourceRange(int start, int end)` with `int start()`, `int end()`, `int length()`, `boolean overlaps(SourceRange)`, `boolean contains(int)`; `ScilabIdentifier.isValid(String)` and `ScilabIdentifier.requireValid(String)`.

- [ ] **Step 1: Create the Maven module and register it in the reactor**

`modules/guibuilder/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.scilab</groupId>
    <artifactId>scilab-modules-parent</artifactId>
    <version>2027.0.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>guibuilder</artifactId>
  <packaging>jar</packaging>
  <name>Scilab GUI Designer</name>
</project>
```

Dependencies are added in the tasks that need them (Task 3 adds `scinotes`, Task 8 adds `gui`), so the module always builds with the smallest dependency set that its code justifies.

In `pom.xml` at the repo's `scilab/` level, add after the `modules/scinotes` line:

```xml
    <module>modules/guibuilder</module>
```

- [ ] **Step 2: Write the failing tests**

`SourceRangeTest.java`:

```java
package org.scilab.modules.guibuilder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SourceRangeTest {

    @Test
    public void lengthIsEndMinusStart() {
        assertEquals(5, new SourceRange(10, 15).length());
    }

    @Test
    public void anEmptyRangeIsLegalBecauseAnInsertionPointIsOne() {
        assertEquals(0, new SourceRange(7, 7).length());
    }

    @Test
    public void rangesAreHalfOpenSoTouchingRangesDoNotOverlap() {
        // [0,5) and [5,10) are adjacent. If these counted as overlapping, two
        // edits to neighbouring properties would be rejected for no reason.
        assertFalse(new SourceRange(0, 5).overlaps(new SourceRange(5, 10)));
        assertTrue(new SourceRange(0, 6).overlaps(new SourceRange(5, 10)));
    }

    @Test
    public void containsUsesTheSameHalfOpenRule() {
        SourceRange r = new SourceRange(3, 6);
        assertTrue(r.contains(3));
        assertTrue(r.contains(5));
        assertFalse(r.contains(6));
    }

    @Test
    public void negativeOrInvertedRangesAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new SourceRange(-1, 4));
        assertThrows(IllegalArgumentException.class, () -> new SourceRange(9, 4));
    }
}
```

`ScilabIdentifierTest.java`:

```java
package org.scilab.modules.guibuilder.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ScilabIdentifierTest {

    @Test
    public void ordinaryNamesAreValid() {
        assertTrue(ScilabIdentifier.isValid("okButton"));
        assertTrue(ScilabIdentifier.isValid("btn_2"));
        assertTrue(ScilabIdentifier.isValid("A"));
    }

    @Test
    public void namesThatWouldNotSurviveBecomingAStructFieldAreRejected() {
        assertFalse(ScilabIdentifier.isValid(""));
        assertFalse(ScilabIdentifier.isValid("2fast"));
        assertFalse(ScilabIdentifier.isValid("has space"));
        assertFalse(ScilabIdentifier.isValid("has-dash"));
        assertFalse(ScilabIdentifier.isValid(null));
    }

    @Test
    public void scilabKeywordsAreRejected() {
        // A tag becomes both a variable and a struct field in generated code,
        // so a keyword here produces a file that will not parse.
        assertFalse(ScilabIdentifier.isValid("function"));
        assertFalse(ScilabIdentifier.isValid("end"));
        assertFalse(ScilabIdentifier.isValid("select"));
    }

    @Test
    public void requireValidNamesTheOffendingValue() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> ScilabIdentifier.requireValid("has space"));
        assertTrue(e.getMessage().contains("has space"));
    }
}
```

- [ ] **Step 3: Run the tests and watch them fail**

Run from `scilab/`: `mvn -pl modules/guibuilder -am test`
Expected: compilation failure — `SourceRange` and `ScilabIdentifier` do not exist.

- [ ] **Step 4: Implement**

`SourceRange.java`:

```java
package org.scilab.modules.guibuilder.model;

/**
 * A half-open character span [start, end) into a source file.
 *
 * Half-open matters: adjacent spans must not count as overlapping, or edits to
 * two neighbouring properties would be rejected as conflicting.
 */
public final class SourceRange {

    private final int start;
    private final int end;

    public SourceRange(int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end (" + end + ") must not precede start (" + start + ")");
        }
        this.start = start;
        this.end = end;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public int length() {
        return end - start;
    }

    public boolean contains(int offset) {
        return offset >= start && offset < end;
    }

    public boolean overlaps(SourceRange other) {
        return start < other.end && other.start < end;
    }

    @Override
    public String toString() {
        return "[" + start + "," + end + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SourceRange)) {
            return false;
        }
        SourceRange r = (SourceRange) o;
        return start == r.start && end == r.end;
    }

    @Override
    public int hashCode() {
        return start * 31 + end;
    }
}
```

`ScilabIdentifier.java`:

```java
package org.scilab.modules.guibuilder.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a widget tag.
 *
 * A tag is used twice in generated code: as a struct field (handles.okButton)
 * and in the widget's own "tag" property. Deliberately stricter than Scilab's
 * full identifier grammar -- Scilab tolerates % and # in some positions, but a
 * tag that needs explaining is a tag that will confuse someone later.
 */
public final class ScilabIdentifier {

    private static final Pattern SHAPE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "abort", "break", "case", "catch", "continue", "do", "else", "elseif",
        "end", "endfunction", "for", "function", "global", "if", "otherwise",
        "pause", "quit", "return", "select", "then", "try", "while")));

    private ScilabIdentifier() {
    }

    public static boolean isValid(String name) {
        return name != null && SHAPE.matcher(name).matches() && !KEYWORDS.contains(name);
    }

    public static void requireValid(String name) {
        if (!isValid(name)) {
            throw new IllegalArgumentException("not a usable Scilab tag: " + name);
        }
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `mvn -pl modules/guibuilder -am test`
Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: add the module skeleton and its two smallest value types

First slice of the GUI designer's round-trip core (phase 1 of
docs/design/guibuilder-rewrite.md). Nothing is wired into CMake yet: the
Maven module builds and tests on its own, so the headless units can be
proven before any native or Swing plumbing exists.

SourceRange is half-open on purpose. Adjacent spans must not count as
overlapping, or edits to two neighbouring properties would be rejected as
conflicting when they are not.

ScilabIdentifier is deliberately stricter than Scilab's own grammar. A
tag becomes both a variable and a struct field in generated code, so
keywords and anything needing quoting are refused up front rather than
producing a file that will not parse.
EOF
git add modules/guibuilder pom.xml
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Task 2: The widget model

**Files:**
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/model/WidgetStyle.java`
- Create: `.../model/PropertyValue.java`
- Create: `.../model/Node.java`
- Create: `.../model/Frame.java`
- Create: `.../model/UnmodelledRegion.java`
- Create: `.../model/Design.java`
- Test: `.../model/DesignTest.java`
- Test: `.../model/NodeTest.java`

**Interfaces:**
- Consumes: `SourceRange`, `ScilabIdentifier` from Task 1.
- Produces: `WidgetStyle.fromScilab(String)` returning `null` when unknown; `PropertyValue.literal(String sourceText, SourceRange, Object value)` and `PropertyValue.computed(String sourceText, SourceRange, String reason)`, with `boolean isLocked()`; `Node(String tag, WidgetStyle style, SourceRange range)` with `String tag()`, `WidgetStyle style()`, `SourceRange sourceRange()`, `Map<String,PropertyValue> properties()`, `void putProperty(String,PropertyValue)`, `boolean isLocked()`, `Frame parent()`; `Frame extends Node` with `List<Node> children()`; `UnmodelledRegion(SourceRange, String reason)`; `Design(String source, Frame root)` with `String source()`, `Frame root()`, `List<UnmodelledRegion> unmodelled()`, `void addUnmodelled(UnmodelledRegion)`, `void add(Frame parent, Node child)`, `Node byTag(String)`, `List<Node> allNodes()`.

- [ ] **Step 1: Write the failing tests**

`NodeTest.java`:

```java
package org.scilab.modules.guibuilder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NodeTest {

    private static SourceRange anywhere() {
        return new SourceRange(0, 10);
    }

    @Test
    public void styleNamesMapFromScilabSpelling() {
        assertEquals(WidgetStyle.PUSHBUTTON, WidgetStyle.fromScilab("pushbutton"));
        assertEquals(WidgetStyle.POPUPMENU, WidgetStyle.fromScilab("popupmenu"));
    }

    @Test
    public void anUnknownStyleIsNullRatherThanAnException() {
        // The parser must be able to ask "is this a style I know?" without
        // catching. An unknown style locks the widget; it never aborts a parse.
        assertNull(WidgetStyle.fromScilab("hologram"));
    }

    @Test
    public void aNodeWithOnlyLiteralPropertiesIsNotLocked() {
        Node n = new Node("okButton", WidgetStyle.PUSHBUTTON, anywhere());
        n.putProperty("string", PropertyValue.literal("\"OK\"", anywhere(), "OK"));
        assertFalse(n.isLocked());
    }

    @Test
    public void oneComputedPropertyLocksTheNode() {
        Node n = new Node("okButton", WidgetStyle.PUSHBUTTON, anywhere());
        n.putProperty("string", PropertyValue.literal("\"OK\"", anywhere(), "OK"));
        n.putProperty("position", PropertyValue.computed("[x y w h]", anywhere(),
                                                         "position is computed from variables"));
        assertTrue(n.isLocked());
        // ...but only that property is locked; the rest stay editable.
        assertFalse(n.properties().get("string").isLocked());
        assertTrue(n.properties().get("position").isLocked());
    }
}
```

`DesignTest.java`:

```java
package org.scilab.modules.guibuilder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DesignTest {

    private static SourceRange at(int a, int b) {
        return new SourceRange(a, b);
    }

    private static Design emptyDesign() {
        return new Design("source text", new Frame("root", WidgetStyle.FRAME, at(0, 11)));
    }

    @Test
    public void addingAChildLinksItBothWays() {
        Design d = emptyDesign();
        Node n = new Node("okButton", WidgetStyle.PUSHBUTTON, at(0, 5));
        d.add(d.root(), n);
        assertTrue(d.root().children().contains(n));
        assertSame(d.root(), n.parent());
        assertSame(n, d.byTag("okButton"));
    }

    @Test
    public void duplicateTagsAreRejected() {
        Design d = emptyDesign();
        d.add(d.root(), new Node("okButton", WidgetStyle.PUSHBUTTON, at(0, 5)));
        assertThrows(IllegalArgumentException.class,
                     () -> d.add(d.root(), new Node("okButton", WidgetStyle.EDIT, at(6, 9))));
    }

    @Test
    public void invalidTagsAreRejectedAtTheModelBoundary() {
        Design d = emptyDesign();
        assertThrows(IllegalArgumentException.class,
                     () -> d.add(d.root(), new Node("has space", WidgetStyle.EDIT, at(0, 5))));
    }

    @Test
    public void unmodelledRegionsAreKeptInSourceOrder() {
        // The tab lists them, and the writer walks them to detect collisions,
        // so an unordered list would make both jobs harder than they need to be.
        Design d = emptyDesign();
        d.addUnmodelled(new UnmodelledRegion(at(50, 60), "loop creates controls"));
        d.addUnmodelled(new UnmodelledRegion(at(10, 20), "unrecognised call"));
        assertEquals(10, d.unmodelled().get(0).range().start());
        assertEquals(50, d.unmodelled().get(1).range().start());
    }

    @Test
    public void allNodesWalksTheWholeTree() {
        Design d = emptyDesign();
        Frame panel = new Frame("panel", WidgetStyle.FRAME, at(0, 5));
        d.add(d.root(), panel);
        d.add(panel, new Node("inner", WidgetStyle.TEXT, at(6, 9)));
        assertEquals(2, d.allNodes().size());
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `mvn -pl modules/guibuilder -am test`
Expected: compilation failure — the model classes do not exist.

- [ ] **Step 3: Implement the model**

`WidgetStyle.java`:

```java
package org.scilab.modules.guibuilder.model;

/** The uicontrol styles Scilab supports, plus axes. */
public enum WidgetStyle {

    PUSHBUTTON("pushbutton"),
    EDIT("edit"),
    TEXT("text"),
    CHECKBOX("checkbox"),
    RADIOBUTTON("radiobutton"),
    LISTBOX("listbox"),
    POPUPMENU("popupmenu"),
    SLIDER("slider"),
    SPINNER("spinner"),
    TABLE("table"),
    IMAGE("image"),
    FRAME("frame"),
    AXES("axes");

    private final String scilabName;

    WidgetStyle(String scilabName) {
        this.scilabName = scilabName;
    }

    public String scilabName() {
        return scilabName;
    }

    /** The style with this Scilab spelling, or null when it is not one we model. */
    public static WidgetStyle fromScilab(String name) {
        if (name == null) {
            return null;
        }
        for (WidgetStyle s : values()) {
            if (s.scilabName.equals(name)) {
                return s;
            }
        }
        return null;
    }
}
```

`PropertyValue.java`:

```java
package org.scilab.modules.guibuilder.model;

/**
 * One property of a widget, together with the exact source text it came from.
 *
 * COMPUTED means the parser could see the property but not its value -- the
 * value is an expression, a variable, a call. Such a property is LOCKED: it is
 * displayed, carried through untouched, and refused as an edit target. Locking
 * one property never locks the others.
 */
public final class PropertyValue {

    public enum Kind { LITERAL, COMPUTED }

    private final Kind kind;
    private final String sourceText;
    private final SourceRange range;
    private final Object value;
    private final String reason;

    private PropertyValue(Kind kind, String sourceText, SourceRange range, Object value, String reason) {
        this.kind = kind;
        this.sourceText = sourceText;
        this.range = range;
        this.value = value;
        this.reason = reason;
    }

    public static PropertyValue literal(String sourceText, SourceRange range, Object value) {
        return new PropertyValue(Kind.LITERAL, sourceText, range, value, null);
    }

    public static PropertyValue computed(String sourceText, SourceRange range, String reason) {
        return new PropertyValue(Kind.COMPUTED, sourceText, range, null, reason);
    }

    public Kind kind() {
        return kind;
    }

    public String sourceText() {
        return sourceText;
    }

    public SourceRange range() {
        return range;
    }

    /** The parsed value, or null when this property is computed. */
    public Object value() {
        return value;
    }

    /** Why this property is locked, or null when it is not. */
    public String reason() {
        return reason;
    }

    public boolean isLocked() {
        return kind == Kind.COMPUTED;
    }
}
```

`Node.java`:

```java
package org.scilab.modules.guibuilder.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One widget in a design. */
public class Node {

    private final String id = UUID.randomUUID().toString();
    private final String tag;
    private final WidgetStyle style;
    private final SourceRange sourceRange;
    private final Map<String, PropertyValue> properties = new LinkedHashMap<>();
    private Frame parent;

    public Node(String tag, WidgetStyle style, SourceRange sourceRange) {
        ScilabIdentifier.requireValid(tag);
        if (style == null) {
            throw new IllegalArgumentException("style must not be null");
        }
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
        this.tag = tag;
        this.style = style;
        this.sourceRange = sourceRange;
    }

    public String id() {
        return id;
    }

    public String tag() {
        return tag;
    }

    public WidgetStyle style() {
        return style;
    }

    public SourceRange sourceRange() {
        return sourceRange;
    }

    /** Insertion-ordered, so the tab and the writer see properties as the file has them. */
    public Map<String, PropertyValue> properties() {
        return Collections.unmodifiableMap(properties);
    }

    public void putProperty(String name, PropertyValue value) {
        properties.put(name, value);
    }

    public Frame parent() {
        return parent;
    }

    void setParent(Frame parent) {
        this.parent = parent;
    }

    /** True when any property could not be modelled. */
    public boolean isLocked() {
        for (PropertyValue v : properties.values()) {
            if (v.isLocked()) {
                return true;
            }
        }
        return false;
    }
}
```

`Frame.java`:

```java
package org.scilab.modules.guibuilder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A widget that contains other widgets. The figure's content is the root Frame. */
public final class Frame extends Node {

    private final List<Node> children = new ArrayList<>();

    public Frame(String tag, WidgetStyle style, SourceRange sourceRange) {
        super(tag, style, sourceRange);
    }

    public List<Node> children() {
        return Collections.unmodifiableList(children);
    }

    void addChild(Node child) {
        children.add(child);
        child.setParent(this);
    }
}
```

`UnmodelledRegion.java`:

```java
package org.scilab.modules.guibuilder.model;

/**
 * A span of the file the parser could not model.
 *
 * The reason is shown to the user verbatim, so write it as an explanation, not
 * as a diagnostic code.
 */
public final class UnmodelledRegion {

    private final SourceRange range;
    private final String reason;

    public UnmodelledRegion(SourceRange range, String reason) {
        if (range == null) {
            throw new IllegalArgumentException("range must not be null");
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("every unmodelled region must carry a reason");
        }
        this.range = range;
        this.reason = reason;
    }

    public SourceRange range() {
        return range;
    }

    public String reason() {
        return reason;
    }
}
```

`Design.java`:

```java
package org.scilab.modules.guibuilder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One .sce file, as far as we understand it. */
public final class Design {

    private final String source;
    private final Frame root;
    private final List<UnmodelledRegion> unmodelled = new ArrayList<>();
    private final Map<String, Node> byTag = new HashMap<>();

    public Design(String source, Frame root) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.source = source;
        this.root = root;
        byTag.put(root.tag(), root);
    }

    public String source() {
        return source;
    }

    public Frame root() {
        return root;
    }

    public void add(Frame parent, Node child) {
        ScilabIdentifier.requireValid(child.tag());
        if (byTag.containsKey(child.tag())) {
            throw new IllegalArgumentException("duplicate tag: " + child.tag());
        }
        parent.addChild(child);
        byTag.put(child.tag(), child);
    }

    public Node byTag(String tag) {
        return byTag.get(tag);
    }

    public void addUnmodelled(UnmodelledRegion region) {
        unmodelled.add(region);
        unmodelled.sort(Comparator.comparingInt(r -> r.range().start()));
    }

    /** In source order, which is the order the tab lists them. */
    public List<UnmodelledRegion> unmodelled() {
        return Collections.unmodifiableList(unmodelled);
    }

    /** Every node except the root, depth-first. */
    public List<Node> allNodes() {
        List<Node> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private void collect(Frame frame, List<Node> out) {
        for (Node child : frame.children()) {
            out.add(child);
            if (child instanceof Frame) {
                collect((Frame) child, out);
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `mvn -pl modules/guibuilder -am test`
Expected: `Tests run: 18, Failures: 0, Errors: 0` (9 from Task 1, 9 here)

- [ ] **Step 5: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: the widget model, with locking as a per-property property

The model carries source ranges everywhere, because the writer rewrites
spans rather than regenerating files, and it cannot do that from a model
that has forgotten where everything came from.

Locking is per-PROPERTY, not per-widget. A position computed from
variables locks that position and nothing else: the widget keeps its
editable string, font and colours. Node.isLocked() is a convenience over
the properties, not a separate piece of state that could drift from them.

WidgetStyle.fromScilab returns null rather than throwing on an unknown
style. The parser has to be able to ask whether it recognises something
without catching an exception, and an unknown style locks a widget rather
than aborting the parse -- opening a file we only partly understand is
the point of the design, not a failure mode.
EOF
git add modules/guibuilder
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Task 3: Positioned token stream over SciNotes' lexer

**Files:**
- Modify: `modules/guibuilder/pom.xml` (add the `scinotes` dependency)
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/parse/Token.java`
- Create: `.../parse/ScilabTokenStream.java`
- Test: `.../parse/ScilabTokenStreamTest.java`

**Interfaces:**
- Consumes: `SourceRange` from Task 1.
- Produces: `Token(Token.Type type, String text, SourceRange range)` with `type()`, `text()`, `range()`; `Token.Type` enum `{ IDENTIFIER, STRING, NUMBER, OPERATOR, PUNCTUATION, COMMENT, WHITESPACE, EOF }`; `ScilabTokenStream.tokenize(String source)` returning `List<Token>` with comments and whitespace **included** (the writer needs them to preserve formatting).

- [ ] **Step 1: Add the dependency**

In `modules/guibuilder/pom.xml`, inside a new `<dependencies>` element:

```xml
  <dependencies>
    <dependency>
      <groupId>org.scilab</groupId>
      <artifactId>scinotes</artifactId>
      <version>${project.version}</version>
    </dependency>
  </dependencies>
```

- [ ] **Step 2: Write the failing test**

`ScilabTokenStreamTest.java`:

```java
package org.scilab.modules.guibuilder.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class ScilabTokenStreamTest {

    @Test
    public void everyTokenRangeIndexesBackIntoTheSource() {
        // This is the property the whole writer depends on. If a token's range
        // does not slice its own text out of the source, every rewrite is wrong.
        String src = "h = uicontrol(f, \"style\", \"pushbutton\"); // make it\n";
        for (Token t : ScilabTokenStream.tokenize(src)) {
            if (t.type() != Token.Type.EOF) {
                assertEquals(t.text(), src.substring(t.range().start(), t.range().end()),
                             "token " + t.type() + " does not slice back to its own text");
            }
        }
    }

    @Test
    public void tokensCoverTheSourceWithNoGaps() {
        // Comments and whitespace are tokens too. A gap would mean bytes the
        // writer cannot account for, and formatting would be lost on save.
        String src = "a = 1;   // note\nb = 2;\n";
        List<Token> tokens = ScilabTokenStream.tokenize(src);
        int cursor = 0;
        for (Token t : tokens) {
            if (t.type() == Token.Type.EOF) {
                continue;
            }
            assertEquals(cursor, t.range().start(), "gap or overlap before " + t.text());
            cursor = t.range().end();
        }
        assertEquals(src.length(), cursor, "tokens do not reach the end of the source");
    }

    @Test
    public void stringsAndCommentsAreRecognisedAsSuch() {
        List<Token> tokens = ScilabTokenStream.tokenize("x = \"hi\"; // done\n");
        assertTrue(tokens.stream().anyMatch(t -> t.type() == Token.Type.STRING && t.text().equals("\"hi\"")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == Token.Type.COMMENT && t.text().startsWith("//")));
    }

    @Test
    public void anEmptySourceYieldsOnlyEof() {
        List<Token> tokens = ScilabTokenStream.tokenize("");
        assertEquals(1, tokens.size());
        assertEquals(Token.Type.EOF, tokens.get(0).type());
    }
}
```

- [ ] **Step 3: Run the test and watch it fail**

Run: `mvn -pl modules/guibuilder -am test`
Expected: compilation failure — `Token` and `ScilabTokenStream` do not exist.

- [ ] **Step 4: Discover the lexer's actual API before writing against it**

Do not guess at SciNotes' lexer interface. Read it first:

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
sed -n '1,120p' modules/scinotes/src/java/org/scilab/modules/scinotes/ScilabLexer.java
grep -n "public " modules/scinotes/src/java/org/scilab/modules/scinotes/ScilabLexer.java | head -30
grep -n "public static final int" modules/scinotes/src/java/org/scilab/modules/scinotes/ScilabLexerConstants.java | head -40
```

`ScilabLexer` is built for a `javax.swing.text.Document` and returns SciNotes' own token constants. Two consequences to handle in the implementation:

1. If it can be driven from a plain `String` (via a `javax.swing.text.PlainDocument`), wrap it and map its constants onto `Token.Type`. `PlainDocument` is in `javax.swing.text`, which is available headlessly — it is a document model, not a component.
2. If driving it requires a live editor pane, **do not** pull Swing UI into the `parse` unit. Write a small self-contained scanner in `ScilabTokenStream` instead, covering identifiers, numbers, `"` and `'` strings with `""` escapes, `//` comments, operators and punctuation. Record the decision and the reason in the class javadoc.

Either way the observable contract is the four tests above, which is what the rest of the plan depends on.

- [ ] **Step 5: Implement `Token`**

```java
package org.scilab.modules.guibuilder.parse;

import org.scilab.modules.guibuilder.model.SourceRange;

/** One lexical token, carrying the exact span it occupies in the source. */
public final class Token {

    public enum Type { IDENTIFIER, STRING, NUMBER, OPERATOR, PUNCTUATION, COMMENT, WHITESPACE, EOF }

    private final Type type;
    private final String text;
    private final SourceRange range;

    public Token(Type type, String text, SourceRange range) {
        this.type = type;
        this.text = text;
        this.range = range;
    }

    public Type type() {
        return type;
    }

    public String text() {
        return text;
    }

    public SourceRange range() {
        return range;
    }

    @Override
    public String toString() {
        return type + "(" + text + ")" + range;
    }
}
```

- [ ] **Step 6: Implement `ScilabTokenStream` and make the tests pass**

Implement `tokenize(String)` per the decision made in Step 4. Whichever path is taken, it must emit `WHITESPACE` and `COMMENT` tokens so the stream covers the source with no gaps, and finish with a single `EOF` token whose range is `[source.length(), source.length())`.

Run: `mvn -pl modules/guibuilder -am test`
Expected: `Tests run: 22, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: a positioned token stream that accounts for every byte

Two properties are tested because everything downstream rests on them:
each token slices back out of the source at its own range, and the
tokens cover the source with no gaps. Comments and whitespace are
tokens for exactly that reason -- a gap would be bytes the writer cannot
account for, and formatting would be lost the first time a file is saved.

The lexer choice is recorded in the class javadoc. SciNotes' ScilabLexer
is built around a Swing Document; it is reused when it can be driven from
a PlainDocument, which is a document model rather than a component and so
is safe headlessly. Where that is not possible the scanner here stays
self-contained rather than dragging Swing UI into the parse unit, which
must remain headless-testable.
EOF
git add modules/guibuilder
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Task 4: The GUI parser

**Files:**
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/parse/ScilabGuiParser.java`
- Test: `.../parse/ScilabGuiParserTest.java`

**Interfaces:**
- Consumes: `Token`, `ScilabTokenStream` (Task 3); the whole `model` package (Tasks 1–2).
- Produces: `ScilabGuiParser.parse(String source)` returning a `Design`. Never throws on unrecognised input — unrecognised spans become `UnmodelledRegion`s.

- [ ] **Step 1: Write the failing tests**

`ScilabGuiParserTest.java`:

```java
package org.scilab.modules.guibuilder.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.PropertyValue;
import org.scilab.modules.guibuilder.model.WidgetStyle;

import org.junit.jupiter.api.Test;

public class ScilabGuiParserTest {

    @Test
    public void aSimpleUicontrolBecomesANode() {
        String src = ""
            + "f = figure(\"figure_name\", \"Demo\");\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n";
        Design d = ScilabGuiParser.parse(src);
        Node ok = d.byTag("ok");
        assertNotNull(ok, "the pushbutton should have been modelled");
        assertEquals(WidgetStyle.PUSHBUTTON, ok.style());
        assertEquals("OK", ok.properties().get("string").value());
        assertFalse(ok.isLocked());
    }

    @Test
    public void theNodeRangeCoversExactlyItsOwnCall() {
        String src = "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\");\n";
        Node ok = ScilabGuiParser.parse(src).byTag("ok");
        String span = src.substring(ok.sourceRange().start(), ok.sourceRange().end());
        assertTrue(span.startsWith("ok = uicontrol("), "span was: " + span);
        assertTrue(span.endsWith(")"), "span was: " + span);
    }

    @Test
    public void aComputedPositionLocksThatPropertyOnly() {
        String src = ""
            + "w = 100;\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", "
            + "\"position\", [10 10 w 20], \"string\", \"OK\");\n";
        Node ok = ScilabGuiParser.parse(src).byTag("ok");
        assertTrue(ok.properties().get("position").isLocked());
        assertNotNull(ok.properties().get("position").reason());
        assertFalse(ok.properties().get("string").isLocked(), "string is a literal and must stay editable");
        assertTrue(ok.isLocked(), "the node reports locked because one property is");
    }

    @Test
    public void anUnknownStyleLocksTheWidgetWithoutAbortingTheParse() {
        String src = ""
            + "a = uicontrol(f, \"style\", \"hologram\", \"tag\", \"a\");\n"
            + "b = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"b\");\n";
        Design d = ScilabGuiParser.parse(src);
        assertNotNull(d.byTag("b"), "a later widget must still be modelled");
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.reason().contains("hologram")),
                   "the unknown style should be reported with its name");
    }

    @Test
    public void codeWeDoNotModelBecomesAnUnmodelledRegionRatherThanDisappearing() {
        String src = ""
            + "for k = 1:5\n"
            + "  uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\" + string(k));\n"
            + "end\n";
        Design d = ScilabGuiParser.parse(src);
        assertFalse(d.unmodelled().isEmpty(), "a loop that creates controls must be reported, not dropped");
        assertTrue(d.unmodelled().get(0).reason().length() > 0);
    }

    @Test
    public void parsingNeverThrowsOnGarbage() {
        // The contract from the spec: a file we only partly understand opens.
        // There is no input for which parse() is allowed to fail.
        ScilabGuiParser.parse("this is (not ][ scilab at all \"unterminated");
        ScilabGuiParser.parse("");
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `mvn -pl modules/guibuilder -am test`
Expected: compilation failure — `ScilabGuiParser` does not exist.

- [ ] **Step 3: Implement the parser**

Write `ScilabGuiParser` as a recursive-descent pass over the token list. It is not a general Scilab parser and must not try to be. Required behaviour:

- Scan for `IDENTIFIER` tokens whose text is `uicontrol` or `figure` and which are followed by `(`.
- Walk backwards over whitespace from that identifier; if the preceding tokens are `IDENTIFIER =` (optionally `IDENTIFIER . IDENTIFIER =` for `handles.ok = ...`), the node's source range starts at that identifier. Otherwise it starts at the call itself.
- Find the matching `)` by counting bracket depth across `(`, `[`, `{`. The node's range ends just after it. An unbalanced call to end-of-file becomes an `UnmodelledRegion` with the reason `unterminated call`.
- Split the argument list on top-level commas. A pair of `STRING` then value is a property. The property name is the string's content, lowercased.
- A value that is a single `STRING` or `NUMBER` token, or a `[` … `]` containing only `NUMBER` and whitespace tokens, is `PropertyValue.literal(...)` with the parsed value: `String` for strings, `Double` for numbers, `double[]` for numeric vectors.
- Any other value is `PropertyValue.computed(sourceText, range, reason)` where the reason names what was seen, e.g. `position is computed from an expression: [10 10 w 20]`.
- The tag is the value of the `tag` property when it is a literal string; otherwise the capturing variable name; otherwise a generated `widget1`, `widget2`, … A tag that fails `ScilabIdentifier.isValid` is replaced by a generated one and an `UnmodelledRegion` records that the original tag could not be used.
- The style is the value of the `style` property. `WidgetStyle.fromScilab` returning `null` produces an `UnmodelledRegion` naming the unknown style, and the widget is not added to the tree.
- Any span between the end of one recognised call and the start of the next that contains a non-whitespace, non-comment token becomes an `UnmodelledRegion` whose reason describes the first significant token, e.g. `code we do not model: for`.
- Every method is wrapped so that no input escapes as an exception: the top-level `parse` catches `RuntimeException`, records the whole remaining span as an `UnmodelledRegion` with the exception's message, and returns what it has.

The root `Frame` is built from the `figure(...)` call when one is found (tag from its `tag` property, else `figure`), otherwise a synthetic root with tag `figure` and range `[0,0)`.

- [ ] **Step 4: Run the tests and watch them pass**

Run: `mvn -pl modules/guibuilder -am test`
Expected: `Tests run: 28, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: parse figure and uicontrol construction, lock the rest

The parser recognises exactly what it needs to and treats everything
else as something to carry through rather than something to reject. It
is not a general Scilab parser and must not grow into one.

parse() never throws. That is a contract, not an implementation detail:
the design says a file we only partly understand opens anyway, so there
is no input for which parsing may fail. Garbage in produces a Design
consisting almost entirely of unmodelled regions, which is exactly what
the tab should then show the user.

Locking is decided per property. A position computed from a variable
locks that position and leaves the widget's string, font and colours
editable, so one dynamic value does not cost the user the whole widget.
An unknown style is reported by name, because "hologram is not a style
we model" is actionable and "parse error" is not.
EOF
git add modules/guibuilder
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Task 5: The writer, and the byte-identical invariant

**Files:**
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/write/SourceDocument.java`
- Create: `.../write/SourceValidator.java`
- Create: `.../write/WriteRefusedException.java`
- Create: `.../write/DesignWriter.java`
- Test: `.../write/SourceDocumentTest.java`
- Test: `.../write/DesignWriterTest.java`

**Interfaces:**
- Consumes: `Design`, `Node`, `SourceRange`, `PropertyValue` (Tasks 1–2); `ScilabGuiParser` (Task 4) for the round-trip test.
- Produces: `SourceDocument(String original)` with `void replace(SourceRange, String)` and `String render()`; `interface SourceValidator { boolean isValidScilab(String source); }`; `WriteRefusedException extends Exception`; `DesignWriter.write(Design design, SourceDocument edits, SourceValidator validator)` returning `String`, throwing `WriteRefusedException`.

- [ ] **Step 1: Write the failing tests**

`SourceDocumentTest.java`:

```java
package org.scilab.modules.guibuilder.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.scilab.modules.guibuilder.model.SourceRange;

import org.junit.jupiter.api.Test;

public class SourceDocumentTest {

    @Test
    public void withNoEditsTheOutputIsTheInputExactly() {
        String src = "a = 1;   // spaced out\n\n\tb = 2;\n";
        assertEquals(src, new SourceDocument(src).render());
    }

    @Test
    public void oneReplacementChangesOnlyItsOwnSpan() {
        String src = "a = 1; b = 2;";
        SourceDocument doc = new SourceDocument(src);
        doc.replace(new SourceRange(4, 5), "99");
        assertEquals("a = 99; b = 2;", doc.render());
    }

    @Test
    public void replacementsApplyInSourceOrderRegardlessOfTheOrderTheyWereAdded() {
        String src = "a = 1; b = 2;";
        SourceDocument doc = new SourceDocument(src);
        doc.replace(new SourceRange(11, 12), "8");
        doc.replace(new SourceRange(4, 5), "7");
        assertEquals("a = 7; b = 8;", doc.render());
    }

    @Test
    public void overlappingReplacementsAreRejectedRatherThanSilentlyResolved() {
        SourceDocument doc = new SourceDocument("abcdefgh");
        doc.replace(new SourceRange(2, 5), "X");
        assertThrows(IllegalArgumentException.class, () -> doc.replace(new SourceRange(4, 7), "Y"));
    }

    @Test
    public void adjacentReplacementsAreFineBecauseRangesAreHalfOpen() {
        SourceDocument doc = new SourceDocument("abcdefgh");
        doc.replace(new SourceRange(0, 2), "X");
        doc.replace(new SourceRange(2, 4), "Y");
        assertEquals("XYefgh", doc.render());
    }
}
```

`DesignWriterTest.java`:

```java
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
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `mvn -pl modules/guibuilder -am test`
Expected: compilation failure — the `write` package does not exist.

- [ ] **Step 3: Implement `SourceDocument`**

```java
package org.scilab.modules.guibuilder.write;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.scilab.modules.guibuilder.model.SourceRange;

/**
 * Records replacements against an original text and renders the result.
 *
 * Everything not explicitly replaced is copied through byte for byte. That is
 * what makes saving a file we only partly understand safe: the parts we did
 * not touch cannot be reformatted, reordered, or lost.
 */
public final class SourceDocument {

    private static final class Edit {
        final SourceRange range;
        final String replacement;

        Edit(SourceRange range, String replacement) {
            this.range = range;
            this.replacement = replacement;
        }
    }

    private final String original;
    private final List<Edit> edits = new ArrayList<>();

    public SourceDocument(String original) {
        if (original == null) {
            throw new IllegalArgumentException("original must not be null");
        }
        this.original = original;
    }

    public String original() {
        return original;
    }

    public List<SourceRange> editedRanges() {
        List<SourceRange> out = new ArrayList<>();
        for (Edit e : edits) {
            out.add(e.range);
        }
        return out;
    }

    public boolean isEmpty() {
        return edits.isEmpty();
    }

    public void replace(SourceRange range, String replacement) {
        if (range.end() > original.length()) {
            throw new IllegalArgumentException("range " + range + " is outside the source");
        }
        for (Edit e : edits) {
            if (e.range.overlaps(range)) {
                throw new IllegalArgumentException("overlapping edits: " + e.range + " and " + range);
            }
        }
        edits.add(new Edit(range, replacement));
    }

    public String render() {
        List<Edit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(e -> e.range.start()));
        StringBuilder out = new StringBuilder(original.length());
        int cursor = 0;
        for (Edit e : ordered) {
            out.append(original, cursor, e.range.start());
            out.append(e.replacement);
            cursor = e.range.end();
        }
        out.append(original, cursor, original.length());
        return out.toString();
    }
}
```

- [ ] **Step 4: Implement the validator interface, the exception, and the writer**

```java
package org.scilab.modules.guibuilder.write;

/** "Is this valid Scilab?" Implementations may be expensive; call once per save. */
public interface SourceValidator {
    boolean isValidScilab(String source);
}
```

```java
package org.scilab.modules.guibuilder.write;

/** Thrown instead of writing something unsafe. The file on disk is left untouched. */
public class WriteRefusedException extends Exception {
    public WriteRefusedException(String message) {
        super(message);
    }
}
```

```java
package org.scilab.modules.guibuilder.write;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.SourceRange;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;

/**
 * Turns a design plus a set of edits into new source text.
 *
 * Two refusals, both deliberate. An edit that touches an unmodelled region is
 * refused, because those bytes are code we did not understand and have no right
 * to rewrite. And a result that does not parse is refused outright -- leaving
 * the user with a broken file is the one outcome worse than refusing to save.
 */
public final class DesignWriter {

    private DesignWriter() {
    }

    public static String write(Design design, SourceDocument document, SourceValidator validator)
            throws WriteRefusedException {

        for (SourceRange edited : document.editedRanges()) {
            for (UnmodelledRegion region : design.unmodelled()) {
                if (region.range().overlaps(edited)) {
                    throw new WriteRefusedException(
                        "refusing to write: edit at " + edited + " touches a locked region — "
                        + region.reason());
                }
            }
        }

        String rendered = document.render();

        if (!validator.isValidScilab(rendered)) {
            throw new WriteRefusedException(
                "refusing to write: the result does not parse as Scilab, so the file was left unchanged");
        }

        return rendered;
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `mvn -pl modules/guibuilder -am test`
Expected: `Tests run: 37, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: position-preserving writer, and the invariant it exists for

Saving is rewriting spans, not regenerating a file. Everything not
explicitly replaced is copied through byte for byte, which is what makes
saving a file we only partly understand safe: the parts we did not touch
cannot be reformatted, reordered or lost.

The first test is the controlling invariant -- open a file, change
nothing, save, and the bytes are identical, double spaces and trailing
comments included. Every later feature is built on top of that, so it is
the test to run first when something goes wrong.

Two refusals are deliberate. An edit overlapping an unmodelled region is
refused, because those bytes are code we did not understand and have no
business rewriting. And a result that will not parse is refused outright:
leaving the user with a broken file is the one outcome worse than
refusing to save at all. Overlapping edits are likewise rejected rather
than silently resolved, since any resolution would be a guess about
intent.
EOF
git add modules/guibuilder
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Task 6: Corpus tests against real files

**Files:**
- Create: `modules/guibuilder/src/test/resources/corpus/atoms-generated.sce`
- Create: `modules/guibuilder/src/test/resources/corpus/handwritten-simple.sce`
- Create: `modules/guibuilder/src/test/resources/corpus/handwritten-dynamic.sce`
- Test: `modules/guibuilder/src/test/java/org/scilab/modules/guibuilder/CorpusRoundTripTest.java`

**Interfaces:**
- Consumes: `ScilabGuiParser` (Task 4), `SourceDocument`, `DesignWriter`, `SourceValidator` (Task 5).
- Produces: nothing new; this task exists to prove the invariant against files nobody wrote for the tests.

- [ ] **Step 1: Collect real corpus files**

Generate one with the existing ATOMS builder, and take two from the toolbox tree:

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
ls modules/guibuilder/src/test/resources/corpus/ 2>/dev/null || mkdir -p modules/guibuilder/src/test/resources/corpus
grep -rl "uicontrol(" /Users/josemoya/Projects/SciLabProjects/*/macros/*.sci \
                      /Users/josemoya/Projects/SciLabProjects/*/demos/*.sce 2>/dev/null | head -20
```

Copy two of the results in as `handwritten-simple.sce` (mostly literal properties) and `handwritten-dynamic.sce` (positions computed from variables, or controls created in a loop). If no suitable file is found, write them by hand — `handwritten-dynamic.sce` must contain at least one computed property and one loop that creates controls, since those are what exercise the locking contract.

Write `atoms-generated.sce` by using the existing ATOMS `guibuilder` to place two buttons and an edit box and generating its code, so the new module is proven against its predecessor's output. If that is impractical, hand-write a file matching the shape `guigencode.sci` emits: `handles.<tag> = uicontrol(...)` assignments followed by `<tag>_callback` function stubs.

- [ ] **Step 2: Write the test**

`CorpusRoundTripTest.java`:

```java
package org.scilab.modules.guibuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;
import org.scilab.modules.guibuilder.write.DesignWriter;
import org.scilab.modules.guibuilder.write.SourceDocument;
import org.scilab.modules.guibuilder.write.SourceValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CorpusRoundTripTest {

    private static final SourceValidator ALWAYS_VALID = source -> true;

    private static String read(String name) throws IOException {
        try (InputStream in = CorpusRoundTripTest.class.getResourceAsStream("/corpus/" + name)) {
            assertNotNull(in, "corpus file missing: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"atoms-generated.sce", "handwritten-simple.sce", "handwritten-dynamic.sce"})
    public void openingAndSavingAnyCorpusFileIsByteIdentical(String name) throws Exception {
        String src = read(name);
        Design d = ScilabGuiParser.parse(src);
        assertEquals(src, DesignWriter.write(d, new SourceDocument(src), ALWAYS_VALID),
                     name + " was disturbed by a no-op save");
    }

    @ParameterizedTest
    @ValueSource(strings = {"atoms-generated.sce", "handwritten-simple.sce", "handwritten-dynamic.sce"})
    public void everyUnmodelledRegionCarriesAReasonFitToShowAUser(String name) throws Exception {
        for (UnmodelledRegion r : ScilabGuiParser.parse(read(name)).unmodelled()) {
            assertFalse(r.reason().isBlank(), name + " has an unmodelled region with no reason");
        }
    }

    @Test
    public void theAtomsGeneratedFileIsUnderstoodNotJustPreserved() throws Exception {
        // Preserving a file we understood nothing about is easy and useless.
        // Its predecessor's own output must actually come back as widgets.
        Design d = ScilabGuiParser.parse(read("atoms-generated.sce"));
        assertTrue(d.allNodes().size() >= 3,
                   "expected at least the three controls the file creates, got " + d.allNodes().size());
    }

    @Test
    public void theDynamicFileLocksRatherThanFailing() throws Exception {
        Design d = ScilabGuiParser.parse(read("handwritten-dynamic.sce"));
        boolean somethingLocked = !d.unmodelled().isEmpty()
            || d.allNodes().stream().anyMatch(n -> n.isLocked());
        assertTrue(somethingLocked, "the dynamic corpus file should exercise the locking contract");
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `mvn -pl modules/guibuilder -am test`
Expected: all corpus tests pass. If `openingAndSavingAnyCorpusFileIsByteIdentical` fails, the writer or the token coverage is wrong — fix that rather than adjusting the test, since this is the invariant the phase exists to establish.

- [ ] **Step 4: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: prove the round trip against files nobody wrote for the tests

Unit tests prove the writer does what the writer was written to do. The
corpus proves it against real Scilab: a file the old ATOMS builder
generated, a hand-written GUI with literal properties, and a hand-written
GUI with computed positions and a loop that creates controls.

Two of the assertions are there to stop the suite passing for the wrong
reason. Preserving a file we understood nothing about is trivial and
worthless, so the ATOMS-generated file must come back as actual widgets.
And the dynamic file must genuinely lock something, or it is not
exercising the contract it was added for.

If the byte-identical assertion ever fails, the writer or the token
coverage is wrong. It is not a test to relax.
EOF
git add modules/guibuilder
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Task 7: CMake wiring, gateway, and the `guidesigner` command

**Files:**
- Create: `modules/guibuilder/CMakeLists.txt`
- Create: `modules/guibuilder/sci_gateway/guibuilder_gateway.xml`
- Create: `modules/guibuilder/sci_gateway/cpp/sci_guidesigner.cpp`
- Create: `modules/guibuilder/macros/guidesigner.sci`
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/ui/GuiDesigner.java`
- Modify: `CMakeLists.txt` (add `guibuilder` to the `foreach(m ...)` list at line 156–168)
- Modify: `etc/classpath.xml` — regenerated, not hand-edited

**Interfaces:**
- Consumes: everything above.
- Produces: the Scilab command `guidesigner()` and `guidesigner(path)`; Java entry `GuiDesigner.open(String path)` annotated `@ScilabExported(module = "guibuilder", filename = "GuiDesigner.giws.xml")`.

- [ ] **Step 1: Read how a comparable module is wired, before writing any of it**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat modules/scinotes/CMakeLists.txt
cat modules/scinotes/sci_gateway/scinotes_gateway.xml
sed -n '1,80p' modules/scinotes/sci_gateway/cpp/sci_scinotes.cpp
sed -n '840,900p' modules/xcos/src/java/org/scilab/modules/xcos/Xcos.java   # @ScilabExported examples
```

Mirror the structure. Do not invent a different pattern.

- [ ] **Step 2: Add the Java entry point**

```java
package org.scilab.modules.guibuilder.ui;

import org.scilab.modules.graph.utils.ScilabExported;

/** Scilab-facing entry point for the GUI designer. */
public final class GuiDesigner {

    private GuiDesigner() {
    }

    /**
     * Open the designer, on a file when one is given.
     *
     * @param path a .sce to open, or the empty string for an empty designer
     * @return true when the tab was opened
     */
    @ScilabExported(module = "guibuilder", filename = "GuiDesigner.giws.xml")
    public static boolean open(String path) {
        return GuiDesignerTab.openOn(path);
    }
}
```

`GuiDesignerTab.openOn` is written in Task 8. For this task, add a temporary implementation that parses the file and prints a one-line summary to stdout, so the command can be proven end to end before any UI exists:

```java
package org.scilab.modules.guibuilder.ui;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;

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
```

- [ ] **Step 3: Add the macro**

`modules/guibuilder/macros/guidesigner.sci`:

```scilab
// Copyright (C) 2026 - Scilab GUI Designer
//
// This file is hereby licensed under the terms of the GNU GPL v2.0.

// Open the GUI designer, optionally on a file.
function guidesigner(fname)
    if ~exists("fname", "local") then
        fname = "";
    end
    if type(fname) <> 10 then
        error(gettext("guidesigner: the argument must be a file name."));
    end
    guidesigner_open(fname);
endfunction
```

`guidesigner_open` is the gateway-registered primitive declared in `guibuilder_gateway.xml`.

- [ ] **Step 4: Register the module in CMake and rebuild**

Add `guibuilder` to the `foreach(m ...)` list in the top-level `CMakeLists.txt` (line 156–168), in Batch B beside `scinotes`, since it shares that dependency shape.

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cmake -S . -B build-cmake -DCMAKE_Fortran_COMPILER="$(which gfortran)" 2>&1 | tee /tmp/gd-configure.log
grep -n "guibuilder" etc/classpath.xml
```

`etc/classpath.xml` is regenerated by the configure step. `ScilabClasspath.cmake` raises `FATAL_ERROR` when a token's glob matches anything other than exactly one jar, so read the configure log rather than assuming it worked.

- [ ] **Step 5: Build and prove the command exists**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cmake --build build-cmake --target drop-in-all -j8 2>&1 | tee /tmp/gd-build.log
tail -5 /tmp/gd-build.log
./package-macos.sh 2>&1 | tail -3
```

Then:

```bash
cat > /tmp/gd-smoke.sce <<'EOF'
mprintf("@@ guidesigner defined: %d\n", isdef("guidesigner"));
mprintf("@@ primitive defined  : %d\n", isdef("guidesigner_open"));
exit(0);
EOF
timeout 400 /Applications/Scilab-2027.0.0.app/Contents/MacOS/Scilab-2027.0.0 -nwni -nb -f /tmp/gd-smoke.sce </dev/null 2>&1 | grep -a "^@@"
```

Expected: both report `1`.

- [ ] **Step 6: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: wire the module into the build and add the guidesigner command

The module has built and tested standalone until now, deliberately: the
headless units were worth proving before any native plumbing existed to
get in the way. This task adds the CMake registration, the gateway, the
macro and the Java entry point.

The command is guidesigner, not guibuilder. The ATOMS toolbox still owns
guibuilder and still works; two commands of the same name would shadow
each other unpredictably, and retiring the toolbox before the replacement
can edit anything would leave the user worse off than before we started.
The name transfers in phase 2, when guidesigner can actually edit.

GuiDesignerTab.openOn only prints a summary for now. That is enough to
prove the whole path -- macro to gateway to giws to Java to parser --
before the UI exists, so a failure in the next task is a UI failure and
nothing else.
EOF
git add modules/guibuilder CMakeLists.txt etc/classpath.xml
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Task 8: The read-only tab

**Files:**
- Modify: `modules/guibuilder/pom.xml` (add the `gui` dependency)
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/ui/GuiDesignerTabFactory.java`
- Rewrite: `.../ui/GuiDesignerTab.java` (replacing the Task 7 placeholder)
- Test: `.../ui/DesignTreeModelTest.java`
- Create: `.../ui/DesignTreeModel.java`

**Interfaces:**
- Consumes: `Design`, `Node`, `UnmodelledRegion`, `ScilabGuiParser`, `DesignWriter`, `SourceDocument`.
- Produces: `GuiDesignerTab.openOn(String path)` returning `boolean`; `DesignTreeModel implements javax.swing.tree.TreeModel` over a `Design`.

- [ ] **Step 1: Write the failing test for the tree model**

The tree model is the only part of the UI with logic worth testing, and it is headless-safe (`TreeModel` is a data interface, not a component).

`DesignTreeModelTest.java`:

```java
package org.scilab.modules.guibuilder.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;

import org.junit.jupiter.api.Test;

public class DesignTreeModelTest {

    private static final String SRC = ""
        + "f = figure(\"tag\", \"fig\");\n"
        + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n"
        + "for k = 1:3\n"
        + "  uicontrol(f, \"style\", \"text\", \"tag\", \"t\" + string(k));\n"
        + "end\n";

    @Test
    public void theRootIsTheFigureAndWidgetsHangBeneathIt() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);
        assertEquals(d.root(), m.getRoot());
        assertTrue(m.getChildCount(d.root()) >= 1);
    }

    @Test
    public void lockedRegionsAppearInTheTreeSoTheyCannotBeMissed() {
        // The spec requires unmodelled code to be visible. If it is only in a
        // side panel it will be ignored; the tree is where users look.
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);
        assertTrue(m.lockedNodeCount() >= 1, "the loop should surface as a locked entry");
    }

    @Test
    public void everyLockedEntryCanExplainItself() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);
        for (String reason : m.lockedReasons()) {
            assertTrue(reason != null && !reason.isBlank());
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn -pl modules/guibuilder -am test`
Expected: compilation failure — `DesignTreeModel` does not exist.

- [ ] **Step 3: Implement `DesignTreeModel`**

Implement `javax.swing.tree.TreeModel` over the `Design`: the root is `design.root()`, a `Frame`'s children are its `children()` followed by any `UnmodelledRegion` whose range falls inside that frame's range, and every other node is a leaf. Add `int lockedNodeCount()` and `List<String> lockedReasons()` collecting both locked properties and unmodelled regions. `addTreeModelListener`/`removeTreeModelListener` may be no-ops in phase 1, since nothing mutates the design yet — say so in a comment rather than leaving them bare.

- [ ] **Step 4: Implement the tab and the factory**

Add the `gui` dependency to `modules/guibuilder/pom.xml`:

```xml
    <dependency>
      <groupId>org.scilab</groupId>
      <artifactId>gui</artifactId>
      <version>${project.version}</version>
    </dependency>
```

`GuiDesignerTabFactory extends AbstractScilabTabFactory` — mirror `modules/scinotes/src/java/org/scilab/modules/scinotes/tabfactory/CodeNavigatorTabFactory.java`, which is the smallest example in the tree. Read it before writing this.

`GuiDesignerTab` is a `SwingScilabDockablePanel` containing a `JSplitPane`: a `JTree` on the `DesignTreeModel` at the left, and on the right a `JTable` of the selected node's properties (name, value, and a Locked column) above a list of unmodelled regions with their reasons. A Save button calls `DesignWriter.write(design, new SourceDocument(design.source()), validator)` and writes the result back to the file, reporting a `WriteRefusedException` in a dialog rather than swallowing it.

Do not set any colour explicitly. The tab inherits the FlatLaf theme, and hardcoded colours are what made GED need a themed-refresh mechanism.

- [ ] **Step 5: Build, package, and check it by hand**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
mvn -pl modules/guibuilder -am test 2>&1 | tail -20
cmake --build build-cmake --target drop-in-all -j8 2>&1 | tee /tmp/gd-build2.log
tail -5 /tmp/gd-build2.log
pkill -f "Contents/Resources/scilab/.libs/Scilab-2027.0.0" 2>/dev/null; sleep 2
./package-macos.sh 2>&1 | tail -3
open "/Applications/Scilab-2027.0.0.app"
```

In the app, run `guidesigner("<a corpus file>")`. Confirm: the tree lists the widgets, selecting one shows its properties, locked entries show their reasons, and Save leaves the file byte-identical:

```bash
cp <corpus file> /tmp/before.sce
# ... press Save in the app ...
diff /tmp/before.sce <corpus file> && echo "byte-identical"
```

- [ ] **Step 6: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: a tab that shows what the parser understood, and what it did not

Phase 1's visible deliverable. It opens a .sce, lists the widgets it
modelled with their properties, and shows every locked region beside the
reason it is locked -- then saves without disturbing a byte.

Locked entries are in the TREE, not only in a side panel. Anything shown
only off to one side gets ignored, and the whole point of the degradation
contract is that the user can see what the tool will not touch.

Only DesignTreeModel is unit-tested. It is a data interface rather than a
component, so it runs headlessly; the rest of the tab is wiring, checked
by hand against a corpus file with a diff to prove the save was lossless.

No colours are set anywhere in the tab. It inherits the FlatLaf theme,
and hardcoded colours are precisely what left GED needing a themed
refresh mechanism.
EOF
git add modules/guibuilder
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Task 9: The `macr2tree` validation oracle

**Files:**
- Create: `modules/guibuilder/src/java/org/scilab/modules/guibuilder/write/Macr2TreeValidator.java`
- Test: `modules/guibuilder/src/test/java/org/scilab/modules/guibuilder/write/Macr2TreeValidatorIT.java`
- Modify: `.../ui/GuiDesignerTab.java` (use the real validator instead of a permissive stub)

**Interfaces:**
- Consumes: `SourceValidator` (Task 5).
- Produces: `Macr2TreeValidator implements SourceValidator`.

- [ ] **Step 1: Confirm the oracle's behaviour from Scilab before coding against it**

```bash
cat > /tmp/oracle.sce <<'EOF'
function r = try_parse(txt)
  fn = TMPDIR + "/probe.sci";
  mputl(txt, fn);
  r = execstr("exec(""" + fn + """, -1);", "errcatch");
endfunction
mprintf("@@ valid   rc=%d\n", try_parse(["function f()"; "  a = 1;"; "endfunction"]));
mprintf("@@ invalid rc=%d\n", try_parse(["function f()"; "  a = ((;"; "endfunction"]));
exit(0);
EOF
timeout 400 /Applications/Scilab-2027.0.0.app/Contents/MacOS/Scilab-2027.0.0 -nwni -nb -f /tmp/oracle.sce </dev/null 2>&1 | grep -a "^@@"
```

Expected: valid `rc=0`, invalid non-zero. Note the actual codes; the validator asserts on `rc == 0`, not on a specific failure code.

- [ ] **Step 2: Write the integration test**

Name it `...IT.java` so it is recognisable as needing a Scilab runtime, and guard it so the hermetic suite is unaffected when Scilab is absent:

```java
package org.scilab.modules.guibuilder.write;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

public class Macr2TreeValidatorIT {

    private static final String SCILAB =
        "/Applications/Scilab-2027.0.0.app/Contents/MacOS/Scilab-2027.0.0";

    private static void requireScilab() {
        assumeTrue(new File(SCILAB).canExecute(), "needs a packaged Scilab; skipped");
    }

    @Test
    public void wellFormedScilabIsAccepted() {
        requireScilab();
        assertTrue(new Macr2TreeValidator(SCILAB).isValidScilab(
            "function f()\n  a = 1;\nendfunction\n"));
    }

    @Test
    public void malformedScilabIsRejected() {
        requireScilab();
        assertFalse(new Macr2TreeValidator(SCILAB).isValidScilab(
            "function f()\n  a = ((;\nendfunction\n"));
    }

    @Test
    public void anUnavailableScilabIsTreatedAsUnableToConfirm() {
        // Refusing every save because the oracle is missing would be worse than
        // the problem. Unknown is not the same as invalid -- see the class doc.
        assertTrue(new Macr2TreeValidator("/nonexistent/scilab").isValidScilab("a = 1;\n"));
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

Run: `mvn -pl modules/guibuilder -am test`
Expected: compilation failure — `Macr2TreeValidator` does not exist.

- [ ] **Step 4: Implement the validator**

Write the candidate source to a temp file, run the packaged Scilab with `-nwni -nb -f` on a probe script that `exec`s it under `errcatch`, and report `rc == 0`. Use a 60-second timeout and destroy the process on expiry. Delete the temp files in a `finally`.

The class javadoc must record the judgement the third test pins down: when Scilab cannot be run at all, the validator returns `true`. It is an oracle, not an authority — the writer's other guards still apply, and refusing every save because the oracle is unavailable would be a worse failure than the one it prevents.

- [ ] **Step 5: Use it in the tab**

In `GuiDesignerTab`, replace the permissive stub with `new Macr2TreeValidator(<path to the running Scilab>)`. Resolve the path from `SCI` via `ScilabConstants.SCI` rather than hardcoding `/Applications`.

- [ ] **Step 6: Run everything and check it by hand**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
mvn -pl modules/guibuilder -am test 2>&1 | tail -20
```

Expected: all tests pass; the three `IT` tests run when the packaged app is present.

- [ ] **Step 7: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cat > /tmp/msg.txt <<'EOF'
guibuilder: validate every write with Scilab itself before touching the file

The writer already refuses edits that touch locked regions. This closes
the other half: the rendered result is handed to Scilab and only written
if Scilab can parse it. Leaving the user with a broken file is the one
outcome worse than refusing to save.

Using Scilab as the oracle rather than trusting our own parser is the
point. Ours understands GUI construction and deliberately nothing else,
so it is exactly the wrong thing to ask whether a whole file is
well-formed.

One judgement is worth stating because a future reader will question it:
when Scilab cannot be run at all, the validator returns true rather than
false. It is an oracle, not an authority, and the writer's other guards
still apply -- refusing every save because the oracle is unavailable
would be a worse failure than the one it prevents. The behaviour is
pinned by a test so it cannot be changed by accident.
EOF
git add modules/guibuilder
git commit -F /tmp/msg.txt
git push origin main && git push gitlab main
```

---

## Self-Review

**1. Spec coverage.** Section 5.1 module → Tasks 1, 7. Section 5.2 launch path → Task 7. Section 5.3 units and dependency direction → Tasks 1–5, 8 (no Swing in `model`/`parse`/`write`; enforced by the Global Constraints and by those tasks importing none). Section 6 data model → Tasks 1–2; `LayoutSpec` is **deliberately deferred to phase 3** and no task creates it, matching the phase-1 row of section 11 ("layouts are modelled but only `None` is selectable" applies from phase 2 onward — phase 1 has no layout editing at all, and adding an unused class now would be speculative). Section 7 reading → Task 4; 7.1 degradation contract → Tasks 4, 5, 6, 8. Section 8 writing → Tasks 5, 9, including the byte-identical invariant and the refuse-on-unparseable rule. Section 10 testing → every task, plus Task 6 for the corpus. Section 12 naming → Task 7. **Gap found and closed:** the spec's requirement that a file building more than one figure lists them and edits one at a time is not covered; phase 1 has no editing, so Task 4's parser treats a second `figure(...)` call as an ordinary node and the tab shows all of them — the selection UI belongs to phase 2, and this is noted here rather than silently dropped.

**2. Placeholder scan.** No "TBD", "TODO", "handle edge cases", or "similar to Task N". Task 3 Step 4 and Task 4 Step 3 give behavioural specifications rather than complete code — deliberate, because the lexer's real API must be read first in one case and the parser is too long to transcribe usefully in the other; both are pinned by complete, executable tests, which is the contract that matters.

**3. Type consistency.** `SourceRange`, `PropertyValue.literal/computed`, `WidgetStyle.fromScilab`, `Design.add/byTag/allNodes/unmodelled`, `SourceDocument.replace/render/editedRanges`, `DesignWriter.write(Design, SourceDocument, SourceValidator)`, `SourceValidator.isValidScilab`, `GuiDesignerTab.openOn` — each is defined once and used with the same signature everywhere. `Node.isLocked()` is derived from properties in Task 2 and relied on in Tasks 4 and 8 with that meaning.
