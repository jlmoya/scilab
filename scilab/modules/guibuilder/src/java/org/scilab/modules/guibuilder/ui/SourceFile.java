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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@code .sce} read off disk, together with <em>the charset it was decoded
 * with</em>, so that saving it writes the same bytes back rather than the
 * bytes of whatever charset the code happened to assume.
 *
 * <p><b>Why this class exists.</b> The obvious pair -- {@code new
 * String(bytes, UTF_8)} to read and {@code String#getBytes(UTF_8)} to write
 * -- silently corrupts every file that is not UTF-8. That constructor does
 * not report a malformed byte; it substitutes U+FFFD for it, and the
 * re-encode then writes that replacement character's own three bytes over the
 * user's data. Measured against a real file in this tree before this class
 * was written: a {@code .sci} carrying "Universit&eacute;" as a single
 * ISO-8859-1 {@code 0xE9} went in at 795 bytes and came back out at 797, with
 * the first divergence at byte 142 -- and nothing anywhere in the save path
 * noticed. The parse-back oracle still said it parsed, the atomic write still
 * succeeded, and no dialog appeared. For a phase whose headline promise is
 * "open a file and save it back byte for byte", that is the worst possible
 * defect, and it needs no editing UI to trigger: an open-then-save is enough.
 *
 * <p><b>The rule.</b> Decode strictly as UTF-8 ({@link
 * CodingErrorAction#REPORT}, so a malformed byte raises rather than
 * substitutes). If that fails, fall back to ISO-8859-1, which is total -- all
 * 256 byte values map to a character -- so it always succeeds and, crucially,
 * round-trips every byte unchanged. The chosen charset is then remembered on
 * this object and used to encode on the way back out, so the two ends of the
 * round trip cannot disagree.
 *
 * <p><b>Why not {@code ScilabEditorKit.tryToGuessEncoding}.</b> SciNotes
 * solves the same problem, and reusing it was considered first. It would
 * oblige {@code guibuilder} to take a Maven dependency on {@code scinotes}
 * for one static method -- a dependency this module deliberately does not
 * have (see {@code ScilabTokenStream}'s javadoc for the same call made about
 * its lexer) -- and its extra windows-1252 rung buys nothing here: the fallback
 * only has to be lossless on the way back out, which ISO-8859-1 is and
 * windows-1252 is not, since five byte values are unmapped in it. The
 * strict-then-fallback rule below is the part of that class that matters,
 * implemented in eight lines and testable without a UI.
 *
 * <p><b>Encoding refuses rather than substitutes.</b> {@link
 * #encode(String)} reports an unmappable character instead of writing
 * {@code "?"} over it. Phase 1 makes no edits so it cannot happen yet; when
 * phase 2's inspector can put a character into a Latin-1 file that Latin-1
 * cannot represent, the save must fail visibly -- the caller already turns an
 * {@link IOException} into a dialog -- rather than quietly mangle the file.
 */
final class SourceFile {

    private final String text;
    private final Charset charset;

    private SourceFile(String text, Charset charset) {
        this.text = text;
        this.charset = charset;
    }

    /**
     * Reads {@code path}, decoding strictly as UTF-8 and falling back to
     * ISO-8859-1 when that is not what the file holds.
     *
     * @throws IOException if the file cannot be read at all
     */
    static SourceFile read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return new SourceFile(decode(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        } catch (MalformedInputException | UnmappableCharacterException e) {
            // Not UTF-8. ISO-8859-1 maps every one of the 256 byte values, so
            // this cannot fail and cannot lose a byte on the way back out.
            return new SourceFile(decode(bytes, StandardCharsets.ISO_8859_1),
                                  StandardCharsets.ISO_8859_1);
        }
    }

    /** The empty designer: no file, nothing to decode, nothing to preserve. */
    static SourceFile empty() {
        return new SourceFile("", StandardCharsets.UTF_8);
    }

    String text() {
        return text;
    }

    Charset charset() {
        return charset;
    }

    /**
     * Encodes {@code rendered} in this file's own charset.
     *
     * @throws CharacterCodingException if the text contains a character this
     *         file's charset cannot represent -- reported, never substituted
     */
    byte[] encode(String rendered) throws IOException {
        return encode(rendered, charset);
    }

    /**
     * The same encoding rule for a caller that kept only the charset -- the
     * designer tab holds the source text in its {@code Design} already, so
     * carrying a second copy of it around just to save would be waste.
     *
     * @throws CharacterCodingException if {@code charset} cannot represent
     *         some character of {@code rendered}
     */
    static byte[] encode(String rendered, Charset charset) throws IOException {
        CharsetEncoder encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer encoded = encoder.encode(CharBuffer.wrap(rendered));
        byte[] out = new byte[encoded.remaining()];
        encoded.get(out);
        return out;
    }

    private static String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }
}
