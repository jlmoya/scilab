// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
// For more information, see the COPYING file which you should have received
// along with this program.


function ret = parquetRead(filename)
    arguments
        filename {mustBeA(filename, "string"), mustBeScalar}
    end

    if ~isfile(filename) then
        error(msprintf(_("%s: Wrong value for input argument #%d: File ""%s"" does not exist.\n"), "parquetRead", 1, filename));
    end

    if and(fileext(filename) <> [".parquet", ".arrow"]) then
        error(msprintf(_("%s: Wrong extension for input argument #%d: %s expected.\n"), "parquetRead", 1, sci2exp([".parquet", ".arrow"])));
    end

    [l, varnames, vartypes] = %parquetRead(filename);
    idx = find(vartypes == "datetime");
    for i = idx
        l(i) = convertDoubleColumnToDatetime(l(i));
    end

    idx = find(vartypes == "duration");
    for i = idx
        l(i) = milliseconds(l(i));
    end
    ret = table(l(:), "VariableNames", varnames);
    
endfunction

function convertedColumn = convertDoubleColumnToDatetime(data)

    nrows = length(data);

    convertedColumn = [];

    epoch_datenum = datenum(1970, 1, 1, 0, 0, 0);
    convertedColumn = NaT(nrows, 1);
    mask = ~isnan(data);

    microseconds = data(mask);
    daysz = microseconds / 1000000 / 86400;
    target_datenum = epoch_datenum + daysz;
    convertedColumn(mask) = datetime(target_datenum, "ConvertFrom", "datenum");
endfunction