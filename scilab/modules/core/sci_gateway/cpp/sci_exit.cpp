/*
 *  Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *  Copyright (C) 2010-2010 - DIGITEO - Bruno JOFRET
 *  Copyright (C) 2015 - Scilab Enterprises - Anais AUBERT
 *  Copyright (C) 2015 - Scilab Enterprises - Cedric Delamarre
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

#include "core_gw.hxx"
#include "function.hxx"
#include "configvariable.hxx"
#include "double.hxx"

extern "C"
{
#include "localization.h"
#include "Scierror.h"
#include "sciprint.h"
#include "configvariable_interface.h"
#include "createMainScilabObject.h"
}

types::Function::ReturnValue sci_exit(types::typed_list &in, int _iRetCount, types::typed_list &out)
{

    BOOL shouldExit = TRUE;
    double dExit = 0;
    // in[0] Should be a scalar double value.
    if (in.size() > 1)
    {
        Scierror(999, _("%s: Wrong number of input argument(s): %d to %d expected."), "exit", 0, 1);
        return types::Function::Error;
    }

    if (in.size() != 0)
    {
        types::InternalType* pIT = in[0];
        if (pIT->isDouble() == false)
        {
            Scierror(999, _("%s: Wrong type for input argument #%d: A scalar expected.\n"), "exit", 1);
            return types::Function::Error;
        }

        types::Double* pD = pIT->getAs<types::Double>();
        if (pD->isScalar() == false)
        {
            Scierror(999, _("%s: Wrong size for input argument #%d: A scalar expected.\n"), "exit", 1);
            return types::Function::Error;
        }

        dExit = pD->get(0);
        if (dExit != (int) dExit)
        {
            Scierror(999, _("%s: Wrong value for input argument #%d: An integer value expected.\n"), "exit", 1);
            return types::Function::Error;
        }
    }

    // A batch quit (-quit, or EOF on redirected stdin) queues a bare exit().
    // It is not a user asking to leave, so it never takes no for an answer, and
    // it reports the status of the run rather than 0.
    bool batchQuit = in.size() == 0 && ConfigVariable::getExitOnUncaughtError();

    if (ConfigVariable::getScilabMode() != SCILAB_NWNI)
    {
        if (in.size() == 0 && batchQuit == false)
        {
            shouldExit = canCloseMainScilabObject();
        }
        else
        {
            // Batch quits force-close like the argument form always did: the
            // command this replaced was exit(<number>), so it landed here.
            // Routing it through canCloseMainScilabObject() instead would let
            // the GUI refuse, and -quit would not quit.
            forceCloseMainScilabObject();
        }
    }

    // 0 unless some command ended in an UNCAUGHT error. An explicit exit(n) in
    // the script still wins, via the block below.
    if (batchQuit)
    {
        dExit = ConfigVariable::getUncaughtErrorStatus();
    }

    // exit have already been called,
    // keep the last status if defferent than 0
    // or exit with the new status.
    if(ConfigVariable::getExitStatus() != 0)
    {
        dExit = ConfigVariable::getExitStatus();
    }

    if (shouldExit)
    {
        ConfigVariable::setExitStatus((int)dExit);
        ConfigVariable::setForceQuit(true);
        // go out without continue any execution
        throw ast::InternalAbort();
    }

    return types::Function::OK;
}
