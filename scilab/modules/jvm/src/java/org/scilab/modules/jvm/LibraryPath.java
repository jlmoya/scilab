/*--------------------------------------------------------------------------*/
/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) INRIA - Allan CORNET
 * Copyright (C) 2012 - 2016 - Scilab Enterprises
 * Copyright (C) 2023 - Dassault Systèmes S.E. - Vincent COUVERT
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

/*--------------------------------------------------------------------------*/
package org.scilab.modules.jvm;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import jdk.internal.loader.NativeLibraries;
import sun.misc.Unsafe;

/**
 * LibraryPath to overload java.library.path.
 */
public class LibraryPath {

    private static final String JAVALIBRARYPATH = "java.library.path";
    /**
     * Constructor
     */
    protected LibraryPath() {
        /*  indicate that the requested operation is not supported */
        throw new UnsupportedOperationException();
    }
    /*--------------------------------------------------------------------------*/
    /**
     * checks if pathToAdd already exists
     * @param currentpaths list of current paths
     * @param pathToAdd path to add
     * @return a boolean true if path already exists
     */
    private static boolean pathAlreadyExists(String currentpaths, String pathToAdd) {
        String[] paths = currentpaths.split("" + File.pathSeparatorChar);
        for (String libraryPath : paths) {
            if (libraryPath.equalsIgnoreCase(pathToAdd)) {
                return true;
            }
        }
        return false;
    }
    /*--------------------------------------------------------------------------*/
    /**
     * add a path to java.library.path
     * @param p path to add
     * @throws IOException return a exception
     */
    public static void addPath(final String p) throws IOException {
        if (pathAlreadyExists(System.getProperty(JAVALIBRARYPATH), p)) {
            return;
        }
        /* The order matter here... see bug #4022 */
        System.setProperty(JAVALIBRARYPATH, System.getProperty(JAVALIBRARYPATH) + File.pathSeparator + p);
        try {
            /*
             * Append p to the JVM's cached native-library search paths, i.e. the static final
             * String[] jdk.internal.loader.NativeLibraries$LibraryPaths.USER_PATHS.
             *
             * JDK 18+ (JEP 416 - core reflection on method handles) forbids reflective writes to
             * static final fields (Field.set throws UnsupportedOperationException, even after the
             * old "clear the FINAL modifier" trick). So we read the array via reflection (reads of
             * final fields are still allowed) and write the new array via Unsafe, which is not
             * subject to that restriction and works on JDK 17 through 25.
             *
             * Best effort: a failure here must NOT abort Scilab startup. The java.library.path
             * property is already updated above; if a future JDK removes this avenue too, the
             * launcher must seed -Djava.library.path at JVM creation instead.
             */
            final Class<?> libraryPaths = Arrays.stream(NativeLibraries.class.getDeclaredClasses())
                                          .filter(klass -> klass.getSimpleName().equals("LibraryPaths"))
                                          .findFirst().get();
            final Field field = libraryPaths.getDeclaredField("USER_PATHS");
            field.setAccessible(true);
            final String[] paths = (String[]) field.get(null);
            final String[] newPaths = Arrays.copyOf(paths, paths.length + 1);
            newPaths[paths.length] = p;

            final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            final Unsafe unsafe = (Unsafe) theUnsafe.get(null);
            unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), newPaths);
        } catch (Throwable t) {
            System.err.println("[LibraryPath] could not patch " + JAVALIBRARYPATH
                               + " native search paths (" + p + "): " + t);
        }
    }

    /*--------------------------------------------------------------------------*/
    /**
     * get the scilab java.library.path.
     * @return librarypath
     */
    public static String[] getLibraryPath() {
        String librarypath = System.getProperty(JAVALIBRARYPATH);
        String[] paths = librarypath.split("" + File.pathSeparatorChar);
        return paths;
    }
}
/*--------------------------------------------------------------------------*/
