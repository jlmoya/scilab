// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

#include "function.hxx"
#include "double.hxx"
#include "int.hxx"
#include "bool.hxx"
#include "string.hxx"
#include "cell.hxx"
#include "struct.hxx"
#include "list.hxx"
#include "xlsx_lib.hxx"
#include "spreadsheet_gw.hxx"

extern "C"
{
#include "Scierror.h"
#include "getversion.h"
#include "localization.h"
#include "sciprint.h"
}

#define FUNCTION_NAME "isXlsx"

types::Function::ReturnValue sci_isXlsx(types::typed_list &in, int _iRetCount, types::typed_list &out)
{
    types::Bool* pOut = new types::Bool(false);
    if (in.size() != 1)
    {
        Scierror(77, _("%s: Wrong number of input arguments: 1 expected.\n"), FUNCTION_NAME);
        out.push_back(pOut);
        return types::Function::Error;
    }

    if (!in[0]->isString())
    {
        Scierror(999, _("%s: Wrong type for input argument #%d: string expected.\n"), FUNCTION_NAME, 1);
        out.push_back(pOut);
        return types::Function::Error;
    }

    types::String* pStrFilename = in[0]->getAs<types::String>();
    if (pStrFilename->getSize() != 1)
    {
        Scierror(999, _("%s: Wrong size for input argument #%d: single string expected.\n"), FUNCTION_NAME, 1);
        out.push_back(pOut);
        return types::Function::Error;
    }

    char* filename = wide_string_to_UTF8(pStrFilename->get(0));
    if (!filename)
    {
        Scierror(999, _("%s: Memory allocation error.\n"), FUNCTION_NAME);
        out.push_back(pOut);
        return types::Function::Error;
    }

    int result = xlsx_valid_file(filename);
    free(filename);

    if (result != 1)
    {
        //! only for debug
        // switch (result)
        // {
        // case 0:
        //     Scierror(999, _("%s: File is empty or NULL.\n"), FUNCTION_NAME);
        //     break;
        // case -1:
        //     Scierror(999, _("%s: Cannot open file.\n"), FUNCTION_NAME);
        //     break;
        // case -2:
        //     Scierror(999, _("%s: The file contains no sheets.\n"), FUNCTION_NAME);
        //     break;
        // case -3:
        //     Scierror(999, _("%s: Cannot access the active sheet.\n"), FUNCTION_NAME);
        //     break;
        // case -4:
        //     Scierror(999, _("%s: XLNT library error while loading the file.\n"), FUNCTION_NAME);
        //     break;
        // case -5:
        //     Scierror(999, _("%s: Error while loading the file.\n"), FUNCTION_NAME);
        //     break;
        // case -6:
        //     Scierror(999, _("%s: Unknown error while loading the file.\n"), FUNCTION_NAME);
        //     break;
        // default:
        //     Scierror(999, _("%s: Unknown error.\n"), FUNCTION_NAME);
        //     break;
        // }
        out.push_back(pOut);
        return types::Function::OK;
    }

    pOut->setTrue();
    out.push_back(pOut);
    return types::Function::OK;
}