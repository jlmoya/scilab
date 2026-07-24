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

package org.scilab.modules.xcos.configuration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB value class {@link SettingType}.
 *
 * <p>
 * {@code SettingType} exposes two {@link List}&lt;{@link DocumentType}&gt;
 * properties, {@code recent} and {@code tab}. The generated accessors have no
 * matching setters; instead each getter lazily allocates the backing list on
 * first access and returns the <em>live</em> list on every subsequent call
 * (the documented JAXB idiom). These tests pin that lazy-init and
 * live-list contract.
 */
public class SettingTypeTest {

    @Test
    public void getRecentIsNeverNull() {
        assertNotNull(new SettingType().getRecent());
    }

    @Test
    public void getTabIsNeverNull() {
        assertNotNull(new SettingType().getTab());
    }

    @Test
    public void getRecentStartsEmpty() {
        assertTrue(new SettingType().getRecent().isEmpty());
    }

    @Test
    public void getTabStartsEmpty() {
        assertTrue(new SettingType().getTab().isEmpty());
    }

    @Test
    public void getRecentReturnsTheSameLiveListAcrossCalls() {
        SettingType s = new SettingType();
        List<DocumentType> first = s.getRecent();
        List<DocumentType> second = s.getRecent();
        assertSame(first, second);
    }

    @Test
    public void getTabReturnsTheSameLiveListAcrossCalls() {
        SettingType s = new SettingType();
        assertSame(s.getTab(), s.getTab());
    }

    @Test
    public void mutationsThroughGetRecentArePersisted() {
        SettingType s = new SettingType();
        DocumentType d = new DocumentType();

        s.getRecent().add(d);

        assertEquals(1, s.getRecent().size());
        assertSame(d, s.getRecent().get(0));
    }

    @Test
    public void mutationsThroughGetTabArePersisted() {
        SettingType s = new SettingType();
        DocumentType d = new DocumentType();

        s.getTab().add(d);

        assertEquals(1, s.getTab().size());
        assertSame(d, s.getTab().get(0));
    }

    @Test
    public void recentAndTabAreDistinctIndependentLists() {
        SettingType s = new SettingType();

        s.getRecent().add(new DocumentType());

        assertNotSame(s.getRecent(), s.getTab());
        assertEquals(1, s.getRecent().size());
        assertTrue(s.getTab().isEmpty());
    }
}
