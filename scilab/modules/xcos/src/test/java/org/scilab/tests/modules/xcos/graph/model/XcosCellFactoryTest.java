/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2011 - Scilab Enterprises - Clement DAVID
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
package org.scilab.tests.modules.xcos.graph.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.action_binding.highlevel.ScilabInterpreterManagement;
import org.scilab.modules.xcos.JavaController;
import org.scilab.modules.xcos.Kind;
import org.scilab.modules.xcos.block.BasicBlock;
import org.scilab.modules.xcos.graph.model.BlockInterFunction;
import org.scilab.modules.xcos.graph.model.XcosCellFactory;

/**
 * NOTE createOneSpecificBlock CANNOT PASS HERE, and this class stays excluded
 * from surefire by name (register B21, see pom.xml's
 * scilab.test.exclude.engine.xcos). The reason is environmental, not a defect:
 * XcosCellFactory.createBlock posts <pre>xcosCellCreated(BIGSOM_f("define"))</pre>
 * through synchronousScilabExec and then waits for Scilab to call notify()
 * back, which needs
 * <ol>
 * <li>a RUNNING interpreter — loadLibrary("scilab") below only maps the dylib,
 *     it starts no engine, so the command sat in a queue with no consumer and
 *     this test waited forever rather than failing;</li>
 * <li>an ADVANCED-mode one — this module's libscixcos links the REAL libscijvm
 *     while the NWNI libjavasci2 that -Pnative-tests puts first links
 *     libscijvm-disable, and loading both is what checkForLinkerErrors()
 *     calls exit(1) on;</li>
 * <li>loadXcosLibs() — engine startup does not load the xcos macros (the Xcos
 *     GUI does it itself), so BIGSOM_f is otherwise simply undefined and the
 *     factory reports "unable to allocate".</li>
 * </ol>
 * Advanced mode also wants the full etc/classpath.xml jar set, which surefire
 * cannot supply without restating those 86 entries in a pom. So the scenario
 * lives in <pre>modules/xcos/tests/native/run_xcos_cell_factory.sh</pre>, which
 * derives all of it from the files the real launcher reads and verifies the
 * same assertion this method makes. It passes: the product is correct.
 *
 * createAllSpecificBlocks needs none of that (reflection plus the MVC
 * controller) and runs fine; it is held back only because the exclusion is
 * per-class.
 */
public class XcosCellFactoryTest {
    private JavaController controller;

    @BeforeEach
    public void loadLibrary() {
        System.loadLibrary("scilab");
        controller = new JavaController();
    }

    @Test
    public void createOneSpecificBlock() throws ScilabInterpreterManagement.InterpreterException {
        final String interfaceFunction = "BIGSOM_f";
        BasicBlock blk = XcosCellFactory.createBlock(interfaceFunction);

        assert blk.getStyle().contains(interfaceFunction);
    }

    @Test
    public void createAllSpecificBlocks() throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException,
        IllegalArgumentException, InvocationTargetException {
        EnumSet<BlockInterFunction> blocks = EnumSet.allOf(BlockInterFunction.class);
        blocks.remove(BlockInterFunction.BASIC_BLOCK);

        for (BlockInterFunction b : blocks) {
            Constructor<? extends BasicBlock> cstr = b.getKlass().getConstructor(Long.TYPE);
            BasicBlock blk = cstr.newInstance(controller.createObject(Kind.BLOCK));

            // the block should have no children
            assert blk.getChildCount() == 0;
        }
    }
}
