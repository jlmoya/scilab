package org.scilab.modules.javasci;

import org.scilab.modules.types.ScilabBoolean;
import org.scilab.modules.types.ScilabType;

/**
 * A LIVE view of a Scilab boolean variable (register B18).
 *
 * getByReference() used to hand back a raw pointer into engine memory with no
 * invalidation protocol. The boolean path was the last one still doing it:
 * `Scilab.getInCurrentScilabSession` routes sci_boolean through the byref
 * branch, ScilabToJava's sendConvertedBooleanVariable calls
 * sendBooleanDataAsBuffer, and the resulting ScilabBooleanReference reads AND
 * WRITES a direct java.nio.IntBuffer over the engine's own storage
 * (intBuffer.put(i + nbRows * j, ...)). A type-promoting or growing assignment
 * reallocates the variable, after which that buffer is freed memory. This
 * re-resolves the variable by NAME on every accessor, so a freed buffer is
 * never touched while the view still reflects Scilab's writes. It is no longer
 * zero-copy; use get() when a snapshot is what you want.
 *
 * Every method on ScilabBoolean's public surface was reviewed against one
 * rule: no silent lost writes, and no stale shape. Methods that read or write
 * the frozen construction-time `data` field are overridden below to delegate
 * through live()/store(). Methods left inherited fall into three groups:
 *  (a) ones that reach live data anyway, transitively, because their bodies
 *      call only virtual accessors on `this` -- equals() (isEmpty, getWidth,
 *      getHeight, getRawData, isSwaped) and getSerializedObject() (just
 *      `return getData();`). Verified by reading the bodies, not assumed;
 *  (b) ones that describe this object's identity or representation rather
 *      than engine data -- getType, isReference, isSwaped, getVarName (see
 *      the per-method block at the bottom);
 *  (c) the Externalizable pair. writeExternal is transitively live (it writes
 *      getData(), plus the correctly-populated varName field and the constant
 *      swaped flag). readExternal is left inherited because it is
 *      unreachable: this class has no public no-arg constructor, so standard
 *      Java deserialization of one can never succeed regardless of what
 *      readExternal does.
 *
 * Three properties of ALL the *Ref views that are easy to get wrong:
 *
 *  - GETTERS RETURN DETACHED ARRAYS. getData()/getRawData() hand back the
 *    array live() built for this call, not a window onto the variable, so
 *    `ref.getData()[0][0] = true;` is a silent no-op -- even though the same
 *    expression on a plain ScilabBoolean would mutate that object. Under this
 *    class's own "no silent lost writes" rule: mutate-through-getter is NOT a
 *    write path. Use setElement()/setData(), which store() back to the engine.
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
public final class ScilabBooleanRef extends ScilabBoolean {
    private static final long serialVersionUID = 1L;

    /**
     * Routed through the 3-arg ScilabBoolean(String, boolean[][], boolean)
     * super constructor so the inherited `varName` field -- and therefore the
     * inherited getVarName() -- is actually populated. Declaring a `varName`
     * field here instead would SHADOW rather than feed the superclass field
     * (fields are not polymorphic), leaving getVarName() reading a never-set
     * field and returning null.
     *
     * That super constructor is safe to call here, which is NOT true of every
     * ScilabType: ScilabInteger's varName-carrying constructors populate their
     * data via `this.setData(...)`, and virtual dispatch applies even while a
     * superclass constructor is running, so they would run ScilabIntegerRef's
     * own overridden setData on a half-built object (observed: JVM exit 139) --
     * which is why ScilabIntegerRef uses the empty super() plus direct field
     * assignment. ScilabBoolean's is a plain three-field write
     * (this.varName/this.data/this.swaped), calling nothing overridable, so
     * the cleaner 4-argument-style route is available and taken. Confirmed by
     * reading the constructor body, not inferred from the signature.
     *
     * swaped=false matches the convention every other by-value ScilabBoolean
     * in this module uses, and is what elementsOf() below actually builds --
     * see the isSwaped() note at the bottom for why it is deliberately NOT
     * delegated to live().
     *
     * The array is built element-by-element via getElement(), NOT via
     * snapshot.getData(): the snapshot is a ScilabBooleanReference over a
     * direct buffer, and going through its per-element accessor is the same
     * discipline ScilabDoubleRef/ScilabIntegerRef adopted after finding their
     * bulk accessors mis-read the buffer's layout. (The boolean bulk path
     * happens to be the one that agrees -- ScilabTypeUtils.setBuffer's
     * boolean[][] overload indexes `buffer.get(i + c * j)` rather than doing
     * the sequential row-major bulk get its numeric siblings do -- but relying
     * on that asymmetry would be relying on an accident.) The resulting array
     * is inert: every accessor that could read the `data` field is overridden
     * below. It is built anyway so the object is a coherent ScilabBoolean
     * rather than one holding a null, matching its two siblings.
     */
    ScilabBooleanRef(String varName, ScilabBoolean snapshot) {
        super(varName, elementsOf(snapshot), false);
    }

    private static boolean[][] elementsOf(ScilabBoolean s) {
        final int height = s.getHeight();
        final int width = s.getWidth();
        boolean[][] data = new boolean[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                data[i][j] = s.getElement(i, j);
            }
        }
        return data;
    }

    /**
     * Re-resolves {@code varName} in the current session. Always returns a
     * plain ScilabBoolean (Scilab.getInCurrentScilabSession(String) fetches by
     * name, non-byref -- never a ScilabBooleanReference, never another
     * ScilabBooleanRef), so every delegate below terminates in one hop; none
     * of this recurses back into ScilabBooleanRef's own overrides.
     *
     * NOTE the cost: this is a full JNI fetch AND a full re-marshal of the
     * whole variable, not a cheap name lookup. Per-element accessors pay it
     * once per element on purpose -- that is what makes them live. Whole-object
     * methods must call it exactly ONCE and delegate (see toString below);
     * leaving one inherited would issue a live() per loop iteration and turn
     * an O(H*W) method into O((H*W)^2) element copies.
     */
    private ScilabBoolean live() {
        try {
            ScilabType current = Scilab.getInCurrentScilabSession(varName);
            if (!(current instanceof ScilabBoolean)) {
                throw new ScilabReferenceException(
                    "variable '" + varName + "' is no longer a boolean");
            }
            return (ScilabBoolean) current;
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot resolve variable '" + varName + "'", e);
        }
    }

    private void store(ScilabBoolean updated) {
        try {
            Scilab.putInCurrentScilabSession(varName, updated);
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot write variable '" + varName + "'", e);
        }
    }

    @Override
    public boolean getElement(final int i, final int j) {
        return live().getElement(i, j);
    }

    @Override
    public void setElement(final int i, final int j, final boolean x) {
        ScilabBoolean current = live();
        current.setElement(i, j, x);
        store(current);
    }

    // -- Whole-object accessors and mutators below: all read or write the
    // frozen `data` field if left inherited, so all delegate.

    @Override
    public boolean[][] getData() {
        return live().getData();
    }

    @Override
    public void setData(boolean[][] data) {
        ScilabBoolean current = live();
        current.setData(data);
        store(current);
    }

    @Override
    public Object getRawData() {
        return live().getRawData();
    }

    @Override
    public boolean isEmpty() {
        return live().isEmpty();
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
     * Inherited ScilabBoolean.hashCode() is Arrays.deepHashCode(data) -- a
     * direct field read, and fields are never polymorphic -- so left
     * inherited it would hash this object's frozen snapshot forever.
     * equals() does not need the same override: it is written entirely in
     * terms of virtual calls (isEmpty(), getWidth(), getHeight(),
     * getRawData(), isSwaped()), all live once overridden above, so it
     * already compares current engine state on both operands. Overriding
     * hashCode() to delegate keeps it consistent with that live equals(),
     * per the equals/hashCode contract.
     */
    @Override
    public int hashCode() {
        return live().hashCode();
    }

    /**
     * Resolved ONCE, for cost. Inherited ScilabBoolean.toString() is already
     * transitively live -- it calls isEmpty(), getHeight(), getWidth() and
     * getElement() -- but its loop CONDITIONS call getHeight()/getWidth()
     * every iteration, so left inherited it would issue roughly 2*H*W live()
     * calls, each a full fetch and re-marshal of the whole variable: about
     * 2*(H*W)^2 element copies, which on a 1000x1000 boolean presents as a
     * hang rather than a slow print. Delegating to the plain ScilabBoolean
     * live() returns costs exactly one fetch; that object's own virtual calls
     * dispatch to ITSELF, not back into this class, so there is no recursion.
     */
    @Override
    public String toString() {
        return live().toString();
    }

    // getType(): always sci_boolean. Constant regardless of what the named
    // variable currently holds -- this object's whole nature is "a boolean
    // view"; if the variable stops being a boolean, that surfaces as
    // ScilabReferenceException from live() the moment data is actually
    // touched (getElement, isEmpty, getHeight, ... above), which is the
    // correct place for it to surface, not from a type tag alone.
    //
    // isReference(): inherited, returns false (the `byref` field, never set
    // true). Per ScilabType's own contract ("true if data are backed in a
    // java.nio.Buffer"), false is the accurate answer for this class: unlike
    // ScilabBooleanReference, ScilabBooleanRef is deliberately NOT
    // buffer-backed -- that aliasing is exactly what register B18 was.
    //
    // isSwaped(): inherited, returns the `swaped` field, always false here
    // (see the constructor). This is a REPRESENTATION FLAG describing the
    // array THIS object hands out, not engine data, and it must NOT be
    // delegated to live() -- see the block below for why.
    //
    // getVarName(): inherited, reads the properly-populated superclass field
    // (see the constructor). The name is this view's own identity and lookup
    // key, not engine data Scilab could invalidate out from under it, so it
    // does not need to be live either.

    // -- Why isSwaped() stays false, and must not be "corrected" to
    //    `return live().isSwaped()`.
    //
    // live() returns an object whose swaped flag is TRUE, not false:
    // Scilab.getInCurrentScilabSession -> ScilabVariablesJavasci
    // .getScilabVariable(name, true, false) passes swapRowCol=1 into
    // GetScilabVariable.getScilabVariable, which reaches ScilabToJava's
    // sendVariable(name, swaped=true, ...) and ends at
    // ScilabVariables.sendData(..., boolean[][], swaped, ...) constructing
    // `new ScilabBoolean(varName, data, true)`. So this class's false is a
    // deliberate divergence from what live() reports, NOT an oversight, and
    // NOT something to make "consistent".
    //
    // It is harmless today by dispatch luck: ScilabTypeUtils.equalsBoolean
    // sends an array-vs-array comparison to the Object[] overload, which
    // discards BOTH flags and calls Arrays.deepEquals. It stops being
    // harmless the moment the other operand is a ScilabBooleanReference,
    // because a buffer-vs-array comparison routes to the IntBuffer overload,
    // where `dswaped` selects how the ARRAY is read -- and that overload's
    // polarity is the OPPOSITE of ScilabToJava's. There, dswaped == false
    // means the array is natural [row][col] (it compares data[i][j] against
    // buffer.get(i + rows * j)); in ScilabToJava::getMatrix, swaped == false
    // means the array is the TRANSPOSE, [col][row], and swaped == true is the
    // natural one. The arrays this class hands out are natural [row][col], so
    // false is the flag that makes the comparison correct. Delegating to
    // live()'s true would flip the overload into reading a [row][col] array
    // as [col][row] and return false for any non-square matrix.
}
