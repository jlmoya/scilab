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
 * accessors that are themselves live -- getElement, setElement, getData,
 * getRawData, getSerializedObject, toString, equals; (b) ones that describe
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
    // (see the constructor) and always false for whatever live() returns too
    // (Scilab.getInCurrentScilabSession's non-byref fetch path also
    // constructs its plain ScilabInteger with the same convention
    // ScilabDoubleRef relies on). A constant, consistent representation
    // convention, not engine data -- nothing to delegate.
    //
    // getVarName(): inherited, reads the properly-populated superclass field
    // (see the constructor). The name is this view's own identity and lookup
    // key, not engine data Scilab could invalidate out from under it, so it
    // does not need to be live either.
}
