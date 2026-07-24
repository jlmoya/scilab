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
package org.scilab.modules.xcos.graph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.scilab.modules.xcos.block.AfficheBlock;
import org.scilab.modules.xcos.block.BasicBlock;
import org.scilab.modules.xcos.block.SplitBlock;
import org.scilab.modules.xcos.block.SuperBlock;
import org.scilab.modules.xcos.block.TextBlock;
import org.scilab.modules.xcos.block.custom.BigSom;
import org.scilab.modules.xcos.block.custom.GroundBlock;
import org.scilab.modules.xcos.block.custom.Product;
import org.scilab.modules.xcos.block.custom.RoundBlock;
import org.scilab.modules.xcos.block.custom.Summation;
import org.scilab.modules.xcos.block.custom.VoltageSensorBlock;
import org.scilab.modules.xcos.block.io.EventInBlock;
import org.scilab.modules.xcos.block.io.EventOutBlock;
import org.scilab.modules.xcos.block.io.ExplicitInBlock;
import org.scilab.modules.xcos.block.io.ExplicitOutBlock;
import org.scilab.modules.xcos.block.io.ImplicitInBlock;
import org.scilab.modules.xcos.block.io.ImplicitOutBlock;

/**
 * Hermetic unit tests for {@link BlockInterFunction}.
 *
 * <p>{@code BlockInterFunction} maps each Scilab block interface-function name
 * (e.g. {@code SUM_f}) to the {@link BasicBlock} subclass that models it. This
 * test exercises only the enum metadata &mdash; {@link BlockInterFunction#getKlass()}
 * and the {@code Class} literals it returns. Referencing a {@code Class} literal
 * <em>loads and links</em> the target class but does <em>not</em> initialise it
 * (JLS 12.4.1), so no static initialiser or native code runs and no block is
 * ever instantiated. The tests are therefore hermetic and require no native
 * runtime.</p>
 *
 * <p>The enum's Javadoc states an ordering invariant: &ldquo;Specific instance
 * must be registered after the most generic one in order to [serialize] all the
 * non-default values.&rdquo; The most generic mapping is {@code BASIC_BLOCK ->
 * BasicBlock}, and the tests assert it is declared last and is the sole mapping
 * to the bare {@link BasicBlock} class.</p>
 */
public class BlockInterFunctionTest {

    /** Current number of registered interface functions. */
    private static final int EXPECTED_COUNT = 24;

    @Test
    @DisplayName("the expected number of interface functions is registered")
    public void enumSizeIsPinned() {
        assertEquals(EXPECTED_COUNT, BlockInterFunction.values().length);
    }

    @Test
    @DisplayName("every constant maps to a non-null BasicBlock subclass")
    public void everyConstantMapsToABasicBlockSubclass() {
        for (BlockInterFunction fn : BlockInterFunction.values()) {
            Class<? extends BasicBlock> klass = fn.getKlass();
            assertNotNull(klass, fn.name() + " must map to a class");
            assertTrue(BasicBlock.class.isAssignableFrom(klass),
                       fn.name() + " -> " + klass.getName() + " must be a BasicBlock");
        }
    }

    @Test
    @DisplayName("each interface function maps to exactly the expected block class")
    public void mappingsAreExact() {
        assertSame(TextBlock.class, BlockInterFunction.TEXT_f.getKlass());
        assertSame(SuperBlock.class, BlockInterFunction.SUPER_f.getKlass());
        assertSame(SuperBlock.class, BlockInterFunction.DSUPER.getKlass());
        assertSame(AfficheBlock.class, BlockInterFunction.AFFICH_m.getKlass());
        assertSame(ExplicitInBlock.class, BlockInterFunction.IN_f.getKlass());
        assertSame(ExplicitOutBlock.class, BlockInterFunction.OUT_f.getKlass());
        assertSame(ImplicitInBlock.class, BlockInterFunction.INIMPL_f.getKlass());
        assertSame(ImplicitOutBlock.class, BlockInterFunction.OUTIMPL_f.getKlass());
        assertSame(EventInBlock.class, BlockInterFunction.CLKINV_f.getKlass());
        assertSame(EventOutBlock.class, BlockInterFunction.CLKOUTV_f.getKlass());
        assertSame(EventOutBlock.class, BlockInterFunction.CLKOUT_f.getKlass());
        assertSame(SplitBlock.class, BlockInterFunction.SPLIT_f.getKlass());
        assertSame(SplitBlock.class, BlockInterFunction.IMPSPLIT_f.getKlass());
        assertSame(SplitBlock.class, BlockInterFunction.CLKSPLIT_f.getKlass());
        assertSame(GroundBlock.class, BlockInterFunction.Ground.getKlass());
        assertSame(VoltageSensorBlock.class, BlockInterFunction.VoltageSensor.getKlass());
        assertSame(RoundBlock.class, BlockInterFunction.SUM_f.getKlass());
        assertSame(RoundBlock.class, BlockInterFunction.PROD_f.getKlass());
        assertSame(RoundBlock.class, BlockInterFunction.CLKSOM_f.getKlass());
        assertSame(RoundBlock.class, BlockInterFunction.CLKSOMV_f.getKlass());
        assertSame(BigSom.class, BlockInterFunction.BIGSOM_f.getKlass());
        assertSame(Summation.class, BlockInterFunction.SUMMATION.getKlass());
        assertSame(Product.class, BlockInterFunction.PRODUCT.getKlass());
        assertSame(BasicBlock.class, BlockInterFunction.BASIC_BLOCK.getKlass());
    }

    @Test
    @DisplayName("interface functions sharing a block class agree on that class")
    public void sharedClassGroupsAreConsistent() {
        // Three split flavours -> one SplitBlock.
        assertSame(BlockInterFunction.SPLIT_f.getKlass(), BlockInterFunction.IMPSPLIT_f.getKlass());
        assertSame(BlockInterFunction.SPLIT_f.getKlass(), BlockInterFunction.CLKSPLIT_f.getKlass());
        // Four round blocks -> one RoundBlock.
        assertSame(BlockInterFunction.SUM_f.getKlass(), BlockInterFunction.PROD_f.getKlass());
        assertSame(BlockInterFunction.SUM_f.getKlass(), BlockInterFunction.CLKSOM_f.getKlass());
        assertSame(BlockInterFunction.SUM_f.getKlass(), BlockInterFunction.CLKSOMV_f.getKlass());
        // Two super-block flavours -> one SuperBlock.
        assertSame(BlockInterFunction.SUPER_f.getKlass(), BlockInterFunction.DSUPER.getKlass());
        // Two event-out flavours -> one EventOutBlock.
        assertSame(BlockInterFunction.CLKOUTV_f.getKlass(), BlockInterFunction.CLKOUT_f.getKlass());
    }

    @Test
    @DisplayName("BASIC_BLOCK is the most-generic mapping and is declared last")
    public void genericMappingIsRegisteredLast() {
        BlockInterFunction[] values = BlockInterFunction.values();
        assertSame(BlockInterFunction.BASIC_BLOCK, values[values.length - 1],
                   "the generic BASIC_BLOCK must be registered last");

        // ...and it is the *only* entry mapping to the bare BasicBlock class,
        // which is what makes "register the generic one last" well defined.
        for (BlockInterFunction fn : values) {
            if (fn.getKlass() == BasicBlock.class) {
                assertSame(BlockInterFunction.BASIC_BLOCK, fn,
                           "only BASIC_BLOCK may map to the bare BasicBlock class, found " + fn.name());
            }
        }
    }

    @Test
    @DisplayName("getKlass() returns a stable reference across calls")
    public void getKlassIsStable() {
        for (BlockInterFunction fn : BlockInterFunction.values()) {
            assertSame(fn.getKlass(), fn.getKlass(), fn.name() + " getKlass() must be stable");
        }
    }

    @Test
    @DisplayName("all constant names are unique")
    public void namesAreUnique() {
        Set<String> names = new HashSet<>();
        for (BlockInterFunction fn : BlockInterFunction.values()) {
            assertTrue(names.add(fn.name()), "duplicate name: " + fn.name());
        }
        assertEquals(BlockInterFunction.values().length, names.size());
    }

    @Test
    @DisplayName("valueOf round-trips with name() for every constant")
    public void valueOfRoundTrips() {
        for (BlockInterFunction fn : BlockInterFunction.values()) {
            assertSame(fn, BlockInterFunction.valueOf(fn.name()));
        }
    }

    @Test
    @DisplayName("valueOf of an unknown name throws IllegalArgumentException")
    public void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> BlockInterFunction.valueOf("NOT_A_BLOCK"));
        // Names are case-sensitive: "Ground" exists but "GROUND" does not.
        assertThrows(IllegalArgumentException.class, () -> BlockInterFunction.valueOf("GROUND"));
    }

    @Test
    @DisplayName("valueOf(null) throws NullPointerException")
    public void valueOfNullThrows() {
        assertThrows(NullPointerException.class, () -> BlockInterFunction.valueOf(null));
    }

    @Test
    @DisplayName("values() hands back a fresh defensive copy each call")
    public void valuesReturnsDefensiveCopy() {
        BlockInterFunction[] first = BlockInterFunction.values();
        assertNotSame(first, BlockInterFunction.values(), "values() must not leak a shared array");
        first[0] = BlockInterFunction.BASIC_BLOCK;
        assertSame(BlockInterFunction.TEXT_f, BlockInterFunction.values()[0]);
    }

    @Test
    @DisplayName("every constant reports BlockInterFunction as its declaring class")
    public void declaringClassIsBlockInterFunction() {
        for (BlockInterFunction fn : BlockInterFunction.values()) {
            assertSame(BlockInterFunction.class, fn.getDeclaringClass());
        }
    }
}
