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

package org.scilab.modules.xcos.modelica.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB data-binding class {@link ModelicaValue}.
 *
 * <p>{@code ModelicaValue} wraps a single required {@code String} attribute
 * whose field is initialized to the empty string. No native runtime is required.
 */
public class ModelicaValueTest {

    /**
     * The field is initialized to {@code ""} (not {@code null}). The source
     * comment states "A non-null value is required by xml2modelica", so the
     * empty-string default is deliberate and must be preserved.
     */
    @Test
    public void defaultValueIsEmptyStringNotNull() {
        ModelicaValue mv = new ModelicaValue();

        assertNotNull(mv.getValue(), "default must not be null");
        assertEquals("", mv.getValue());
    }

    @Test
    public void setValueRoundTrips() {
        ModelicaValue mv = new ModelicaValue();

        mv.setValue("x = 1.0");

        assertEquals("x = 1.0", mv.getValue());
    }

    @Test
    public void setValueOverwritesPreviousValue() {
        ModelicaValue mv = new ModelicaValue();

        mv.setValue("first");
        mv.setValue("second");

        assertEquals("second", mv.getValue());
    }

    @Test
    public void emptyStringIsAValidExplicitValue() {
        ModelicaValue mv = new ModelicaValue();
        mv.setValue("non-empty");

        mv.setValue("");

        assertEquals("", mv.getValue());
    }

    /**
     * Whitespace and unicode content is stored verbatim (no trimming/escaping
     * happens at the model layer).
     */
    @Test
    public void valueIsStoredVerbatim() {
        ModelicaValue mv = new ModelicaValue();
        String raw = "  der(x) = a*x + b   \n\té";

        mv.setValue(raw);

        assertEquals(raw, mv.getValue());
    }

    /**
     * Defect characterization: despite the {@code @XmlAttribute(required = true)}
     * contract and the "non-null required" comment, the setter performs no
     * null-check and will overwrite the safe empty-string default with null.
     */
    @Test
    public void setValueAcceptsNullDespiteRequiredContract_defectCharacterization() {
        ModelicaValue mv = new ModelicaValue();

        mv.setValue(null);

        assertNull(mv.getValue());
    }

    /**
     * Behavior characterization: {@code ModelicaValue} does not override
     * {@code equals}/{@code hashCode}; equality is JVM identity even when the
     * wrapped strings are equal.
     */
    @Test
    public void equalsIsIdentityBased_defectCharacterization() {
        ModelicaValue a = new ModelicaValue();
        ModelicaValue b = new ModelicaValue();
        a.setValue("same");
        b.setValue("same");

        assertNotEquals(a, b, "no value semantics: distinct instances must not be equal");
        assertEquals(a, a);
        assertSame(a, a);
        assertEquals(a.hashCode(), a.hashCode(), "hashCode must be stable across calls");
    }
}
