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

package org.scilab.modules.xcos.configuration.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import javax.xml.datatype.DatatypeFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.xcos.configuration.model.DocumentType;

/**
 * Hermetic unit tests for {@link ConfigurationConstants}: the two
 * {@link java.util.Comparator}s it exposes over {@link DocumentType} and its
 * public property-name constant.
 *
 * <p>
 * The comparators are the only real behaviour here; the date one leans on
 * {@link javax.xml.datatype.XMLGregorianCalendar#compare}, which is a JDK
 * ({@code java.xml}) facility, so nothing native is required.
 */
public class ConfigurationConstantsTest {

    private static DocumentType docWithDate(String isoDateTime) throws Exception {
        DocumentType d = new DocumentType();
        d.setDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(isoDateTime));
        return d;
    }

    private static DocumentType docWithUrl(String url) {
        DocumentType d = new DocumentType();
        d.setUrl(url);
        return d;
    }

    // ---- RECENT_FILES_CHANGED --------------------------------------------

    @Test
    public void recentFilesChangedPropertyNameIsStable() {
        assertEquals("recentFilesHasChanged", ConfigurationConstants.RECENT_FILES_CHANGED);
    }

    // ---- DATE_COMPARATOR -------------------------------------------------

    @Test
    public void dateComparatorOrdersEarlierBeforeLater() throws Exception {
        DocumentType early = docWithDate("2011-12-01T15:54:28");
        DocumentType late = docWithDate("2020-01-01T00:00:00");

        assertTrue(ConfigurationConstants.DATE_COMPARATOR.compare(early, late) < 0);
        assertTrue(ConfigurationConstants.DATE_COMPARATOR.compare(late, early) > 0);
    }

    @Test
    public void dateComparatorReturnsZeroForEqualDates() throws Exception {
        DocumentType a = docWithDate("2011-12-01T15:54:28");
        DocumentType b = docWithDate("2011-12-01T15:54:28");

        assertEquals(0, ConfigurationConstants.DATE_COMPARATOR.compare(a, b));
    }

    @Test
    public void dateComparatorIsAntisymmetric() throws Exception {
        DocumentType a = docWithDate("2011-12-01T15:54:28");
        DocumentType b = docWithDate("2020-01-01T00:00:00");

        int ab = ConfigurationConstants.DATE_COMPARATOR.compare(a, b);
        int ba = ConfigurationConstants.DATE_COMPARATOR.compare(b, a);

        assertEquals(Integer.signum(ab), -Integer.signum(ba));
    }

    /**
     * Defect characterization: {@code DATE_COMPARATOR} dereferences
     * {@code getDate()} with no null guard, so a {@link DocumentType} whose
     * date was never set makes it throw rather than ordering the element.
     */
    @Test
    public void dateComparatorThrowsOnNullDate_defectCharacterization() throws Exception {
        DocumentType withDate = docWithDate("2011-12-01T15:54:28");
        DocumentType noDate = new DocumentType();

        assertThrows(NullPointerException.class,
            () -> ConfigurationConstants.DATE_COMPARATOR.compare(noDate, withDate));
    }

    // ---- FILENAME_COMPARATOR ---------------------------------------------

    @Test
    public void filenameComparatorOrdersLexicographically() {
        DocumentType a = docWithUrl("a.xcos");
        DocumentType b = docWithUrl("b.xcos");

        assertTrue(ConfigurationConstants.FILENAME_COMPARATOR.compare(a, b) < 0);
        assertTrue(ConfigurationConstants.FILENAME_COMPARATOR.compare(b, a) > 0);
    }

    @Test
    public void filenameComparatorReturnsZeroForEqualUrls() {
        assertEquals(0, ConfigurationConstants.FILENAME_COMPARATOR.compare(
                           docWithUrl("same.xcos"), docWithUrl("same.xcos")));
    }

    @Test
    public void filenameComparatorMatchesStringCompareToSign() {
        DocumentType a = docWithUrl("alpha");
        DocumentType b = docWithUrl("beta");

        assertEquals(Integer.signum("alpha".compareTo("beta")),
                     Integer.signum(ConfigurationConstants.FILENAME_COMPARATOR.compare(a, b)));
    }

    /**
     * Defect characterization: {@code FILENAME_COMPARATOR} dereferences
     * {@code getUrl()} with no null guard.
     */
    @Test
    public void filenameComparatorThrowsOnNullUrl_defectCharacterization() {
        assertThrows(NullPointerException.class,
            () -> ConfigurationConstants.FILENAME_COMPARATOR.compare(
                      new DocumentType(), docWithUrl("b.xcos")));
    }

    // ---- static-utility-class design -------------------------------------

    @Test
    public void classIsFinal() {
        assertTrue(Modifier.isFinal(ConfigurationConstants.class.getModifiers()));
    }

    @Test
    public void theSoleConstructorIsPrivate() {
        Constructor<?>[] ctors = ConfigurationConstants.class.getDeclaredConstructors();

        assertEquals(1, ctors.length);
        assertEquals(0, ctors[0].getParameterCount());
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()));
    }
}
