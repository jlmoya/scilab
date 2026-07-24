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
package org.scilab.modules.xcos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ObjectProperties}.
 *
 * <p>{@code ObjectProperties} is a SWIG-generated enum mirroring the native C++
 * property enumeration. The Java code passes an {@code ObjectProperties} across
 * JNI to the model controller (e.g.
 * {@code JavaController.getObjectProperty(uid, kind, ObjectProperties.NAME, ...)})
 * where its <em>ordinal</em> selects a slot in the native model. Therefore:</p>
 *
 * <ul>
 *   <li>The declaration order (and hence every ordinal) is a wire contract.</li>
 *   <li>{@code MAX_OBJECT_PROPERTIES} is the trailing sentinel whose ordinal
 *       equals the number of real properties &mdash; it is used as an array
 *       bound on the native side.</li>
 * </ul>
 *
 * <p>The assertions below pin the current contract as characterization tests: a
 * change to the property list (add / remove / reorder) will deliberately fail
 * one of these, flagging that the native counterpart must be kept in sync.
 * Pure Java; no native library is loaded.</p>
 */
public class ObjectPropertiesTest {

    /** Current size of the enum, including the trailing sentinel. */
    private static final int EXPECTED_COUNT = 70;

    @Test
    @DisplayName("the enum declares exactly the expected number of constants")
    public void enumSizeIsPinned() {
        assertEquals(EXPECTED_COUNT, ObjectProperties.values().length);
    }

    @Test
    @DisplayName("MAX_OBJECT_PROPERTIES is the trailing sentinel")
    public void maxIsTheLastConstant() {
        ObjectProperties[] values = ObjectProperties.values();
        assertSame(ObjectProperties.MAX_OBJECT_PROPERTIES, values[values.length - 1],
                   "MAX_OBJECT_PROPERTIES must be declared last");
    }

    @Test
    @DisplayName("MAX_OBJECT_PROPERTIES.ordinal() equals the count of real properties")
    public void maxOrdinalEqualsRealPropertyCount() {
        // The sentinel's ordinal is the highest index, i.e. size - 1, and it
        // equals the number of genuine properties that precede it.
        assertEquals(ObjectProperties.values().length - 1,
                     ObjectProperties.MAX_OBJECT_PROPERTIES.ordinal());
        assertEquals(EXPECTED_COUNT - 1, ObjectProperties.MAX_OBJECT_PROPERTIES.ordinal());
    }

    @Test
    @DisplayName("selected ordinals pin the JNI wire contract")
    public void selectedOrdinalsArePinned() {
        // A spread across the whole enum so a reorder anywhere is caught.
        assertEquals(0, ObjectProperties.AUTHOR.ordinal());
        assertEquals(1, ObjectProperties.CHILDREN.ordinal());
        assertEquals(2, ObjectProperties.COLOR.ordinal());
        assertEquals(9, ObjectProperties.DATATYPE.ordinal());
        assertEquals(32, ObjectProperties.KIND.ordinal());
        assertEquals(35, ObjectProperties.NAME.ordinal());
        assertEquals(50, ObjectProperties.PORT_KIND.ordinal());
        assertEquals(65, ObjectProperties.STYLE.ordinal());
        assertEquals(67, ObjectProperties.UID.ordinal());
        assertEquals(68, ObjectProperties.VERSION_NUMBER.ordinal());
        assertEquals(69, ObjectProperties.MAX_OBJECT_PROPERTIES.ordinal());
    }

    @Test
    @DisplayName("values()[i].ordinal() == i for every constant")
    public void ordinalEqualsArrayIndex() {
        ObjectProperties[] values = ObjectProperties.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].ordinal(), "ordinal mismatch at index " + i);
        }
    }

    @Test
    @DisplayName("all constant names are unique")
    public void namesAreUnique() {
        Set<String> names = new HashSet<>();
        for (ObjectProperties p : ObjectProperties.values()) {
            assertTrue(names.add(p.name()), "duplicate name: " + p.name());
        }
        assertEquals(ObjectProperties.values().length, names.size());
    }

    @Test
    @DisplayName("valueOf round-trips with name() for every constant")
    public void valueOfRoundTrips() {
        for (ObjectProperties p : ObjectProperties.values()) {
            assertSame(p, ObjectProperties.valueOf(p.name()));
        }
    }

    @Test
    @DisplayName("valueOf of an unknown name throws IllegalArgumentException")
    public void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> ObjectProperties.valueOf("NOPE"));
        assertThrows(IllegalArgumentException.class, () -> ObjectProperties.valueOf("author"));
    }

    @Test
    @DisplayName("valueOf(null) throws NullPointerException")
    public void valueOfNullThrows() {
        assertThrows(NullPointerException.class, () -> ObjectProperties.valueOf(null));
    }

    @Test
    @DisplayName("distinct-but-similar names are separate constants")
    public void similarlyNamedConstantsAreDistinct() {
        // DATATYPE and its qualified siblings must not collapse.
        assertNotSame(ObjectProperties.DATATYPE, ObjectProperties.DATATYPE_TYPE);
        assertNotSame(ObjectProperties.DATATYPE, ObjectProperties.DATATYPE_ROWS);
        assertNotSame(ObjectProperties.DATATYPE, ObjectProperties.DATATYPE_COLS);
        // FONT vs FONT_SIZE
        assertNotSame(ObjectProperties.FONT, ObjectProperties.FONT_SIZE);
        // The source/destination pairing used by links.
        assertNotSame(ObjectProperties.SOURCE_PORT, ObjectProperties.DESTINATION_PORT);
        assertNotSame(ObjectProperties.SOURCE_BLOCK, ObjectProperties.PARENT_BLOCK);
    }

    @Test
    @DisplayName("EnumSet.allOf sees the whole enum with no gaps")
    public void enumSetCoversEverything() {
        EnumSet<ObjectProperties> all = EnumSet.allOf(ObjectProperties.class);
        assertEquals(ObjectProperties.values().length, all.size());
        assertTrue(all.contains(ObjectProperties.AUTHOR));
        assertTrue(all.contains(ObjectProperties.MAX_OBJECT_PROPERTIES));
    }

    @Test
    @DisplayName("values() hands back a fresh defensive copy each call")
    public void valuesReturnsDefensiveCopy() {
        ObjectProperties[] first = ObjectProperties.values();
        assertNotSame(first, ObjectProperties.values(), "values() must not leak a shared array");
        first[0] = ObjectProperties.UID;
        assertSame(ObjectProperties.AUTHOR, ObjectProperties.values()[0]);
    }
}
