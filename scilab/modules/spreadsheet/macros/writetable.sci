// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2022 - Dassault Systèmes S.E. - Adeline CARNIS
// Copyright (C) 2022 - Dassault Systèmes S.E. - Antoine ELIAS
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function writetable(t, varargin)

    delim = ",";
    fname = "writetable";
    writerownames = %f;
    writevarnames = %t;
    sheet = 1;
    sheet_is_index = %t;
    range_value = "";
    clear_value = %f;
    append_value = %f;

    if ~istable(t) then
        error(msprintf(_("%s: Wrong type for input argument #%d: table expected.\n"), fname, 1));
    end

    if nargin == 1 then 
        filename = fullfile(TMPDIR, "table.txt");
    else
        filename = varargin(1);
    end

    if type(filename) <> 10 then
        error(msprintf(_("%s: Wrong type for input argument #%d: file name expected.\n"), fname, 2));
    end

    extension = fileext(filename);
    availableFormats = [".xlsx", ".txt", ".dat", ".csv"];
    // ".xlsx" or [.txt, .dat or .csv] for delimited text files
    if and(extension <> availableFormats) then
        error(msprintf(_("%s: Wrong extension for input argument #%d: %s expected.\n"), fname, 2, sci2exp(availableFormats)));
    end

    if nargin > 2 then
        for i = nargin-2:-2:2
            key = varargin(i);
            if type(key) <> 10 then
                break;
            end

            select convstr(key, "l")
            case "delimiter"
                delim = varargin(i + 1);
                if type(delim) <> 10 then
                    error(msprintf(_("%s: Wrong type for %s argument: string expected.\n"), fname, key));
                end
                if ~isscalar(delim) then
                    error(msprintf(_("%s: Wrong size for %s argument: scalar expected.\n"), fname, key));
                end
            case "writerownames"
                writerownames = varargin(i + 1);
                if type(writerownames) <> 4 then
                    error(msprintf(_("%s: Wrong type for %s argument: boolean expected.\n"), fname, key));
                end
                if or(size(writerownames) <> [1 1]) then
                    error(msprintf(_("%s: Wrong size for %s argument: scalar expected.\n"), fname, key));
                end
            case "writevariablenames"
                writevarnames = varargin(i + 1);
                if type(writevarnames) <> 4 then
                    error(msprintf(_("%s: Wrong type for %s argument: boolean expected.\n"), fname, key));
                end
                if or(size(writevarnames) <> [1 1]) then
                    error(msprintf(_("%s: Wrong size for %s argument: scalar expected.\n"), fname, key));
                end
            case 'sheet' then
                sheet = varargin(i + 1);
                if type(sheet) == 10 then
                    sheet_is_index = %f;
                elseif type(sheet) == 1 then
                    if sheet <> int(sheet) | sheet < 1 then
                        error(msprintf(_("%s: Wrong value for ""%s"" argument: Must be a positive integer >=1.\n"), fname, key));
                    end
                    sheet_is_index = %t;
                else
                    error(msprintf(_("%s: Wrong type for ""%s"" argument: A double or string expected.\n"), fname, key));
                end
                
            case 'range' then
                range_value = varargin(i + 1);
                if type(range_value) == 1 then
                    if and(size(range_value) == [2 2]) then
                        range_value = %matrix_to_range(range_value, fname);
                    else
                        error(msprintf(_("%s: Wrong value for ""%s"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), fname, key));
                    end
                else
                    if type(range_value) <> 10 then
                        error(msprintf(_("%s: Wrong type for ""%s"" argument: Must be a string or a 2x2 matrix.\n"), fname, key));
                    end
                end
            case 'writemode' then
                writemode = varargin(i + 1);
                if type(writemode) <> 10 then
                    error(msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), fname, key));
                end
                select convstr(writemode, "l")
                case "overwrite" then
                    clear_value = %t;
                case "append" then
                    append_value = %t;
                else
                    error(msprintf(_("%s: Wrong value for ""%s"" argument: Must be in %s.\n"), fname, key, sci2exp(["overwrite", "append"])));
                end
            else
                error(msprintf(_("%s: Wrong value for input argument #%d: ''%s'' not allowed.\n"), fname, i, key));
            end
        end
    end

    rownames = [];
    varnames = t.props.variableNames;

    if extension == ".xlsx" then
        if writerownames then
            rownames = t.props.rowNames;
            if rownames <> [] then
                rownames = num2cell(rownames);
                varnames = ["Row", varnames];
            end
        end

        c = table2cell(t);

        tss = [rownames, c];
        if writevarnames then
            varnames = num2cell(varnames);
            tss = [varnames; tss];
        end

        [rows, cols] = size(tss);

        count = 1;
        if isfile(filename) then
            info = xlsxInfo(filename);
            sheets = info.sheet_names;
            count = info.total_sheets;
            if sheet_is_index then
                if sheet <= size(sheets, "*") then
                    sheet = sheets(sheet);
                else
                    sheet = "Sheet" + string(sheet);
                    xlsxSheet(filename, "create", sheet_);
                    count = count + 1;
                end
            else
                isValidSheet = find(info.sheet_names == sheet);
                if isValidSheet == [] then
                    xlsxSheet(filename, "create", sheet);
                    count = count + 1;
                end
            end
        end

        sheet_struct = struct();
        sheet_struct.name = sheet;
        sheet_struct.max_row = rows;
        sheet_struct.max_col = cols;
        sheet_struct.data = tss;

        data_struct = struct();
        data_struct.filename = filename;
        data_struct.sheet_count = count;
        data_struct.sheets = list(sheet_struct);
        data_struct.title = "";
        data_struct.subject = "";
        data_struct.description = "";
        
        %xlsxWrite(filename, data_struct, sheet, range_value, clear_value, append_value);
    else
        
        if writerownames then
            rownames = string(t.props.rowNames);
            varnames = ["Row" varnames];
        end

        tss = [rownames string(t)];

        if writevarnames then
            tss = [varnames; tss];
        end

        csvWrite(tss, filename, delim)
    end

endfunction
