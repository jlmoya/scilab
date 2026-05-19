// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function xlsxWrite(data, filename, varargin)
//   xlsxWrite(matrix, filename)
//   xlsxWrite(matrix, filename, 'sheet', 2, 'range', 'A1:B2', 'title', 'my title', 'subject', 'my subject', 'description', 'my description', 'clear', %t, 'append', %f)
    arguments
        data {mustBeA(data, ["double", "string", "cell"])}
        filename {mustBeA(filename, "string"), mustBeScalar}
        varargin
    end

    nargs = nargin;    
    fname = "xlsxWrite";

    if fileext(filename) <> ".xlsx" then
        error(msprintf(_("%s: Wrong extension for input argument #%d: .xlsx expected.\n"), fname, 2));
    end

    if modulo(nargs - 2, 2) <> 0 then
        error(msprintf(_("%s: Options must be specified as key-value pairs\n"), fname));
    end

    sheet_value = 1;
    sheet_is_index = %t;
    range_value = "";
    title_value = "";
    subject_value = "";
    description_value = "";
    clear_value = %f;
    append_value = %f;
 
    for i = 1:2:nargs-3
        key = varargin(i);
        value = varargin(i+1);
        
        if type(key) <> 10 then
            error(msprintf(_("%s: Option keys must be strings\n"), fname));
        end

        typeValue = type(value);
        
        select convstr(key, "l")
        case 'sheet' then
            // xlsxWrite(filename, matrix, 'sheet', 'Sheet1')
            // xlsxWrite(filename, matrix, 'sheet', 2)       
            if typeValue == 10 then
                sheet_value = value;
                sheet_is_index = %f;
            elseif typeValue == 1 then
                if value <> int(value) | value < 1 then
                    error(msprintf(_("%s: Wrong value for ""%s"" argument: Must be a positive integer >=1.\n"), fname, key));
                end
                sheet_value = value;
                sheet_is_index = %t;
            else
                error(msprintf(_("%s: Wrong type for ""%s"" argument: A double or string expected.\n"), fname, key));
            end
            
        case 'range' then
            // xlsxWrite(filename, matrix, 'range', 'A1')      
            // xlsxWrite(filename, matrix, 'range', 'A1:B2')   
            // xlsxWrite(filename, matrix, 'range', 'A:C')     
            // xlsxWrite(filename, matrix, 'range', '1:3')     
            // xlsxWrite(filename, matrix, 'range', [1 3; 2 4])
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

        case 'title' then
            // xlsxWrite(filename, matrix, 'title', 'my title')
            if typeValue <> 10 then
                error(msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), fname, key));
            end
            title_value = value;

        case 'subject' then
            // xlsxWrite(filename, matrix, 'subject', 'my subject')
            if typeValue <> 10 then
                error(msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), fname, key));
            end
            subject_value = value;

        case 'description' then
            // xlsxWrite(filename, matrix, 'description', 'my description')
            if typeValue <> 10 then
                error(msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), fname, key));
            end
            description_value = value;

        case 'writemode' then
            // xlsxWrite(filename, matrix, 'writemode', 'overwrite')
            // xlsxWrite(filename, matrix, 'writemode', 'append')
            if typeValue <> 10 then
                error(msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), fname, key));
            end
            select convstr(value, "l")
            case "overwrite" then
                clear_value = %t;
            case "append" then
                append_value = %t;
            else
                error(msprintf(_("%s: Wrong value for ""%s"" argument: Must be in %s.\n"), fname, key, sci2exp(["overwrite", "append"])));
            end

        else
            error(msprintf(_("%s: Unknown option ""%s"". Valid options are %s.\n"), fname, key, sci2exp(["range", "title", "subject", "description", "writemode"])));
        end
    end

    [rows, cols] = size(data);

    sheet_struct = struct();
    if sheet_is_index then
        sheet_struct.name = "Sheet" + string(sheet_value);
    else
        sheet_struct.name = sheet_value;
    end
    sheet_struct.max_row = rows;
    sheet_struct.max_col = cols;
    sheet_struct.data = data;

    data_struct = struct();
    data_struct.filename = filename;
    data_struct.sheet_count = 1;
    data_struct.sheets = list(sheet_struct);

    if title_value <> "" then
        data_struct.title = title_value;
    end
    if subject_value <> "" then
        data_struct.subject = subject_value;
    end
    if description_value <> "" then
        data_struct.description = description_value;
    end
    
    %xlsxWrite(filename, data_struct, sheet_value, range_value, clear_value, append_value);

endfunction
