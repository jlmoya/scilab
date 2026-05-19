// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function result = xlsxSheet(filename, operation, varargin)

    nargs = nargin;
    fname = "xlsxSheet";

    if nargs < 2 | nargs > 4 then
        error(msprintf(_("%s: Wrong number of input arguments: %d to %d expected.\n"), fname, 2, 4));
    end

    // filename
    if type(filename) <> 10 | ~isscalar(filename) then
        error(msprintf(_("%s: Wrong type for input argument #%d: A valid file expected.\n"), fname, 1));
    end

    if fileext(filename) <> ".xlsx" then
        error(msprintf(_("%s: Wrong extension for input argument #%d: .xlsx expected.\n"), fname, 1));
    end

    // operation
    if type(operation) <> 10 | ~isscalar(operation)then
        error(msprintf(_("%s: Wrong type for input argument #%d: A string expected.\n"), fname, 2));
    end
    
    select convstr(operation, "l")
    case "create" then
        // xlsxSheet(filename, "create", sheet_name [, sheet_index])
        if nargs < 3 | nargs > 4 then
            error(msprintf(_("%s: Wrong number of input arguments: %d to %d expected.\n"), fname, 3, 4));
        end
        
        sheet_name = varargin(1);
        if type(sheet_name) <> 10 then
            error(msprintf(_("%s: Wrong type for input argument #%d: A string expected.\n"), fname, 3));
        end

        if nargs == 4 then
            sheet_index = varargin(2);
            if type(sheet_index) <> 1 then
                error(msprintf(_("%s: Wrong type for input argument #%d: A double expected.\n"), fname, 4));
            end
            if sheet_index < 1 | floor(sheet_index) <> sheet_index then
                error(msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), fname, 4));
            end
            result = %xlsxSheet(filename, 0, sheet_name, sheet_index);
        else
            result = %xlsxSheet(filename, 0, sheet_name, -1);
        end

    case "delete" then
        // xlsxSheet(filename, "delete", sheet_name or index)
        if nargs <> 3 then
            error(msprintf(_("%s: Wrong number of input arguments: %d expected.\n"), fname, 3));
        end

        sheet_param = varargin(1);

        if type(sheet_param) == 10 then
            result = %xlsxSheet(filename, 1, sheet_param, -1);
        elseif type(sheet_param) == 1 | floor(sheet_param) <> sheet_param then
            if sheet_param < 1 then
                error(msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), fname, 3));
            end
            result = %xlsxSheet(filename, 1, "", sheet_param);
        else
            error(msprintf(_("%s: Wrong type for input argument #%d: A double or string expected.\n"), fname, 3));
        end

    case "rename" then
        // xlsxSheet(filename, "rename", sheet_name or index, new_name)
        if nargs <> 4 then
            error(msprintf(_("%s: Wrong number of input arguments: %d expected.\n"), fname, 4));
        end
        
        sheet_param = varargin(1);
        new_name = varargin(2);

        if type(new_name) <> 10 | ~isscalar(new_name) then
            error(msprintf(_("%s: Wrong type for input argument #%d: A string expected.\n"), fname, 4));
        end

        if type(sheet_param) == 10 then
            result = %xlsxSheet(filename, 2, sheet_param, -1, new_name);
        elseif type(sheet_param) == 1 then
            if sheet_param < 1 | floor(sheet_param) <> sheet_param then
                error(msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), fname, 3));
            end
            result = %xlsxSheet(filename, 2, "", sheet_param, new_name);
        else
            error(msprintf(_("%s: Wrong type for input argument #%d: A double or string expected.\n"), fname, 3));
        end

    case "info" then
        // xlsxSheet(filename, "info") 
        // xlsxSheet(filename, "info", sheet_name or index)
        if nargs <> 2 & nargs <> 3 then
             error(msprintf(_("%s: Wrong number of input arguments: %d to %d expected.\n"), fname, 2, 3));
        end
        if nargs == 2 then
            result = xlsxInfo(filename);
        else
            sheet_param = varargin(1);
            if type(sheet_param) == 10 then
                result = xlsxInfo(filename, sheet_param);
            elseif type(sheet_param) == 1 then
                if sheet_param < 1 | floor(sheet_param) <> sheet_param then
                error(msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), fname, 3));
                end
                result = xlsxInfo(filename, sheet_param);
            else
                error(msprintf(_("%s: Wrong type for input argument #%d: A double or string expected.\n"), fname, 3));
            end
        end

    else
        error(msprintf(_("%s: Unknown operation ''%s''. Valid operations are %s.\n"), fname, operation, sci2exp(["create", "deleter", "rename", "info"])));
    end

endfunction
