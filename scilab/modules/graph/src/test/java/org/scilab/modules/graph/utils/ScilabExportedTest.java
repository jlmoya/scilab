/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graph.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;

/**
 * Hermetic reflection tests documenting the declared contract of the
 * {@link ScilabExported} marker annotation.
 */
public class ScilabExportedTest {

    @Test
    public void isAnAnnotationType() {
        assertTrue(ScilabExported.class.isAnnotation());
    }

    @Test
    public void declaresStringModuleAndFilenameMembers() throws Exception {
        assertEquals(String.class,
                     ScilabExported.class.getDeclaredMethod("module").getReturnType());
        assertEquals(String.class,
                     ScilabExported.class.getDeclaredMethod("filename").getReturnType());
    }

    @Test
    public void neitherMemberHasADefaultValue() throws Exception {
        // Both attributes are mandatory: no default is declared.
        assertNull(ScilabExported.class.getDeclaredMethod("module").getDefaultValue());
        assertNull(ScilabExported.class.getDeclaredMethod("filename").getDefaultValue());
    }

    @Test
    public void targetsMethodsAndConstructors() {
        Target target = ScilabExported.class.getAnnotation(Target.class);
        assertNotNull(target);
        assertArrayEquals(new ElementType[] {ElementType.METHOD, ElementType.CONSTRUCTOR},
                          target.value());
    }

    @Test
    public void hasNoExplicitRetention_defaultsToClass() {
        // No @Retention is declared, so the default (CLASS, not RUNTIME) applies:
        // the annotation is a build-time/giws marker, not reflectively visible on usages.
        assertNull(ScilabExported.class.getAnnotation(Retention.class));
    }
}
