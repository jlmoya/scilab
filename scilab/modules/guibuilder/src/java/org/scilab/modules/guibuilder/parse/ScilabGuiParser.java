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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Frame;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.PropertyValue;
import org.scilab.modules.guibuilder.model.ScilabIdentifier;
import org.scilab.modules.guibuilder.model.SourceRange;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
import org.scilab.modules.guibuilder.model.WidgetStyle;

/**
 * Turns Scilab source into a {@link Design}: the widgets we understand, and a
 * span for every piece of the file we do not.
 *
 * <p>This is deliberately <em>not</em> a Scilab parser and must not grow into
 * one. It recognises {@code figure(...)} and {@code uicontrol(...)} calls, the
 * assignments that capture them, and their property-list arguments. Everything
 * else is recorded as an {@link UnmodelledRegion} and carried through
 * untouched.
 *
 * <p>Two contracts govern everything here.
 *
 * <p><b>{@link #parse(String)} never throws.</b> Not "rarely", not "only on
 * valid input": there is no input for which parsing may fail, because the
 * design says a file we only partly understand still opens. Garbage in
 * produces a Design consisting almost entirely of unmodelled regions, which is
 * exactly what the designer tab should then show the user. Each widget is
 * built behind its own guard so one unreadable call cannot cost the reader the
 * rest of the file, and the whole pass sits behind a last-resort guard that
 * records the unread remainder as unmodelled rather than propagating.
 *
 * <p><b>Nothing is silently dropped.</b> Every span carrying a significant
 * token that is not part of a modelled widget becomes an
 * {@link UnmodelledRegion}, because the writer refuses edits that overlap one:
 * a span this class fails to record is a span the writer would happily
 * overwrite. The reasons are written for a user to read ("code we do not
 * model: for"), not as diagnostic codes.
 *
 * <p>Locking is decided per property. A position computed from a variable
 * locks that position and leaves the widget's string, font and colours
 * editable, so one dynamic value does not cost the user the whole widget.
 */
public final class ScilabGuiParser {

    private static final String FIGURE = "figure";
    private static final String UICONTROL = "uicontrol";

    /** The tag a figure gets when the file does not name one. */
    private static final String DEFAULT_FIGURE_TAG = "figure";

    /** One recognised call, located in the token stream. */
    private static final class Call {

        private final String name;
        private final int startIndex;
        private final int openIndex;
        private final int closeIndex;
        private final int captureIndex;

        Call(String name, int startIndex, int openIndex, int closeIndex, int captureIndex) {
            this.name = name;
            this.startIndex = startIndex;
            this.openIndex = openIndex;
            this.closeIndex = closeIndex;
            this.captureIndex = captureIndex;
        }

        boolean isFigure() {
            return FIGURE.equals(name);
        }
    }

    private final String source;
    private final List<Call> calls = new ArrayList<>();
    private final List<UnmodelledRegion> pending = new ArrayList<>();

    private List<Token> tokens;
    private boolean[] insignificant;
    private Design design;

    /** How far into the source we got, so a failure can report the rest as unread. */
    private int consumed;

    /** Counter behind the generated widget1, widget2, ... tags. */
    private int generated;

    private ScilabGuiParser(String source) {
        // Normalised here rather than at the entry point because the whole
        // class, the last-resort guard in salvage() included, relies on there
        // being a source to build a Design from.
        this.source = source == null ? "" : source;
    }

    /**
     * Reads {@code source} into a {@link Design}. Never throws, for any input,
     * including null.
     */
    public static Design parse(String source) {
        return new ScilabGuiParser(source).run();
    }

    private Design run() {
        try {
            tokens = ScilabTokenStream.tokenize(source);
            insignificant = markInsignificant();
            scan();
            return buildDesign();
        } catch (RuntimeException e) {
            return salvage(e);
        }
    }

    /**
     * The last-resort guard. Whatever went wrong, the caller gets a Design:
     * the widgets already built, the regions already found, and the span we
     * never reached, recorded with the failure's own message so it is visible
     * to the user rather than swallowed.
     */
    private Design salvage(RuntimeException failure) {
        try {
            if (design == null) {
                design = new Design(source, syntheticRoot());
            }
            drainPending();
            int from = Math.max(0, Math.min(consumed, source.length()));
            design.addUnmodelled(new UnmodelledRegion(
                new SourceRange(from, source.length()),
                "the rest of this file could not be read, so it is carried through unchanged: "
                + describe(failure)));
            return design;
        } catch (RuntimeException nested) {
            return new Design(source, syntheticRoot());
        }
    }

    // ------------------------------------------------------------------
    // Pass 1: locate the calls, and the gaps between them
    // ------------------------------------------------------------------

    /**
     * Marks the tokens that carry no meaning for this parser, so every scan
     * below can ignore them uniformly: whitespace, comments, and line
     * continuations.
     *
     * <p>The continuations are the reason this is a separate pass. The token
     * stream deliberately neither merges {@code ..}/{@code ...} into one token
     * nor suppresses the rest of the continued line (see
     * {@link ScilabTokenStream}'s javadoc), and real GUI code wraps long
     * {@code uicontrol(...)} calls exactly there. Left alone, two OPERATOR
     * tokens would land in the middle of an argument list and every property
     * pair after the wrap would be misread; a bracket in a continuation's
     * ignored trailing text would also be counted when matching the closing
     * parenthesis. Scilab ignores the dots and everything after them up to the
     * newline, so this parser does too.
     */
    private boolean[] markInsignificant() {
        boolean[] skip = new boolean[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            Token.Type type = tokens.get(i).type();
            skip[i] = type == Token.Type.WHITESPACE || type == Token.Type.COMMENT;
        }
        int i = 0;
        while (i < tokens.size()) {
            if (continuationRun(i) < 2) {
                i++;
                continue;
            }
            while (i < tokens.size() && tokens.get(i).type() != Token.Type.EOF && !endsLine(tokens.get(i))) {
                skip[i] = true;
                i++;
            }
        }
        return skip;
    }

    /**
     * The length of the run of adjacent {@code "."} operators starting at
     * {@code index}. Two or more of them is Scilab's line continuation; a
     * single one is a field access or a dot operator and means nothing here.
     */
    private int continuationRun(int index) {
        int count = 0;
        int previousEnd = -1;
        for (int i = index; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type() != Token.Type.OPERATOR || !".".equals(token.text())) {
                break;
            }
            if (previousEnd >= 0 && token.range().start() != previousEnd) {
                break;
            }
            previousEnd = token.range().end();
            count++;
        }
        return count;
    }

    private static boolean endsLine(Token token) {
        return token.text().indexOf('\n') >= 0 || token.text().indexOf('\r') >= 0;
    }

    private void scan() {
        int gapStart = 0;
        int i = 0;
        while (i < tokens.size()) {
            Token token = tokens.get(i);
            if (token.type() == Token.Type.EOF) {
                break;
            }
            if (insignificant[i] || token.type() != Token.Type.IDENTIFIER || !isCallName(token.text())) {
                i++;
                continue;
            }
            int open = nextSignificant(i + 1);
            if (open < 0 || !isPunctuation(open, "(")) {
                i++;
                continue;
            }

            // An assignment prefix only counts if it is still unclaimed source:
            // never walk backwards into a call we have already modelled.
            int[] prefix = assignment(i);
            boolean assigned = prefix[0] >= gapStart;
            int start = assigned ? prefix[0] : i;
            int capture = assigned ? prefix[1] : -1;
            recordGap(gapStart, start);

            int close = matchingClose(open);
            if (close < 0) {
                // A call whose end we never saw. Everything from here on is
                // unaccounted for: we cannot tell where the next statement
                // begins, so we stop rather than guess.
                addRegion(tokens.get(start).range().start(), source.length(),
                          "unterminated call: the \"(\" after " + token.text()
                          + " is never closed, so the rest of the file is carried through unchanged");
                consumed = source.length();
                return;
            }

            calls.add(new Call(token.text(), start, open, close, capture));
            consumed = tokens.get(close).range().end();
            i = afterStatementSeparator(close + 1);
            gapStart = i;
        }
        recordGap(gapStart, tokens.size());
    }

    /**
     * The index just past the {@code ;} or {@code ,} terminating a call we
     * modelled. It belongs to that statement, so it is not a gap: without
     * this, every modelled line in the file would leave its own semicolon
     * behind as "code we do not model", and the writer would then refuse
     * edits to a file the user can perfectly well edit.
     */
    private int afterStatementSeparator(int from) {
        int next = nextSignificant(from);
        if (next >= 0 && tokens.get(next).type() == Token.Type.PUNCTUATION
            && (";".equals(tokens.get(next).text()) || ",".equals(tokens.get(next).text()))) {
            consumed = tokens.get(next).range().end();
            return next + 1;
        }
        return from;
    }

    /**
     * Walks backwards from a call over the assignment that captures it.
     * Returns {@code {startIndex, captureIndex}}: the token the node's source
     * range starts at, and the variable the handle lands in ({@code ok} for
     * both {@code ok = uicontrol(...)} and {@code handles.ok = uicontrol(...)}),
     * or {@code -1} when the call is not assigned to anything.
     */
    private int[] assignment(int nameIndex) {
        int equals = previousSignificant(nameIndex - 1);
        if (equals < 0 || !isOperator(equals, "=")) {
            return new int[] {nameIndex, -1};
        }
        int capture = previousSignificant(equals - 1);
        if (capture < 0 || tokens.get(capture).type() != Token.Type.IDENTIFIER) {
            return new int[] {nameIndex, -1};
        }
        // handles.ok, or handles.panel.ok: the range starts at the leftmost
        // name, the tag comes from the rightmost one.
        int leftmost = capture;
        while (true) {
            int dot = previousSignificant(leftmost - 1);
            if (dot < 0 || !isOperator(dot, ".")) {
                break;
            }
            int owner = previousSignificant(dot - 1);
            if (owner < 0 || tokens.get(owner).type() != Token.Type.IDENTIFIER) {
                break;
            }
            leftmost = owner;
        }
        return new int[] {leftmost, capture};
    }

    /**
     * The index of the {@code )} that closes the {@code (} at {@code open},
     * counting depth across all three bracket kinds, or -1 when the file ends
     * first or the brackets do not nest.
     */
    private int matchingClose(int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            if (insignificant[i]) {
                continue;
            }
            Token token = tokens.get(i);
            if (token.type() == Token.Type.EOF) {
                break;
            }
            if (token.type() != Token.Type.PUNCTUATION) {
                continue;
            }
            if (isOpener(token.text())) {
                depth++;
            } else if (isCloser(token.text())) {
                depth--;
                if (depth == 0) {
                    // Only a ")" can close this call. A "]" here means the
                    // brackets are crossed, which we treat as unterminated.
                    return ")".equals(token.text()) ? i : -1;
                }
            }
        }
        return -1;
    }

    /** Records the significant part of [from, to) as one unmodelled region. */
    private void recordGap(int from, int to) {
        int first = -1;
        int last = -1;
        for (int i = from; i < to && i < tokens.size(); i++) {
            if (insignificant[i] || tokens.get(i).type() == Token.Type.EOF) {
                continue;
            }
            if (first < 0) {
                first = i;
            }
            last = i;
        }
        if (first < 0) {
            return;
        }
        addRegion(tokens.get(first).range().start(), tokens.get(last).range().end(),
                  "code we do not model: " + tokens.get(first).text());
    }

    // ------------------------------------------------------------------
    // Pass 2: turn the located calls into widgets
    // ------------------------------------------------------------------

    private Design buildDesign() {
        Call figureCall = firstFigure();
        List<UnmodelledRegion> rootStray = new ArrayList<>();
        Frame root = figureCall == null ? syntheticRoot() : buildRoot(figureCall, rootStray);

        design = new Design(source, root);
        drainPending();
        for (UnmodelledRegion region : rootStray) {
            design.addUnmodelled(region);
        }

        for (Call call : calls) {
            if (call == figureCall) {
                continue;
            }
            try {
                if (call.isFigure()) {
                    // The spec: a file that builds more than one figure is
                    // edited one figure at a time, and the others are carried
                    // through as unmodelled. Half-modelling the second one
                    // would let an edit land in the wrong window.
                    addRegion(rangeOf(call), "this file builds more than one figure and only the first is "
                              + "edited here, so this figure is carried through unchanged");
                } else {
                    buildWidget(call);
                }
            } catch (RuntimeException e) {
                // One unreadable call must not cost the reader the rest of the
                // file. It is reported by name rather than swallowed.
                addRegion(rangeOf(call), "this widget could not be read, so it is carried through "
                          + "unchanged: " + describe(e));
            }
            consumed = Math.max(consumed, rangeOf(call).end());
        }
        return design;
    }

    private Call firstFigure() {
        for (Call call : calls) {
            if (call.isFigure()) {
                return call;
            }
        }
        return null;
    }

    private Frame syntheticRoot() {
        return new Frame(DEFAULT_FIGURE_TAG, WidgetStyle.FRAME, new SourceRange(0, 0));
    }

    /** The root frame, built from the file's own figure(...) call. */
    private Frame buildRoot(Call call, List<UnmodelledRegion> stray) {
        try {
            Map<String, PropertyValue> properties = readProperties(call, stray);
            String tag = DEFAULT_FIGURE_TAG;
            PropertyValue tagProperty = properties.get("tag");
            String named = literalString(tagProperty);
            if (named != null) {
                if (ScilabIdentifier.isValid(named)) {
                    tag = named;
                } else {
                    stray.add(new UnmodelledRegion(tagProperty.range(),
                              "the figure's tag \"" + named + "\" is not a name we can use, so it is shown as "
                              + DEFAULT_FIGURE_TAG));
                }
            }
            Frame root = new Frame(tag, WidgetStyle.FRAME, rangeOf(call));
            for (Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
                root.putProperty(entry.getKey(), entry.getValue());
            }
            return root;
        } catch (RuntimeException e) {
            stray.clear();
            stray.add(new UnmodelledRegion(rangeOf(call),
                      "this figure could not be read, so it is carried through unchanged: " + describe(e)));
            return syntheticRoot();
        }
    }

    private void buildWidget(Call call) {
        List<UnmodelledRegion> stray = new ArrayList<>();
        Map<String, PropertyValue> properties = readProperties(call, stray);

        WidgetStyle style = styleOf(properties);
        if (style == null) {
            // Reported as one region over the whole call: the individual
            // arguments are not worth listing for a widget we are not showing.
            addRegion(rangeOf(call), unreadableStyleReason(properties));
            return;
        }

        String tag = chooseTag(call, properties);
        Node node = style == WidgetStyle.FRAME
                    ? new Frame(tag, style, rangeOf(call))
                    : new Node(tag, style, rangeOf(call));
        for (Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
            node.putProperty(entry.getKey(), entry.getValue());
        }
        design.add(design.root(), node);
        for (UnmodelledRegion region : stray) {
            design.addUnmodelled(region);
        }
    }

    private WidgetStyle styleOf(Map<String, PropertyValue> properties) {
        String name = literalString(properties.get("style"));
        return name == null ? null : WidgetStyle.fromScilab(name.trim().toLowerCase(Locale.ROOT));
    }

    private String unreadableStyleReason(Map<String, PropertyValue> properties) {
        PropertyValue style = properties.get("style");
        if (style == null) {
            return "a uicontrol with no style we could read, so it is carried through unchanged";
        }
        String name = literalString(style);
        if (name == null) {
            return "this uicontrol's style is computed (" + style.sourceText()
                   + "), so the widget is carried through unchanged";
        }
        return "\"" + name + "\" is not a uicontrol style we model, so this widget is carried through unchanged";
    }

    /**
     * The widget's tag: its own {@code tag} property when that is a literal
     * string we can use, else the variable that captured the call, else a
     * generated name. A name we cannot use is reported, so the user can see
     * why the tree calls the widget something else.
     */
    private String chooseTag(Call call, Map<String, PropertyValue> properties) {
        PropertyValue tagProperty = properties.get("tag");
        String named = literalString(tagProperty);
        if (named != null) {
            return acceptTag(named, tagProperty.range(), "the tag");
        }
        if (call.captureIndex >= 0) {
            Token variable = tokens.get(call.captureIndex);
            return acceptTag(variable.text(), variable.range(), "the variable name");
        }
        return generateTag();
    }

    private String acceptTag(String candidate, SourceRange range, String what) {
        if (!ScilabIdentifier.isValid(candidate)) {
            String replacement = generateTag();
            addRegion(range, what + " \"" + candidate + "\" is not a name we can use, so this widget is shown as "
                      + replacement);
            return replacement;
        }
        if (design.byTag(candidate) != null) {
            String replacement = generateTag();
            addRegion(range, "two widgets in this file are called \"" + candidate
                      + "\", so this one is shown as " + replacement);
            return replacement;
        }
        return candidate;
    }

    private String generateTag() {
        while (true) {
            generated++;
            String candidate = "widget" + generated;
            if (design == null || design.byTag(candidate) == null) {
                return candidate;
            }
        }
    }

    // ------------------------------------------------------------------
    // Arguments and property values
    // ------------------------------------------------------------------

    /**
     * Reads a call's argument list as property pairs. A {@code STRING}
     * followed by a value is a property, named by the string's content,
     * lowercased -- the ATOMS builder writes {@code 'Style'} and hand-written
     * code writes {@code "style"}, and they are the same property.
     *
     * <p>The first argument is the parent handle and is understood without
     * being modelled. Any other argument that is not part of a pair is
     * something we did not understand inside a call we did, so it is recorded.
     */
    private Map<String, PropertyValue> readProperties(Call call, List<UnmodelledRegion> stray) {
        Map<String, PropertyValue> properties = new LinkedHashMap<>();
        List<int[]> arguments = splitArguments(call.openIndex, call.closeIndex);
        int i = 0;
        while (i < arguments.size()) {
            List<Integer> name = significant(arguments.get(i));
            if (name.isEmpty()) {
                i++;
                continue;
            }
            String property = name.size() == 1 ? stringContent(tokens.get(name.get(0))) : null;
            if (property != null && i + 1 < arguments.size()) {
                List<Integer> value = significant(arguments.get(i + 1));
                if (!value.isEmpty()) {
                    properties.put(property.toLowerCase(Locale.ROOT), classify(property, value));
                    i += 2;
                    continue;
                }
            }
            if (i > 0 || property != null) {
                SourceRange range = spanOf(name);
                stray.add(new UnmodelledRegion(range, "an argument we do not model in this " + call.name
                          + " call: " + source.substring(range.start(), range.end())));
            }
            i++;
        }
        return properties;
    }

    /** Argument token ranges, split on the commas at the call's own depth. */
    private List<int[]> splitArguments(int open, int close) {
        List<int[]> arguments = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        for (int i = open + 1; i < close; i++) {
            if (insignificant[i]) {
                continue;
            }
            Token token = tokens.get(i);
            if (token.type() != Token.Type.PUNCTUATION) {
                continue;
            }
            if (isOpener(token.text())) {
                depth++;
            } else if (isCloser(token.text())) {
                depth--;
            } else if (",".equals(token.text()) && depth == 0) {
                arguments.add(new int[] {start, i});
                start = i + 1;
            }
        }
        arguments.add(new int[] {start, close});
        return arguments;
    }

    /**
     * A property value: a literal when we can read it exactly, computed
     * otherwise. Computed means locked -- displayed, carried through
     * untouched, and refused as an edit target -- and locking one property
     * never locks the others.
     */
    private PropertyValue classify(String name, List<Integer> value) {
        SourceRange range = spanOf(value);
        String text = source.substring(range.start(), range.end());
        Object literal = literalValue(value);
        if (literal != null) {
            return PropertyValue.literal(text, range, literal);
        }
        return PropertyValue.computed(text, range, name + " is computed from an expression: " + text);
    }

    /** The value of a literal argument, or null when it is not one. */
    private Object literalValue(List<Integer> value) {
        Token first = tokens.get(value.get(0));
        if (value.size() == 1) {
            if (first.type() == Token.Type.STRING) {
                return stringContent(first);
            }
            if (first.type() == Token.Type.NUMBER) {
                return number(first.text());
            }
            return null;
        }
        Token last = tokens.get(value.get(value.size() - 1));
        if ("[".equals(first.text()) && "]".equals(last.text())) {
            return numericVector(value.get(0), value.get(value.size() - 1));
        }
        return null;
    }

    /**
     * The numbers in {@code [ ... ]}, or null when it holds anything else.
     *
     * <p>Elements may be separated by spaces or by commas: the ATOMS builder
     * writes {@code [0.1,0.1,0.2,0.1]} and hand-written code writes
     * {@code [10 10 100 20]}, and locking either of them would lock the
     * position of most of the widgets that exist.
     *
     * <p>A leading {@code -} or {@code +} counts only where Scilab counts it:
     * at the start of an element (after {@code [}, a space or a comma) and
     * directly against its digits. {@code [1 -2]} is therefore two elements
     * and {@code [1-2]} is not a vector at all but a subtraction -- Scilab
     * reads it as the single element {@code [-1]} -- so it is left computed
     * rather than guessed at. Reading it wrongly and writing it back would
     * silently change what the file does.
     *
     * <p>Scanned over the raw tokens rather than the significant ones because
     * that spacing is exactly what carries the meaning. A {@code ;} row
     * separator makes it a matrix rather than a position, so that too is left
     * computed.
     */
    private double[] numericVector(int open, int close) {
        List<Double> values = new ArrayList<>();
        int sign = 0;
        boolean elementStart = true;
        for (int i = open + 1; i < close; i++) {
            Token token = tokens.get(i);
            if (token.type() == Token.Type.WHITESPACE || token.type() == Token.Type.COMMENT) {
                if (sign != 0) {
                    return null;
                }
                elementStart = true;
                continue;
            }
            if (token.type() == Token.Type.NUMBER) {
                Double parsed = number(token.text());
                if (parsed == null) {
                    return null;
                }
                values.add(sign < 0 ? -parsed : parsed);
                sign = 0;
                elementStart = false;
                continue;
            }
            if (token.type() == Token.Type.PUNCTUATION && ",".equals(token.text())) {
                if (sign != 0) {
                    return null;
                }
                elementStart = true;
                continue;
            }
            if (token.type() == Token.Type.OPERATOR
                && ("-".equals(token.text()) || "+".equals(token.text()))
                && elementStart && sign == 0) {
                sign = "-".equals(token.text()) ? -1 : 1;
                continue;
            }
            return null;
        }
        if (sign != 0) {
            return null;
        }
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static Double number(String text) {
        try {
            // Scilab writes a double-precision exponent as 1d3; Java does not.
            return Double.valueOf(text.replace('d', 'e').replace('D', 'E'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The content of a string literal, without its quotes and with Scilab's
     * doubled-quote escape undone ({@code "a""b"} is {@code a"b},
     * {@code 'it''s'} is {@code it's}). Null when the token is not a string
     * that closed cleanly: an unterminated one is an expression we could not
     * read, not a value we can hand to the user as text.
     */
    private static String stringContent(Token token) {
        if (token.type() != Token.Type.STRING) {
            return null;
        }
        String text = token.text();
        if (text.length() < 2) {
            return null;
        }
        char quote = text.charAt(0);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        StringBuilder content = new StringBuilder(text.length());
        int i = 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != quote) {
                content.append(c);
                i++;
                continue;
            }
            if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                content.append(quote);
                i += 2;
                continue;
            }
            // A closing quote anywhere but the end means the token is not one
            // well-formed literal, which should not happen and is not guessed at.
            return i == text.length() - 1 ? content.toString() : null;
        }
        return null;
    }

    private String literalString(PropertyValue value) {
        return value != null && value.value() instanceof String ? (String) value.value() : null;
    }

    // ------------------------------------------------------------------
    // Token helpers
    // ------------------------------------------------------------------

    private static boolean isCallName(String text) {
        return UICONTROL.equals(text) || FIGURE.equals(text);
    }

    private static boolean isOpener(String text) {
        return "(".equals(text) || "[".equals(text) || "{".equals(text);
    }

    private static boolean isCloser(String text) {
        return ")".equals(text) || "]".equals(text) || "}".equals(text);
    }

    private boolean isPunctuation(int index, String text) {
        return tokens.get(index).type() == Token.Type.PUNCTUATION && text.equals(tokens.get(index).text());
    }

    private boolean isOperator(int index, String text) {
        return tokens.get(index).type() == Token.Type.OPERATOR && text.equals(tokens.get(index).text());
    }

    private int nextSignificant(int from) {
        for (int i = Math.max(0, from); i < tokens.size(); i++) {
            if (!insignificant[i] && tokens.get(i).type() != Token.Type.EOF) {
                return i;
            }
        }
        return -1;
    }

    private int previousSignificant(int from) {
        for (int i = Math.min(from, tokens.size() - 1); i >= 0; i--) {
            if (!insignificant[i] && tokens.get(i).type() != Token.Type.EOF) {
                return i;
            }
        }
        return -1;
    }

    private List<Integer> significant(int[] range) {
        List<Integer> out = new ArrayList<>();
        for (int i = range[0]; i < range[1]; i++) {
            if (!insignificant[i] && tokens.get(i).type() != Token.Type.EOF) {
                out.add(i);
            }
        }
        return out;
    }

    private SourceRange spanOf(List<Integer> indices) {
        return new SourceRange(tokens.get(indices.get(0)).range().start(),
                               tokens.get(indices.get(indices.size() - 1)).range().end());
    }

    private SourceRange rangeOf(Call call) {
        return new SourceRange(tokens.get(call.startIndex).range().start(),
                               tokens.get(call.closeIndex).range().end());
    }

    private void addRegion(SourceRange range, String reason) {
        addRegion(range.start(), range.end(), reason);
    }

    private void addRegion(int start, int end, String reason) {
        int from = Math.max(0, Math.min(start, source.length()));
        int to = Math.max(from, Math.min(end, source.length()));
        UnmodelledRegion region = new UnmodelledRegion(new SourceRange(from, to), reason);
        if (design == null) {
            pending.add(region);
        } else {
            design.addUnmodelled(region);
        }
    }

    private void drainPending() {
        for (UnmodelledRegion region : pending) {
            design.addUnmodelled(region);
        }
        pending.clear();
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
