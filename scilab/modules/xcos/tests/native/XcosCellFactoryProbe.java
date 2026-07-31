/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 * ---------------------------------------------------------------------------
 * Register B21: the scenario of XcosCellFactoryTest, run somewhere it can
 * actually work.
 *
 * XcosCellFactory.createBlock("BIGSOM_f") posts
 *
 *     xcosCellCreated(BIGSOM_f("define"));
 *
 * to the interpreter through synchronousScilabExec and then blocks in
 * Signal.wait until Scilab calls notify("<uid>") back. That needs a RUNNING
 * interpreter. The JUnit class never starts one -- its @BeforeEach only does
 * System.loadLibrary("scilab") and new JavaController() -- so the command sat
 * in a queue with no consumer and the test waited forever.
 *
 * It cannot simply be given an engine under surefire either, for a reason that
 * is structural rather than incidental: xcos's own libscixcos links the REAL
 * libscijvm, while the NWNI libjavasci2 that -Pnative-tests puts first on
 * java.library.path links libscijvm-disable. Loading both is exactly what
 * InitScilab.cpp's checkForLinkerErrors() calls exit(1) on. So the engine here
 * has to be an ADVANCED-mode one (new Scilab(true)), which wants the GUI
 * libjavasci2 and the full etc/classpath.xml jar set -- see the runner script
 * for why that is a script and not a surefire execution.
 *
 * Run through run_xcos_cell_factory.sh.
 * ---------------------------------------------------------------------------
 */
import org.scilab.modules.javasci.Scilab;
import org.scilab.modules.xcos.block.BasicBlock;
import org.scilab.modules.xcos.graph.model.XcosCellFactory;

public class XcosCellFactoryProbe {

    private static int failures = 0;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "[xcos] ok   " : "[xcos] FAIL ") + what);
        System.out.flush();
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        final String interfaceFunction = "BIGSOM_f";

        Scilab sci = new Scilab(true);
        check(sci.open(), "advanced-mode engine opened");

        // Xcos macros are NOT loaded by engine startup; the Xcos GUI calls this
        // itself when it opens. Without it BIGSOM_f is simply undefined.
        check(sci.exec("loadXcosLibs();"), "loadXcosLibs()");

        // This is createOneSpecificBlock, verbatim. It is the call that used to
        // hang: everything below the assert happens on the interpreter thread.
        BasicBlock blk = XcosCellFactory.createBlock(interfaceFunction);
        check(blk != null, "createBlock(\"" + interfaceFunction + "\") returned a block");
        if (blk != null) {
            check(blk.getStyle().contains(interfaceFunction),
                  "the block's style contains \"" + interfaceFunction + "\" (was: " + blk.getStyle() + ")");
        }

        check(sci.close(), "engine closed");
        System.out.println(failures == 0 ? "[xcos] PASS" : "[xcos] " + failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
