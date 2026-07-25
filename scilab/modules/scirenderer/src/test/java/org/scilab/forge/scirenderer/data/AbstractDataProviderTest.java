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

package org.scilab.forge.scirenderer.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hermetic unit tests for {@link AbstractDataProvider}: its data-user
 * registry ({@code addDataUser}/{@code removeDataUser}/{@code fireUpdate}) and
 * the {@code toByte} colour-component quantisers.
 *
 * A tiny concrete subclass exposes the {@code protected} members under test.
 */
public class AbstractDataProviderTest {

    /** Records how many times it was notified. */
    private static final class CountingUser implements DataUser {
        int updates;
        @Override
        public void dataUpdated() {
            updates++;
        }
    }

    /** Concrete provider exposing the protected surface for testing. */
    private static final class TestProvider extends AbstractDataProvider<DataUser> {
        @Override
        public boolean isValid() {
            return true;
        }
        byte call(double v) {
            return toByte(v);
        }
        byte[] call(float[] v) {
            return toByte(v);
        }
        void notifyUsers() {
            fireUpdate();
        }
    }

    @Test
    public void toByteMapsTheUnitIntervalToByteRange() {
        TestProvider p = new TestProvider();
        assertEquals((byte) 0x00, p.call(0.0));
        assertEquals((byte) 0x7F, p.call(0.5));     // (int) 127.5 == 127
        assertEquals((byte) 0xFF, p.call(1.0));     // (int) 255   == 0xFF
    }

    @Test
    public void toByteTruncatesTowardZero() {
        TestProvider p = new TestProvider();
        // 0.999 * 255 == 254.7 -> truncated to 254.
        assertEquals((byte) 254, p.call(0.999));
    }

    @Test
    public void toByteWrapsForOutOfRangeInputs() {
        // Defect characterization: values outside [0, 1] are masked with 0xFF and
        // therefore wrap rather than clamp.
        TestProvider p = new TestProvider();
        // 2.0 * 255 == 510 -> (510 & 0xFF) == 254.
        assertEquals((byte) 254, p.call(2.0));
        // -1.0 * 255 == -255 -> (-255 & 0xFF) == 1.
        assertEquals((byte) 1, p.call(-1.0));
    }

    @Test
    public void toByteArrayQuantisesEachComponent() {
        TestProvider p = new TestProvider();
        assertArrayEquals(new byte[] {0x00, (byte) 0xFF, 0x7F},
                          p.call(new float[] {0f, 1f, 0.5f}));
    }

    @Test
    public void fireUpdateNotifiesRegisteredUsers() {
        TestProvider p = new TestProvider();
        CountingUser user = new CountingUser();
        p.addDataUser(user);
        p.notifyUsers();
        assertEquals(1, user.updates);
    }

    @Test
    public void registeringTheSameUserTwiceNotifiesItOnce() {
        // Backed by a Set, so a duplicate registration collapses.
        TestProvider p = new TestProvider();
        CountingUser user = new CountingUser();
        p.addDataUser(user);
        p.addDataUser(user);
        p.notifyUsers();
        assertEquals(1, user.updates);
    }

    @Test
    public void removedUsersAreNoLongerNotified() {
        TestProvider p = new TestProvider();
        CountingUser user = new CountingUser();
        p.addDataUser(user);
        p.removeDataUser(user);
        p.notifyUsers();
        assertEquals(0, user.updates);
    }
}
