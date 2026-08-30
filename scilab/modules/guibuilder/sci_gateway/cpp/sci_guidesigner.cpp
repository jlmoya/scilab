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

#include "GuiDesigner.hxx"

extern "C"
{
#include "api_scilab.h"
#include "Scierror.h"
#include "localization.h"
#include "getScilabJavaVM.h"
#include "BOOL.h"
}

/*--------------------------------------------------------------------------*/
/*
 * guidesigner_open(path) -- the primitive modules/guibuilder/macros/
 * guidesigner.sci calls after validating its argument. Mirrors the shape of
 * modules/gui/sci_gateway/cpp/sci_setlookandfeel.cpp (single string in,
 * optional boolean out), calling into the hand-written JNI bridge in
 * src/jni/GuiDesigner.{hxx,cpp} instead of instantiating a GIWS object,
 * since org.scilab.modules.guibuilder.ui.GuiDesigner.open is static.
 * GuiDesigner::open() never throws (see GuiDesigner.hxx for why), so unlike
 * sci_scinotes.cpp there is no try/catch here.
 *
 * extern "C" on the definition below is load-bearing, not decoration: the
 * gateway is found at runtime by dlsym()'ing the exact string
 * "sci_guidesigner_open" (guibuilder_gateway.xml's gateway/@name, looked up
 * by modules/functions_manager/src/cpp/dynamic_modules.cpp's
 * DynamicFunction machinery) -- without C linkage this would compile fine
 * but export the C++-mangled name instead, and dlsym would report the
 * unmangled name "not found" only the first time the primitive is actually
 * called, well after a successful build. Reproduced and fixed while wiring
 * this module in. sci_scinotes.cpp gets the same linkage differently: its
 * definition is plain, but gw_scinotes.h's matching prototype (included
 * inside an extern "C" block earlier in that file) already fixed the
 * linkage before the compiler ever reaches the definition -- one prototype
 * declaration is enough to bind a name to C linkage for the rest of the
 * translation unit. This module has no separate gw_guibuilder.h (nothing
 * else needs to declare this prototype), so the extern "C" sits directly on
 * the definition instead.
 */
extern "C" int sci_guidesigner_open(char* fname, void* pvApiCtx)
{
    SciErr sciErr;
    int* piAddr = NULL;
    char* path = NULL;
    int result = FALSE;

    CheckInputArgument(pvApiCtx, 1, 1);
    CheckOutputArgument(pvApiCtx, 0, 1);

    sciErr = getVarAddressFromPosition(pvApiCtx, 1, &piAddr);
    if (sciErr.iErr)
    {
        printError(&sciErr, 0);
        Scierror(999, _("%s: Can not read input argument #%d.\n"), fname, 1);
        return 1;
    }

    if (!checkInputArgumentType(pvApiCtx, 1, sci_strings))
    {
        Scierror(999, _("%s: Wrong type for input argument #%d: string expected.\n"), fname, 1);
        return 1;
    }

    if (getAllocatedSingleString(pvApiCtx, piAddr, &path))
    {
        Scierror(999, _("%s: No more memory.\n"), fname);
        return 1;
    }

    if (getScilabJavaVM() == NULL)
    {
        // Always true under -nwni/-nogui: those modes never start an
        // embedded JVM (see GuiDesigner.cpp's matching check for the exact
        // mechanism), and this feature is Java-backed by nature. Caught
        // here, before the call, so the failure is a clear Scilab error
        // instead of a null JavaVM* reaching the JNI bridge.
        freeAllocatedSingleString(path);
        Scierror(999, _("%s: no Java virtual machine is available in this Scilab session.\n"), fname);
        return 1;
    }

    result = booltoBOOL(org_scilab_modules_guibuilder_ui::GuiDesigner::open(getScilabJavaVM(), path));

    freeAllocatedSingleString(path);

    if (createScalarBoolean(pvApiCtx, nbInputArgument(pvApiCtx) + 1, result))
    {
        Scierror(999, _("%s: Memory allocation error.\n"), fname);
        return 1;
    }

    AssignOutputVariable(pvApiCtx, 1) = nbInputArgument(pvApiCtx) + 1;
    ReturnArguments(pvApiCtx);
    return 0;
}
/*--------------------------------------------------------------------------*/
