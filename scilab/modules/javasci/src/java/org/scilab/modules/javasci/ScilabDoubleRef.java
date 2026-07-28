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
 */
public final class ScilabDoubleRef extends ScilabDouble {
    private static final long serialVersionUID = 1L;

    private final String varName;

    ScilabDoubleRef(String varName, ScilabDouble snapshot) {
        // Built element-by-element via getRealElement()/getImaginaryElement(),
        // NOT via snapshot.getRealPart()/getImaginaryPart(). The by-reference
        // snapshot returned by getByReference() is a ScilabDoubleReference
        // (modules/types), whose whole-matrix accessors are unsafe on the
        // direct buffer it wraps: getImaginaryPart() dereferences a null
        // imaginary buffer for a real-only variable and crashes the JVM
        // natively rather than throwing, and getRealPart()/getImaginaryPart()
        // bulk-read the column-major buffer as if it were row-major, scrambling
        // any non-square matrix relative to what getRealElement()/
        // getImaginaryElement() (which index it correctly) report. Those two
        // element accessors are what the rest of this class -- and the
        // pre-existing by-reference tests -- already rely on, so reusing them
        // here keeps the initial snapshot correct and crash-free.
        super(realElementsOf(snapshot), snapshot.isReal() ? null : imaginaryElementsOf(snapshot));
        this.varName = varName;
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
}
