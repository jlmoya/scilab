// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E - Vincent COUVERT

// For more information, see the COPYING file which you should have received
// along with this program.

function status = check_help(moduleName, localeName, xUnitFile)

    arguments
        moduleName string {mustBeScalarOrEmpty} = []
        localeName (1, 1) string = getlanguage()
        xUnitFile string {mustBeScalarOrEmpty} = []
    end

    fname = "check_help";

    // RNG file for Scilab XML help files
    relaxngFile = "SCI/modules/helptools/schema/scilab.rng";
    relaxngObject = xmlRelaxNG(relaxngFile);

    rhs = argn(2);

    xmlfileNames = [];
    dirsToBeListed = [];
    
    // For xUnit export
    generateXUnit = (xUnitFile <> []) && (xUnitFile <> "");
    testCases = list(); // List of structs: {name, module, passed, errorMessages}

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
    end
    if moduleName == [] then
        moduleName = allModules;
    elseif size(moduleName, "*") == 1 then

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
    
    end


    // List file to be checked
    if xmlfileNames == [] then // File list has to be generated
        if dirsToBeListed == [] then
            for i=1:size(moduleName, "*")
                dirsToBeListed($+1) = fullfile(SCI, "modules", moduleName(i), "help", localeName); 
            end
        end
        while dirsToBeListed <> []
            curDir = dirsToBeListed(1);
            contents = gsort(listfiles(curDir), "lr", "i");
            for i=1:size(contents, "*")
                item = fullfile(curDir, contents(i));
                if isfile(item) && fileparts(item, "extension") == ".xml" then
                    xmlfileNames = [xmlfileNames;item];
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

        // Extract module name from path for grouping in xUnit
        moduleOfFile = "unknown";
        if getos() == "Windows" then
            xmlfileName = strsubst(xmlfileName, "\", "/");
        end
        [_, _, match] = regexp(xmlfileName, "#(?<=modules/)[^/]+(?=/help)#");
        if match <> "" then
            moduleOfFile = match;
        end

        filename = xmlfileName;
        if getos() == "Windows" then
            filename = strsubst(filename, strsubst(TMPDIR, "\","/"), "TMPDIR");
            filename = strsubst(filename, strsubst(SCI, "\","/"), "SCI");
        else
            filename = strsubst(filename, TMPDIR, "TMPDIR");
            filename = strsubst(filename, SCI, "SCI");
        end

        printf("%04d/%04d - %s ", ifile, size(xmlfileNames, "*"), filename);
        for i=length(filename):110
            printf(".");
        end
    
        errmsg = xmlValidate(xmlfileName, relaxngObject);
    
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
            
        // Store result for xUnit
        if generateXUnit then
            tc = struct("name", "", "module", "", "passed", %f, "errorMessages", []);
            tc.name = filename;
            tc.module = moduleOfFile;
            tc.passed = (errmsg == []);
            tc.errorMessages = errmsg;
            testCases($+1) = tc;
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

    // Generate xUnit report if requested
    if generateXUnit then
        // Group test cases by module
        modulesMap = list();
        for i = 1:size(testCases)
            tc = testCases(i);
            moduleNameKey = tc.module;
            if moduleNameKey == "" then
                moduleNameKey = "unknown";
            end
            
            found = %f;
            for j = 1:size(modulesMap)
                if modulesMap(j).name == moduleNameKey then
                    modulesMap(j).testcases($+1) = tc;
                    found = %t;
                    break;
                end
            end
            if ~found then
                newModule.name = moduleNameKey;
                newModule.testcases = list(tc);
                modulesMap($+1) = newModule;
            end
        end
        
        // Create XML document
        doc = xmlDocument(xUnitFile);
        root = xmlElement(doc, "testsuites");
        root.attributes.name = "check_help";
        root.attributes.tests = string(size(testCases));
        root.attributes.failures = string(failed);
        
        // Create properties
        properties = xmlElement(doc, "properties");
        [branch, info] = getversion();
        branchProperty = xmlElement(doc, "property");
        branchProperty.attributes.name = "branch";
        branchProperty.attributes.value = branch;
        properties.children(1) = branchProperty;
        
        // Add testsuites (one per module)
        for i = 1:size(modulesMap)
            module = modulesMap(i);
            testsuite = xmlElement(doc, "testsuite");
            testsuite.attributes.name = module.name;
            
            // Count tests and failures for this module
            modTests = size(module.testcases);
            modFailures = 0;
            for j = 1:modTests
                if ~module.testcases(j).passed then
                    modFailures = modFailures + 1;
                end
            end
            testsuite.attributes.tests = string(modTests);
            testsuite.attributes.failures = string(modFailures);
            
            // Add testcases for this module
            propertiesCopy = xmlElement(doc, "properties");
            branchPropertyCopy = xmlElement(doc, "property");
            branchPropertyCopy.attributes.name = "branch";
            branchPropertyCopy.attributes.value = branch;
            propertiesCopy.children(1) = branchPropertyCopy;
            
            for j = 1:modTests
                tc = module.testcases(j);
                testcase = xmlElement(doc, "testcase");
                testcase.attributes.name = tc.name;
                testcase.attributes.classname = basename(tc.name);
                
                if ~tc.passed then
                    failure = xmlElement(doc, "failure");
                    failure.attributes.message = "XML validation failed";
                    // Combine error messages
                    errorText = "";
                    for k = 1:size(tc.errorMessages, "*")
                        if k > 1 then
                            errorText = errorText + ascii(10) + tc.errorMessages(k);
                        else
                            errorText = tc.errorMessages(k);
                        end
                    end
                    // Escape CDATA end marker
                    errorText = strsubst(errorText, "]]>", "]] >");
                    failure.content = ["<![CDATA["; errorText; "]]>"];
                    testcase.children(1) = failure;
                end
                testsuite.children(j) = testcase;
            end
            testsuite.children(length(testsuite.children)+1) = propertiesCopy;
            root.children(i) = testsuite;
        end
        
        doc.root = root;
        xmlWrite(doc);
        printf("   Export to          %s\n", xUnitFile);
        printf("   -----------------------------------------------------------------------------\n");
    end

    status = failed == 0;
        
endfunction
