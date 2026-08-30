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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Asks a real, packaged Scilab whether a piece of text parses as Scilab, by
 * actually handing it to one.
 *
 * <p>{@link org.scilab.modules.guibuilder.parse.ScilabGuiParser} understands
 * GUI-construction calls and deliberately nothing else, so it is exactly the
 * wrong tool to ask whether an entire rendered file is well-formed Scilab.
 * Scilab itself is the only authority on that question. This class writes
 * the candidate text to a temporary file, wrapped inside a throwaway
 * function definition (see {@link #wrap(String)} for why), and runs a
 * packaged Scilab on a small probe script that {@code exec}s the wrapped
 * file under {@code errcatch} and reports what came back.
 *
 * <p><b>One judgement is deliberate and is pinned by a test:</b> when Scilab
 * cannot be run at all -- the executable does not exist, cannot be started,
 * or does not answer inside the timeout -- {@link #isValidScilab} returns
 * {@code true}, not {@code false}. This class is an oracle, not an
 * authority: {@link DesignWriter}'s other guard (refusing an edit that
 * touches an unmodelled region) still applies regardless of this class, and
 * refusing every save because the oracle happens to be unavailable would be
 * a worse failure than the one this class exists to prevent. The same
 * reasoning covers a probe that runs but never produces a clean answer (for
 * instance a hard crash partway through startup): that is "cannot confirm",
 * not "confirmed invalid", so it degrades the same way.
 */
public final class Macr2TreeValidator implements SourceValidator {

    /**
     * Generous on purpose. This is a subprocess launch of a full Scilab --
     * observed, on this machine's own packaged build with its usual
     * autoloaded toolboxes, to take a few seconds even when the machine is
     * idle -- and a save must never wait forever on a hung probe. The
     * process is destroyed on expiry (see {@link #runProbe}) so a wedged
     * probe cannot wedge a save.
     */
    private static final long TIMEOUT_SECONDS = 60;

    /**
     * Deliberately obscure, so a candidate that happens to define a
     * function of its own can never collide with it.
     */
    private static final String WRAPPER_FUNCTION = "__guibuilder_macr2tree_validate_probe__";

    private static final String RESULT_PREFIX = "@@GUIBUILDER_MACR2TREE_RC=";

    private static final Pattern RESULT_LINE =
        Pattern.compile("(?m)^" + Pattern.quote(RESULT_PREFIX) + "(-?\\d+)\\s*$");

    private final String scilabExecutable;

    /**
     * @param scilabExecutable path to a Scilab launcher (for instance
     *                         {@code <SCI>/bin/scilab}, or a packaged
     *                         app's own launcher); may be {@code null} or
     *                         name nothing runnable, in which case every
     *                         call to {@link #isValidScilab} answers
     *                         {@code true} without attempting to run it
     */
    public Macr2TreeValidator(String scilabExecutable) {
        this.scilabExecutable = scilabExecutable;
    }

    @Override
    public boolean isValidScilab(String source) {
        if (scilabExecutable == null || !new File(scilabExecutable).canExecute()) {
            return true;
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("guibuilder-validate-");
            Path candidateFile = workDir.resolve("candidate.sci");
            Path probeFile = workDir.resolve("probe.sce");
            Path outputFile = workDir.resolve("output.txt");

            Files.write(candidateFile, wrap(source).getBytes(StandardCharsets.UTF_8));
            Files.write(probeFile, probeScript(candidateFile).getBytes(StandardCharsets.UTF_8));

            return runProbe(probeFile, outputFile);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        } catch (IOException | RuntimeException e) {
            // Never let a problem in the oracle itself refuse a save -- see
            // the class javadoc. Reported rather than swallowed, so a real
            // bug here still surfaces to whoever is watching Scilab's own
            // console, instead of only ever manifesting as "saves always
            // succeed" with no trace of why.
            System.err.println("[guibuilder] Macr2TreeValidator could not confirm validity, "
                                + "treating as valid: " + e);
            return true;
        } finally {
            if (workDir != null) {
                deleteRecursively(workDir);
            }
        }
    }

    /**
     * Wraps {@code source} inside a throwaway function definition so that
     * {@code exec}-ing the file below PARSES AND COMPILES it into a macro
     * without ever CALLING that macro.
     *
     * <p>This is load-bearing, not decorative. A rendered GUI file's top
     * level is not a function body -- it is a sequence of calls like
     * {@code f = figure(...)} and {@code uicontrol(...)} that a plain
     * {@code exec} would actually run. Every one of those constructors is
     * Java-backed, and {@code -nwni} never starts a JVM (the same fact that
     * governed the launch flags in Task 7), so a plain {@code exec} of an
     * unwrapped, perfectly well-formed GUI file fails for a reason that has
     * nothing to do with whether the text is valid Scilab -- confirmed
     * directly against a real packaged Scilab before this class was
     * written: an unwrapped top-level {@code figure()} call errors out
     * under {@code -nwni}, while the same call wrapped as below parses
     * cleanly, and a genuine syntax error inside the wrapped body still
     * surfaces as a compile failure of the wrapping function. Wrapping
     * turns "run this" into "define this", which is enough to make Scilab's
     * parser and compiler commit to every line without ever constructing a
     * widget.
     */
    private static String wrap(String source) {
        return "function " + WRAPPER_FUNCTION + "()\n" + source + "\nendfunction\n";
    }

    /**
     * The probe script: read {@code candidateFile} back with {@code exec}
     * under {@code errcatch} so a compile failure is caught and returned as
     * a number rather than raised, print that number behind a marker
     * distinctive enough to find in whatever else Scilab's own startup
     * writes to stdout (autoloaded toolboxes routinely print banners of
     * their own), and exit cleanly regardless of the result -- the verdict
     * travels in the marker line, not in the process exit code.
     */
    private static String probeScript(Path candidateFile) {
        String path = candidateFile.toAbsolutePath().toString().replace("\"", "\"\"");
        return "r = execstr(\"exec(\"\"" + path + "\"\", -1);\", \"errcatch\");\n"
             + "mprintf(\"" + RESULT_PREFIX + "%d\\n\", r);\n"
             + "exit(0);\n";
    }

    /**
     * Runs the probe with stdout and stderr merged into {@code outputFile},
     * waits up to {@link #TIMEOUT_SECONDS}, and forcibly kills the process
     * if it has not answered by then -- a hung probe must never hang a
     * save. Nothing is ever written to the probe's stdin, so it is closed
     * immediately rather than left open for a script that never reads it
     * anyway.
     */
    private boolean runProbe(Path probeFile, Path outputFile) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
            scilabExecutable, "-nwni", "-nb", "-f", probeFile.toAbsolutePath().toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(outputFile.toFile());

        Process process = builder.start();
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // The probe never reads its stdin either way.
        }

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return true;
        }

        String output = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8);
        Matcher matcher = RESULT_LINE.matcher(output);
        if (!matcher.find()) {
            // The probe ran to completion but never printed a clean
            // verdict -- a hard crash partway through, say. Unknown is not
            // the same as invalid; see the class javadoc.
            return true;
        }
        return Integer.parseInt(matcher.group(1)) == 0;
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort cleanup; the OS temp directory is swept regardless.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup; the OS temp directory is swept regardless.
        }
    }
}
