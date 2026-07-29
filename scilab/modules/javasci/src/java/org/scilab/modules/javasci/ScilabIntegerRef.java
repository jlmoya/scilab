package org.scilab.modules.javasci;

import org.scilab.modules.types.ScilabInteger;
import org.scilab.modules.types.ScilabIntegerTypeEnum;
import org.scilab.modules.types.ScilabType;

/**
 * A LIVE view of a Scilab integer variable (register B18).
 *
 * The integer case is what exposed the use-after-free: `c(2,3)=123` puts a
 * DOUBLE into an int8 array, so Scilab reallocates the variable on conversion
 * and the old view pointed at freed memory (observed: 126, then 50 on a
 * rerun, then SIGTRAP on write). `c(2,3)=int8(123)` needs no conversion,
 * keeps the buffer, and read correctly -- which is how the trigger was
 * identified. This re-resolves the variable by NAME on every accessor, the
 * same fix ScilabDoubleRef applies to doubles; it is no longer zero-copy, use
 * get() when a snapshot is what you want.
 *
 * ScilabInteger has four backing widths (byte/short/int/long), selected by
 * `precision`, where ScilabDouble only has a real/imaginary split -- one more
 * axis a stale snapshot can get wrong. Every method on the public surface was
 * reviewed against the same rule ScilabDoubleRef used: no silent lost writes,
 * no stale shape, and (new here) no stale precision. Methods that read or
 * write the frozen construction-time fields are overridden below to delegate
 * through live()/store() -- this includes the per-width bulk accessors
 * (getDataAsByte/Short/Int, the four setData overloads) that ScilabDouble has
 * no analogue of. Methods left inherited fall into three groups: (a) ones
 * that reach live data anyway, transitively, by calling only virtual
 * accessors that are themselves live, in a BOUNDED number of live() calls --
 * getElement, setElement, getRawData, getSerializedObject, equals. (getData
 * and toString are transitively live in the same way, but their live() count
 * scales with H*W, so they are overridden below for cost, not correctness.)
 * (b) ones that describe
 * this object's identity or representation rather than engine data --
 * getType, isReference, isSwaped, getVarName (see the block below); (c) the
 * 64-bit width itself -- getLongElement, setLongElement, getDataAsLong,
 * setData(long[][], boolean) are left inherited not because they are live,
 * but because they are provably unreachable: Scilab.getInCurrentScilabSession
 * throws UnsupportedTypeException for int64/uint64 before any value comes
 * back, both at construction time (getByReference) and on every later live()
 * call (see live() below), so no ScilabIntegerRef instance can ever actually
 * hold or observe 64-bit data. Left inherited, those four accessors fail
 * exactly as they do on a plain int8/16/32-precision ScilabInteger queried at
 * the wrong width (null data, NullPointerException on the null array) --
 * pre-existing behaviour this class does not change and cannot improve on,
 * since there is no live variable state for them to go stale against.
 * readExternal/writeExternal (Externalizable) are left inherited too, for the
 * same reason as ScilabDoubleRef: this class has no public no-arg
 * constructor, so standard Java deserialization of one can never succeed
 * regardless of what readExternal does.
 *
 * Three properties of ALL the *Ref views that are easy to get wrong:
 *
 *  - GETTERS RETURN DETACHED ARRAYS. getDataAsByte()/getDataAsShort()/
 *    getDataAsInt()/getData()/getCorrectData()/getRawData() hand back the
 *    array live() built for this call, not a window onto the variable, so
 *    `ref.getDataAsByte()[0][0] = 42;` is a silent no-op -- even though the
 *    same expression on a plain ScilabInteger would mutate that object. Under
 *    this class's own "no silent lost writes" rule: mutate-through-getter is
 *    NOT a write path. Use setElement()/setByteElement()/setData(), which
 *    store() back to the engine.
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
public final class ScilabIntegerRef extends ScilabInteger {
    private static final long serialVersionUID = 1L;

    /**
     * Deliberately does NOT call one of ScilabInteger's varName-carrying
     * constructors (String, byte[][]/short[][]/int[][]/long[][], boolean,
     * boolean): every one of them populates its data through
     * `this.setData(...)`, and setData is overridden below to delegate
     * through live()/store(). Virtual dispatch applies even while a
     * superclass constructor is still running -- the object's runtime class
     * is already ScilabIntegerRef the moment it is allocated -- so that
     * super() call would run OUR setData override on a not-yet-initialized
     * object (this.varName still null) instead of the plain field write the
     * base class constructor intends. ScilabDouble sidesteps this because its
     * own constructor chain assigns realPart/imaginaryPart directly rather
     * than through setRealPart/setImaginaryPart; ScilabInteger's does not,
     * so the equivalent super constructor is not safe to call here once
     * setData is overridden. super() (no-arg, empty body) plus direct field
     * assignment below reaches the same end state without going through any
     * overridable method.
     *
     * The per-width array is built element-by-element via
     * getByteElement()/getShortElement()/getIntElement(), NOT via
     * snapshot.getDataAsByte()/getDataAsShort()/getDataAsInt(). The snapshot
     * returned by getByReference() is a ScilabIntegerReference
     * (modules/types), whose whole-array accessors bulk-read the buffer
     * row-major while its per-element accessors correctly index it
     * column-major (getXElement(i,j) reads buffer.get(i + nbRows*j); the
     * bulk getDataAsX() methods route through ScilabTypeUtils.setBuffer(),
     * which fills each output row in sequential buffer order -- the same
     * row/column-major mismatch ScilabDoubleRef found in
     * ScilabDoubleReference.getRealPart()/getImaginaryPart(), confirmed here
     * by reading ScilabTypeUtils.setBuffer's identical structure for every
     * primitive width, not by reproducing the scramble live). Per-element
     * access is what the rest of this class -- and the pre-existing
     * by-reference tests -- already rely on, so reusing it here keeps the
     * initial snapshot correct.
     */
    ScilabIntegerRef(String varName, ScilabInteger snapshot) {
        super();
        this.varName = varName;
        this.swaped = false;
        this.precision = snapshot.getPrec();
        switch (this.precision) {
            case sci_int8:
            case sci_uint8:
                this.byteData = byteElementsOf(snapshot);
                break;
            case sci_int16:
            case sci_uint16:
                this.shortData = shortElementsOf(snapshot);
                break;
            case sci_int32:
            case sci_uint32:
                this.intData = intElementsOf(snapshot);
                break;
            default:
                throw new ScilabReferenceException(
                    "unsupported integer precision for variable '" + varName + "': " + this.precision);
        }
    }

    private static byte[][] byteElementsOf(ScilabInteger s) {
        final int height = s.getHeight();
        final int width = s.getWidth();
        byte[][] data = new byte[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                data[i][j] = s.getByteElement(i, j);
            }
        }
        return data;
    }

    private static short[][] shortElementsOf(ScilabInteger s) {
        final int height = s.getHeight();
        final int width = s.getWidth();
        short[][] data = new short[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                data[i][j] = s.getShortElement(i, j);
            }
        }
        return data;
    }

    private static int[][] intElementsOf(ScilabInteger s) {
        final int height = s.getHeight();
        final int width = s.getWidth();
        int[][] data = new int[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                data[i][j] = s.getIntElement(i, j);
            }
        }
        return data;
    }

    /**
     * Re-resolves {@code varName} in the current session. Always returns a
     * plain ScilabInteger (Scilab.getInCurrentScilabSession(String) fetches
     * by name, non-byref -- never a ScilabIntegerReference, never another
     * ScilabIntegerRef), so every delegate below terminates in one hop; none
     * of this recurses back into ScilabIntegerRef's own overrides.
     *
     * NOTE the cost: this is a full JNI fetch AND a full re-marshal of the
     * whole variable, not the cheap name lookup it might read as. Per-element
     * accessors pay it once per element on purpose -- that is what makes them
     * live. Whole-object methods must call it exactly ONCE and delegate (see
     * getData and toString below); leaving one inherited would issue a live()
     * per loop iteration and turn an O(H*W) method into O((H*W)^2) element
     * copies.
     */
    private ScilabInteger live() {
        try {
            ScilabType current = Scilab.getInCurrentScilabSession(varName);
            if (!(current instanceof ScilabInteger)) {
                throw new ScilabReferenceException(
                    "variable '" + varName + "' is no longer an integer");
            }
            return (ScilabInteger) current;
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot resolve variable '" + varName + "'", e);
        }
    }

    private void store(ScilabInteger updated) {
        try {
            Scilab.putInCurrentScilabSession(varName, updated);
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot write variable '" + varName + "'", e);
        }
    }

    @Override
    public byte getByteElement(final int i, final int j) {
        return live().getByteElement(i, j);
    }

    @Override
    public short getShortElement(final int i, final int j) {
        return live().getShortElement(i, j);
    }

    @Override
    public int getIntElement(final int i, final int j) {
        return live().getIntElement(i, j);
    }

    @Override
    public void setByteElement(final int i, final int j, final byte x) {
        ScilabInteger current = live();
        current.setByteElement(i, j, x);
        store(current);
    }

    @Override
    public void setShortElement(final int i, final int j, final short x) {
        ScilabInteger current = live();
        current.setShortElement(i, j, x);
        store(current);
    }

    @Override
    public void setIntElement(final int i, final int j, final int x) {
        ScilabInteger current = live();
        current.setIntElement(i, j, x);
        store(current);
    }

    // getElement(i,j)/setElement(i,j,x) (the long-typed generic accessors) are
    // deliberately NOT repeated here: both are inherited unchanged, and both
    // are written entirely in terms of virtual calls -- this.getPrec() (live,
    // overridden below) to pick a branch, then getByteElement/getShortElement/
    // getIntElement or setByteElement/setShortElement/setIntElement (live,
    // overridden above) to do the read or write. Once those dependencies are
    // live, dynamic dispatch makes the inherited getElement/setElement live
    // too, with no override needed -- this is testReadWriteBuf's actual call
    // path (it calls getElement/setElement, never the per-width accessors
    // directly) and it is exercised by the acceptance run.

    @Override
    public void setData(byte[][] data, boolean bUnsigned) {
        ScilabInteger current = live();
        current.setData(data, bUnsigned);
        store(current);
    }

    @Override
    public void setData(short[][] data, boolean bUnsigned) {
        ScilabInteger current = live();
        current.setData(data, bUnsigned);
        store(current);
    }

    @Override
    public void setData(int[][] data, boolean bUnsigned) {
        ScilabInteger current = live();
        current.setData(data, bUnsigned);
        store(current);
    }

    /**
     * Overridden alongside the other three widths for a uniform rule (every
     * setData overload delegates), even though int64/uint64 can never be
     * this object's own live precision (see the class comment). Left
     * inherited, this would silently repoint THIS object's own precision/
     * longData fields to a 64-bit value that never reaches the engine and
     * that nothing downstream ever reads back (getPrec() below no longer
     * reads the raw field) -- harmless in effect, but a silent no-op instead
     * of the loud failure a caller doing something this unusual should get.
     * Delegating turns it into the same UnsupportedTypeException ->
     * ScilabReferenceException every unsupported write in this class
     * produces, via Scilab.putInCurrentScilabSession's own sci_int64/
     * sci_uint64 branch (which is empty -- no put call is wired up there
     * either).
     */
    @Override
    public void setData(long[][] data, boolean bUnsigned) {
        ScilabInteger current = live();
        current.setData(data, bUnsigned);
        store(current);
    }

    @Override
    public byte[][] getDataAsByte() {
        return live().getDataAsByte();
    }

    @Override
    public short[][] getDataAsShort() {
        return live().getDataAsShort();
    }

    @Override
    public int[][] getDataAsInt() {
        return live().getDataAsInt();
    }

    // getDataAsLong() is left inherited: it directly returns the longData
    // field, which is unconditionally null for the lifetime of any
    // ScilabIntegerRef (see the class comment on the 64-bit width) --
    // returning null is correct and matches what live() would also report,
    // so there is nothing to delegate.

    @Override
    public ScilabIntegerTypeEnum getPrec() {
        return live().getPrec();
    }

    /**
     * Inherited ScilabInteger.isUnsigned() switches on the bare `precision`
     * field, not on this.getPrec() -- so even with getPrec() overridden
     * above, isUnsigned() left inherited would still read this object's own
     * frozen, construction-time field directly (fields are never
     * polymorphic) and never see a precision change live() would report.
     */
    @Override
    public boolean isUnsigned() {
        return live().isUnsigned();
    }

    // -- Whole-object accessors below: all read the frozen snapshot fields
    // directly if left inherited, so all delegate.

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
     * Inherited ScilabInteger.getCorrectData() switches on this.getPrec()
     * (live) but then directly returns byteData/shortData/intData/longData
     * -- this object's own frozen fields -- so left inherited it would still
     * hand back stale data at whichever width is actually live. getRawData()
     * and getSerializedObject() are deliberately NOT repeated here: both are
     * inherited unchanged and both are written entirely in terms of virtual
     * calls (getRawData() is just `return getCorrectData();`;
     * getSerializedObject() calls getPrec() and getCorrectData()), so once
     * getCorrectData() is live, both are transitively live too.
     */
    @Override
    public Object getCorrectData() {
        return live().getCorrectData();
    }

    /**
     * Inherited ScilabInteger.hashCode() reads byteData/intData/longData/
     * precision/shortData directly (Arrays.deepHashCode plus a field read),
     * not through any getter -- left inherited it would hash this object's
     * frozen snapshot forever. equals() does not need the same override: it
     * is written entirely in terms of virtual calls (isEmpty(), getWidth(),
     * getHeight(), getRawData(), isSwaped()), all of which are live once
     * overridden above, so equals() already compares current engine state on
     * both operands without any override of its own -- this is also
     * testReadWriteBuf's actual call path (aFromScilab.equals(aOriginal)
     * right after getByReference()). Overriding hashCode() to delegate keeps
     * it consistent with that live equals(), per the equals/hashCode
     * contract.
     */
    @Override
    public int hashCode() {
        return live().hashCode();
    }

    /**
     * Resolved ONCE, for cost. Inherited ScilabInteger.getData()
     * (ScilabInteger.java:356-389) is already transitively live -- it calls
     * this.getPrec(), then getByteElement()/getShortElement()/getIntElement()
     * per cell -- but every one of its loop CONDITIONS calls this.getHeight()
     * or this.getWidth(), so left inherited it would issue roughly 2*H*W
     * live() calls, each a full fetch and re-marshal of the whole variable:
     * about 2*(H*W)^2 element copies to produce an O(H*W) result. Delegating
     * to the plain ScilabInteger live() returns costs exactly one fetch; that
     * object's own virtual calls dispatch to ITSELF, not back into this class,
     * so there is no recursion.
     */
    @Override
    public long[][] getData() {
        return live().getData();
    }

    /**
     * Resolved ONCE, for the same reason as getData(). Inherited
     * ScilabInteger.toString() reaches appendData(), whose loop conditions
     * call getHeight()/getWidth() per iteration on top of its single
     * getData(), so left inherited it would be O(H*W) live() calls and
     * O((H*W)^2) element copies. Same non-recursion argument: live() is a
     * plain ScilabInteger.
     */
    @Override
    public String toString() {
        return live().toString();
    }

    // getType(): always sci_ints. Constant regardless of which width the
    // named variable currently holds -- this object's whole nature is "an
    // integer view"; if the variable stops being an integer, that surfaces
    // as ScilabReferenceException from live() the moment data is actually
    // touched (getByteElement, isEmpty, getHeight, ... above), which is the
    // correct place for it to surface, not from a type tag alone.
    //
    // isReference(): inherited, returns false (the `byref` field, never set
    // true). Per ScilabType's own contract ("true if data are backed in a
    // java.nio.Buffer"), false is the accurate answer for this class: unlike
    // ScilabIntegerReference, ScilabIntegerRef is deliberately NOT
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
    // live() returns an object whose swaped flag is TRUE, not false. Chain:
    // Scilab.getInCurrentScilabSession(varname) -> its sci_ints case
    // (Scilab.java:699, the per-precision dispatch line) ->
    // ScilabVariablesJavasci.getScilabVariable(varname,
    // true, byref) -> ScilabVariablesJavasci.java:67 passes
    // `swapRowCol ? 1 : 0` into GetScilabVariable.getScilabVariable ->
    // ScilabToJava.cpp:812-814 -> :71-89 (sendVariable with swaped=true) ->
    // the ScilabVariables.sendData overload for this width, which passes that
    // flag straight into the constructed plain ScilabInteger -- exactly as
    // ScilabVariables.java:111-117 does for the double case. So this class's
    // false is a deliberate divergence from what live() reports, NOT an
    // oversight, and NOT something to make "consistent".
    //
    // It is harmless today by dispatch luck: ScilabTypeUtils's equals* entry
    // points send an array-vs-array comparison to the Object[] overload,
    // which discards BOTH flags and calls Arrays.deepEquals. It stops being
    // harmless the moment the other operand is a ScilabIntegerReference,
    // because a buffer-vs-array comparison routes to the per-width
    // buffer/array overloads (the byte one at ScilabTypeUtils.java:319, the
    // double analogue at :283-306 being the clearest written form), where
    // `dswaped` selects how the ARRAY is read -- and that polarity is the
    // OPPOSITE of ScilabToJava's. There, dswaped == false means the array is
    // natural [row][col] (it compares data[i][j] against
    // buffer.get(i + rows * j)); in ScilabToJava::getMatrix
    // (ScilabToJava.cpp:720-745), swaped == false means the array is the
    // TRANSPOSE, [col][row], and swaped == true is the natural one. The
    // arrays this class hands out are natural [row][col], so false is the
    // flag that makes the comparison correct. Delegating to live()'s true
    // would flip the overload into reading a [row][col] array as [col][row]
    // and return false for any non-square matrix.
}
