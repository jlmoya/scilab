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

package org.scilab.modules.preferences.Component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link NumericalSpinner} preference component (a
 * {@link javax.swing.JSpinner} backed by a {@code SpinnerNumberModel}). The
 * bounds/value/increment/length actuators and the change-to-action bridge are
 * pure Swing model manipulation; no native code or display is required.
 */
public class NumericalSpinnerTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorAppliesBoundsValueAndIncrement() throws Exception {
        NumericalSpinner c = new NumericalSpinner(
            el("NumericalSpinner", "min-value", "-5", "max-value", "5",
               "value", "2", "increment", "0.5", "length", "4"));
        assertEquals("2.0", c.value());
        assertEquals("-5.0", c.minvalue());
        assertEquals("5.0", c.maxvalue());
        assertEquals("0.5", c.increment());
        assertEquals("4", c.length());
    }

    @Test
    public void defaultsAreUnboundedWithUnitStep() throws Exception {
        NumericalSpinner c = new NumericalSpinner(el("NumericalSpinner"));
        assertEquals("0.0", c.value());
        assertEquals("1.0", c.increment());
        assertEquals("-Infinity", c.minvalue());
        assertEquals("Infinity", c.maxvalue());
    }

    @Test
    public void valueActuatorAcceptsAParsableNumber() throws Exception {
        NumericalSpinner c = new NumericalSpinner(el("NumericalSpinner"));
        c.value("3");
        assertEquals("3.0", c.value());
        assertEquals("3.0", c.choose(), "choose() surfaces the current value");
    }

    /**
     * Defect characterization: {@code value(String)} calls {@code new Double(value)}
     * with <em>no</em> try/catch, unlike the {@code min-value}/{@code max-value}/
     * {@code increment} actuators which swallow {@code NumberFormatException}. So an
     * unparsable value propagates the exception rather than being ignored.
     */
    @Test
    public void valueActuatorThrowsOnUnparsableInputUnlikeTheOthers() throws Exception {
        NumericalSpinner c = new NumericalSpinner(el("NumericalSpinner"));
        assertThrows(NumberFormatException.class, () -> c.value("abc"));
    }

    @Test
    public void incrementActuatorSilentlyIgnoresGarbage() throws Exception {
        NumericalSpinner c = new NumericalSpinner(el("NumericalSpinner"));
        c.increment("not-a-number");
        assertEquals("1.0", c.increment(), "the increment actuator catches the format error and keeps the step");
    }

    @Test
    public void modelChangeIsForwardedToTheRegisteredActionListener() throws Exception {
        NumericalSpinner c = new NumericalSpinner(el("NumericalSpinner"));
        AtomicInteger fired = new AtomicInteger();
        c.addActionListener(e -> fired.incrementAndGet());
        c.setValue(Double.valueOf(7));
        assertTrue(fired.get() >= 1, "stateChanged bridges the spinner change to an ActionEvent");
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"length", "increment", "min-value", "max-value", "value", "tooltip", "enable"},
                          new NumericalSpinner(el("NumericalSpinner")).actuators());
    }
}
