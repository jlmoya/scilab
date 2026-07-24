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

package org.scilab.modules.helptools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link TemplateLocalization#getLocalized(String, String)}.
 *
 * <p>The lookup table is a tiny hard-coded map (currently a single key,
 * "Report an issue"). These tests pin its four decision branches: the en_US
 * short-circuit, an unknown key, an unknown language, and a real translation —
 * plus a defect-characterization test documenting the placeholder-looking
 * pt_BR / es_ES strings that currently ship.
 */
public class TemplateLocalizationTest {

    private static final String KEY = "Report an issue";

    @Test
    public void enUsAlwaysReturnsTheKeyVerbatim() {
        // en_US short-circuits before the map is even consulted.
        assertEquals(KEY, TemplateLocalization.getLocalized("en_US", KEY));
        assertEquals("anything at all", TemplateLocalization.getLocalized("en_US", "anything at all"));
    }

    @Test
    public void knownKeyReturnsTranslationForKnownLanguage() {
        assertEquals("Signaler un problème", TemplateLocalization.getLocalized("fr_FR", KEY));
        assertEquals("問題を報告", TemplateLocalization.getLocalized("ja_JP", KEY));
        assertEquals("Сообщить об ошибке", TemplateLocalization.getLocalized("ru_RU", KEY));
    }

    @Test
    public void unknownKeyFallsBackToTheKeyItself() {
        assertEquals("Not a known label",
                     TemplateLocalization.getLocalized("fr_FR", "Not a known label"));
    }

    @Test
    public void unknownLanguageFallsBackToTheKey() {
        // Key is known, but there is no de_DE entry => the original string is returned.
        assertEquals(KEY, TemplateLocalization.getLocalized("de_DE", KEY));
    }

    @Test
    public void documentsCurrentPlaceholderTranslations() {
        // Defect characterization: these two ship as obvious placeholders today.
        assertEquals("Reportero a bugo", TemplateLocalization.getLocalized("pt_BR", KEY));
        assertEquals("Raportare el bugo", TemplateLocalization.getLocalized("es_ES", KEY));
    }
}
