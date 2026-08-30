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
 * See GuiDesigner.hxx for what this mirrors, and why it handles JNI errors
 * itself (ExceptionCheck/ExceptionDescribe/ExceptionClear) rather than
 * throwing modules/commons' GiwsException as modules/scinotes/src/jni/
 * SciNotes.cpp and modules/gui/src/jni/EditorManager.cpp (isModifyEnabled,
 * also a static boolean-returning method) do.
 */

#include "GuiDesigner.hxx"

#include <cstdio>

namespace org_scilab_modules_guibuilder_ui
{

bool GuiDesigner::open(JavaVM* jvm_, char const* path)
{
    if (jvm_ == NULL)
    {
        // getScilabJavaVM() returns NULL whenever this process never started
        // an embedded JVM -- notably always true under -nwni/-nogui
        // (modules/call_scilab/src/c/call_scilab.c sets iNoJvm from
        // getScilabMode() == SCILAB_NWNI, which modules/core/src/cpp/
        // InitScilab.cpp uses to skip InitializeJVM() entirely; there is no
        // primitive that starts one later in that same process). Caught
        // here too, defensively, in case a future caller of this bridge
        // does not already check (sci_guidesigner.cpp does).
        fprintf(stderr, "guidesigner: no Java virtual machine is available in this Scilab session\n");
        return false;
    }

    JNIEnv* curEnv = NULL;
    jvm_->AttachCurrentThread(reinterpret_cast<void**>(&curEnv), NULL);

    jclass cls = initClass(curEnv);
    if (cls == NULL)
    {
        fprintf(stderr, "guidesigner: could not find class %s\n", className().c_str());
        return false;
    }

    jmethodID openID = curEnv->GetStaticMethodID(cls, "open", "(Ljava/lang/String;)Z");
    if (openID == NULL)
    {
        if (curEnv->ExceptionCheck())
        {
            curEnv->ExceptionDescribe();
            curEnv->ExceptionClear();
        }
        fprintf(stderr, "guidesigner: could not find GuiDesigner.open(String)\n");
        return false;
    }

    jstring path_ = curEnv->NewStringUTF(path);
    if (path != NULL && path_ == NULL)
    {
        fprintf(stderr, "guidesigner: out of memory building the path argument\n");
        return false;
    }

    jboolean res = curEnv->CallStaticBooleanMethod(cls, openID, path_);

    if (path_ != NULL)
    {
        curEnv->DeleteLocalRef(path_);
    }

    if (curEnv->ExceptionCheck())
    {
        curEnv->ExceptionDescribe();
        curEnv->ExceptionClear();
        return false;
    }

    return (res == JNI_TRUE);
}

}
