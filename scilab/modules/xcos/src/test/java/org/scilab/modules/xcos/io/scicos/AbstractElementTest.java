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

package org.scilab.modules.xcos.io.scicos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.types.ScilabList;
import org.scilab.modules.types.ScilabString;
import org.scilab.modules.types.ScilabTList;
import org.scilab.modules.types.ScilabType;

/**
 * Hermetic unit tests for {@link AbstractElement}.
 *
 * <p>
 * {@code AbstractElement} is abstract, so a minimal concrete {@link StubElement}
 * is defined below to exercise the constructor and the default (pass-through)
 * {@link Element} implementations. The remainder are {@code static} utility
 * methods that operate purely on {@code org.scilab.modules.types.*} value
 * objects.
 *
 * <p>
 * The base class references {@code org.scilab.modules.xcos.JavaController}
 * (a SWIG/JNI class) only as its constructor parameter type. Passing
 * {@code null} loads but never <em>initialises</em> that class, so the native
 * library backing it is never touched — these tests stay hermetic.
 */
public class AbstractElementTest {

    /**
     * Minimal concrete subclass. It implements the only two {@link Element}
     * methods that {@link AbstractElement} leaves abstract ({@code canDecode}
     * and {@code decode}) and inherits everything else unchanged.
     */
    private static final class StubElement extends AbstractElement<String> {
        StubElement() {
            super(null);
        }

        @Override
        public boolean canDecode(ScilabType element) {
            return false;
        }

        @Override
        public String decode(ScilabType element, String into) {
            return into;
        }
    }

    // ------------------------------------------------------------------
    // constructor
    // ------------------------------------------------------------------

    @Test
    public void constructorStoresTheSuppliedController() {
        // The package-private final field is visible from this same-package test.
        // Reading a null reference-typed field does not initialise JavaController.
        StubElement el = new StubElement();
        assertNull(el.controller, "a null controller must be stored verbatim");
    }

    // ------------------------------------------------------------------
    // isEmptyField
    // ------------------------------------------------------------------

    @Test
    public void isEmptyFieldTrueForEmptyDouble() {
        assertTrue(AbstractElement.isEmptyField(new ScilabDouble()));
    }

    @Test
    public void isEmptyFieldFalseForNonEmptyDouble() {
        assertFalse(AbstractElement.isEmptyField(new ScilabDouble(3.14)));
    }

    @Test
    public void isEmptyFieldTrueForEmptyString() {
        assertTrue(AbstractElement.isEmptyField(new ScilabString()));
    }

    @Test
    public void isEmptyFieldFalseForNonEmptyString() {
        assertFalse(AbstractElement.isEmptyField(new ScilabString("hello")));
    }

    @Test
    public void isEmptyFieldTrueForEmptyList() {
        assertTrue(AbstractElement.isEmptyField(new ScilabList()));
    }

    @Test
    public void isEmptyFieldFalseForNonEmptyList() {
        ScilabList list = new ScilabList();
        list.add(new ScilabDouble(1));
        assertFalse(AbstractElement.isEmptyField(list));
    }

    @Test
    public void isEmptyFieldFalseForNull() {
        // No branch matches null, so the method must return false (not throw).
        assertFalse(AbstractElement.isEmptyField(null));
    }

    /**
     * Defect characterization: {@code isEmptyField} only special-cases
     * {@link ScilabDouble}, {@link ScilabString} and {@link ScilabList}. A
     * {@link ScilabTList} is a different {@code ArrayList} subclass, so even a
     * genuinely empty one is reported as <em>not</em> empty.
     */
    @Test
    public void isEmptyFieldFalseForEmptyTList_defectCharacterization() {
        assertFalse(AbstractElement.isEmptyField(new ScilabTList()));
    }

    // ------------------------------------------------------------------
    // getIndexes
    // ------------------------------------------------------------------

    @Test
    public void getIndexesColumnDominantFillsFirstSlot() {
        assertArrayEquals(new int[] {5, 0}, AbstractElement.getIndexes(5, true));
    }

    @Test
    public void getIndexesRowDominantFillsSecondSlot() {
        assertArrayEquals(new int[] {0, 5}, AbstractElement.getIndexes(5, false));
    }

    @Test
    public void getIndexesZeroIsAllZeros() {
        assertArrayEquals(new int[] {0, 0}, AbstractElement.getIndexes(0, true));
        assertArrayEquals(new int[] {0, 0}, AbstractElement.getIndexes(0, false));
    }

    /**
     * Defect characterization: the index is not validated, so a negative value
     * is propagated verbatim into the returned pair.
     */
    @Test
    public void getIndexesPropagatesNegativeIndex_defectCharacterization() {
        assertArrayEquals(new int[] { -3, 0}, AbstractElement.getIndexes(-3, true));
    }

    @Test
    public void getIndexesReturnsAFreshArrayEachCall() {
        int[] a = AbstractElement.getIndexes(7, true);
        int[] b = AbstractElement.getIndexes(7, true);
        assertNotSame(a, b, "each call must allocate a new array");
        assertArrayEquals(a, b);
    }

    // ------------------------------------------------------------------
    // incrementIndexes
    // ------------------------------------------------------------------

    @Test
    public void incrementIndexesColumnDominantBumpsFirstSlot() {
        int[] idx = {0, 0};
        AbstractElement.incrementIndexes(idx, true);
        assertArrayEquals(new int[] {1, 0}, idx);
    }

    @Test
    public void incrementIndexesRowDominantBumpsSecondSlot() {
        int[] idx = {0, 0};
        AbstractElement.incrementIndexes(idx, false);
        assertArrayEquals(new int[] {0, 1}, idx);
    }

    @Test
    public void incrementIndexesAccumulatesAcrossCalls() {
        int[] idx = {2, 5};
        AbstractElement.incrementIndexes(idx, true);
        AbstractElement.incrementIndexes(idx, true);
        assertArrayEquals(new int[] {4, 5}, idx);

        AbstractElement.incrementIndexes(idx, false);
        assertArrayEquals(new int[] {4, 6}, idx);
    }

    @Test
    public void incrementIndexesNullThrowsNPE() {
        assertThrows(NullPointerException.class, () -> AbstractElement.incrementIndexes(null, true));
    }

    @Test
    public void incrementIndexesRowDominantOnLengthOneArrayThrows() {
        // isColumnDominant == false writes index [1], which does not exist here.
        assertThrows(ArrayIndexOutOfBoundsException.class,
                     () -> AbstractElement.incrementIndexes(new int[] {0}, false));
    }

    // ------------------------------------------------------------------
    // canGet
    // ------------------------------------------------------------------

    /** A 2 (height) x 3 (width) string matrix. */
    private static ScilabString twoByThree() {
        return new ScilabString(new String[][] {{"a", "b", "c"}, {"d", "e", "f"}});
    }

    @Test
    public void canGetTrueWhenBothIndexesInsideBounds() {
        assertTrue(AbstractElement.canGet(twoByThree(), new int[] {0, 0}));
        assertTrue(AbstractElement.canGet(twoByThree(), new int[] {1, 2}));
    }

    @Test
    public void canGetFalseWhenRowIndexReachesHeight() {
        // height is 2, so row index 2 is out of range (strict greater-than test).
        assertFalse(AbstractElement.canGet(twoByThree(), new int[] {2, 0}));
    }

    @Test
    public void canGetFalseWhenColumnIndexReachesWidth() {
        // width is 3, so column index 3 is out of range.
        assertFalse(AbstractElement.canGet(twoByThree(), new int[] {0, 3}));
    }

    @Test
    public void canGetFalseForEmptyData() {
        assertFalse(AbstractElement.canGet(new ScilabString(), new int[] {0, 0}));
    }

    @Test
    public void canGetNullIndexesThrowsNPE() {
        assertThrows(NullPointerException.class, () -> AbstractElement.canGet(twoByThree(), null));
    }

    // ------------------------------------------------------------------
    // default Element pass-through implementations
    // ------------------------------------------------------------------

    @Test
    public void beforeEncodeReturnsElementUnchanged() {
        StubElement el = new StubElement();
        ScilabString element = new ScilabString("x");
        assertSame(element, el.beforeEncode("from", element));
    }

    @Test
    public void encodeWithElementReturnsThatElement() {
        StubElement el = new StubElement();
        ScilabString element = new ScilabString("x");
        assertSame(element, el.encode("from", element));
    }

    @Test
    public void afterEncodeReturnsElementUnchanged() {
        StubElement el = new StubElement();
        ScilabString element = new ScilabString("x");
        assertSame(element, el.afterEncode("from", element));
    }

    /**
     * The single-argument {@code encode(from)} delegates to
     * {@code encode(from, null)}, and the default {@code encode(from, element)}
     * simply returns its {@code element} argument. The default implementation
     * therefore always encodes nothing and yields {@code null}.
     */
    @Test
    public void encodeFromOnlyReturnsNullByDefault() {
        StubElement el = new StubElement();
        assertNull(el.encode("from"));
        assertNull(el.encode("from", null));
    }

    @Test
    public void beforeDecodeReturnsIntoUnchanged() {
        StubElement el = new StubElement();
        String into = "target";
        assertSame(into, el.beforeDecode(new ScilabString("x"), into));
    }

    @Test
    public void afterDecodeReturnsIntoUnchanged() {
        StubElement el = new StubElement();
        String into = "target";
        assertSame(into, el.afterDecode(new ScilabString("x"), into));
    }

    @Test
    public void beforeAndAfterDecodeTolerateNulls() {
        StubElement el = new StubElement();
        assertNull(el.beforeDecode(null, null));
        assertNull(el.afterDecode(null, null));
    }
}
