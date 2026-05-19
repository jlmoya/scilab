// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function result = xlsxRead(filename, varargin)
    // xlsxRead(filename)
    // xlsxRead(filename, 'sheet', 2, 'range', 'A1:B2', 'conversion', 'string', ...)
    arguments
        filename {mustBeA(filename, "string"), mustBeScalar}
        varargin
    end

    rhs = nargin;
    fname = "xlsxRead";

    if ~isfile(filename) then
        error(msprintf(_("%s: Wrong value for input argument #%d: File ""%s"" does not exist.\n"), fname, 1, filename));
    end

    if fileext(filename) <> ".xlsx" then
        error(msprintf(_("%s: Wrong extension for input argument #%d: .xlsx expected.\n"), fname, 1));
    end

    if modulo(rhs - 1, 2) <> 0 then
        error(msprintf(_("%s: Options must be specified as key-value pairs.\n"), fname));
    end

    sheet_value = 1;
    range_value = "";
    conversion = "double"; // "double", "string", "cell"

    for i = 1:2:rhs-1
        key = varargin(i);
        value = varargin(i+1);
        
        if type(key) <> 10 then
            error(msprintf(_("%s: Option keys must be strings.\n"), fname));
        end

        typeValue = type(value);

        select convstr(key, "l")
        case 'sheet' then
            // xlsxRead(filename, 'sheet', 'name')
            // xlsxRead(filename, 'sheet', 1)
            if typeValue == 10 then
                sheet_value = value;
            elseif typeValue == 1 then
                if value <> int(value) | value < 1 then
                    error(msprintf(_("%s: Wrong value for ""%s"" argument: Must be a positive integer >=1.\n"), fname, key));
                end
                sheet_value = value;
            else
                error(msprintf(_("%s: Wrong type for ""%s"" argument: A double or string expected.\n"), fname, key));
            end

        case 'range' then
            // xlsxRead(filename, 'range', 'A1:B2')
            // xlsxRead(filename, 'range', 'A:C')
            // xlsxRead(filename, 'range', '1:3')
            // xlsxRead(filename, 'range', 'A1')
            // xlsxRead(filename, 'range', 'A')
            // xlsxRead(filename, 'range', '1')
            // xlsxRead(filename, 'range', [1 3; 2 4])
            if typeValue == 10 then
                range_value = value;
            elseif typeValue == 1 then
                if and(size(value) == [2 2]) then
                    range_value = %matrix_to_range(value, fname);
                else
                    error(msprintf(_("%s: Wrong value for ""%s"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), fname, key));
                end
            else
                error(msprintf(_("%s: Wrong type for ""%s"" argument: Must be a string or a 2x2 matrix.\n"), fname, key));
            end

        case 'conversion' then
            // xlsxRead(filename, 'conversion', 'double')
            // xlsxRead(filename, 'conversion', 'string')
            // xlsxRead(filename, 'conversion', 'cell')
            if typeValue <> 10 then
                error(msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), fname, key));
            end
            if and(value <> ["double", "string", "cell"]) then
                if or(value == ["table", "timeseries"]) then
                    error(msprintf(_("%s: Wrong value for ""%s"" argument. For ""%s"" conversion, use ""%s"" function.\n"), fname, key, value, "read"+value));
                else
                    error(msprintf(_("%s: Wrong value for ""%s"" argument: Must be in %s.\n"), fname, key, sci2exp(["double", "string", "cell"])));
                end
            end
            conversion = value;

        else
            error(msprintf(_("%s: Unknown option ""%s"".\n"), fname, key));
        end
    end

    result = %xlsxRead(filename, sheet_value, range_value, conversion);

endfunction
