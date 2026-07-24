/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.textObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.textObject.FormattedText.InterpreterType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link FormattedText}: a (text, font, interpreter)
 * holder with defensive null handling and an {@link InterpreterType} enum.
 */
public class FormattedTextTest {

    @Test
    public void defaultConstructorGivesEmptyTextAndDefaultFont() {
        FormattedText ft = new FormattedText();
        assertEquals("", ft.getText());
        assertNotNull(ft.getFont());
        // Default interpreter is AUTO (ordinal 0).
        assertEquals(Integer.valueOf(0), ft.getInterpreter());
        assertEquals(InterpreterType.AUTO, ft.getInterpreterAsEnum());
    }

    @Test
    public void textFontConstructorCoercesNullTextToEmpty() {
        Font f = new Font();
        FormattedText ft = new FormattedText(null, f);
        assertEquals("", ft.getText());
        assertSame(f, ft.getFont());
    }

    @Test
    public void setTextNullBecomesEmptyString() {
        FormattedText ft = new FormattedText();
        assertEquals(UpdateStatus.Success, ft.setText("hello"));
        assertEquals("hello", ft.getText());
        assertEquals(UpdateStatus.Success, ft.setText(null));
        assertEquals("", ft.getText());
    }

    @Test
    public void copyConstructorDeepCopiesTextAndFont() {
        FormattedText src = new FormattedText("abc", new Font());
        src.getFont().setStyle(11);
        FormattedText copy = new FormattedText(src);

        assertEquals("abc", copy.getText());
        // Font is a fresh instance carrying the same values.
        assertNotSame(src.getFont(), copy.getFont());
        assertEquals(Integer.valueOf(11), copy.getFont().getStyle());

        // Mutating the copy's font does not affect the source.
        copy.getFont().setStyle(1);
        assertEquals(Integer.valueOf(11), src.getFont().getStyle());
    }

    @Test
    public void copyConstructorDoesNotCopyInterpreter() {
        // Characterisation: the copy constructor only copies text + font, NOT the
        // interpreter, so a LATEX source yields an AUTO copy.
        FormattedText src = new FormattedText("x", new Font());
        src.setInterpreter(InterpreterType.LATEX);
        assertEquals(Integer.valueOf(1), src.getInterpreter());

        FormattedText copy = new FormattedText(src);
        assertEquals(InterpreterType.AUTO, copy.getInterpreterAsEnum());
        assertEquals(Integer.valueOf(0), copy.getInterpreter());
    }

    @Test
    public void equalsUsesReferenceEqualityOnFontBecauseFontHasNoEquals() {
        // Characterisation of a real behaviour: FormattedText.equals() compares the
        // Font field with font.equals(), but Font does not override equals(), so it
        // is reference equality. Two independently-built FormattedText objects with
        // identical field VALUES are therefore NOT equal.
        assertNotEquals(new FormattedText(), new FormattedText());

        Font shared = new Font();
        FormattedText a = new FormattedText("hi", shared);
        FormattedText b = new FormattedText("hi", shared);
        // Same Font instance + equal text -> equal.
        assertEquals(a, b);

        // Same text but a distinct (value-equal) Font instance -> NOT equal.
        FormattedText c = new FormattedText("hi", new Font());
        assertNotEquals(a, c);
    }

    @Test
    public void equalsReflexiveAndRejectsForeignTypes() {
        FormattedText ft = new FormattedText("z", new Font());
        assertEquals(ft, ft);
        assertFalse(ft.equals("z"));
        assertFalse(ft.equals(null));
    }

    @Test
    public void setInterpreterReportsChangeVsNoChange() {
        FormattedText ft = new FormattedText();
        // Default is AUTO, so setting AUTO again is a no-op.
        assertEquals(UpdateStatus.NoChange, ft.setInterpreter(InterpreterType.AUTO));
        assertEquals(UpdateStatus.Success, ft.setInterpreter(InterpreterType.MATHML));
        assertEquals(InterpreterType.MATHML, ft.getInterpreterAsEnum());
        assertEquals(UpdateStatus.NoChange, ft.setInterpreter(InterpreterType.MATHML));
    }

    @Test
    public void setInterpreterByIntegerMapsThroughIntToEnum() {
        FormattedText ft = new FormattedText();
        ft.setInterpreter(Integer.valueOf(1));
        assertEquals(InterpreterType.LATEX, ft.getInterpreterAsEnum());
        ft.setInterpreter(Integer.valueOf(3));
        assertEquals(InterpreterType.NONE, ft.getInterpreterAsEnum());
    }

    @Test
    public void interpreterIntToEnumCoversAllAndDefaults() {
        assertEquals(InterpreterType.AUTO, InterpreterType.intToEnum(0));
        assertEquals(InterpreterType.LATEX, InterpreterType.intToEnum(1));
        assertEquals(InterpreterType.MATHML, InterpreterType.intToEnum(2));
        assertEquals(InterpreterType.NONE, InterpreterType.intToEnum(3));
        // Unknown positive and negative values fall through to AUTO.
        assertEquals(InterpreterType.AUTO, InterpreterType.intToEnum(99));
        assertEquals(InterpreterType.AUTO, InterpreterType.intToEnum(-5));
    }

    @Test
    public void interpreterStringToEnumMatchesKnownStringsElseAuto() {
        assertEquals(InterpreterType.LATEX, InterpreterType.stringToEnum("latex"));
        assertEquals(InterpreterType.MATHML, InterpreterType.stringToEnum("mathml"));
        assertEquals(InterpreterType.NONE, InterpreterType.stringToEnum("none"));
        assertEquals(InterpreterType.AUTO, InterpreterType.stringToEnum("auto"));
        assertEquals(InterpreterType.AUTO, InterpreterType.stringToEnum("something-else"));
        // Case-sensitive: "LATEX" is not recognised.
        assertEquals(InterpreterType.AUTO, InterpreterType.stringToEnum("LATEX"));
    }

    @Test
    public void interpreterEnumToStringRoundTrips() {
        assertEquals("auto", InterpreterType.enumToString(InterpreterType.AUTO));
        assertEquals("latex", InterpreterType.enumToString(InterpreterType.LATEX));
        assertEquals("mathml", InterpreterType.enumToString(InterpreterType.MATHML));
        assertEquals("none", InterpreterType.enumToString(InterpreterType.NONE));
    }

    @Test
    public void interpreterConvertersRejectNull() {
        // switch on an Integer / String argument unboxes / dereferences null.
        assertThrows(NullPointerException.class, () -> InterpreterType.intToEnum(null));
        assertThrows(NullPointerException.class, () -> InterpreterType.stringToEnum(null));
    }

    @Test
    public void propertyEnumHasThreeEntries() {
        assertEquals(3, FormattedText.FormattedTextProperty.values().length);
    }
}
