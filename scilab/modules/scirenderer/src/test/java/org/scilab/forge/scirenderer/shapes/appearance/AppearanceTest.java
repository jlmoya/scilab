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

package org.scilab.forge.scirenderer.shapes.appearance;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.texture.Texture;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hermetic unit tests for {@link Appearance}, the mutable line/fill style holder.
 * The texture-dependent path is exercised through a tiny dynamic-proxy {@link Texture}
 * whose {@code isValid()} answer is controllable.
 */
public class AppearanceTest {

    /** Build a Texture stub whose isValid() returns the given value; other calls yield defaults. */
    private static Texture texture(final boolean valid) {
        return (Texture) Proxy.newProxyInstance(
                   AppearanceTest.class.getClassLoader(),
                   new Class<?>[] {Texture.class},
        (proxy, method, args) -> {
            if ("isValid".equals(method.getName())) {
                return valid;
            }
            Class<?> rt = method.getReturnType();
            if (rt == boolean.class) {
                return false;
            }
            return null;
        });
    }

    @Test
    public void defaultsMatchThePublishedConstants() {
        Appearance a = new Appearance();
        assertEquals(Appearance.DEFAULT_LINE_WIDTH, a.getLineWidth(), 0f);
        assertEquals(1.0f, Appearance.DEFAULT_LINE_WIDTH, 0f);
        assertEquals(Appearance.DEFAULT_LINE_PATTERN, a.getLinePattern());
        assertEquals((short) 0xFFFF, a.getLinePattern());
        assertSame(Appearance.DEFAULT_LINE_COLOR, a.getLineColor());
        assertSame(Appearance.DEFAULT_FILL_COLOR, a.getFillColor());
    }

    @Test
    public void getDefaultReturnsAFreshInstance() {
        assertNotNull(Appearance.getDefault());
    }

    @Test
    public void noTextureByDefault() {
        assertNull(new Appearance().getTexture());
    }

    @Test
    public void lineWidthRoundTrips() {
        Appearance a = new Appearance();
        a.setLineWidth(3.5f);
        assertEquals(3.5f, a.getLineWidth(), 0f);
    }

    @Test
    public void linePatternRoundTrips() {
        Appearance a = new Appearance();
        a.setLinePattern((short) 0x00FF);
        assertEquals((short) 0x00FF, a.getLinePattern());
    }

    @Test
    public void lineAndFillColorRoundTrip() {
        Appearance a = new Appearance();
        Color line = new Color(0.1f, 0.2f, 0.3f);
        Color fill = new Color(0.4f, 0.5f, 0.6f);
        a.setLineColor(line);
        a.setFillColor(fill);
        assertSame(line, a.getLineColor());
        assertSame(fill, a.getFillColor());
    }

    @Test
    public void materialRoundTrips() {
        Appearance a = new Appearance();
        assertNull(a.getMaterial());
        Material m = new Material();
        a.setMaterial(m);
        assertSame(m, a.getMaterial());
    }

    @Test
    public void aValidTextureIsReturned() {
        Appearance a = new Appearance();
        Texture valid = texture(true);
        a.setTexture(valid);
        assertSame(valid, a.getTexture());
    }

    @Test
    public void anInvalidTextureIsTreatedAsAbsent() {
        Appearance a = new Appearance();
        a.setTexture(texture(false));
        assertNull(a.getTexture(), "getTexture filters out textures reporting isValid()==false");
    }
}
