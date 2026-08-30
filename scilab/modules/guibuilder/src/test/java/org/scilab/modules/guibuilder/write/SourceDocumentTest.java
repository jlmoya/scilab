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

    // --- Beyond the brief: the controlling invariant is "byte-identical",
    // not "identical after normalisation", so it has to survive the line
    // endings and the missing trailing newline that normalisation would
    // otherwise erase. ---

    @Test
    public void withNoEditsCrlfLineEndingsAreCopiedThroughExactly() {
        String src = "a = 1;\r\n\r\nb = 2;\r\n";
        assertEquals(src, new SourceDocument(src).render());
    }

    @Test
    public void withNoEditsAFileWithNoTrailingNewlineRoundTripsExactly() {
        String src = "a = 1;\nb = 2;";
        assertEquals(src, new SourceDocument(src).render());
    }

    // --- Beyond the brief: the two ends of the file are where an
    // off-by-one in the copy loop would show up first. ---

    @Test
    public void aReplacementAtTheVeryStartOfTheFileReplacesOnlyThatSpan() {
        SourceDocument doc = new SourceDocument("abcdef");
        doc.replace(new SourceRange(0, 2), "XY");
        assertEquals("XYcdef", doc.render());
    }

    @Test
    public void aReplacementAtTheVeryEndOfTheFileReplacesOnlyThatSpan() {
        SourceDocument doc = new SourceDocument("abcdef");
        doc.replace(new SourceRange(4, 6), "XY");
        assertEquals("abcdXY", doc.render());
    }

    @Test
    public void aZeroLengthRangeInsertsWithoutConsumingAnyOriginalText() {
        SourceDocument doc = new SourceDocument("abcdef");
        doc.replace(new SourceRange(3, 3), "---");
        assertEquals("abc---def", doc.render());
    }

    @Test
    public void aZeroLengthRangeAtEndOfFileAppendsWithoutConsumingAnyText() {
        SourceDocument doc = new SourceDocument("abcdef");
        doc.replace(new SourceRange(6, 6), "XYZ");
        assertEquals("abcdefXYZ", doc.render());
    }

    // --- Beyond the brief: a range past the end of the source must be
    // refused at replace()-time, not discovered later as a crash inside
    // render(). ---

    @Test
    public void aRangeEntirelyOutsideTheSourceIsRejected() {
        SourceDocument doc = new SourceDocument("abcdef");
        assertThrows(IllegalArgumentException.class, () -> doc.replace(new SourceRange(10, 12), "Z"));
    }

    @Test
    public void aRangeStartingInsideButEndingPastTheSourceIsRejected() {
        SourceDocument doc = new SourceDocument("abcdef");
        assertThrows(IllegalArgumentException.class, () -> doc.replace(new SourceRange(4, 7), "Z"));
    }

    // --- Beyond the brief: a rejected edit must leave no trace -- the
    // document must still behave exactly as if the rejected call had never
    // been made. ---

    @Test
    public void aRejectedOverlappingEditLeavesThePreviousEditsIntact() {
        SourceDocument doc = new SourceDocument("abcdefgh");
        doc.replace(new SourceRange(2, 5), "X");
        assertThrows(IllegalArgumentException.class, () -> doc.replace(new SourceRange(4, 7), "Y"));
        assertEquals("abXfgh", doc.render());
        assertEquals(1, doc.editedRanges().size());
    }
}
