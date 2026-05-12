// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2009 - DIGITEO - Vincent COUVERT <vincent.couvert@scilab.org>
// Copyright (C) 2009-2010 - DIGITEO - Pierre MARECHAL <pierre.marechal@scilab.org>
// Copyright (C) 2014 - Scilab Enterprises - Antoine ELIAS
// Copyright (C) 2026 - Dassault Systemes S.E. - Antoine ELIAS
//
// Copyright (C) 2012 - 2016 - Scilab Enterprises
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function cbAtomsGui(msg, cb)
    // Callback for the ATOMS browser uicontrol.
    // Receives messages from JavaScript and responds accordingly.

    // The browser uicontrol may have been destroyed between the moment the
    // event was queued and the moment Scilab executes this callback. The
    // generic Java-side guard skips most stale events, but the figure can
    // still be closed while we are running here.
    if isempty(get("atomsFigure")) then
        return;
    end

    if ~exists("atomsinternalslib") then
        load("SCI/modules/atoms/macros/atoms_internals/lib");
    end

    // String message: page loaded
    if type(msg) == 10 then
        if msg == "loaded" then
            atomsGuiSendData();
        end
        return;
    end

    select msg.type
    case "loaded"
        atomsGuiSendData();

    case "install"
        moduleName = msg.name;
        atomsGuiSetStatus("info", _("Installing") + " " + moduleName + "...");
        if execstr("atomsInstall(""" + moduleName + """)", "errcatch") <> 0 then
            atomsGuiSetStatus("error", _("Installation failed!"));
        else
            atomsGuiSetStatus("success", _("Installation done! Please restart Scilab to take changes into account."));
            atomsGuiRefreshData();
            atomsGuiShowDetail(moduleName);
        end

    case "update"
        moduleName = msg.name;
        atomsGuiSetStatus("info", _("Updating") + " " + moduleName + "...");
        if execstr("atomsUpdate(""" + moduleName + """)", "errcatch") <> 0 then
            atomsGuiSetStatus("error", _("Update failed!"));
        else
            atomsGuiSetStatus("success", _("Update done! Please restart Scilab to take changes into account."));
            atomsGuiRefreshData();
            atomsGuiShowDetail(moduleName);
        end

    case "remove"
        moduleName = msg.name;
        atomsGuiSetStatus("info", _("Removing") + " " + moduleName + "...");
        if execstr("atomsRemove(""" + moduleName + """)", "errcatch") <> 0 then
            atomsGuiSetStatus("error", _("Remove failed!"));
        else
            atomsGuiSetStatus("success", _("Remove done! Please restart Scilab to take changes into account."));
            atomsGuiRefreshData();
            atomsGuiShowDetail(moduleName);
        end

    case "autoload"
        moduleName = msg.name;
        if msg.enable then
            atomsAutoloadAdd(moduleName);
            atomsGuiSetStatus("info", _("The module will be automatically loaded at next startup."));
        else
            atomsAutoloadDel(moduleName);
            atomsGuiSetStatus("info", _("Autoload at startup is canceled. The ""Toolboxes"" menu or atomsLoad() can be used to load the module when needed."));
        end

    case "help"
        doc("atoms");

    case "systemUpdate"
        atomsGuiSetStatus("info", _("Updating the list of packages. Please wait..."));
        if execstr("atomsSystemUpdate()", "errcatch") <> 0 then
            atomsGuiSetStatus("error", _("Failed to update the list of packages."));
        else
            // Refresh cached module descriptions and rebuild the full payload.
            allModules = atomsDESCRIPTIONget();
            set("atoms_browser", "userdata", allModules);
            atomsGuiSendData();
            atomsGuiSetStatus("success", _("Package list updated."));
        end

    end
endfunction

// =============================================================================
// atomsGuiSendData()
//  Send all module and category data to the browser
// =============================================================================
function atomsGuiSendData()
    allModules = get("atoms_browser", "userdata");
    [OSNAME, ARCH, LINUX, MACOSX, SOLARIS, BSD] = atomsGetPlatform();

    // Build module list
    modulesNames = getfield(1, allModules);
    modulesNames(1:2) = [];

    MRVersions = atomsGetMRVersion(modulesNames);

    // Get installed info
    installed = atomsGetInstalled();
    if installed == [] then
        installedNames = [];
        installedVersions = [];
    else
        installedNames = installed(:, 1);
        installedVersions = installed(:, 2);
    end

    // Get autoloaded modules
    autoloadedList = atomsAutoloadList("all");
    if autoloadedList == [] then
        autoloadedNames = [];
    else
        autoloadedNames = autoloadedList(:, 1);
    end

    // Build flat module array as list of structs
    modulesList = list();
    installedNamesList = list();

    for i = 1:size(modulesNames, "*")
        name = modulesNames(i);
        ver = MRVersions(i);
        if ver == "-1" then
            continue;
        end

        details = allModules(name)(ver);
        mod = struct();
        mod.name = name;
        mod.title = details.Title;
        mod.version = ver;
        mod.summary = atomsGuiBridgeSafe(strcat(details.Summary, ascii(10)));

        // Description
        if isfield(details, "Description") then
            mod.description = atomsGuiBridgeSafe(strcat(details.Description, ascii(10)));
        end

        // Authors
        if isfield(details, "Author") then
            mod.authors = atomsGuiBridgeSafe(strcat(details.Author, ", "));
        end

        // Category
        if isfield(details, "Category") then
            mod.category = details.Category(1);
            if size(details.Category, "*") > 1 then
                mod.categories = details.Category;
            end
        end

        // URLs
        URLs = [];
        if isfield(details, "URL") & details.URL <> "" then
            URLs = [URLs; details.URL];
        end
        if isfield(details, "WebSite") & details.WebSite <> "" then
            URLs = [URLs; details.WebSite];
        end
        if ~isempty(URLs) then
            mod.urls = URLs;
        end

        // Date
        if isfield(details, "Date") then
            if ~isempty(regexp(details.Date, "/^[0-9]{4}-[0-1][0-9]-[0-3][0-9]\s/")) then
                mod.date = part(details.Date, 1:10);
            end
        end

        // Download size
        if isfield(details, OSNAME + ARCH + "Size") then
            mod.size = atomsGuiSize2human(details(OSNAME + ARCH + "Size"));
        end

        // Installed?
        isInst = or(name == installedNames);
        mod.isInstalled = isInst;

        if isInst then
            instVers = atomsVersionSort(atomsGetInstalledVers(name), "DESC");
            instVer = instVers(1);
            mod.installedVersion = instVer;

            // Check if update is available
            if atomsVersionCompare(instVer, ver) == -1 then
                mod.updateAvailable = %t;
            else
                mod.updateAvailable = %f;
            end

            // Use installed version details for title
            mod.title = allModules(name)(instVer).Title;

            // Autoloaded?
            mod.autoloaded = or(name == autoloadedNames);
        else
            mod.updateAvailable = %f;
            mod.autoloaded = %f;
        end

        modulesList($+1) = mod;
        if isInst then
            installedNamesList($+1) = name;
        end
    end

    // Build category tree
    mainCategories = atomsCategoryGet("filter:main");
    catTree = list();

    for i = 1:size(mainCategories, "*")
        catName = mainCategories(i);
        subCategories = atomsCategoryGet(catName);

        cat = struct();
        cat.name = _(catName);

        // Get modules in this category
        catModNames = atomsGetAvailable(catName, %T);
        cat.moduleCount = size(catModNames, "*");
        cat.modules = catModNames;

        // Build sub-categories
        if ~isempty(subCategories) then
            children = list();
            for j = 1:size(subCategories, "*")
                subCatName = catName + " - " + subCategories(j);
                subCat = struct();
                subCat.name = _(subCategories(j));

                subModNames = atomsGetAvailable(subCatName, %T);
                subCat.moduleCount = size(subModNames, "*");
                subCat.modules = subModNames;

                children($+1) = subCat;
            end
            cat.children = children;
        end

        catTree($+1) = cat;
    end

    // Build installed names as flat string vector
    instNames = [];
    for i = 1:length(installedNamesList)
        instNames = [instNames; installedNamesList(i)];
    end

    // Build localized labels
    labels = struct( ...
        "title", _("ATOMS - Package Manager"), ...
        "home", _("Home"), ...
        "browse", _("Browse"), ...
        "installed", _("Installed"), ...
        "search", _("Search modules..."), ...
        "install", _("Install"), ...
        "update", _("Update"), ...
        "remove", _("Remove"), ...
        "autoload", _("Autoload"), ...
        "version", _("Version"), ...
        "authors", _("Author(s)"), ...
        "description", _("Description"), ...
        "see_also", _("See also"), ...
        "release_date", _("Release date"), ...
        "download_size", _("Download size"), ...
        "modules_title", _("Modules"), ...
        "n_modules", _("%d module(s)"), ...
        "n_results_for", _("%d result(s) for ""%s"""), ...
        "no_result", _("No modules match your search."), ...
        "no_installed", _("No modules installed."), ...
        "update_available", _("Update available"), ...
        "installed_label", _("Installed"), ...
        "not_installed", _("Not installed"), ...
        "back", _("Back"), ...
        "autoload_tooltip", _("Load automatically at startup"));

    data = struct( ...
        "type", "modulelist", ...
        "modules", modulesList, ...
        "categories", catTree, ...
        "installed", instNames, ...
        "labels", labels);

    set("atoms_browser", "data", data);
endfunction

// =============================================================================
// atomsGuiRefreshData()
//  Re-read installed/autoload state and send refresh to browser
// =============================================================================
function atomsGuiRefreshData()
    allModules = get("atoms_browser", "userdata");
    [OSNAME, ARCH, LINUX, MACOSX, SOLARIS, BSD] = atomsGetPlatform();

    modulesNames = getfield(1, allModules);
    modulesNames(1:2) = [];
    MRVersions = atomsGetMRVersion(modulesNames);

    installed = atomsGetInstalled();
    if installed == [] then
        installedNames = [];
    else
        installedNames = installed(:, 1);
    end

    autoloadedList = atomsAutoloadList("all");
    if autoloadedList == [] then
        autoloadedNames = [];
    else
        autoloadedNames = autoloadedList(:, 1);
    end

    modulesList = list();
    instNames = [];

    for i = 1:size(modulesNames, "*")
        name = modulesNames(i);
        ver = MRVersions(i);
        if ver == "-1" then
            continue;
        end

        details = allModules(name)(ver);
        mod = struct();
        mod.name = name;
        mod.title = details.Title;
        mod.version = ver;
        mod.summary = atomsGuiBridgeSafe(strcat(details.Summary, ascii(10)));

        if isfield(details, "Description") then
            mod.description = atomsGuiBridgeSafe(strcat(details.Description, ascii(10)));
        end

        if isfield(details, "Author") then
            mod.authors = atomsGuiBridgeSafe(strcat(details.Author, ", "));
        end

        if isfield(details, "Category") then
            mod.category = details.Category(1);
            if size(details.Category, "*") > 1 then
                mod.categories = details.Category;
            end
        end

        URLs = [];
        if isfield(details, "URL") & details.URL <> "" then
            URLs = [URLs; details.URL];
        end
        if isfield(details, "WebSite") & details.WebSite <> "" then
            URLs = [URLs; details.WebSite];
        end
        if ~isempty(URLs) then
            mod.urls = URLs;
        end

        if isfield(details, "Date") then
            if ~isempty(regexp(details.Date, "/^[0-9]{4}-[0-1][0-9]-[0-3][0-9]\s/")) then
                mod.date = part(details.Date, 1:10);
            end
        end

        if isfield(details, OSNAME + ARCH + "Size") then
            mod.size = atomsGuiSize2human(details(OSNAME + ARCH + "Size"));
        end

        isInst = or(name == installedNames);
        mod.isInstalled = isInst;

        if isInst then
            instVers = atomsVersionSort(atomsGetInstalledVers(name), "DESC");
            instVer = instVers(1);
            mod.installedVersion = instVer;
            mod.updateAvailable = atomsVersionCompare(instVer, ver) == -1;
            mod.title = allModules(name)(instVer).Title;
            mod.autoloaded = or(name == autoloadedNames);
            instNames = [instNames; name];
        else
            mod.updateAvailable = %f;
            mod.autoloaded = %f;
        end

        modulesList($+1) = mod;
    end

    data = struct( ...
        "type", "refresh", ...
        "modules", modulesList, ...
        "installed", instNames);

    set("atoms_browser", "data", data);
endfunction

// =============================================================================
// atomsGuiSetStatus()
// =============================================================================
function atomsGuiSetStatus(level, msg)
    data = struct("type", "status", "level", level, "message", msg);
    set("atoms_browser", "data", data);
endfunction

// =============================================================================
// atomsGuiShowDetail()
// =============================================================================
function atomsGuiShowDetail(moduleName)
    data = struct("type", "detail", "name", moduleName);
    set("atoms_browser", "data", data);
endfunction

// =============================================================================
// atomsGuiBridgeSafe()
//  Sanitize strings before sending to the browser.
//  The Java bridge wraps JSON in single-quoted JS strings, so newlines
//  and single quotes in the data would break the executeJavaScript call.
// =============================================================================
function s = atomsGuiBridgeSafe(s)
    s = strsubst(s, ascii(13), "");
    s = strsubst(s, ascii(9), " ");
endfunction

// =============================================================================
// atomsGuiSize2human()
// =============================================================================
function human_str = atomsGuiSize2human(size_str)
    size_int = strtod(size_str);
    if size_int < 1024 then
        human_str = string(size_int) + " " + _("Bytes");
    elseif size_int < 1024*1024 then
        human_str = string(round(size_int/1024)) + " " + _("KB");
    else
        human_str = string(round((size_int*10)/(1024*1024)) / 10) + " " + _("MB");
    end
endfunction
