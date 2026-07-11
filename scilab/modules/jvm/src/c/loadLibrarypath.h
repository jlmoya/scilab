
/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) INRIA - Allan CORNET
 *
 * Copyright (C) 2012 - 2016 - Scilab Enterprises
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
#ifndef __LOADLIBRARYPATH_H__
#define __LOADLIBRARYPATH_H__

#include "BOOL.h" /* BOOL */

/**
* add paths to java.library.path
* @param a filename SCI/etc/librarypath.xml
* @return TRUE or FALSE
*/
BOOL LoadLibrarypath(char *xmlfilename);

/**
* Build the PATH_SEPARATOR-joined java.library.path from SCI/etc/librarypath.xml,
* including only directories that exist. Used to seed -Djava.library.path at JVM
* creation: with those paths already present, the post-boot LoadLibrarypath()
* additions short-circuit in LibraryPath.addPath() ("path already present")
* instead of reaching the deprecated sun.misc.Unsafe patch of the JVM's cached
* native search paths. A path we miss simply falls back to the old runtime
* mechanism, so seeding can never break native-library loading.
* @param sciPath the SCI path
* @return a newly allocated string the caller must FREE, or NULL
*/
char *getLibrarypathString(char *sciPath);

#endif /* __LOADLIBRARYPATH_H__ */
/*--------------------------------------------------------------------------*/
