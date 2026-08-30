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

package org.scilab.modules.guibuilder.parse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.scilab.modules.guibuilder.CoverageInvariant;
import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.PropertyValue;
import org.scilab.modules.guibuilder.model.ScilabIdentifier;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
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
        // The call became a region rather than a node, so the ";" the scan
        // already absorbed for it belongs to no node either -- it has to be in
        // the region, or it is in nothing at all and the writer may edit it.
        assertNothingIsUnaccountedFor(src);
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

    // ------------------------------------------------------------------
    // Regression tests added on top of the brief's six. Each one was first
    // watched to fail: see task-4-report.md for the run that produced the
    // failures and the exact behaviour each of them pins.
    // ------------------------------------------------------------------

    @Test
    public void aCallWrappedAcrossLinesWithContinuationDotsIsStillOneWidget() {
        // Real GUI code wraps long uicontrol calls with "..". The token stream
        // does not merge those dots or suppress the rest of the line, so a
        // consumer that splits arguments on commas sees them as argument text
        // and loses the pairing of every property after the wrap.
        String src = ""
            + "ok = uicontrol(f, \"style\", \"pushbutton\", ..\n"
            + "               \"tag\", \"ok\", ..  wrapped again\n"
            + "               \"string\", \"OK\");\n";
        Design d = ScilabGuiParser.parse(src);
        Node ok = d.byTag("ok");
        assertNotNull(ok, "a wrapped call is one widget; unmodelled: " + reasons(d));
        assertEquals(WidgetStyle.PUSHBUTTON, ok.style());
        assertEquals("OK", ok.properties().get("string").value(), "the property after the wrap was lost");
        assertFalse(ok.isLocked(), "nothing here is computed");
        assertTrue(d.unmodelled().isEmpty(), "a wrapped call is ordinary code:" + reasons(d));
    }

    @Test
    public void aDoubledQuoteInsideAStringValueIsUnescaped() {
        // Scilab escapes a quote by doubling it. Handing "say ""hi""" back with
        // its doubling intact would show the user the escape, and writing that
        // value back out would double it again.
        String src = ""
            + "a = uicontrol(f, \"style\", \"text\", \"tag\", \"a\", \"string\", \"say \"\"hi\"\"\");\n"
            + "b = uicontrol(f, 'style', 'text', 'tag', 'b', 'string', 'it''s');\n";
        Design d = ScilabGuiParser.parse(src);
        assertEquals("say \"hi\"", d.byTag("a").properties().get("string").value());
        assertEquals("it's", d.byTag("b").properties().get("string").value());
    }

    @Test
    public void anUnbalancedCallRunningToEndOfFileIsReportedNotThrown() {
        String src = "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\"\n";
        Design d = ScilabGuiParser.parse(src);
        assertNull(d.byTag("ok"), "a call whose end we never saw must not be modelled as a widget");
        UnmodelledRegion r = null;
        for (UnmodelledRegion candidate : d.unmodelled()) {
            if (candidate.reason().contains("unterminated call")) {
                r = candidate;
            }
        }
        assertNotNull(r, "expected an unterminated call to be reported:" + reasons(d));
        assertEquals(0, r.range().start(), "the region should start at the assignment");
        assertEquals(src.length(), r.range().end(), "everything from there on is unaccounted for");
    }

    @Test
    public void aFileWithNoFigureCallStillParsesUnderASyntheticRoot() {
        String src = "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\");\n";
        Design d = ScilabGuiParser.parse(src);
        assertEquals("figure", d.root().tag());
        assertEquals(0, d.root().sourceRange().length(), "a root that is not in the file owns no bytes");
        Node ok = d.byTag("ok");
        assertNotNull(ok, "widgets are still modelled without a figure call");
        assertSame(d.root(), ok.parent());
    }

    @Test
    public void anOrdinaryFileProducesNoUnmodelledRegionsAtAll() {
        // Comments, blank lines and the trailing semicolon of a modelled call
        // are not code we failed to understand. Reporting them would lock a
        // file the user can perfectly well edit.
        String src = ""
            + "// A GUI somebody wrote by hand.\n"
            + "f = figure(\"figure_name\", \"Demo\");\n"
            + "\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");   // the button\n";
        Design d = ScilabGuiParser.parse(src);
        assertTrue(d.unmodelled().isEmpty(), "nothing here is unmodelled:" + reasons(d));
        assertEquals("Demo", d.root().properties().get("figure_name").value());
        assertEquals(1, d.allNodes().size());
    }

    @Test
    public void aSecondFigureIsCarriedThroughRatherThanIgnored() {
        // The spec: a file that builds more than one figure is edited one
        // figure at a time, and the others are carried through as unmodelled.
        String src = ""
            + "f = figure(\"figure_name\", \"First\");\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\");\n"
            + "g = figure(\"figure_name\", \"Second\");\n";
        Design d = ScilabGuiParser.parse(src);
        assertEquals("First", d.root().properties().get("figure_name").value());
        UnmodelledRegion second = null;
        for (UnmodelledRegion r : d.unmodelled()) {
            if (r.range().start() == src.indexOf("g = figure")) {
                second = r;
            }
        }
        assertNotNull(second, "the second figure must not be silently dropped:" + reasons(d));
        assertTrue(second.reason().contains("figure"), "reason was: " + second.reason());
        // Same hole as the unreadable-style case above: the second figure is a
        // region, not a node, so the ";" absorbed for it must be inside that
        // region. The span therefore runs to the terminator, not to the ")".
        assertEquals("g = figure(\"figure_name\", \"Second\");",
                     src.substring(second.range().start(), second.range().end()));
        assertNothingIsUnaccountedFor(src);
    }

    @Test
    public void numbersAndNumericVectorsComeBackAsNumbers() {
        // The ATOMS builder emits positions as [0.1,0.1,0.2,0.1] -- comma
        // separated. Treating those as computed would lock the position of
        // every widget in every file its predecessor ever generated.
        String src = ""
            + "a = uicontrol(f, \"style\", \"slider\", \"tag\", \"a\", \"value\", 0.5, \"position\", [10,20,100,30]);\n"
            + "b = uicontrol(f, \"style\", \"text\", \"tag\", \"b\", \"position\", [-10 20 100 30]);\n";
        Design d = ScilabGuiParser.parse(src);
        assertEquals(Double.valueOf(0.5), d.byTag("a").properties().get("value").value());
        assertArrayEquals(new double[] {10, 20, 100, 30},
                          (double[]) d.byTag("a").properties().get("position").value());
        assertArrayEquals(new double[] { -10, 20, 100, 30},
                          (double[]) d.byTag("b").properties().get("position").value());
        assertFalse(d.byTag("b").isLocked(), "a negative coordinate is still a literal");
    }

    @Test
    public void arithmeticInsideBracketsIsNotMistakenForAVector() {
        // [1-2] is the one-element vector [-1] in Scilab, not [1 -2]. Reading
        // it as two elements and writing it back would silently change what
        // the file does, so anything that is not plainly a list of numbers is
        // locked instead of guessed at.
        String src = "a = uicontrol(f, \"style\", \"text\", \"tag\", \"a\", \"position\", [1-2 3 4 5]);\n";
        PropertyValue pos = ScilabGuiParser.parse(src).byTag("a").properties().get("position");
        assertTrue(pos.isLocked(), "an expression must not be read as a literal vector");
        assertTrue(pos.reason().contains("[1-2 3 4 5]"), "reason was: " + pos.reason());
    }

    @Test
    public void theShapeTheAtomsBuilderGeneratesIsUnderstood() {
        // Copied from what guicontrolcreate.sci actually emits: single quotes,
        // mixed-case property names, no spaces, comma-separated vectors, and
        // handles.<tag> = ... as the capturing assignment.
        String src = "handles.ok=uicontrol(f,'unit','normalized','Position',[0.1,0.1,0.2,0.1],"
            + "'Style','pushbutton','Tag','ok','Callback','ok_callback(handles)');\n";
        Design d = ScilabGuiParser.parse(src);
        Node ok = d.byTag("ok");
        assertNotNull(ok, "the ATOMS shape must be understood:" + reasons(d));
        assertEquals(WidgetStyle.PUSHBUTTON, ok.style());
        assertEquals("ok_callback(handles)", ok.properties().get("callback").value());
        assertArrayEquals(new double[] {0.1, 0.1, 0.2, 0.1},
                          (double[]) ok.properties().get("position").value());
        String span = src.substring(ok.sourceRange().start(), ok.sourceRange().end());
        assertTrue(span.startsWith("handles.ok=uicontrol("), "span was: " + span);
        assertTrue(d.unmodelled().isEmpty(), "nothing here is unmodelled:" + reasons(d));
    }

    @Test
    public void aTagThatIsNotAUsableNameIsReplacedAndSaidSo() {
        String src = "a = uicontrol(f, \"style\", \"text\", \"tag\", \"my-button\");\n";
        Design d = ScilabGuiParser.parse(src);
        assertNull(d.byTag("my-button"), "that name could never be written back out");
        assertEquals(1, d.allNodes().size(), "the widget itself is still modelled");
        assertTrue(ScilabIdentifier.isValid(d.allNodes().get(0).tag()));
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.reason().contains("my-button")),
                   "the user has to be told which tag we could not use:" + reasons(d));
    }

    @Test
    public void twoWidgetsSharingATagBothSurvive() {
        String src = ""
            + "a = uicontrol(f, \"style\", \"text\", \"tag\", \"same\");\n"
            + "b = uicontrol(f, \"style\", \"text\", \"tag\", \"same\");\n";
        Design d = ScilabGuiParser.parse(src);
        assertEquals(2, d.allNodes().size(), "the second widget must not be dropped:" + reasons(d));
        assertNotNull(d.byTag("same"));
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.reason().contains("same")),
                   "a tag we had to rename has to be reported:" + reasons(d));
    }

    @Test
    public void aPropertyRangeCoversItsValueIncludingTheQuotes() {
        // The writer replaces exactly this span with a new literal, quotes
        // included. A range one character out would corrupt the file.
        String src = "ok = uicontrol(f, \"style\", \"text\", \"tag\", \"ok\", \"string\", \"OK\");\n";
        PropertyValue s = ScilabGuiParser.parse(src).byTag("ok").properties().get("string");
        assertEquals("\"OK\"", src.substring(s.range().start(), s.range().end()));
        assertEquals("\"OK\"", s.sourceText());
    }

    @Test
    public void everySignificantTokenIsInsideAWidgetOrInsideAnUnmodelledRegion() {
        assertNothingIsUnaccountedFor(""
            + "// header\n"
            + "w = 100;\n"
            + "f = figure(\"figure_name\", \"Demo\");\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"position\", [10 10 w 20]);\n"
            + "disp(uicontrol(f, \"style\", \"text\", \"tag\", \"d\"), \"hello\");\n"
            + "for k = 1:3\n"
            + "  uicontrol(f, \"style\", \"text\", \"tag\", \"t\" + string(k));\n"
            + "end\n"
            + "function ok_callback()\n"
            + "  disp(\"hi\");\n"
            + "endfunction\n");
    }

    @Test
    public void aSeparatorInsideAnEnclosingCallIsNotAbsorbed() {
        // This comma separates disp's two arguments; it does not terminate the
        // uicontrol statement. Absorbing it leaves it inside no region at all,
        // and the writer only refuses edits that OVERLAP a region -- so an edit
        // landing on it would be permitted.
        String src = "disp(uicontrol(f, \"style\", \"text\", \"tag\", \"a\"), \"hello\");\n";
        Design d = ScilabGuiParser.parse(src);
        int comma = src.indexOf("), \"hello\"") + 1;
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.range().contains(comma)),
                   "the comma at " + comma + " belongs to disp's argument list:" + reasons(d));
        assertNothingIsUnaccountedFor(src);
    }

    @Test
    public void aRowSeparatorInsideBracketsIsNotAbsorbed() {
        // A ";" between matrix rows is load-bearing: it is what makes this two
        // rows rather than one. It is not a statement terminator to swallow.
        String src = "h = [uicontrol(f, \"style\", \"text\", \"tag\", \"a\"); "
            + "uicontrol(f, \"style\", \"text\", \"tag\", \"b\")];\n";
        Design d = ScilabGuiParser.parse(src);
        int semicolon = src.indexOf("\"a\"); ") + "\"a\")".length();
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.range().contains(semicolon)),
                   "the row separator at " + semicolon + " must be accounted for:" + reasons(d));
        assertNothingIsUnaccountedFor(src);
    }

    @Test
    public void aWidgetCreatedInsideALoopIsNotModelledAsOneWidget() {
        // Every property here is literal, so nothing else would lock it. One
        // node standing for five runtime widgets is a lie both the canvas and
        // the writer would act on.
        String src = ""
            + "for k = 1:5\n"
            + "  uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\", \"string\", \"Go\");\n"
            + "end\n";
        Design d = ScilabGuiParser.parse(src);
        assertTrue(d.allNodes().isEmpty(), "a widget created in a loop must not be modelled as one widget");
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.reason().contains("for")),
                   "the reason should name the block it sits inside:" + reasons(d));
        assertNothingIsUnaccountedFor(src);
    }

    @Test
    public void aGuiWrappedInAFunctionYieldsOrdinaryEditableWidgets() {
        // The single most common way people write a Scilab GUI. A function
        // body scopes widgets, it does not multiply them: one call here is
        // still exactly one widget, so editing that call is exactly right.
        // Locking these would make the designer blind to most real files.
        String src = ""
            + "function demo()\n"
            + "  f = figure(\"figure_name\", \"Demo\");\n"
            + "  ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n"
            + "  cancel = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"cancel\", \"string\", \"Cancel\");\n"
            + "endfunction\n"
            + "demo();\n";
        Design d = ScilabGuiParser.parse(src);
        assertEquals(2, d.allNodes().size(), "a function-wrapped GUI must open as widgets:" + reasons(d));
        assertEquals("Demo", d.root().properties().get("figure_name").value());
        assertNotNull(d.byTag("ok"));
        assertFalse(d.byTag("ok").isLocked(), "nothing in this widget is computed");
        assertEquals("OK", d.byTag("ok").properties().get("string").value());
        assertNothingIsUnaccountedFor(src);
    }

    @Test
    public void aWidgetInsideAConditionalIsModelledBecauseItIsStillOneCall() {
        // Zero or one runtime widgets, never many, so the call is still the
        // right thing to edit. That the tree may show a widget which will not
        // appear is a canvas-fidelity question for phase 2, not a reason to
        // refuse writes now.
        String src = ""
            + "if wide then\n"
            + "  ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"Go\");\n"
            + "end\n";
        Design d = ScilabGuiParser.parse(src);
        assertNotNull(d.byTag("ok"), "a conditional widget is still one call:" + reasons(d));
        assertFalse(d.byTag("ok").isLocked());
        assertNothingIsUnaccountedFor(src);
    }

    @Test
    public void aWidgetInsideALoopBodyIsStillRecordedAsAnUnmodelledRegion() {
        // Repetition is the whole hazard, and it is the only one: one call,
        // five runtime widgets. The region has to cover the CALL, because the
        // writer refuses an edit only when it overlaps a region.
        String loop = ""
            + "for k = 1:5\n"
            + "  uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\", \"string\", \"Go\");\n"
            + "end\n";
        String whileLoop = ""
            + "while more\n"
            + "  uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\", \"string\", \"Go\");\n"
            + "end\n";
        for (String src : new String[] {loop, whileLoop}) {
            Design d = ScilabGuiParser.parse(src);
            assertTrue(d.allNodes().isEmpty(), "a repeated call is not one widget:" + reasons(d));
            int call = src.indexOf("uicontrol(");
            assertTrue(d.unmodelled().stream().anyMatch(r -> r.range().contains(call)),
                       "the call itself must be inside a region, or the writer may edit it:" + reasons(d));
            assertNothingIsUnaccountedFor(src);
        }
    }

    /**
     * The other half of ruling 8, which the first implementation left
     * undone. {@code if}, {@code select} and {@code try} do not lock -- that
     * decision stands -- but their {@code end} is still an {@code end}, and a
     * counter that only pushes for loops pops the enclosing loop when it
     * arrives. The loop is then no longer open at the {@code uicontrol}
     * below, so a call standing for three runtime widgets comes back as one
     * editable node: exactly the lie the lock exists to prevent, reachable by
     * a line of code as ordinary as {@code if wide then x = 1; end}.
     *
     * <p>Three constructs, one bug, so three tests rather than one: each
     * closes with {@code end} for its own grammatical reason and a future
     * change could plausibly get one right and another wrong.
     */
    @Test
    public void anIfEndInsideALoopDoesNotCancelTheLoopsLock() {
        assertTheLoopStillLocksAcross(""
            + "for k = 1:3\n"
            + "  if wide then x = 1; end\n"
            + "  uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\", \"string\", \"Go\");\n"
            + "end\n");
    }

    @Test
    public void aSelectEndInsideALoopDoesNotCancelTheLoopsLock() {
        assertTheLoopStillLocksAcross(""
            + "for k = 1:3\n"
            + "  select k\n"
            + "  case 1 then x = 1;\n"
            + "  else x = 2;\n"
            + "  end\n"
            + "  uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\", \"string\", \"Go\");\n"
            + "end\n");
    }

    @Test
    public void aTryCatchEndInsideALoopDoesNotCancelTheLoopsLock() {
        assertTheLoopStillLocksAcross(""
            + "for k = 1:3\n"
            + "  try\n"
            + "    x = 1;\n"
            + "  catch\n"
            + "    x = 2;\n"
            + "  end\n"
            + "  uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\", \"string\", \"Go\");\n"
            + "end\n");
    }

    private static void assertTheLoopStillLocksAcross(String src) {
        Design d = ScilabGuiParser.parse(src);
        assertTrue(d.allNodes().isEmpty(),
                   "the enclosing loop is still open at the uicontrol:" + reasons(d));
        int call = src.indexOf("uicontrol(");
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.range().contains(call)),
                   "the repeated call must be inside a region, or the writer may edit it:" + reasons(d));
        assertNothingIsUnaccountedFor(src);
    }

    /**
     * The one ruling-8 combination that had no committed guard: a loop nested
     * inside a function. Both halves of the ruling meet here -- the function
     * must not lock, the loop must -- and a single test pins the pair,
     * because getting one right by breaking the other would still fail it.
     */
    @Test
    public void aLoopNestedInsideAFunctionLocksTheLoopAndNothingElse() {
        String src = ""
            + "function demo()\n"
            + "  f = figure(\"figure_name\", \"Demo\");\n"
            + "  for k = 1:3\n"
            + "    uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\", \"string\", \"Go\");\n"
            + "  end\n"
            + "  ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n"
            + "endfunction\n";
        Design d = ScilabGuiParser.parse(src);

        int repeated = src.indexOf("uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"btn\"");
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.range().contains(repeated)),
                   "the call inside the loop must be carried through:" + reasons(d));

        assertNotNull(d.byTag("ok"), "the call after the loop is ordinary code:" + reasons(d));
        assertFalse(d.byTag("ok").isLocked());
        assertEquals(1, d.allNodes().size(), "exactly one widget is editable here:" + reasons(d));
        assertEquals("Demo", d.root().properties().get("figure_name").value());
        assertNothingIsUnaccountedFor(src);
    }

    @Test
    public void endUsedAsAnIndexDoesNotCloseTheBlockItIsInside() {
        // Scilab lexes "end" inside parentheses as the last-index idiom, not as
        // a block close: scanscilab.ll:348-361 returns DOLLAR when
        // paren_levels.top() > 0. Counting this one as a block close would
        // reopen the loop and let the widget through as editable.
        String src = ""
            + "for k = 1:3\n"
            + "  name = names(end);\n"
            + "  uicontrol(f, \"style\", \"text\", \"tag\", \"a\", \"string\", \"Go\");\n"
            + "end\n";
        Design d = ScilabGuiParser.parse(src);
        assertTrue(d.allNodes().isEmpty(), "the loop is still open at the uicontrol:" + reasons(d));
        assertNothingIsUnaccountedFor(src);
    }

    @Test
    public void parsingReturnsADesignForEveryAwkwardInputWeCouldThinkOf() {
        String[] awkward = {
            null,
            "   ",
            "//just a comment\n",
            "(",
            ")))",
            "uicontrol(",
            "uicontrol()",
            "uicontrol(f, \"style\")",
            "uicontrol(f, \"style\", )",
            "figure(",
            "f = figure(\"tag\", \"9lives\");\n",
            "a = uicontrol(f, \"style\", \"text\", \"tag\", \"a\"\n",
            "a = uicontrol(f, \"style\", \"text\", \"tag\", \"a\", \"string\", \"unterminated);\n",
            "uicontrol(uicontrol(uicontrol(f)))",
            "..\n",
            "a = ..\n  uicontrol(f, \"style\", \"text\", \"tag\", \"a\");\n",
            "😀 = uicontrol(f, \"style\", \"text\");\n",
        };
        for (String src : awkward) {
            Design d = ScilabGuiParser.parse(src);
            assertNotNull(d, "parse returned null for: " + src);
            assertNotNull(d.root(), "no root for: " + src);
            if (src != null) {
                // Not crashing is the floor, not the bar. Malformed input must
                // still account for every byte it could not model, or the
                // writer would be free to edit into the wreckage.
                assertNothingIsUnaccountedFor(src);
            }
        }
    }

    /**
     * {@code guigencode.sci:70} unconditionally emits
     * {@code f.visible = ""on"";} at the end of every file the real ATOMS
     * builder ever generated. Inside a single-quoted Scilab string only
     * {@code ''} escapes a quote -- {@code ""} does not -- so the two
     * double-quotes around {@code on} are literal characters, not an escaped
     * one: this is not a rare corner, it is what every genuine
     * ATOMS-generated file actually contains, and it is not valid Scilab.
     * Confirmed by direct inspection before writing this test (not assumed):
     * it tokenizes as two empty strings around the identifier {@code on},
     * calls neither {@code figure} nor {@code uicontrol}, so it models no
     * widget, and the parser carries the whole line through as one
     * unmodelled region rather than dropping any of it.
     */
    @Test
    public void theAtomsGeneratorsUnescapedVisibilityLineIsAccountedForNotDropped() {
        String src = "f.visible = \"\"on\"\";\n";
        Design d = ScilabGuiParser.parse(src);
        assertNotNull(d, "parse must never return null, even for this line");
        assertTrue(d.allNodes().isEmpty(),
                   "this line calls neither figure nor uicontrol, so it models no widget: " + reasons(d));
        assertFalse(d.unmodelled().isEmpty(),
                    "the line must be recorded as unmodelled rather than silently dropped: " + reasons(d));
        assertFalse(d.unmodelled().get(0).reason().isBlank());
        assertNothingIsUnaccountedFor(src);
    }

    /**
     * A region's reason is not a diagnostic string: {@code GuiDesignerTab}'s
     * cell renderer uses it verbatim as the tree label. A UTF-8 byte order
     * mark is a one-character token that no other rule claims, so it became
     * its own region -- labelled "code we do not model: " followed by an
     * invisible character. The user is shown an entry that appears to say
     * nothing, about a file that opens fine.
     */
    @Test
    public void aByteOrderMarkIsExplainedRatherThanLabelledWithAnInvisibleCharacter() {
        String src = "﻿f = figure(\"figure_name\", \"Demo\");\n"
            + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\");\n";
        Design d = ScilabGuiParser.parse(src);

        UnmodelledRegion bom = null;
        for (UnmodelledRegion r : d.unmodelled()) {
            if (r.range().contains(0)) {
                bom = r;
            }
        }
        assertNotNull(bom, "the byte order mark must still be accounted for:" + reasons(d));
        assertTrue(bom.reason().contains("byte order mark"),
                   "the reason has to say what it is; was: <" + bom.reason() + ">");
        assertFalse(bom.reason().indexOf('﻿') >= 0,
                    "the reason must not itself contain the invisible character");
        assertNotNull(d.byTag("ok"), "a leading BOM must not stop the file being read:" + reasons(d));
        assertNothingIsUnaccountedFor(src);
    }

    /**
     * A gap reason named only its first token, so an entire unmodelled block
     * -- a callback function, a data-preparation section, a thousand
     * characters of it -- was labelled "code we do not model: handles". The
     * label is what the user has to decide from, so it describes the span:
     * its first line, elided if long, and how many more lines it covers.
     */
    @Test
    public void aGapReasonDescribesTheSpanRatherThanNamingOneToken() {
        String src = ""
            + "handles.dummy = 0;\n"
            + "handles.data = [1 2 3];\n"
            + "handles.more = 4;\n";
        Design d = ScilabGuiParser.parse(src);
        assertEquals(1, d.unmodelled().size(), "expected one gap over the lot:" + reasons(d));

        String reason = d.unmodelled().get(0).reason();
        assertTrue(reason.contains("handles.dummy = 0;"),
                   "the first line of the span belongs in the label; was: <" + reason + ">");
        assertTrue(reason.contains("2 more lines"),
                   "how much else it covers belongs in the label; was: <" + reason + ">");
    }

    @Test
    public void aLongFirstLineIsElidedRatherThanPastedWholeIntoTheLabel() {
        String src = "handles.averyLongVariableNameIndeed = someFunction(1, 2, 3) "
            + "+ anotherFunction(4, 5, 6) + yetMore(7, 8, 9);\n";
        String reason = ScilabGuiParser.parse(src).unmodelled().get(0).reason();
        assertTrue(reason.contains("..."), "a long line must be elided; was: <" + reason + ">");
        assertTrue(reason.length() < src.length(),
                   "the label must be shorter than the code it describes; was: <" + reason + ">");
    }

    /**
     * The coverage invariant lives in {@link CoverageInvariant}, shared with
     * {@code CorpusRoundTripTest} so the two narrow exemptions it encodes are
     * defined exactly once.
     */
    private static void assertNothingIsUnaccountedFor(String src) {
        CoverageInvariant.assertNothingIsUnaccountedFor(src);
    }

    private static String reasons(Design d) {
        StringBuilder sb = new StringBuilder();
        for (UnmodelledRegion r : d.unmodelled()) {
            sb.append("\n  ").append(r.range()).append(' ').append(r.reason());
        }
        return sb.length() == 0 ? " (none)" : sb.toString();
    }
}
