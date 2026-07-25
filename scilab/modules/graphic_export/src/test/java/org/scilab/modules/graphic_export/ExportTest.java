/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_export;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_export.Export.TYPE;

/**
 * Hermetic unit tests for the pure, static, side-effect-free surface of
 * {@link Export}:
 * <ul>
 *   <li>the public export-status integer constants,</li>
 *   <li>{@link Export#getType(String)} — the extension&nbsp;&rarr;&nbsp;numeric
 *       type-code lookup (a plain {@code HashMap} lookup, lower-cased),</li>
 *   <li>{@link Export#isBitmapFormat(Export.TYPE)} — the raster-vs-vector
 *       predicate,</li>
 *   <li>the {@link Export.TYPE} enum itself.</li>
 * </ul>
 *
 * <p>SCOPE / hermeticity note. Unlike {@code DriverTest} and
 * {@code ExportBitmapTest}, which deliberately avoid <em>loading</em> the
 * {@code Export} class (its full import list drags in batik / fop /
 * xmlgraphics / scirenderer / JoGL), this test <em>must</em> load it — those
 * are all compile-scope dependencies of the module and therefore on the test
 * classpath, so class-loading + static initialisation succeed. Static init
 * only populates two in-memory maps and a {@code TYPE[]} array; it touches no
 * native code, no {@code GraphicController} and no renderer. The methods
 * exercised here are equally inert. The heavyweight export pipeline
 * ({@code export}, {@code exportVectorial}, {@code exportBitmap},
 * {@code setVisitor}) and the private inner {@code Exporter} implementations
 * are intentionally out of scope: they need a live figure, a running
 * {@code GraphicController} and the JoGL/G2D renderer.</p>
 */
public class ExportTest {

    @TempDir
    File tempDir;

    // ------------------------------------------------------------------
    // Public status-code constants
    // ------------------------------------------------------------------

    @Test
    public void statusConstantsHaveTheirDocumentedValues() {
        assertAll(
            () -> assertEquals(0, Export.SUCCESS),
            () -> assertEquals(1, Export.IOEXCEPTION_ERROR),
            () -> assertEquals(2, Export.INVALID_FILE),
            () -> assertEquals(3, Export.MEMORY_ERROR),
            () -> assertEquals(4, Export.UNKNOWN_ERROR),
            () -> assertEquals(5, Export.FILENOTFOUND_ERROR)
        );
    }

    /**
     * Defect characterization: {@code NOWRITER_ERROR} and
     * {@code FILENOTFOUND_ERROR} are two <em>distinct</em> named error
     * conditions that share the same numeric value (5). Any caller that
     * {@code switch}es on the returned int (as {@code FileExporter} does)
     * therefore cannot tell them apart — a duplicate {@code case} label would
     * not even compile. This test pins that collision so a future
     * renumbering is a conscious, reviewed change rather than a silent one.
     */
    @Test
    public void noWriterErrorCollidesWithFileNotFoundError() {
        assertEquals(5, Export.NOWRITER_ERROR);
        assertEquals(Export.FILENOTFOUND_ERROR, Export.NOWRITER_ERROR,
                     "NOWRITER_ERROR and FILENOTFOUND_ERROR are indistinguishable (both 5)");
    }

    @Test
    public void successIsTheZeroSentinelDistinctFromEveryError() {
        assertEquals(0, Export.SUCCESS);
        // Every error code is non-zero and pairwise distinct from SUCCESS.
        int[] errors = {
            Export.IOEXCEPTION_ERROR, Export.INVALID_FILE, Export.MEMORY_ERROR,
            Export.UNKNOWN_ERROR, Export.FILENOTFOUND_ERROR
        };
        for (int e : errors) {
            assertNotEquals(Export.SUCCESS, e);
        }
    }

    // ------------------------------------------------------------------
    // getType(String)
    // ------------------------------------------------------------------

    @Test
    public void getTypeMapsEveryKnownExtensionToItsCode() {
        assertAll(
            () -> assertEquals(1, Export.getType("bmp")),
            () -> assertEquals(2, Export.getType("gif")),
            () -> assertEquals(3, Export.getType("jpeg")),
            () -> assertEquals(3, Export.getType("jpg")),
            () -> assertEquals(4, Export.getType("png")),
            () -> assertEquals(5, Export.getType("ppm")),
            () -> assertEquals(6, Export.getType("eps")),
            () -> assertEquals(7, Export.getType("pdf")),
            () -> assertEquals(8, Export.getType("svg")),
            () -> assertEquals(9, Export.getType("ps")),
            () -> assertEquals(9, Export.getType("pos")),
            () -> assertEquals(10, Export.getType("emf"))
        );
    }

    @Test
    public void getTypeTreatsJpgAndJpegAsTheSameCode() {
        assertEquals(Export.getType("jpeg"), Export.getType("jpg"));
    }

    @Test
    public void getTypeTreatsPsAndPosAsTheSameCode() {
        // "pos" is a legacy PostScript alias — it must resolve to the PS code.
        assertEquals(Export.getType("ps"), Export.getType("pos"));
    }

    @Test
    public void getTypeIsCaseInsensitive() {
        // getType() lower-cases its argument before the map lookup.
        assertAll(
            () -> assertEquals(4, Export.getType("PNG")),
            () -> assertEquals(4, Export.getType("Png")),
            () -> assertEquals(4, Export.getType("pNg")),
            () -> assertEquals(3, Export.getType("JPEG")),
            () -> assertEquals(10, Export.getType("EMF")),
            () -> assertEquals(8, Export.getType("SvG"))
        );
    }

    @Test
    public void getTypeReturnsMinusOneForUnknownExtensions() {
        assertAll(
            () -> assertEquals(-1, Export.getType("tiff")),
            () -> assertEquals(-1, Export.getType("webp")),
            () -> assertEquals(-1, Export.getType("xyz")),
            () -> assertEquals(-1, Export.getType(""))
        );
    }

    @Test
    public void getTypeDoesNotTrimOrStripTheArgument() {
        // The lookup is exact (after lower-casing): no whitespace trimming and
        // no leading-dot stripping happen, so these are all "unknown".
        assertAll(
            () -> assertEquals(-1, Export.getType(" png")),
            () -> assertEquals(-1, Export.getType("png ")),
            () -> assertEquals(-1, Export.getType(".png")),
            () -> assertEquals(-1, Export.getType("png\n"))
        );
    }

    @Test
    public void getTypeThrowsNpeOnNull() {
        // getType() calls ext.toLowerCase() with no null guard.
        assertThrows(NullPointerException.class, () -> Export.getType(null));
    }

    @Test
    public void getTypeReturnsOnlyKnownCodesOrMinusOne() {
        // Every successful lookup lands in the 1..10 range used to index the
        // internal type table; anything else is the -1 sentinel.
        String[] known = {"bmp", "gif", "jpeg", "jpg", "png", "ppm", "eps", "pdf", "svg", "ps", "pos", "emf"};
        for (String ext : known) {
            int code = Export.getType(ext);
            assertTrue(code >= 1 && code <= 10, ext + " -> " + code + " must be in [1,10]");
        }
    }

    // ------------------------------------------------------------------
    // isBitmapFormat(TYPE)
    // ------------------------------------------------------------------

    @Test
    public void isBitmapFormatIsTrueForRasterFormats() {
        assertAll(
            () -> assertTrue(Export.isBitmapFormat(TYPE.PNG)),
            () -> assertTrue(Export.isBitmapFormat(TYPE.JPEG)),
            () -> assertTrue(Export.isBitmapFormat(TYPE.GIF)),
            () -> assertTrue(Export.isBitmapFormat(TYPE.BMP)),
            () -> assertTrue(Export.isBitmapFormat(TYPE.PPM))
        );
    }

    @Test
    public void isBitmapFormatIsFalseForVectorFormats() {
        assertAll(
            () -> assertFalse(Export.isBitmapFormat(TYPE.SVG)),
            () -> assertFalse(Export.isBitmapFormat(TYPE.PS)),
            () -> assertFalse(Export.isBitmapFormat(TYPE.EPS)),
            () -> assertFalse(Export.isBitmapFormat(TYPE.PDF)),
            () -> assertFalse(Export.isBitmapFormat(TYPE.EMF))
        );
    }

    @Test
    public void isBitmapFormatPartitionsTheEnumExactly() {
        // The raster set and the vector set together cover every TYPE with no
        // overlap and no omission — a change to the enum that forgets to
        // update isBitmapFormat will trip this.
        Set<TYPE> raster = EnumSet.of(TYPE.PNG, TYPE.JPEG, TYPE.GIF, TYPE.BMP, TYPE.PPM);
        for (TYPE t : TYPE.values()) {
            assertEquals(raster.contains(t), Export.isBitmapFormat(t),
                         t + " raster classification");
        }
    }

    @Test
    public void isBitmapFormatReturnsFalseForNull() {
        // The body is a chain of reference comparisons (type == TYPE.PNG || ...)
        // with no unboxing, so a null argument matches no branch and yields
        // false rather than throwing — pinned here as the contract.
        assertFalse(Export.isBitmapFormat(null));
    }

    // ------------------------------------------------------------------
    // TYPE enum
    // ------------------------------------------------------------------

    @Test
    public void typeEnumHasExactlyTenValuesInDeclaredOrder() {
        TYPE[] values = TYPE.values();
        assertEquals(10, values.length);
        assertArrayEquals(
            new TYPE[] {TYPE.PNG, TYPE.JPEG, TYPE.GIF, TYPE.BMP, TYPE.PPM,
                        TYPE.SVG, TYPE.PS, TYPE.EPS, TYPE.PDF, TYPE.EMF},
            values);
    }

    @Test
    public void typeEnumOrdinalsAreStable() {
        assertAll(
            () -> assertEquals(0, TYPE.PNG.ordinal()),
            () -> assertEquals(1, TYPE.JPEG.ordinal()),
            () -> assertEquals(2, TYPE.GIF.ordinal()),
            () -> assertEquals(3, TYPE.BMP.ordinal()),
            () -> assertEquals(4, TYPE.PPM.ordinal()),
            () -> assertEquals(5, TYPE.SVG.ordinal()),
            () -> assertEquals(6, TYPE.PS.ordinal()),
            () -> assertEquals(7, TYPE.EPS.ordinal()),
            () -> assertEquals(8, TYPE.PDF.ordinal()),
            () -> assertEquals(9, TYPE.EMF.ordinal())
        );
    }

    @Test
    public void typeValueOfRoundTripsThroughName() {
        for (TYPE t : TYPE.values()) {
            assertEquals(t, TYPE.valueOf(t.name()));
        }
    }

    @Test
    public void typeValueOfRejectsUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> TYPE.valueOf("TIFF"));
    }

    @Test
    public void typeValueOfIsCaseSensitive() {
        // Enum valueOf is exact-match: the lower-case spelling is not a member.
        assertThrows(IllegalArgumentException.class, () -> TYPE.valueOf("png"));
    }

    @Test
    public void firstFiveTypesAreRasterAndLastFiveAreVector() {
        // Documents the (non-obvious but load-bearing) invariant that the enum
        // is declared raster-first: isBitmapFormat == (ordinal < 5).
        for (TYPE t : TYPE.values()) {
            assertEquals(t.ordinal() < 5, Export.isBitmapFormat(t),
                         t + " ordinal/raster relationship");
        }
    }

    // ------------------------------------------------------------------
    // exportVectorial(int, int, String, ExportParams, boolean) — file-guard
    // early returns
    //
    // Hermeticity: this int/int/String overload consults ONLY
    // Utils.checkWritePermission(File) before it would hand off to the
    // renderer-backed exportVectorial(int, TYPE, File, ...) overload. A null
    // file name (returns INVALID_FILE) and a file whose parent directory does
    // not exist (checkWritePermission's createNewFile throws IOException ->
    // IOEXCEPTION_ERROR) both return BEFORE that hand-off, so no
    // GraphicController / DrawerVisitor / G2D canvas is ever touched. The
    // numeric `type` argument is never used on these early-return paths (it
    // would only index the internal TYPE[] table on the happy path), so any
    // valid index serves.
    // ------------------------------------------------------------------

    @Test
    public void exportVectorialReturnsInvalidFileForNullFilename() {
        ExportParams params = new ExportParams();
        assertEquals(Export.INVALID_FILE,
                     Export.exportVectorial(-1, TYPE.SVG.ordinal(), (String) null, params, false));
        // headless flag makes no difference: the null guard precedes it.
        assertEquals(Export.INVALID_FILE,
                     Export.exportVectorial(-1, TYPE.SVG.ordinal(), (String) null, params, true));
    }

    @Test
    public void exportVectorialReturnsIoExceptionErrorWhenParentDirectoryMissing() {
        ExportParams params = new ExportParams();
        // "ghost/" does not exist -> createNewFile() throws IOException inside
        // checkWritePermission -> IOEXCEPTION_ERROR, returned before any render.
        File ghost = new File(tempDir, "ghost" + File.separator + "figure.svg");
        assertEquals(Export.IOEXCEPTION_ERROR,
                     Export.exportVectorial(-1, TYPE.SVG.ordinal(), ghost.getPath(), params, false));
        assertFalse(ghost.exists());
    }

    // ------------------------------------------------------------------
    // exportBitmap(int, int, String, boolean, ExportParams) — file-guard
    // early returns (same hermetic reasoning as above).
    // ------------------------------------------------------------------

    @Test
    public void exportBitmapReturnsInvalidFileForNullFilename() {
        ExportParams params = new ExportParams();
        assertEquals(Export.INVALID_FILE,
                     Export.exportBitmap(-1, TYPE.PNG.ordinal(), (String) null, true, params));
        assertEquals(Export.INVALID_FILE,
                     Export.exportBitmap(-1, TYPE.PNG.ordinal(), (String) null, false, params));
    }

    @Test
    public void exportBitmapReturnsIoExceptionErrorWhenParentDirectoryMissing() {
        ExportParams params = new ExportParams();
        File ghost = new File(tempDir, "ghost" + File.separator + "shot.png");
        assertEquals(Export.IOEXCEPTION_ERROR,
                     Export.exportBitmap(-1, TYPE.PNG.ordinal(), ghost.getPath(), true, params));
        assertFalse(ghost.exists());
    }
}
