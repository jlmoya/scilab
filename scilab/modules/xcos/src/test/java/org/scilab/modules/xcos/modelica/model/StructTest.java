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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB data-binding class {@link Struct} and its
 * nested {@link Struct.Subnodes} type. No native runtime is required.
 */
public class StructTest {

    @Test
    public void newStructHasNullNameAndSubnodes() {
        Struct struct = new Struct();

        assertNull(struct.getName());
        assertNull(struct.getSubnodes());
    }

    @Test
    public void nameRoundTrips() {
        Struct struct = new Struct();

        struct.setName("motor");

        assertEquals("motor", struct.getName());
    }

    @Test
    public void subnodesRoundTripsPreservingIdentity() {
        Struct struct = new Struct();
        Struct.Subnodes subnodes = new Struct.Subnodes();

        struct.setSubnodes(subnodes);

        assertSame(subnodes, struct.getSubnodes());
    }

    @Test
    public void settersAcceptNullClearingValues() {
        Struct struct = new Struct();
        struct.setName("motor");
        struct.setSubnodes(new Struct.Subnodes());

        struct.setName(null);
        struct.setSubnodes(null);

        assertNull(struct.getName());
        assertNull(struct.getSubnodes());
    }

    /**
     * The {@code structOrTerminal} accessor lazily initializes the backing list:
     * it is never null and starts empty.
     */
    @Test
    public void subnodesListIsLazilyInitializedAndEmpty() {
        Struct.Subnodes subnodes = new Struct.Subnodes();

        List<Object> children = subnodes.getStructOrTerminal();

        assertNotNull(children);
        assertTrue(children.isEmpty());
    }

    /**
     * The accessor returns the same live list on each call, so mutations made
     * through a previously-returned reference are visible on the next call
     * (there is intentionally no setter for this property).
     */
    @Test
    public void subnodesGetterReturnsSameLiveListAndMutationsPersist() {
        Struct.Subnodes subnodes = new Struct.Subnodes();

        List<Object> first = subnodes.getStructOrTerminal();
        first.add(new Struct());
        List<Object> second = subnodes.getStructOrTerminal();

        assertSame(first, second, "getter must expose the same live list instance");
        assertEquals(1, second.size());
    }

    /**
     * The subnode list is heterogeneous: per the schema choice it holds both
     * {@link Struct} and {@link Terminal} children, preserving insertion order.
     */
    @Test
    public void subnodesListHoldsBothStructAndTerminalInOrder() {
        Struct.Subnodes subnodes = new Struct.Subnodes();
        Struct childStruct = new Struct();
        Terminal childTerminal = new Terminal();

        subnodes.getStructOrTerminal().add(childStruct);
        subnodes.getStructOrTerminal().add(childTerminal);

        List<Object> children = subnodes.getStructOrTerminal();
        assertEquals(2, children.size());
        assertSame(childStruct, children.get(0));
        assertSame(childTerminal, children.get(1));
    }

    /**
     * Defect characterization: the backing list is declared {@code List<Object>}
     * with no runtime type guard, so it accepts values that are neither a
     * {@code Struct} nor a {@code Terminal}. Type-safety is enforced only by the
     * JAXB binding at (un)marshal time, not by this container.
     */
    @Test
    public void subnodesListAcceptsArbitraryObject_defectCharacterization() {
        Struct.Subnodes subnodes = new Struct.Subnodes();

        subnodes.getStructOrTerminal().add("not a node");

        assertEquals(1, subnodes.getStructOrTerminal().size());
        assertEquals("not a node", subnodes.getStructOrTerminal().get(0));
    }

    /**
     * A recursive tree (Struct whose subnode is another Struct) can be assembled
     * and navigated, matching the "each node must not be a leaf" data model.
     */
    @Test
    public void nestedStructTreeIsNavigable() {
        Struct root = new Struct();
        root.setName("root");
        Struct.Subnodes subnodes = new Struct.Subnodes();
        Struct leaf = new Struct();
        leaf.setName("child");
        subnodes.getStructOrTerminal().add(leaf);
        root.setSubnodes(subnodes);

        Object firstChild = root.getSubnodes().getStructOrTerminal().get(0);
        assertSame(leaf, firstChild);
        assertEquals("child", ((Struct) firstChild).getName());
    }
}
