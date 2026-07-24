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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB data-binding POJO {@link Terminal} (a leaf
 * node of a Modelica {@code Struct} tree) and its empty nested marker type
 * {@link Terminal.Output}.
 *
 * <p>{@code Terminal} is a plain generated bean: three {@code String} columns
 * (name / kind / id), eight {@link ModelicaValue} columns and one
 * {@code Output} marker, all exposed through straight getter/setter pairs with
 * no defaulting. These tests pin that contract: fresh instances are entirely
 * null, every setter is observed by its matching getter, fields are mutually
 * independent, and null round-trips cleanly.
 */
public class TerminalTest {

    @Test
    public void freshTerminalHasAllPropertiesNull() {
        Terminal t = new Terminal();

        assertNull(t.getName());
        assertNull(t.getKind());
        assertNull(t.getId());
        assertNull(t.getFixed());
        assertNull(t.getInitialValue());
        assertNull(t.getWeight());
        assertNull(t.getMax());
        assertNull(t.getMin());
        assertNull(t.getNominalValue());
        assertNull(t.getComment());
        assertNull(t.getSelected());
        assertNull(t.getOutput());
    }

    @Test
    public void nameRoundTrips() {
        Terminal t = new Terminal();
        t.setName("theta");
        assertSame("theta", t.getName());
    }

    @Test
    public void kindRoundTrips() {
        Terminal t = new Terminal();
        t.setKind("variable");
        assertSame("variable", t.getKind());
    }

    @Test
    public void idRoundTrips() {
        Terminal t = new Terminal();
        t.setId("id-42");
        assertSame("id-42", t.getId());
    }

    @Test
    public void fixedRoundTrips() {
        Terminal t = new Terminal();
        ModelicaValue v = new ModelicaValue();
        t.setFixed(v);
        assertSame(v, t.getFixed());
    }

    @Test
    public void initialValueRoundTrips() {
        Terminal t = new Terminal();
        ModelicaValue v = new ModelicaValue();
        t.setInitialValue(v);
        assertSame(v, t.getInitialValue());
    }

    @Test
    public void weightRoundTrips() {
        Terminal t = new Terminal();
        ModelicaValue v = new ModelicaValue();
        t.setWeight(v);
        assertSame(v, t.getWeight());
    }

    @Test
    public void maxRoundTrips() {
        Terminal t = new Terminal();
        ModelicaValue v = new ModelicaValue();
        t.setMax(v);
        assertSame(v, t.getMax());
    }

    @Test
    public void minRoundTrips() {
        Terminal t = new Terminal();
        ModelicaValue v = new ModelicaValue();
        t.setMin(v);
        assertSame(v, t.getMin());
    }

    @Test
    public void nominalValueRoundTrips() {
        Terminal t = new Terminal();
        ModelicaValue v = new ModelicaValue();
        t.setNominalValue(v);
        assertSame(v, t.getNominalValue());
    }

    @Test
    public void commentRoundTrips() {
        Terminal t = new Terminal();
        ModelicaValue v = new ModelicaValue();
        t.setComment(v);
        assertSame(v, t.getComment());
    }

    @Test
    public void selectedRoundTrips() {
        Terminal t = new Terminal();
        ModelicaValue v = new ModelicaValue();
        t.setSelected(v);
        assertSame(v, t.getSelected());
    }

    @Test
    public void outputRoundTrips() {
        Terminal t = new Terminal();
        Terminal.Output out = new Terminal.Output();
        t.setOutput(out);
        assertSame(out, t.getOutput());
    }

    @Test
    public void settersAcceptNullToClearAValue() {
        Terminal t = new Terminal();
        t.setName("x");
        t.setName(null);
        assertNull(t.getName());

        t.setFixed(new ModelicaValue());
        t.setFixed(null);
        assertNull(t.getFixed());
    }

    @Test
    public void settingOneModelicaValuePropertyDoesNotDisturbTheOthers() {
        Terminal t = new Terminal();
        ModelicaValue fixed = new ModelicaValue();
        ModelicaValue weight = new ModelicaValue();

        t.setFixed(fixed);
        t.setWeight(weight);

        assertSame(fixed, t.getFixed());
        assertSame(weight, t.getWeight());
        // the eight ModelicaValue slots are distinct storage locations
        assertNotSame(t.getFixed(), t.getWeight());
        assertNull(t.getInitialValue());
        assertNull(t.getMax());
        assertNull(t.getMin());
        assertNull(t.getNominalValue());
        assertNull(t.getComment());
        assertNull(t.getSelected());
    }

    @Test
    public void stringColumnsAreIndependent() {
        Terminal t = new Terminal();
        t.setName("n");
        t.setKind("k");
        t.setId("i");

        assertSame("n", t.getName());
        assertSame("k", t.getKind());
        assertSame("i", t.getId());
    }

    @Test
    public void setterOverwritesPreviousValue() {
        Terminal t = new Terminal();
        ModelicaValue first = new ModelicaValue();
        ModelicaValue second = new ModelicaValue();

        t.setInitialValue(first);
        assertSame(first, t.getInitialValue());
        t.setInitialValue(second);
        assertSame(second, t.getInitialValue());
    }

    @Test
    public void outputInstancesHaveIdentitySemantics() {
        // Output carries no state, so two instances are never equal.
        Terminal.Output a = new Terminal.Output();
        Terminal.Output b = new Terminal.Output();
        assertNotSame(a, b);
        assertSame(a, a);
    }
}
