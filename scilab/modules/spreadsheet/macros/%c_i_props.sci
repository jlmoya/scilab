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

function out = %c_i_props(varargin)
    //disp("c_i_props", varargin)
    out = varargin($);
    val = varargin(2);
    opt = varargin(1);
    fields = ["Description", "VariableNames", "VariableDescriptions", "VariableUnits", "VariableContinuity", "RowNames"];
    if grep(fields, opt) == [] then
        error(msprintf(_("Unknown field: %s.\n"), opt));
    end

    if and(opt <> ["Description", "RowNames"]) && (val <> "" && (~isvector(val) || or(size(val, "*") <> out.userdata(2)))) then
        error(msprintf(_("%s: Wrong size for %s property: row vector of size %d expected.\n"), "%c_i_props", opt, out.userdata(2)));
    end

    select opt
    case "Description"
        out.description = val;
    case "VariableNames"
        if val == "" then
            if or(fieldnames(out) == "timeStep") then
                // timeseries
                val(1) = "Time";
                for i = 2:prod(out.userdata)
                    val(1, i) = sprintf("Var%d", i - 1);
                end
            else
                // table
                for i = 1:prod(out.userdata)
                    val(1, i) = sprintf("Var%d", i);
                end
            end
        end

        if or(fieldnames(out) == "rowNames") & or(val == "Row") then
            error(msprintf(_("%s: ""%s"" can not be used.\n"), "c_i_props", "Row"));
        end

        if iscolumn(val) then
            val = val';
        end

        out.variableNames = val;
    case "VariableDescriptions"
        if size(val, "*") == 1 then
            val = val + emptystr(out.variableNames);
        elseif iscolumn(val) then
            val = val';
        end
        out.variableDescriptions = val;
    case "VariableUnits"
        if size(val, "*") == 1 then
            val = val + emptystr(out.variableNames);
        elseif iscolumn(val) then
            val = val';
        end
        out.variableUnits = val;
    case "VariableContinuity"
        if size(val, "*") == 1 then
            val = val + emptystr(out.variableNames);
        elseif iscolumn(val) then
            val = val';
        end
        out.variableContinuity = val
    case "RowNames"
        if isrow(val) then
            val = val';
        end
        out.rowNames = val;
    end

    out.userdata = [];
endfunction
