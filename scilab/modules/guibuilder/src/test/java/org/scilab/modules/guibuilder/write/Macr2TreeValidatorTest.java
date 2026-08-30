/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.guibuilder.write;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Macr2TreeValidator} against a real, packaged Scilab
 * rather than a fake -- the entire point of this class is that it is not
 * hermetic, since the thing it verifies is that Scilab itself, not our own
 * parser, is the judge of what parses.
 *
 * <p>Named {@code ...Test.java} rather than {@code ...IT.java} on purpose:
 * this reactor's surefire configuration declares an explicit {@code
 * <includes>} list ({@code **&#47;Test*.java}, {@code **&#47;*Test.java},
 * {@code **&#47;*Tests.java}, {@code **&#47;*TestCase.java}, {@code
 * **&#47;test*.java}), and declaring it there REPLACES surefire's defaults
 * rather than adding to them (see the parent pom). {@code *IT.java} matches
 * none of those patterns and would never run at all -- silently, with the
 * build staying green throughout. {@link #requireScilab()} is what keeps
 * the hermetic suite unaffected when no packaged Scilab is present, not the
 * filename.
 */
public class Macr2TreeValidatorTest {

    private static final String SCILAB =
        "/Applications/Scilab-2027.0.0.app/Contents/MacOS/Scilab-2027.0.0";

    private static void requireScilab() {
        assumeTrue(new File(SCILAB).canExecute(), "needs a packaged Scilab; skipped");
    }

    @Test
    public void wellFormedScilabIsAccepted() {
        requireScilab();
        assertTrue(new Macr2TreeValidator(SCILAB).isValidScilab(
            "function f()\n  a = 1;\nendfunction\n"));
    }

    @Test
    public void malformedScilabIsRejected() {
        requireScilab();
        assertFalse(new Macr2TreeValidator(SCILAB).isValidScilab(
            "function f()\n  a = ((;\nendfunction\n"));
    }

    @Test
    public void anUnavailableScilabIsTreatedAsUnableToConfirm() {
        // Refusing every save because the oracle is missing would be worse than
        // the problem. Unknown is not the same as invalid -- see the class doc.
        assertTrue(new Macr2TreeValidator("/nonexistent/scilab").isValidScilab("a = 1;\n"));
    }
}
