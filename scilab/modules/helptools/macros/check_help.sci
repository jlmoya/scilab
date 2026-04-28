// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E - Vincent COUVERT

// For more information, see the COPYING file which you should have received
// along with this program.

function status = check_help(moduleName, localeName)

    arguments
        moduleName string {mustBeScalarOrEmpty} = []
        localeName (1, 1) string = getlanguage()
    end

    fname = "check_help";

    // RNG file for Scilab XML help files
    relaxngFile = "SCI/modules/helptools/schema/scilab.rng";
    relaxngObject = xmlRelaxNG(relaxngFile);

    rhs = argn(2)

    xmlfileNames = []
    dirsToBeListed = [];

    allEntries  = listfiles(fullfile(SCI, "modules"));
    for i=1:size(allEntries, "*")
        if isdir(fullfile(SCI, "modules", allEntries(i))) then
            if and(allEntries(i) <> [""])
                allModules($+1) = allEntries(i);
            end
        end
    end

    if rhs == 0 then

        // All modules & current language
        
        allEntries  = gsort(listfiles(fullfile(SCI, "modules")), "lr", "i");
        for i=1:size(allEntries, "*")
            if isdir(fullfile(SCI, "modules", allEntries(i))) then
                if and(allEntries(i) <> [""])
                    moduleName($+1) = allEntries(i);
                end
            end
        end

    elseif rhs == 1 then

        // Module (single) provided by user & current language / Single file name / Directory

        if isfile(moduleName) then
            xmlfileNames = moduleName;
        elseif isdir(moduleName) then
            dirsToBeListed = moduleName;
        else 
            if ~or(moduleName == allModules) then
                error(msprintf(_("%s: Module ''%s'' does not exist.\n"), fname, moduleName));
            end
        end

    elseif rhs == 2 then

        // Module (single) & language provided by user

        if isempty(moduleName) then
            moduleName = allModules;
        end

    end

    // List file to be checked
    if xmlfileNames == [] then // File list has to be generated
        if dirsToBeListed == [] then
            for i=1:size(moduleName, "*")
                dirsToBeListed($+1) = fullfile(SCI, "modules", moduleName(i), "help", localeName); 
            end
        end
        while dirsToBeListed <> []
            curDir = dirsToBeListed(1)
            contents = gsort(listfiles(curDir), "lr", "i");
            for i=1:size(contents, "*")
                item = fullfile(curDir, contents(i));
                if isfile(item) && fileparts(item, "extension") == ".xml" then
                    xmlfileNames = [xmlfileNames;item]
                elseif isdir(item) then
                    dirsToBeListed = [item; dirsToBeListed];
                end
            end
            dirsToBeListed(dirsToBeListed==curDir) = [];
        end
    end

    passed = 0;
    failed = 0;

    for ifile=1:size(xmlfileNames, "*")
        xmlfileName = xmlfileNames(ifile);

        if ~isfile(xmlfileName) then
            error(msprintf(_("%s: File ''%s'' does not exist.\n"), fname, xmlfileName));
        end

        filename = xmlfileName;
        if getos() == "Windows" then
            filename = strsubst(filename, strsubst(TMPDIR, "\","/"), "TMPDIR");
            filename = strsubst(filename, strsubst(TMPDIR, "/","\"), "TMPDIR");
            filename = strsubst(filename, strsubst(SCI, "\","/"), "SCI");
            filename = strsubst(filename, strsubst(SCI, "/","\"), "SCI");
        else
            filename = strsubst(filename, TMPDIR, "TMPDIR");
            filename = strsubst(filename, SCI, "SCI");
        end

        printf("%04d/%04d - %s ", ifile, size(xmlfileNames, "*"), filename);
        for i=length(filename):110
            printf(".");
        end
    
        xmlDoc = xmlRead(xmlfileName);
        errmsg = xmlValidate(xmlDoc, relaxngObject);
        xmlDelete(xmlDoc);

        if errmsg <> [] then
            printf(": FAILED\r\n")
            for iline=1:size(errmsg, "*")
                printf("\t\t%s\r\n", errmsg(iline));
            end
            failed = failed + 1;
        else
            printf(": PASSED\r\n")
            passed = passed + 1;
        end
            
    end
    
    xmlDelete(relaxngObject);
    
    if size(xmlfileNames, "*") <> 0 then
        passedPercent = passed / size(xmlfileNames, "*") * 100;
        failedPercent = failed / size(xmlfileNames, "*") * 100;
    else
        passedPercent = 0;
        failedPercent = 0;
    end

    printf("\n");
    printf("   -----------------------------------------------------------------------------\n");
    printf("   Summary\n\n");
    printf("   passed          %4d - %3d %%\n", passed, passedPercent);
    printf("   failed          %4d - %3d %%\n", failed, failedPercent);
    printf("   -----------------------------------------------------------------------------\n");

    status = failed == 0;
        
endfunction
