/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 * ---------------------------------------------------------------------------
 * Acceptance probe for deferred-fixes-register.md B22 defect (1):
 * embedded ADVANCED-mode Scilab -- new Scilab(true) -- must start, compute and
 * render without the engine touching AppKit from a secondary thread.
 *
 * Until 2026-07-30 this died with EXC_BREAKPOINT/SIGTRAP inside
 * -[NSWMWindowCoordinator performTransactionUsingBlock:], because SwingView's
 * constructor initialised JOGL -- which creates and destroys dummy NSWindows --
 * on whatever thread called StartScilabEngine. That is the process main thread
 * for the standalone launcher and NEVER is for an embedder.
 *
 * Run through run_b22_advanced_mode.sh, which builds the JVM environment from
 * etc/classpath.xml, etc/librarypath.xml and etc/jvm_options.xml so this test
 * cannot drift away from what Scilab itself uses.
 * ---------------------------------------------------------------------------
 */
import java.io.File;
import org.scilab.modules.javasci.Scilab;

public class B22AdvancedMode {

    private static void mark(String s) {
        System.out.println("[B22-adv] " + s);
        System.out.flush();
    }

    private static void check(boolean ok, String what) {
        mark((ok ? "ok   " : "FAIL ") + what);
        if (!ok) {
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        File png = File.createTempFile("b22-advanced-", ".png");
        png.delete();

        // ADVANCED mode: the GUI-linked libjavasci2 and a real Swing/JOGL stack.
        Scilab sci = new Scilab(true);
        check(sci.open(), "engine opened in advanced mode");

        check(sci.exec("a = 1 + 1;"), "interpreter executes");
        check(2.0 == ((org.scilab.modules.types.ScilabDouble) sci.get("a")).getRealPart()[0][0],
              "value reads back as 2.0");

        // The half that used to be impossible: a real figure, rendered and exported.
        // Quoting note: single quotes inside a double-quoted Scilab string would end
        // the string, so the path is passed through a variable.
        sci.put("pngpath", new org.scilab.modules.types.ScilabString(png.getAbsolutePath()));
        check(sci.exec("f = scf(); plot(1:10); xs2png(f, pngpath); close(f);"),
              "figure created, rendered, exported and closed");
        check(png.isFile() && png.length() > 0, "the exported PNG exists and is non-empty");

        check(sci.close(), "engine closed");
        png.delete();
        mark("PASS");
    }
}
