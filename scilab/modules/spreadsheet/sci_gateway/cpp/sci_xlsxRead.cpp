// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

#include "numericconstants.hxx"
#include "overload.hxx"
#include "function.hxx"
#include "double.hxx"
#include "bool.hxx"
#include "string.hxx"
#include "cell.hxx"
#include "xlsx_lib.hxx"
#include "spreadsheet_gw.hxx"

extern "C"
{
    #include "Scierror.h"
    #include "localization.h"
}


types::InternalType* convert_cell_to_scilab_type(const XlsxCell& cell)
{
    switch (cell.type) {
        case XLSX_TYPE_DOUBLE: {
            double value = cell.dblValue;
            return new types::Double(value);
        }

        case XLSX_TYPE_BOOL: {
            bool value = (cell.value == "true" || cell.value == "1");
            return new types::Bool(value);
        }

        case XLSX_TYPE_DURATION: {
            types::typed_list pOut;
            types::typed_list in;
            // number in milliseconds
            double value = cell.dblValue * MS_BY_DAY * 1000;
            double integer_part;
            if (std::modf(value, &integer_part) > 0.5)
            {
                value += 1;
            }

            types::Double* dura = new types::Double(std::floor(value)/1000);
            in.push_back(dura);
            Overload::call(L"milliseconds", in, 1, pOut);
            types::Double* pDDuration = pOut[0]->getAs<types::Double>();
            return pDDuration;
        }

        case XLSX_TYPE_TIMESTAMP: {
            types::typed_list pOut;
            types::typed_list in;
            // datetime(dt, "ConvertFrom", "excel")
            types::Double* datetime = new types::Double(cell.dblValue);
            in.push_back(datetime);
            in.push_back(new types::String(L"ConvertFrom"));
            in.push_back(new types::String(L"excel"));
            Overload::call(L"datetime", in, 1, pOut);
            types::Double* pDDatetime = pOut[0]->getAs<types::Double>();
            return pDDatetime;
        }

        case XLSX_TYPE_STRING:
        default: {
            return new types::String(cell.value.c_str());
        }
    }
}

double conversion_in_double(const XlsxCell& cell)
{
    switch (cell.type) {
        case XLSX_TYPE_DOUBLE: {
            return cell.dblValue;
        }   
        case XLSX_TYPE_BOOL: {
            if (cell.value == "true" || cell.value == "1")
            {
                return 1;
            }
            return 0;
        }

        case XLSX_TYPE_DURATION: {
            // convert in hours (Scilab)
            return cell.dblValue * 24;
        }

        case XLSX_TYPE_TIMESTAMP: {
            // convert scilab to Excel 0000-01-01 -> 1900-01-01
            // 693960 days (from year 0 to 1900)
            return cell.dblValue + 693960.0;
        }

        case XLSX_TYPE_STRING:
        default: {
            return NumericConstants::nan;
        }
    }
}

std::string conversion_in_string(const XlsxCell& cell)
{
    switch (cell.type) {
        case XLSX_TYPE_DOUBLE: {
            return std::to_string(cell.dblValue);
        }  
        case XLSX_TYPE_BOOL: {
            return cell.value;
        }
        case XLSX_TYPE_DURATION: {
            types::typed_list pOut;
            types::typed_list in;
            // duration(dura)
            double value = cell.dblValue * MS_BY_DAY * 1000;
            double integer_part;
            if (std::modf(value, &integer_part) > 0.5)
            {
                value += 1;
            }

            types::Double* dura = new types::Double(std::floor(value) / 1000);
            in.push_back(dura);
            Overload::call(L"milliseconds", in, 1, pOut);
            types::Double* pDDuration = pOut[0]->getAs<types::Double>();
            types::typed_list in2 = {pDDuration};
            types::typed_list pOut2;
            Overload::call(L"%duration_string", in2, 1, pOut2);
            types::String* pStr = pOut2[0]->getAs<types::String>();
            char* str = wide_string_to_UTF8(pStr->get(0));
            std::string duration = std::string(str);
            free(str);
            return duration;
        }
        case XLSX_TYPE_TIMESTAMP: {
            types::typed_list pOut;
            types::typed_list in;
            // datetime(dt, "ConvertFrom", "excel", "OutputFormat", "yyyy-MM-dd HH:mm:ss.SSS")
            types::Double* datetime = new types::Double(cell.dblValue);
            in.push_back(datetime);
            in.push_back(new types::String(L"ConvertFrom"));
            in.push_back(new types::String(L"excel"));
            in.push_back(new types::String(L"OutputFormat"));
            in.push_back(new types::String(L"yyyy-MM-dd HH:mm:ss.SSS"));
            Overload::call(L"datetime", in, 1, pOut);
            types::Double* pDDatetime = pOut[0]->getAs<types::Double>();
            types::typed_list in2 = {pDDatetime};
            types::typed_list pOut2;
            Overload::call(L"%datetime_string", in2, 1, pOut2);
            types::String* pStr = pOut2[0]->getAs<types::String>();
            char* str = wide_string_to_UTF8(pStr->get(0));
            std::string dt = std::string(str);
            free(str);
            return dt;
        }
        case XLSX_TYPE_STRING:
        default: {
            return cell.value;
        }
    }
}


types::Function::ReturnValue sci_xlsxRead(types::typed_list &in, int _iRetCount, types::typed_list &out)
{
    types::String* filename_str = NULL;
    XlsxWriteOptions options;
    if (in.size() != 4)
    {
        Scierror(77, _("%s: Wrong number of input arguments: %d expected.\n"), "xlsxRead", 4);
        return types::Function::Error;
    }

    // filename
    if (!in[0]->isString())
    {
        Scierror(999, _("%s: Wrong type for input argument #%d: A string expected.\n"), "xlsxRead", 1);
        return types::Function::Error;
    }

    filename_str = in[0]->getAs<types::String>();
    if (filename_str->getSize() != 1)
    {
        Scierror(999, _("%s: Wrong size for input argument #%d: A single string expected.\n"), "xlsxRead", 1);
        return types::Function::Error;
    }

    char* filename = wide_string_to_UTF8(filename_str->get(0));
    if (!filename)
    {
        Scierror(999, _("%s: Memory allocation error.\n"), "xlsxRead");
        return types::Function::Error;
    }

    // sheet
    if (in[1]->isString())
    {
        types::String* sheet_str = in[1]->getAs<types::String>();
        if (sheet_str->getSize() != 1)
        {
            Scierror(999, _("%s: Wrong size for input argument #%d: A single string expected.\n"), "xlsxRead", 2);
            return types::Function::Error;
        }

        char* sheet_name = wide_string_to_UTF8(sheet_str->get(0));
        if (sheet_name)
        {
            if (sheet_name[0] != '\0')
            {
                options.sheet_name = std::string(sheet_name);
            }  
            else
            {
                options.sheet_index = 0;
            }  
            free(sheet_name);
        }
    }
    else if (in[1]->isDouble())
    {
        types::Double* sheet_idx = in[1]->getAs<types::Double>();
        if (sheet_idx->getSize() != 1)
        {
            Scierror(999, _("%s: Wrong size for input argument #%d: A double scalar expected.\n"), "xlsxRead", 2);
            return types::Function::Error;
        }

        double idx_val = sheet_idx->get(0);

        if (idx_val < 1 || idx_val != static_cast<int>(idx_val))
        {
            Scierror(999, _("%s: Wrong value for input argument #%d: Sheet index must be a positive integer >= 1.\n"), "xlsxRead", 2);
            return types::Function::Error;
        }

        options.sheet_index = static_cast<int>(idx_val) - 1; // convert en 0-based
    }
    else
    {
        Scierror(999, _("%s: Wrong type for input argument #%d: A string or double expected.\n"), "xlsxRead", 2);
        return types::Function::Error;
    }


    // range
    if (!in[2]->isString())
    {
        Scierror(999, _("%s: Wrong type for input argument #%d: A string expected.\n"), "xlsxRead", 3);
        return types::Function::Error;
    }

    types::String* range_str = in[2]->getAs<types::String>();
    if (range_str->getSize() != 1)
    {
        Scierror(999, _("%s: Wrong size for input argument #%d: A single string expected.\n"), "xlsxRead", 3);
        return types::Function::Error;
    }

    char* range_name = wide_string_to_UTF8(range_str->get(0));
    if (range_name)
    {
        options.range = std::string(range_name);
        free(range_name);
    }

    // conversion
    if (!in[3]->isString())
    {
        Scierror(999, _("%s: Wrong type for input argument #%d: A string expected.\n"), "xlsxRead", 4);
        return types::Function::Error;
    }

    std::wstring conversionType = in[3]->getAs<types::String>()->get(0);
    if (conversionType != L"double" && conversionType != L"string" && conversionType != L"cell")
    {
        Scierror(999, _("%s: Wrong value for input argument #%d: ""%s"", ""%s"" or ""%s"" expected.\n"), "xlsxRead", 4, "double", "string", "cell");
        return types::Function::Error;
    }

    XlsxData data;
    int result = xlsx_read(filename, &data, &options);
    free(filename);

    if (result != 0)
    {
        xlsx_free_data(&data);
        switch (result)
        {
            case -1:
                Scierror(999, _("%s: Invalid parameters.\n"), "xlsxRead");
                break;
            case -2:
                Scierror(999, _("%s: XLNT library error.\n"), "xlsxRead");
                break;
            case -3:
                Scierror(999, _("%s: General error reading file.\n"), "xlsxRead");
                break;
            case -4:
                Scierror(999, _("%s: Unknown error reading file.\n"), "xlsxRead");
                break;
            case -5:
                Scierror(999, _("%s: Invalid range format. Use formats like 'A1:B2', 'A:C', '1:3', 'B5', 'B', or '3'.\n"), "xlsxRead");
                break;
            default:
                Scierror(999, _("%s: Error reading XLSX file (code %d).\n"), "xlsxRead", result);
        }
        return types::Function::Error;
    }

    if (!data.is_loaded)
    {
        xlsx_free_data(&data);
        Scierror(999, _("%s: No data found in file or file is empty.\n"), "xlsxRead");
        return types::Function::Error;
    }
    if (data.sheet_count == 0)
    {
        xlsx_free_data(&data);
        Scierror(999, _("%s: Sheet not found in file or file is empty.\n"), "xlsxRead");
        return types::Function::Error;
    }

    const XlsxSheet& sheet = data.sheets[data.sheet_count-1];
    int max_row = sheet.max_row;
    int max_col = sheet.max_col;

    if (conversionType == L"double")
    {
        if (max_row > 0 && max_col > 0)
        {
            types::Double* data_matrix = new types::Double(max_row, max_col);
            for (int row = 0; row < max_row; row++)
            {
                for (int col = 0; col < max_col; col++)
                {
                    const XlsxCell& cell = sheet.cells[row][col];
                    double cell_value = conversion_in_double(cell);
                    data_matrix->set(row, col, cell_value);
                }
            }
            out.push_back(data_matrix);
        }
        else
        {
            out.push_back(types::Double::Empty());
        }
    }
    else if (conversionType == L"string")
    {
        if (max_row > 0 && max_col > 0)
        {
            types::String* data_matrix = new types::String(max_row, max_col);
            for (int row = 0; row < max_row; row++)
            {
                for (int col = 0; col < max_col; col++)
                {
                    const XlsxCell& cell = sheet.cells[row][col];
                    std::string str = conversion_in_string(cell);
                    data_matrix->set(row, col, str.data());
                }
            }
            out.push_back(data_matrix);
        }
        else
        {
            // empty string matrix
            types::String* res = new types::String(1, 1);
            res->set(0, L"");
            out.push_back(res);
        }
    }
    else
    {
        // conversion == L"cell"
        if (max_row > 0 && max_col > 0)
        {
            types::Cell* data_matrix = new types::Cell(max_row, max_col);
            for (int row = 0; row < max_row; row++)
            {
                for (int col = 0; col < max_col; col++)
                {
                    const XlsxCell& cell = sheet.cells[row][col];
                    types::InternalType* cell_value = convert_cell_to_scilab_type(cell);
                    data_matrix->set(row, col, cell_value);
                }
            }
            out.push_back(data_matrix);
        }
        else
        {
            // empty cell
            out.push_back(new types::Cell());
        }
    }

    xlsx_free_data(&data);
    return types::Function::OK;
}
