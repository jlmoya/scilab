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

package org.scilab.modules.guibuilder.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;
import org.scilab.modules.guibuilder.write.DesignWriter;
import org.scilab.modules.guibuilder.write.SourceDocument;
import org.scilab.modules.guibuilder.write.SourceValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The byte-level half of the round-trip promise.
 *
 * <p>{@code CorpusRoundTripTest} proves the no-op save is byte-identical as
 * far as {@link DesignWriter}: source text in, the same source text out. That
 * says nothing about the two conversions on either side of it. Decoding with
 * {@code new String(bytes, UTF_8)} substitutes U+FFFD for every byte that is
 * not valid UTF-8, silently and without an exception, and re-encoding then
 * writes the replacement character's own three bytes back over the user's
 * data. Measured against a real file in this tree before this test was
 * written -- {@code SciLabProjects/casci/macros/skewness.sci}, which contains
 * "Universit&eacute;" as a single ISO-8859-1 {@code 0xE9}: 795 bytes on disk,
 * 797 bytes written back, first divergence at byte 142. The oracle still said
 * it parsed, the atomic write still succeeded, no dialog appeared, and the
 * original bytes were gone.
 *
 * <p>So the assertions below are on BYTES, through the whole save path the
 * tab uses -- read, parse, write, encode, atomic replace -- not on the String
 * in the middle of it.
 */
public class SourceFileTest {

    private static final SourceValidator ALWAYS_VALID = source -> true;

    /** The save path of {@code GuiDesignerTab#onSave}, minus Swing. */
    private static void noOpSave(Path file) throws Exception {
        SourceFile source = SourceFile.read(file);
        Design design = ScilabGuiParser.parse(source.text());
        String rendered = DesignWriter.write(design, new SourceDocument(design.source()), ALWAYS_VALID);
        AtomicFileWriter.write(file, source.encode(rendered));
    }

    /**
     * ISO-8859-1 source: a lone {@code 0xE9} where UTF-8 would need two
     * bytes. This is not a corner case invented for a test -- it is how every
     * pre-UTF-8 Scilab file with an accented character in a comment is
     * encoded, and this tree's own toolboxes contain them.
     */
    @Test
    public void aNoOpSaveOfALatin1FileLeavesEveryByteAlone(@TempDir Path dir) throws Exception {
        byte[] original = concat(
            "// Universit".getBytes(StandardCharsets.US_ASCII),
            new byte[] {(byte) 0xE9},
            (" de quelque part\n"
             + "f = figure(\"figure_name\", \"Demo\");\n"
             + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n")
            .getBytes(StandardCharsets.US_ASCII));

        Path file = dir.resolve("latin1.sce");
        Files.write(file, original);

        noOpSave(file);

        assertArrayEquals(original, Files.readAllBytes(file),
                          "a no-op save must not rewrite a byte of a file that is not UTF-8");
    }

    /** The same guarantee for the ordinary case, so the fallback cannot cost it. */
    @Test
    public void aNoOpSaveOfAUtf8FileLeavesEveryByteAlone(@TempDir Path dir) throws Exception {
        byte[] original = ("// Université de quelque part -- café, 日本語\n"
                           + "f = figure(\"figure_name\", \"Démo\");\n"
                           + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\");\n")
            .getBytes(StandardCharsets.UTF_8);

        Path file = dir.resolve("utf8.sce");
        Files.write(file, original);

        noOpSave(file);

        assertArrayEquals(original, Files.readAllBytes(file),
                          "a no-op save must not rewrite a byte of a UTF-8 file either");
    }

    @Test
    public void theCharsetIsRememberedRatherThanGuessedAgainAtSaveTime(@TempDir Path dir) throws Exception {
        Path latin1 = dir.resolve("latin1.sce");
        Files.write(latin1, new byte[] {'a', '=', (byte) 0xE9, '\n'});
        assertEquals(StandardCharsets.ISO_8859_1, SourceFile.read(latin1).charset());

        Path utf8 = dir.resolve("utf8.sce");
        Files.write(utf8, "a = é\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(StandardCharsets.UTF_8, SourceFile.read(utf8).charset());
    }

    /**
     * The decoded text has to be the real characters, not U+FFFD: the
     * designer shows it to the user and the parser reads it. A test that only
     * checked the bytes would pass for an implementation that read the file
     * as garbage and wrote the same garbage back.
     */
    @Test
    public void aLatin1ByteDecodesToItsRealCharacterNotAReplacement(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("latin1.sce");
        Files.write(file, concat("// caf".getBytes(StandardCharsets.US_ASCII),
                                 new byte[] {(byte) 0xE9},
                                 "\n".getBytes(StandardCharsets.US_ASCII)));
        String text = SourceFile.read(file).text();
        assertEquals("// café\n", text);
        assertTrue(text.indexOf('�') < 0, "no character may have been replaced");
    }

    /**
     * A file with no path at all -- the empty designer -- is not an I/O error.
     */
    @Test
    public void anEmptyDesignerHasNoBytesAndNoCharsetProblem() throws Exception {
        assertEquals("", SourceFile.empty().text());
        assertArrayEquals(new byte[0], SourceFile.empty().encode(""));
    }

    /**
     * Refusing loudly beats writing "?" over the user's text. A design that
     * gained a character its file's charset cannot represent (phase 2, when
     * the inspector can edit a string) must surface as an error the save path
     * already reports, not as silent substitution.
     */
    @Test
    public void aCharacterTheFilesCharsetCannotRepresentIsRefusedNotSubstituted(@TempDir Path dir)
            throws Exception {
        Path file = dir.resolve("latin1.sce");
        Files.write(file, new byte[] {'a', '=', (byte) 0xE9, '\n'});
        SourceFile source = SourceFile.read(file);
        IOException e = assertThrows(CharacterCodingException.class,
                                     () -> source.encode("a = 日\n"));
        assertTrue(e instanceof CharacterCodingException);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }
}
