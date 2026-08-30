/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab GUI Designer
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

/*
 * Hand-written JNI bridge to org.scilab.modules.guibuilder.ui.GuiDesigner --
 * see GuiDesigner.giws.xml for the interface this reproduces, and this
 * module's CMakeLists.txt for why it is hand-written rather than generated
 * or @ScilabExported.
 *
 * DELIBERATELY DOES NOT USE modules/commons' GiwsException, unlike
 * modules/scinotes/src/jni/SciNotes.{hxx,cpp} (the mechanism this otherwise
 * mirrors, trimmed to the one static method GuiDesigner declares). Measured,
 * not assumed: running under -nwni, scilab-cli-bin links
 * libscicommons-disable.dylib, whose scilab_module(commons-disable) call
 * (modules/commons/CMakeLists.txt) compiles ONLY src/c/fileutils.c -- no
 * GiwsException.cpp -- so GiwsException's vtable/destructor symbols are
 * simply absent from the process and a dlopen of a dylib that references
 * them fails ("symbol not found in flat namespace") the moment this
 * module's real gateway is invoked. scinotes never hits this: it is in
 * FuncManager::CreateNonNwniModuleList's exclusion set, so under -nwni its
 * OWN gateway primitive is replaced by a generic dummy before this dylib is
 * ever dlopen'd, and its real, GiwsException-using code path never runs
 * there. guibuilder is NOT on that exclusion list (Task 7's placeholder has
 * no window to fail to open, and the brief requires it to actually work
 * under -nwni), so unlike scinotes this module's real code path DOES run
 * under -nwni -- and so it must not depend on something the -nwni process
 * image does not have. Plain JNI (ExceptionCheck/ExceptionDescribe/
 * ExceptionClear) costs nothing extra and works in both worlds.
 */

#ifndef __ORG_SCILAB_MODULES_GUIBUILDER_UI_GUIDESIGNER__
#define __ORG_SCILAB_MODULES_GUIBUILDER_UI_GUIDESIGNER__

#include <string>
#include <jni.h>

namespace org_scilab_modules_guibuilder_ui
{
class GuiDesigner
{
public:
    /**
     * Open the designer, on a file when one is given (GuiDesigner.giws.xml).
     * Never throws: returns false on any JNI-level failure (class/method
     * not found, a pending Java exception, jvm_ itself being NULL because
     * this process never started an embedded JVM -- always true under
     * -nwni/-nogui) as well as on a false from Java, describing the failure
     * on stderr first.
     * @param jvm_ the Scilab JVM (getScilabJavaVM(), which is NULL under
     *             -nwni/-nogui -- checked, not assumed non-NULL)
     * @param path a .sce to open, or the empty string for an empty designer
     * @return true when the tab was opened
     */
    static bool open(JavaVM* jvm_, char const* path);

    /**
     * Get the class name to use for static methods.
     * @return class name to use for static methods
     */
    static const std::string className()
    {
        return "org/scilab/modules/guibuilder/ui/GuiDesigner";
    }

    /**
     * Get the class to use for static methods.
     * @return class to use for static methods
     */
    static jclass initClass(JNIEnv* curEnv)
    {
        static jclass cls = 0;

        if (cls == 0)
        {
            jclass _cls = curEnv->FindClass(className().c_str());
            if (_cls)
            {
                cls = static_cast<jclass>(curEnv->NewGlobalRef(_cls));
            }
        }

        return cls;
    }
};

}
#endif
