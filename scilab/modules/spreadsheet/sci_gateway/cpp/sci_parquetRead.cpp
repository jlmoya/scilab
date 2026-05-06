/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
 * Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
 *
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

#include <limits>
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <cstdio>
#include <ctime>
#include <iomanip>
#include <sstream>

#include "spreadsheet_gw.hxx"
#include "function.hxx"
#include "double.hxx"
#include "int.hxx"
#include "bool.hxx"
#include "string.hxx"
#include "cell.hxx"


extern "C"
{
    #include "Scierror.h"
    #include "sciprint.h"
    #include "localization.h"
    #include "arrow_lib.h"
}

/* ==================================================================== */
types::Function::ReturnValue sci_parquetRead(types::typed_list &in, int _iRetCount, types::typed_list &out)
{
    if (in.size() != 1)
    {
        Scierror(999, _("%s: Wrong number of input arguments: %d expected.\n"), "parquetRead", 1);
        return types::Function::Error;
    }

    if (in[0]->isString() == false)
    {
        Scierror(999, _("%s: Invalid type for argument #%d: string expected.\n"), "parquetRead", 1);
        return types::Function::Error;
    }

    types::String* pStr = in[0]->getAs<types::String>();
    char* filename = wide_string_to_UTF8(pStr->get(0));

    ArrowData result = arrow_read(filename);

    if (result.status != 0)
    {
        FREE(filename);
        Scierror(999, _("%s: Error reading file, code: %d\n"), "parquetRead", result.status);
        return types::Function::Error;
    }

    out.push_back(result.pData);
    out.push_back(result.pColNames);
    out.push_back(result.pColTypes);
    FREE(filename);
    return types::Function::OK;
}
