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
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    /**
     * Where to find a packaged Scilab, overridable with
     * {@code -Dguibuilder.test.scilab=/path/to/launcher}.
     *
     * <p>The default is this fork's macOS install location, which is where it
     * is on the machine this module was written on. Hardcoding it outright was
     * the same silent-skip problem the class javadoc above warns about, just
     * relocated: on any machine where that path does not exist, the two
     * substantive tests below would {@code assumeTrue}-skip and the suite
     * would go green having exercised nothing -- and the filename convention
     * that javadoc defends would have bought nothing. The property makes that
     * recoverable rather than fatal, and
     * {@link #requireScilab()} reports the path it looked at so a skip says
     * WHY it skipped instead of only that it did.
     */
    private static final String SCILAB = System.getProperty(
        "guibuilder.test.scilab",
        "/Applications/Scilab-2027.0.0.app/Contents/MacOS/Scilab-2027.0.0");

    private static void requireScilab() {
        assumeTrue(new File(SCILAB).canExecute(),
                   "needs a packaged Scilab at " + SCILAB
                   + " (override with -Dguibuilder.test.scilab=...); skipped");
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

    /**
     * The nonexistent-path case above only pins the {@code canExecute()}
     * guard at the top of {@code isValidScilab}. This pins the OTHER way
     * "cannot run Scilab" happens: a path that exists and passes that same
     * guard, yet cannot actually be started, so the failure surfaces from
     * inside the try block instead -- {@code ProcessBuilder#start()}
     * throwing, caught by {@code catch (IOException | RuntimeException)}.
     *
     * <p>A directory, not a plain file: measured directly before writing
     * this test that a plain empty file marked executable does NOT reach
     * that catch on this machine. {@code File#canExecute()} is true for a
     * directory too (traversal needs the executable bit, and a freshly
     * created directory already has it), so it clears the guard exactly
     * like a real launcher would, but a directory can never be executed as
     * a program -- {@code ProcessBuilder#start()} reliably throws {@code
     * IOException} for one ("Exec failed, error: 13").
     */
    @Test
    public void aLauncherThatExistsButCannotBeStartedIsTreatedAsUnableToConfirm(@TempDir Path tempDir) {
        assertTrue(tempDir.toFile().canExecute(), "precondition: a directory must pass the executable check");
        assertTrue(new Macr2TreeValidator(tempDir.toString()).isValidScilab("a = 1;\n"));
    }
}
