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

package org.scilab.modules.gui.bridge.filechooser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Vector;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ExportData}, a plain data holder describing a
 * graphical export (figure id, file name, extension and export options).
 *
 * <p>The class carries no logic beyond field storage, so these tests pin down
 * the two constructors, the getter/setter round-trips, the null-tolerance of
 * every field, and the (deliberately documented) fact that the options
 * {@link Vector} is stored and returned by reference with no defensive copy.
 */
public class ExportDataTest {

    // --- full constructor ---------------------------------------------------

    @Test
    public void fullConstructorStoresEveryFieldInTheRightSlot() {
        Vector<String> props = new Vector<String>();
        props.add("opt1");
        props.add("opt2");

        // Distinct values so a wrong constructor wiring would be caught.
        ExportData data = new ExportData(Integer.valueOf(42), "myplot", "png", props);

        assertEquals(Integer.valueOf(42), data.getFigureId());
        assertEquals("myplot", data.getExportName());
        assertEquals("png", data.getExportExtension());
        assertSame(props, data.getExportProperties());
    }

    @Test
    public void fullConstructorAcceptsNullForEveryArgument() {
        ExportData data = new ExportData(null, null, null, null);

        assertNull(data.getFigureId());
        assertNull(data.getExportName());
        assertNull(data.getExportExtension());
        assertNull(data.getExportProperties());
    }

    // --- default constructor ------------------------------------------------

    @Test
    public void defaultConstructorLeavesEveryFieldNull() {
        ExportData data = new ExportData();

        assertNull(data.getFigureId());
        assertNull(data.getExportName());
        assertNull(data.getExportExtension());
        assertNull(data.getExportProperties());
    }

    // --- setter / getter round-trips ---------------------------------------

    @Test
    public void settersRoundTripOnADefaultConstructedInstance() {
        ExportData data = new ExportData();
        Vector<String> props = new Vector<String>();
        props.add("A4");

        data.setFigureId(Integer.valueOf(7));
        data.setExportName("figure");
        data.setExportExtension("svg");
        data.setExportProperties(props);

        assertEquals(Integer.valueOf(7), data.getFigureId());
        assertEquals("figure", data.getExportName());
        assertEquals("svg", data.getExportExtension());
        assertSame(props, data.getExportProperties());
    }

    @Test
    public void settersOverrideValuesSuppliedToTheConstructor() {
        ExportData data = new ExportData(Integer.valueOf(1), "old", "eps", new Vector<String>());

        data.setFigureId(Integer.valueOf(2));
        data.setExportName("new");
        data.setExportExtension("pdf");

        assertEquals(Integer.valueOf(2), data.getFigureId());
        assertEquals("new", data.getExportName());
        assertEquals("pdf", data.getExportExtension());
    }

    @Test
    public void settersAcceptNullWithoutValidation() {
        ExportData data = new ExportData(Integer.valueOf(9), "name", "gif", new Vector<String>());

        data.setFigureId(null);
        data.setExportName(null);
        data.setExportExtension(null);
        data.setExportProperties(null);

        assertNull(data.getFigureId());
        assertNull(data.getExportName());
        assertNull(data.getExportExtension());
        assertNull(data.getExportProperties());
    }

    // --- edge / boundary values --------------------------------------------

    @Test
    public void figureIdPreservesNegativeAndZeroValues() {
        ExportData data = new ExportData();

        data.setFigureId(Integer.valueOf(0));
        assertEquals(Integer.valueOf(0), data.getFigureId());

        data.setFigureId(Integer.valueOf(-5));
        assertEquals(Integer.valueOf(-5), data.getFigureId());
    }

    @Test
    public void emptyStringsAreStoredVerbatimAndAreNotCoercedToNull() {
        ExportData data = new ExportData(Integer.valueOf(3), "", "", new Vector<String>());

        assertEquals("", data.getExportName());
        assertEquals("", data.getExportExtension());
    }

    // --- reference semantics (characterization: no defensive copy) ----------

    @Test
    public void exportPropertiesIsSharedByReferenceNotCopied() {
        Vector<String> props = new Vector<String>();
        props.add("initial");
        ExportData data = new ExportData(Integer.valueOf(1), "n", "png", props);

        // Mutating the caller's Vector after construction is visible through
        // the getter: the class stores the reference, not a snapshot.
        props.add("appended");

        Vector<String> returned = data.getExportProperties();
        assertSame(props, returned);
        assertTrue(returned.contains("appended"));
        assertEquals(2, returned.size());
    }

    @Test
    public void exportPropertiesGetterReturnsTheLiveInstalledVector() {
        ExportData data = new ExportData();
        Vector<String> props = new Vector<String>();
        data.setExportProperties(props);

        // The very same object comes back out, and later mutations show up.
        props.add("late");
        assertSame(props, data.getExportProperties());
        assertEquals(1, data.getExportProperties().size());
    }
}
