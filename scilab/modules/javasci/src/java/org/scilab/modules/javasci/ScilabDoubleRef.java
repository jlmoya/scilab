package org.scilab.modules.javasci;

import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.types.ScilabType;

/**
 * A LIVE view of a Scilab double variable (register B18).
 *
 * getByReference() used to hand back a raw pointer into engine memory with no
 * invalidation protocol. A type-promoting assignment reallocates the variable,
 * after which the view read freed memory -- nondeterministic garbage, then
 * SIGTRAP on write. This re-resolves the variable by NAME on every accessor, so
 * a freed buffer is never touched while the view still reflects Scilab's
 * writes. It is no longer zero-copy; use get() when a snapshot is what you want.
 *
 * Every method inherited from ScilabDouble was reviewed against one rule: no
 * silent lost writes, and no stale shape. Methods that read or write the
 * frozen construction-time snapshot fields are overridden below to delegate
 * through live()/store(). Methods left inherited fall into two groups: (a)
 * ones that reach live data anyway, transitively, by calling an already-live
 * method on `this` (getElement, getSerializedComplexMatrix,
 * getSerializedObject, toString, equals -- all call only virtual accessors,
 * so they inherit liveness once their dependencies are live); (b) ones that
 * describe this object's identity or representation rather than engine data
 * (getType, isReference, isSwaped, getVarName -- see per-method comments) and
 * so have nothing to go stale. readExternal/writeExternal (Externalizable)
 * are left inherited too: ScilabDoubleRef has no public no-arg constructor,
 * so standard Java deserialization of one can never succeed regardless of
 * what readExternal does.
 *
 * Three properties of ALL the *Ref views that are easy to get wrong:
 *
 *  - GETTERS RETURN DETACHED ARRAYS. getRealPart()/getImaginaryPart()/
 *    getRawRealPart() hand back the array live() built for this call, not a
 *    window onto the variable, so `ref.getRealPart()[0][0] = 42;` is a silent
 *    no-op -- even though the same expression on a plain ScilabDouble would
 *    mutate that object. Under this class's own "no silent lost writes" rule:
 *    mutate-through-getter is NOT a write path. Use setRealElement()/
 *    setElement()/setRealPart(), which store() back to the engine.
 *
 *  - SESSION IDENTITY IS NEVER CAPTURED. live()/store() call the STATIC
 *    Scilab.getInCurrentScilabSession/putInCurrentScilabSession, so a view
 *    held across close()/open() silently rebinds to the NEW session's
 *    same-named variable, and a view touched after close() re-enters a
 *    torn-down engine. That is the one path where "memory-safe by
 *    construction" does not hold: within a single live session a freed buffer
 *    is never touched, but nothing here defends against outliving the session
 *    itself.
 *
 *  - A *Ref IS A POOR HASH KEY. hashCode() and equals() are live, so both can
 *    now throw ScilabReferenceException if the backing variable was cleared or
 *    retyped -- from inside a HashMap lookup, where an exception is not
 *    expected. Key maps on getVarName() instead.
 */
public final class ScilabDoubleRef extends ScilabDouble {
    private static final long serialVersionUID = 1L;

    ScilabDoubleRef(String varName, ScilabDouble snapshot) {
        // Routed through the 4-arg ScilabDouble(String, double[][], double[][],
        // boolean) super constructor so the inherited `varName` field (and
        // therefore the inherited getVarName()) is actually populated. An
        // earlier version of this class declared its own `private final
        // String varName`, which shadowed rather than fed the superclass
        // field: fields are not polymorphic, so the inherited getVarName()
        // was reading the never-set superclass field and always returned
        // null. swaped=false matches the convention every other by-value
        // ScilabDouble in this module uses (row-by-row, not swaped) -- see
        // realElementsOf/imaginaryElementsOf below, which build exactly that
        // layout.
        //
        // The real/imaginary arrays are built element-by-element via
        // getRealElement()/getImaginaryElement(), NOT via
        // snapshot.getRealPart()/getImaginaryPart(). The snapshot returned by
        // getByReference() is a ScilabDoubleReference (modules/types), whose
        // whole-matrix accessors are unsafe on the direct buffer it wraps:
        //  - getImaginaryPart(), called on a real-only variable (null
        //    imaginary buffer), was OBSERVED to kill the forked JVM outright
        //    (process exit 139 / SIGSEGV) rather than throw a catchable
        //    exception, reproduced repeatedly under -Pnative-tests. The
        //    mechanism was not established (plain JVM semantics say a null
        //    dereference here should throw a normal NullPointerException);
        //    only the crash itself was confirmed, so nothing about why is
        //    asserted here.
        //  - getRealPart()/getImaginaryPart() bulk-read the column-major
        //    buffer as if it were row-major, scrambling any non-square
        //    matrix relative to what getRealElement()/getImaginaryElement()
        //    (which index it correctly) report.
        // Those two element accessors are what the rest of this class -- and
        // the pre-existing by-reference tests -- already rely on, so reusing
        // them here keeps the initial snapshot correct and crash-free.
        super(varName, realElementsOf(snapshot), snapshot.isReal() ? null : imaginaryElementsOf(snapshot), false);
    }

    private static double[][] realElementsOf(ScilabDouble s) {
        final int height = s.getHeight();
        final int width = s.getWidth();
        double[][] data = new double[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                data[i][j] = s.getRealElement(i, j);
            }
        }
        return data;
    }

    private static double[][] imaginaryElementsOf(ScilabDouble s) {
        final int height = s.getHeight();
        final int width = s.getWidth();
        double[][] data = new double[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                data[i][j] = s.getImaginaryElement(i, j);
            }
        }
        return data;
    }

    /**
     * Re-resolves {@code varName} in the current session. Always returns a
     * plain ScilabDouble (Scilab.getInCurrentScilabSession(String) fetches
     * by name, non-byref -- never a ScilabDoubleReference, never another
     * ScilabDoubleRef), so every delegate below terminates in one hop; none
     * of this recurses back into ScilabDoubleRef's own overrides.
     *
     * NOTE the cost: this is a full JNI fetch AND a full re-marshal of the
     * whole variable, not the cheap name lookup it might read as. Per-element
     * accessors pay it once per element on purpose -- that is what makes them
     * live. Whole-object methods must call it exactly ONCE and delegate (see
     * toString and getSerializedComplexMatrix below); leaving one inherited
     * would issue a live() per loop iteration and turn an O(H*W) method into
     * O((H*W)^2) element copies.
     */
    private ScilabDouble live() {
        try {
            ScilabType current = Scilab.getInCurrentScilabSession(varName);
            if (!(current instanceof ScilabDouble)) {
                throw new ScilabReferenceException(
                    "variable '" + varName + "' is no longer a double");
            }
            return (ScilabDouble) current;
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot resolve variable '" + varName + "'", e);
        }
    }

    private void store(ScilabDouble updated) {
        try {
            Scilab.putInCurrentScilabSession(varName, updated);
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot write variable '" + varName + "'", e);
        }
    }

    @Override
    public double getRealElement(final int i, final int j) {
        return live().getRealElement(i, j);
    }

    @Override
    public double getImaginaryElement(final int i, final int j) {
        return live().getImaginaryElement(i, j);
    }

    @Override
    public void setRealElement(final int i, final int j, final double x) {
        ScilabDouble current = live();
        current.setRealElement(i, j, x);
        store(current);
    }

    @Override
    public void setImaginaryElement(final int i, final int j, final double x) {
        ScilabDouble current = live();
        current.setImaginaryElement(i, j, x);
        store(current);
    }

    /**
     * Inherited ScilabDouble.setElement(i,j,x,y) assigns realPart[i][j] and
     * imaginaryPart[i][j] directly -- it does not route through
     * setRealElement()/setImaginaryElement() -- so left inherited it would
     * silently write into this object's frozen construction-time snapshot
     * and never reach the engine. No exception, no signal, just a lost write.
     */
    @Override
    public void setElement(final int i, final int j, final double x, final double y) {
        ScilabDouble current = live();
        current.setElement(i, j, x, y);
        store(current);
    }

    // -- Whole-object / whole-matrix accessors and mutators below: all read
    // or write the frozen snapshot fields if left inherited, so all delegate.
    // getElement(), getSerializedObject() and equals() are deliberately NOT
    // repeated here: each is inherited unchanged, and each is written entirely
    // in terms of virtual calls (isEmpty(), isReal(), getHeight(), getWidth(),
    // getRealElement(), getImaginaryElement(), getRealPart(),
    // getImaginaryPart(), getRawRealPart(), getRawImaginaryPart(),
    // isSwaped()) -- once those dependencies are live, dynamic dispatch makes
    // the inherited method live too, with no override needed. Each of the
    // three costs a BOUNDED handful of live() calls, which is why they stay
    // inherited where toString() and getSerializedComplexMatrix(), whose call
    // counts scale with H*W, do not (see their overrides below).

    @Override
    public boolean isEmpty() {
        return live().isEmpty();
    }

    @Override
    public boolean isReal() {
        return live().isReal();
    }

    @Override
    public double[][] getRealPart() {
        return live().getRealPart();
    }

    @Override
    public void setRealPart(double[][] realPart) {
        ScilabDouble current = live();
        current.setRealPart(realPart);
        store(current);
    }

    @Override
    public double[][] getImaginaryPart() {
        return live().getImaginaryPart();
    }

    @Override
    public void setImaginaryPart(double[][] imaginaryPart) {
        ScilabDouble current = live();
        current.setImaginaryPart(imaginaryPart);
        store(current);
    }

    @Override
    public Object getRawRealPart() {
        return live().getRawRealPart();
    }

    @Override
    public Object getRawImaginaryPart() {
        return live().getRawImaginaryPart();
    }

    @Override
    public int getHeight() {
        return live().getHeight();
    }

    @Override
    public int getWidth() {
        return live().getWidth();
    }

    /**
     * Inherited ScilabDouble.hashCode() reads the realPart/imaginaryPart
     * fields directly (Arrays.deepHashCode), not through getRealPart()/
     * getImaginaryPart() -- fields are never polymorphic, so left inherited
     * it would hash this object's frozen snapshot forever. equals() does not
     * need the same override: it is written entirely in terms of virtual
     * calls (isEmpty(), getWidth(), getHeight(), isReal(), getRawRealPart(),
     * getRawImaginaryPart(), isSwaped()), all of which are live once
     * overridden above, so equals() already compares current engine state on
     * both operands without any override of its own. Overriding hashCode()
     * to delegate keeps it consistent with that live equals(), per the
     * equals/hashCode contract.
     */
    @Override
    public int hashCode() {
        return live().hashCode();
    }

    /**
     * Resolved ONCE, for cost. Inherited ScilabDouble.toString()
     * (ScilabDouble.java:449-469) is already transitively live -- it calls
     * isEmpty(), isReal(), getHeight(), getWidth(), getRealElement() and
     * getImaginaryElement() -- but its loop CONDITIONS call getHeight()/
     * getWidth() every iteration, so left inherited it would issue roughly
     * 2*H*W live() calls, each a full fetch and re-marshal of the whole
     * variable: about 2*(H*W)^2 element copies. On a 1000x1000 matrix that
     * presents as a hang, not as a slow print. Delegating to the plain
     * ScilabDouble live() returns costs exactly one fetch; that object's own
     * virtual calls dispatch to ITSELF, not back into this class, so there is
     * no recursion.
     */
    @Override
    public String toString() {
        return live().toString();
    }

    /**
     * Resolved ONCE, for the same reason as toString(). Inherited
     * ScilabDouble.getSerializedComplexMatrix() (ScilabDouble.java:316-327)
     * calls this.getHeight() twice and this.getWidth() once per iteration on
     * top of the two element reads, so left inherited it would issue O(H*W)
     * live() calls and copy O((H*W)^2) elements to produce an O(H*W) result.
     * Same non-recursion argument: live() is a plain ScilabDouble.
     */
    @Override
    public double[] getSerializedComplexMatrix() {
        return live().getSerializedComplexMatrix();
    }

    // getType(): always sci_matrix. Constant regardless of what the named
    // variable currently holds -- this object's whole nature is "a double
    // view"; if the variable stops being a double, that surfaces as
    // ScilabReferenceException from live() the moment data is actually
    // touched (getRealElement, isEmpty, getHeight, ... above), which is the
    // correct place for it to surface, not from a type tag alone.
    //
    // isReference(): inherited, returns false (the `byref` field, never set
    // true). Per ScilabType's own contract ("true if data are backed in a
    // java.nio.Buffer"), false is the accurate answer for this class: unlike
    // ScilabDoubleReference, ScilabDoubleRef is deliberately NOT buffer-backed
    // -- that aliasing is exactly what register B18 was.
    //
    // isSwaped(): inherited, returns the `swaped` field, always false here
    // (see the constructor comment). This is a REPRESENTATION FLAG describing
    // the array THIS object hands out, not engine data, and it must NOT be
    // delegated to live() -- see the block below for why.
    //
    // getVarName(): inherited, now correctly reads the properly-populated
    // superclass field (see the constructor comment / Finding 3). The name
    // is this view's own identity and lookup key, not engine data Scilab
    // could invalidate out from under it, so it does not need to be live
    // either.

    // -- Why isSwaped() stays false, and must not be "corrected" to
    //    `return live().isSwaped()`.
    //
    // live() returns an object whose swaped flag is TRUE, not false. Chain:
    // Scilab.getInCurrentScilabSession(varname) -> its sci_matrix case
    // (Scilab.java:688, the shared dispatch line) ->
    // ScilabVariablesJavasci.getScilabVariable(varname,
    // true, byref) -> ScilabVariablesJavasci.java:67 passes
    // `swapRowCol ? 1 : 0` into GetScilabVariable.getScilabVariable ->
    // ScilabToJava.cpp:812-814 -> :71-89 (sendVariable with swaped=true) ->
    // ScilabVariables.java:111-117, which constructs
    // `new ScilabDouble(varName, data, null, swaped)` with swaped TRUE.
    // So this class's false is a deliberate divergence from what live()
    // reports, NOT an oversight, and NOT something to make "consistent".
    //
    // It is harmless today by dispatch luck: ScilabTypeUtils.equalsDouble
    // sends an array-vs-array comparison to the Object[] overload, which
    // discards BOTH flags and calls Arrays.deepEquals. It stops being
    // harmless the moment the other operand is a ScilabDoubleReference,
    // because a buffer-vs-array comparison routes to
    // ScilabTypeUtils.java:283-306, where `dswaped` selects how the ARRAY is
    // read -- and that overload's polarity is the OPPOSITE of ScilabToJava's.
    // There, dswaped == false means the array is natural [row][col] (it
    // compares data[i][j] against buffer.get(i + rows * j)); in
    // ScilabToJava::getMatrix (ScilabToJava.cpp:720-745), swaped == false
    // means the array is the TRANSPOSE, [col][row], and swaped == true is the
    // natural one. The arrays this class hands out are natural [row][col], so
    // false is the flag that makes the comparison correct. Delegating to
    // live()'s true would flip the overload into reading a [row][col] array
    // as [col][row] and return false for any non-square matrix.
}
