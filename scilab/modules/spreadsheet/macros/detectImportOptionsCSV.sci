function opts = detectImportOptionsCSV(f, delim, decimal, numHeaderLines)
    if nargin == 1 then
        delim = "";
        decimal = [];
        numHeaderLines = [];
    end

    opts = struct();
    
    while f($) == ""
        f($) = [];
    end

    if numHeaderLines <> [] then
        l = 1:numHeaderLines;
        header = f(l);
    else
        // detect header
        [header, c , l] = detectHeader(f);
    end

    // detect delimiter
    datalines = [1:size(f, "r")];
    if l <> [] then
        v = 1:size(f, "r");
        v(l) = [];
        f(l) = [];
        datalines = v;
    end
    headlines = [1 size(f, "r")];

    if delim == "" then
        if decimal == [] then
            [delim, decimal] = detectDelimiter(f);
        else
            delim = detectDelimiter(f);
        end
    else
        if decimal == [] then
            [a, decimal] = detectDelimiter(f, delim);
        end
    end

    // detect variable names and type
    test = csvTextScan(f(1), delim, decimal);
    variableNames = [];
    index = [];

    if size(f, "*") > 1 then
        if and(isnan(test)) then
            h = csvTextScan(f(1), delim, decimal, "string");
            variableNames = h;
            index = find(variableNames == "");
            if index <> [] then
                variableNames(index) = "Var" + string(index);
            end
            datalines(1) = [];
            headlines(1) = [];
            hasheader = %t;
        end
        f(1) = [];
    end

    // csvTextScan on all the file
    h = csvTextScan(f, delim, decimal, "string");

    variableTypes = emptystr(variableNames);
    inputFormat = [];

    for i = 1:size(h, 'c')
        // types managed : datetime, double, string
        if h(1, i) == "" then
            variableTypes(1,i) = "string";
            inputFormat(1,i) = "";
            mat = h(:, i);
            idx = mat <> "";
            if or(idx) then
                [infmt, _typ] = detectFormatDatetime(mat(idx)(1))
                variableTypes(1,i) = _typ;
                inputFormat(1,i) = infmt;
            else
                // empty column
                if index <> i then
                    index = [index, i];
                end
            end
        else
            [infmt, _typ] = detectFormatDatetime(h(:, i))
            variableTypes(1,i) = _typ;
            inputFormat(1,i) = infmt
        end
    end

    opts.variableNames = variableNames;
    opts.variableTypes = variableTypes;
    opts.delimiter = delim;
    opts.decimal = decimal;
    opts.datalines = datalines;
    opts.header = header;
    opts.inputFormat = inputFormat;
    opts.emptyCol = index;
endfunction