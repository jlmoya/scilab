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
#include "string.hxx"
#include "xlsx_lib.hxx"
#include "spreadsheet_gw.hxx"

extern "C"
{
    #include "Scierror.h"
    #include "localization.h"
    #include "sciprint.h"
}

#define FUNCTION_NAME "xlsxSheet"

types::Function::ReturnValue sci_xlsxSheet(types::typed_list &in, int _iRetCount, types::typed_list &out)
{
    if (in.size() < 3 || in.size() > 5)
    {
        Scierror(77, _("%s: Wrong number of input arguments: 3 to 5 expected, got %d.\n"), FUNCTION_NAME, static_cast<int>(in.size()));
        return types::Function::Error;
    }

    if (!in[0]->isString())
    {
        Scierror(999, _("%s: Wrong type for input argument #%d: A string expected.\n"), FUNCTION_NAME, 1);
        return types::Function::Error;
    }

    types::String* pStrFilename = in[0]->getAs<types::String>();
    if (pStrFilename->getSize() != 1)
    {
        Scierror(999, _("%s: Wrong type for input argument #%d: A single string expected.\n"), FUNCTION_NAME, 1);
        return types::Function::Error;
    }
    char* filename = wide_string_to_UTF8(pStrFilename->get(0));

    if (!in[1]->isDouble())
    {
        free(filename);
        Scierror(999, _("%s: Wrong type for input argument #%d: A double expected.\n"), FUNCTION_NAME, 2);
        return types::Function::Error;
    }

    types::Double* pDblOp = in[1]->getAs<types::Double>();
    int operation = static_cast<int>(pDblOp->get(0));

    if (!in[2]->isString())
    {
        free(filename);
        Scierror(999, _("%s: Wrong type for input argument #%d: A string expected.\n"), FUNCTION_NAME, 3);
        return types::Function::Error;
    }

    types::String* pStrSheetName = in[2]->getAs<types::String>();
    if (pStrSheetName->getSize() != 1)
    {
        free(filename);
        Scierror(999, _("%s: Wrong type for input argument #%d: A single string expected.\n"), FUNCTION_NAME, 3);
        return types::Function::Error;
    }
    char* sheet_name = wide_string_to_UTF8(pStrSheetName->get(0));

    int sheet_index = -1;
    bool use_index = false;
    
    if (in.size() >= 4)
    {
        if (!in[3]->isDouble())
        {
            free(filename);
            free(sheet_name);
            Scierror(999, _("%s: Wrong type for input argument #%d: A double expected.\n"), FUNCTION_NAME, 4);
            return types::Function::Error;
        }
        types::Double* pDblIndex = in[3]->getAs<types::Double>();
        sheet_index = static_cast<int>(pDblIndex->get(0));

        if (sheet_index > 0)
        {
            use_index = true;
        }
    }

    char* new_sheet_name = nullptr;
    if (in.size() == 5)
    {
        if (!in[4]->isString())
        {
            free(filename);
            free(sheet_name);
            Scierror(999, _("%s: Wrong type for input argument #%d: A string expected.\n"), FUNCTION_NAME, 5);
            return types::Function::Error;
        }
        types::String* pStrNewSheetName = in[4]->getAs<types::String>();
        if (pStrNewSheetName->getSize() != 1)
        {
            free(filename);
            free(sheet_name);
            Scierror(999, _("%s: Wrong type for input argument #%d: A single string expected.\n"), FUNCTION_NAME, 5);
            return types::Function::Error;
        }
        new_sheet_name = wide_string_to_UTF8(pStrNewSheetName->get(0));
    }

    XlsxSheetOptions options;
    options.operation = static_cast<XlsxSheetOperation_t>(operation);
    options.sheet_name = sheet_name;
    options.sheet_index = sheet_index;
    options.use_index = use_index;
    if (new_sheet_name != nullptr)
    {
        options.new_sheet_name = new_sheet_name;
    }

    int result = xlsx_sheet(filename, &options);
    free(sheet_name);
    if (new_sheet_name != nullptr)
    {
        free(new_sheet_name);
    }

    if (result != 0)
    {
        switch (result)
        {
            case -1:
                Scierror(999, _("%s: Cannot open file '%s'.\n"), FUNCTION_NAME, filename);
                break;
            case -2:
                Scierror(999, _("%s: Sheet name already exists.\n"), FUNCTION_NAME);
                break;
            case -3:
                Scierror(999, _("%s: Sheet not found.\n"), FUNCTION_NAME);
                break;
            case -4:
                Scierror(999, _("%s: Invalid sheet index.\n"), FUNCTION_NAME);
                break;
            case -5:
                Scierror(999, _("%s: Cannot delete the only remaining sheet.\n"), FUNCTION_NAME);
                break;
            case -6:
                Scierror(999, _("%s: New sheet name already exists.\n"), FUNCTION_NAME);
                break;
            default:
                Scierror(999, _("%s: Error during operation (code: %d).\n"), FUNCTION_NAME, result);
                break;
        }
        free(filename);
        return types::Function::Error;
    }

    free(filename);

    types::Double* pOut = new types::Double(1.0);
    out.push_back(pOut);

    return types::Function::OK;
}
