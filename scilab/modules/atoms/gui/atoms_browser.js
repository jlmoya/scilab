/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Dassault Systemes S.E. - Antoine ELIAS
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 */

// -- State --
var allModules = [];       // flat list of all modules from Scilab
var categories = [];       // category tree
var installedModules = []; // list of installed module names
var navStack = [];         // navigation path (category indices)
var currentTab = "browse"; // "browse" or "installed"
var searchMode = false;
var appLabels = {
    title: "ATOMS - Package Manager",
    home: "Home",
    browse: "Browse",
    installed: "Installed",
    search: "Search modules...",
    install: "Install",
    update: "Update",
    remove: "Remove",
    autoload: "Autoload",
    version: "Version",
    authors: "Author(s)",
    description: "Description",
    see_also: "See also",
    release_date: "Release date",
    download_size: "Download size",
    modules_title: "Modules",
    n_modules: "%d module(s)",
    n_results_for: '%d result(s) for "%s"',
    no_result: "No modules match your search.",
    no_installed: "No modules installed.",
    update_available: "Update available",
    installed_label: "Installed",
    not_installed: "Not installed",
    back: "Back",
    autoload_tooltip: "Load automatically at startup"
};

var COLORS = [
    "#1565C0", "#2E7D32", "#E65100", "#6A1B9A",
    "#00838F", "#AD1457", "#F9A825", "#4E342E",
    "#283593", "#00695C", "#BF360C", "#4527A0",
    "#01579B", "#33691E", "#E91E63", "#546E7A"
];

var selectedCategory = null;  // {path: [...], node: ...} currently selected in sidebar

// -- DOM refs --
var content = document.getElementById("content");
var breadcrumb = document.getElementById("breadcrumb");
var sidebar = document.getElementById("sidebar");
var browseLayout = document.getElementById("browse-layout");
var searchInput = document.getElementById("search");
var searchClear = document.getElementById("search-clear");
var statusbar = document.getElementById("statusbar");

// -- Entry point: receive data from Scilab --
function fromScilab(msg) {
    if (msg.type === "modulelist") {
        allModules = normalizeModules(msg.modules || []);
        categories = normalizeCategories(msg.categories || []);
        installedModules = flattenStringArray(msg.installed || []);
        if (msg.labels) {
            for (var k in msg.labels) {
                appLabels[k] = msg.labels[k];
            }
        }
        document.title = appLabels.title;
        document.getElementById("title").textContent = appLabels.title;
        document.getElementById("tab-browse").textContent = appLabels.browse;
        document.getElementById("tab-installed").textContent = appLabels.installed;
        searchInput.placeholder = appLabels.search;
        render();
        document.getElementById("app").classList.add("ready");
        document.getElementById("loading").style.display = "none";
        var refreshBtn = document.getElementById("header-refresh");
        if (refreshBtn) refreshBtn.classList.remove("spinning");
    } else if (msg.type === "status") {
        showStatus(msg.level || "info", msg.message || "");
        if (msg.level === "error") {
            var refreshBtnErr = document.getElementById("header-refresh");
            if (refreshBtnErr) refreshBtnErr.classList.remove("spinning");
        }
    } else if (msg.type === "refresh") {
        // Module data refreshed after install/remove
        if (msg.modules) allModules = normalizeModules(msg.modules);
        if (msg.installed) installedModules = flattenStringArray(msg.installed);
        render();
    } else if (msg.type === "detail") {
        // Show detail for a specific module after action
        showModuleDetail(msg.name);
    } else if (msg.type === "theme") {
        setTheme(msg.theme || "light");
    }
}

function setTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
}

// -- Status bar --
function showStatus(level, message) {
    statusbar.className = "statusbar " + level;
    var icons = {info: "\u2139\uFE0F", success: "\u2705", warning: "\u26A0\uFE0F", error: "\u274C"};
    statusbar.innerHTML = '<span class="statusbar-icon">' + (icons[level] || "") + '</span>' + escapeHtml(message);
}

function hideStatus() {
    statusbar.className = "statusbar hidden";
}

// -- Tabs --
function switchTab(tab) {
    currentTab = tab;
    document.getElementById("tab-browse").className = "tab-btn" + (tab === "browse" ? " active" : "");
    document.getElementById("tab-installed").className = "tab-btn" + (tab === "installed" ? " active" : "");
    navStack = [];
    selectedCategory = null;
    searchInput.value = "";
    searchMode = false;
    hideStatus();
    // Show/hide sidebar depending on tab
    sidebar.style.display = (tab === "browse") ? "" : "none";
    render();
    resetContentScroll();
}

// -- Search --
searchInput.addEventListener("input", function() {
    var q = this.value.trim();
    if (q.length >= 2) {
        searchMode = true;
        renderSearch(q);
    } else {
        searchMode = false;
        render();
    }
});

searchInput.addEventListener("keydown", function(e) {
    if (e.key === "Escape") {
        this.value = "";
        searchMode = false;
        render();
        this.blur();
    }
});

searchClear.addEventListener("click", function() {
    searchInput.value = "";
    searchMode = false;
    render();
    searchInput.focus();
});

// -- Navigation --
function navigateTo(indices) {
    if (indices.length === 0) {
        // Go back to the selected category root (or welcome)
        navStack = [];
    } else {
        navStack = indices.slice();
    }
    searchInput.value = "";
    searchMode = false;
    render();
    resetContentScroll();
}

function getCategoryAtPath(indices) {
    // Navigate within the selected category's children
    var node = selectedCategory ? (selectedCategory.node.children || []) : categories;
    for (var i = 0; i < indices.length; i++) {
        var cat = node[indices[i]];
        node = cat.children || [];
    }
    return node;
}

function getCategoryNodeAtPath(indices) {
    // Navigate within the selected category's children
    var node = selectedCategory ? (selectedCategory.node.children || []) : categories;
    for (var i = 0; i < indices.length; i++) {
        node = node[indices[i]];
        if (i < indices.length - 1 && node.children) {
            node = node.children;
        }
    }
    return node;
}

// -- Module helpers --
function findModule(name) {
    for (var i = 0; i < allModules.length; i++) {
        if (allModules[i].name === name) return allModules[i];
    }
    return null;
}

function isInstalled(name) {
    for (var i = 0; i < installedModules.length; i++) {
        if (installedModules[i] === name) return true;
    }
    return false;
}

function getModulesInCategory(catName) {
    var result = [];
    for (var i = 0; i < allModules.length; i++) {
        var m = allModules[i];
        if (m.category === catName || (m.categories && m.categories.indexOf(catName) >= 0)) {
            result.push(m);
        }
    }
    return result;
}

function getModuleStatus(mod) {
    if (!mod.isInstalled) return "not-installed";
    if (mod.updateAvailable) return "update-available";
    return "installed";
}

function resetContentScroll() {
    if (content) content.scrollTop = 0;
    var detailScroll = document.querySelector(".module-detail-section-content");
    if (detailScroll) detailScroll.scrollTop = 0;
}

// -- Rendering --
function render() {
    if (currentTab === "installed") {
        sidebar.style.display = "none";
        renderInstalledTab();
    } else {
        sidebar.style.display = "";
        renderSidebar();
        renderBrowseTab();
    }
    // Hide breadcrumb on the welcome screen and on the Installed tab.
    var hideBreadcrumb = currentTab === "installed" || !selectedCategory;
    breadcrumb.style.display = hideBreadcrumb ? "none" : "";
    if (!hideBreadcrumb) renderBreadcrumb();
}

function renderBrowseTab() {
    if (!selectedCategory) {
        // No category selected: show welcome
        renderWelcome();
    } else if (navStack.length === 0) {
        // Top-level category selected: show its content
        var node = selectedCategory.node;
        if (node.modules) {
            renderModuleList(node.modules, node.children || []);
        } else if (node.children) {
            renderMixedView(node.children, []);
        } else {
            renderEmpty();
        }
    } else {
        var lastNode = getCategoryNodeAtPath(navStack);
        if (lastNode.modules) {
            renderModuleList(lastNode.modules, lastNode.children || []);
        } else if (lastNode.children) {
            renderMixedView(lastNode.children, []);
        } else {
            renderEmpty();
        }
    }
}

function renderWelcome() {
    var total = allModules.length;
    content.innerHTML = '<div class="home-welcome">' +
        '<svg class="home-welcome-icon" viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="currentColor" stroke-width="1">' +
        '<circle cx="12" cy="12" r="3"/>' +
        '<ellipse cx="12" cy="12" rx="10" ry="4"/>' +
        '<ellipse cx="12" cy="12" rx="10" ry="4" transform="rotate(60 12 12)"/>' +
        '<ellipse cx="12" cy="12" rx="10" ry="4" transform="rotate(120 12 12)"/>' +
        '</svg>' +
        '<h2>' + escapeHtml(appLabels.title) + '</h2>' +
        '<p>' + escapeHtml(formatLabel(appLabels.n_modules, total)) + '</p>' +
        '</div>';
}

// -- Sidebar --
var expandedSidebar = {}; // track which top-level categories are expanded

function sortedIndices(arr) {
    var indices = [];
    for (var k = 0; k < arr.length; k++) indices.push(k);
    indices.sort(function(a, b) {
        return arr[a].name.localeCompare(arr[b].name);
    });
    return indices;
}

function renderSidebar() {
    var html = '<div class="sidebar-section-title">' + escapeHtml(appLabels.browse) + '</div>';
    var sorted = sortedIndices(categories);
    for (var s = 0; s < sorted.length; s++) {
        var i = sorted[s];
        var cat = categories[i];
        var color = COLORS[i % COLORS.length];
        var path = [i];
        var isActive = selectedCategory && selectedCategory.path.length >= 1 && selectedCategory.path[0] === i;
        var hasChildren = cat.children && cat.children.length > 0;
        var isExpanded = !!expandedSidebar[i];

        html += '<div class="sidebar-item' + (isActive ? ' active' : '') + '" onclick="selectSidebarCategory(' + JSON.stringify(path) + ', event)">';
        html += '<div class="sidebar-item-icon" style="background:' + color + '">' + getInitial(cat.name) + '</div>';
        html += '<div class="sidebar-item-label">' + escapeHtml(cat.name) + '</div>';
        html += '<span class="sidebar-item-count">' + (cat.moduleCount || 0) + '</span>';
        if (hasChildren) {
            html += '<span class="sidebar-toggle' + (isExpanded ? ' expanded' : '') + '"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 6 15 12 9 18"/></svg></span>';
        }
        html += '</div>';

        // Children (sorted)
        if (hasChildren) {
            html += '<div class="sidebar-children' + (isExpanded ? ' expanded' : '') + '">';
            var sortedChildren = sortedIndices(cat.children);
            for (var sc = 0; sc < sortedChildren.length; sc++) {
                var j = sortedChildren[sc];
                var child = cat.children[j];
                var childPath = [i, j];
                var childColor = COLORS[(i * 3 + j) % COLORS.length];
                var childActive = selectedCategory && selectedCategory.path.length === 2 && selectedCategory.path[0] === i && selectedCategory.path[1] === j;
                html += '<div class="sidebar-item' + (childActive ? ' active' : '') + '" onclick="selectSidebarCategory(' + JSON.stringify(childPath) + ', event)">';
                html += '<div class="sidebar-item-icon" style="background:' + childColor + '">' + getInitial(child.name) + '</div>';
                html += '<div class="sidebar-item-label">' + escapeHtml(child.name) + '</div>';
                html += '<span class="sidebar-item-count">' + (child.moduleCount || 0) + '</span>';
                html += '</div>';
            }
            html += '</div>';
        }
    }
    sidebar.innerHTML = html;
}

function selectSidebarCategory(path, event) {
    if (event) event.stopPropagation();
    var topIdx = path[0];
    var node;

    if (path.length === 1) {
        node = categories[topIdx];
        // Toggle expand if has children
        if (node.children && node.children.length > 0) {
            expandedSidebar[topIdx] = !expandedSidebar[topIdx];
        }
        selectedCategory = {path: path, node: node};
        navStack = [];
    } else {
        // Sub-category: path = [topIdx, childIdx]
        expandedSidebar[topIdx] = true;
        node = categories[topIdx].children[path[1]];
        selectedCategory = {path: path, node: node};
        navStack = [];
    }

    searchInput.value = "";
    searchMode = false;
    render();
    resetContentScroll();
}

function renderMixedView(subcats, modules) {
    var html = '<div class="module-list">';

    // Sub-categories
    for (var c = 0; c < subcats.length; c++) {
        var cat = subcats[c];
        var color = COLORS[(navStack.length * 3 + c) % COLORS.length];
        var count = cat.moduleCount || 0;
        html += '<div class="subcat-item" style="--i:' + c + '" onclick="navigateTo(' + JSON.stringify(navStack.concat(c)) + ')">';
        html += '<div class="subcat-item-left">';
        html += '<div class="subcat-icon" style="background:' + color + '">' + getInitial(cat.name) + '</div>';
        html += '<div>';
        html += '<div class="subcat-name">' + escapeHtml(cat.name) + '</div>';
        html += '<div><span class="subcat-count-badge">' + formatLabel(appLabels.n_modules, count) + '</span></div>';
        html += '</div>';
        html += '</div>';
        html += '<div class="subcat-arrow">&#x203A;</div>';
        html += '</div>';
    }

    // Modules
    if (modules.length > 0 && subcats.length > 0) {
        html += '<div class="section-title">' + escapeHtml(appLabels.modules_title) + '</div>';
    }
    html += renderModuleItems(modules, subcats.length);
    html += '</div>';
    content.innerHTML = html;
}

function renderModuleList(moduleNames, subcats) {
    var html = '<div class="module-list">';

    // Sub-categories first
    if (subcats && subcats.length > 0) {
        for (var c = 0; c < subcats.length; c++) {
            var cat = subcats[c];
            var color = COLORS[(navStack.length * 3 + c) % COLORS.length];
            var count = cat.moduleCount || 0;
            html += '<div class="subcat-item" style="--i:' + c + '" onclick="navigateTo(' + JSON.stringify(navStack.concat(c)) + ')">';
            html += '<div class="subcat-item-left">';
            html += '<div class="subcat-icon" style="background:' + color + '">' + getInitial(cat.name) + '</div>';
            html += '<div>';
            html += '<div class="subcat-name">' + escapeHtml(cat.name) + '</div>';
            html += '<div><span class="subcat-count-badge">' + formatLabel(appLabels.n_modules, count) + '</span></div>';
            html += '</div>';
            html += '</div>';
            html += '<div class="subcat-arrow">&#x203A;</div>';
            html += '</div>';
        }
        if (moduleNames.length > 0) {
            html += '<div class="section-title">' + escapeHtml(appLabels.modules_title) + '</div>';
        }
    }

    // Modules
    var modules = [];
    for (var i = 0; i < moduleNames.length; i++) {
        var mod = findModule(moduleNames[i]);
        if (mod) modules.push(mod);
    }
    html += renderModuleItems(modules, (subcats ? subcats.length : 0));
    html += '</div>';
    content.innerHTML = html;
}

function renderModuleItems(modules, offset) {
    var html = "";
    for (var i = 0; i < modules.length; i++) {
        var mod = modules[i];
        var status = getModuleStatus(mod);
        var idx = offset + i;
        html += '<div class="module-item" style="--i:' + idx + '" onclick="showModuleDetail(\'' + escapeAttr(mod.name) + '\')">';
        html += '<div class="module-status-dot ' + status + '" title="' + escapeAttr(getStatusLabel(status)) + '"></div>';
        html += '<div class="module-info">';
        html += '<div class="module-name">' + escapeHtml(mod.title || mod.name) + '</div>';
        if (mod.summary) {
            html += '<div class="module-summary">' + escapeHtml(mod.summary) + '</div>';
        }
        html += '</div>';
        html += '<span class="module-version-badge">' + escapeHtml(mod.version || "") + '</span>';
        html += '</div>';
    }
    return html;
}

function getStatusLabel(status) {
    if (status === "installed") return appLabels.installed_label;
    if (status === "update-available") return appLabels.update_available;
    return appLabels.not_installed;
}

// -- Module Detail --
function showModuleDetail(name) {
    var mod = findModule(name);
    if (!mod) return;

    // Update breadcrumb to show module name
    renderBreadcrumb(mod.title || mod.name);

    var status = getModuleStatus(mod);
    var html = '<div class="module-detail">';

    // Sticky top: header + meta (do not scroll)
    html += '<div class="module-detail-top">';

    // Header
    html += '<div class="module-detail-header">';
    html += '<div class="module-detail-header-left">';
    html += '<button class="module-detail-back" onclick="goBackFromDetail()" title="' + escapeAttr(appLabels.back) + '">';
    html += '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 6 9 12 15 18"/></svg>';
    html += '</button>';
    html += '<div>';
    var displayTitle = mod.title || mod.name;
    html += '<div class="module-detail-title">' + escapeHtml(displayTitle) + '</div>';
    if (displayTitle !== mod.name) {
        html += '<div class="module-detail-subtitle">' + escapeHtml(mod.name) + '</div>';
    }
    html += '</div>';
    html += '</div>';
    html += '<div class="module-detail-actions">';

    if (mod.isInstalled) {
        if (mod.updateAvailable) {
            html += '<button class="btn btn-update" onclick="doAction(\'update\',\'' + escapeAttr(mod.name) + '\')">' + escapeHtml(appLabels.update) + '</button>';
        }
        html += '<button class="btn btn-remove" onclick="doAction(\'remove\',\'' + escapeAttr(mod.name) + '\')">' + escapeHtml(appLabels.remove) + '</button>';
        html += '<label class="autoload-label" title="' + escapeAttr(appLabels.autoload_tooltip) + '">';
        html += '<input type="checkbox" onchange="doAutoload(\'' + escapeAttr(mod.name) + '\', this.checked)"' + (mod.autoloaded ? " checked" : "") + '>';
        html += escapeHtml(appLabels.autoload);
        html += '</label>';
    } else {
        html += '<button class="btn btn-install" onclick="doAction(\'install\',\'' + escapeAttr(mod.name) + '\')">' + escapeHtml(appLabels.install) + '</button>';
    }

    html += '</div>';
    html += '</div>';

    // Meta grid
    html += '<div class="module-detail-meta">';
    html += '<div><div class="meta-item-label">' + escapeHtml(appLabels.version) + '</div>';
    html += '<div class="meta-item-value">' + escapeHtml(mod.version || "-") + '</div></div>';
    if (mod.installedVersion) {
        html += '<div><div class="meta-item-label">' + escapeHtml(appLabels.installed_label) + '</div>';
        html += '<div class="meta-item-value">' + escapeHtml(mod.installedVersion) + '</div></div>';
    }
    if (mod.date) {
        html += '<div><div class="meta-item-label">' + escapeHtml(appLabels.release_date) + '</div>';
        html += '<div class="meta-item-value">' + escapeHtml(mod.date) + '</div></div>';
    }
    if (mod.size) {
        html += '<div><div class="meta-item-label">' + escapeHtml(appLabels.download_size) + '</div>';
        html += '<div class="meta-item-value">' + escapeHtml(mod.size) + '</div></div>';
    }
    html += '</div>';

    html += '</div>'; // end module-detail-top

    // Scrollable body: description only
    html += '<div class="module-detail-body">';
    if (mod.description) {
        html += '<div class="module-detail-section">';
        html += '<div class="module-detail-section-title">' + escapeHtml(appLabels.description) + '</div>';
        html += '<div class="module-detail-section-content">' + linkifyText(mod.description) + '</div>';
        html += '</div>';
    }

    html += '</div>'; // end module-detail-body

    // Fixed footer: Authors + See Also
    var hasFooter = mod.authors || (mod.urls && mod.urls.length > 0);
    if (hasFooter) {
        html += '<div class="module-detail-footer">';
        if (mod.authors) {
            html += '<div class="module-detail-footer-item">';
            html += '<span class="module-detail-footer-label">' + escapeHtml(appLabels.authors) + '</span> ';
            html += '<span>' + escapeHtml(mod.authors) + '</span>';
            html += '</div>';
        }
        if (mod.urls && mod.urls.length > 0) {
            html += '<div class="module-detail-footer-item">';
            html += '<span class="module-detail-footer-label">' + escapeHtml(appLabels.see_also) + '</span> ';
            for (var u = 0; u < mod.urls.length; u++) {
                if (u > 0) html += ' &middot; ';
                html += '<a href="' + escapeAttr(mod.urls[u]) + '" target="_blank">' + escapeHtml(mod.urls[u]) + '</a>';
            }
            html += '</div>';
        }
        html += '</div>';
    }

    html += '</div>';
    content.innerHTML = html;
}

// -- Installed tab --
function renderInstalledTab() {
    var installed = [];
    for (var i = 0; i < allModules.length; i++) {
        if (allModules[i].isInstalled) installed.push(allModules[i]);
    }

    if (installed.length === 0) {
        content.innerHTML = '<div class="empty-state"><p>' + escapeHtml(appLabels.no_installed) + '</p></div>';
        return;
    }

    var html = '<div class="module-list">';
    for (var i = 0; i < installed.length; i++) {
        var mod = installed[i];
        html += '<div class="installed-item" style="--i:' + i + '" onclick="showModuleDetail(\'' + escapeAttr(mod.name) + '\')">';
        html += '<div class="module-status-dot ' + getModuleStatus(mod) + '"></div>';
        html += '<div class="installed-info">';
        html += '<div class="installed-name">' + escapeHtml(mod.title || mod.name) + '</div>';
        html += '<div class="installed-version">' + escapeHtml(mod.name) + ' v' + escapeHtml(mod.installedVersion || mod.version || "") + '</div>';
        html += '</div>';
        html += '<div class="installed-badges">';
        if (mod.autoloaded) {
            html += '<span class="badge badge-autoload">' + escapeHtml(appLabels.autoload) + '</span>';
        }
        if (mod.updateAvailable) {
            html += '<span class="badge badge-update">' + escapeHtml(appLabels.update) + '</span>';
        }
        html += '</div>';
        html += '</div>';
    }
    html += '</div>';
    content.innerHTML = html;
}

// -- Search --
function renderSearch(query) {
    var q = query.toLowerCase();
    var results = [];
    for (var i = 0; i < allModules.length; i++) {
        var mod = allModules[i];
        var haystack = (mod.name + " " + (mod.title || "") + " " + (mod.summary || "") + " " + (mod.description || "")).toLowerCase();
        if (haystack.indexOf(q) >= 0) {
            results.push(mod);
        }
    }

    var html = '<div class="search-results-title">' + escapeHtml(formatLabel(appLabels.n_results_for, results.length, query)) + '</div>';
    html += '<div class="module-list">';

    if (results.length === 0) {
        html += '<div class="empty-state"><p>' + escapeHtml(appLabels.no_result) + '</p></div>';
    }

    for (var i = 0; i < results.length; i++) {
        var mod = results[i];
        var status = getModuleStatus(mod);
        var highlighted = highlightMatch(mod.title || mod.name, query);
        html += '<div class="module-item" style="--i:' + i + '" onclick="showModuleDetail(\'' + escapeAttr(mod.name) + '\')">';
        html += '<div class="module-status-dot ' + status + '"></div>';
        html += '<div class="module-info">';
        html += '<div class="module-name">' + highlighted + '</div>';
        if (mod.summary) {
            html += '<div class="module-summary">' + highlightMatch(mod.summary, query) + '</div>';
        }
        html += '</div>';
        html += '<span class="module-version-badge">' + escapeHtml(mod.version || "") + '</span>';
        html += '</div>';
    }
    html += '</div>';
    content.innerHTML = html;
}

// -- Breadcrumb --
function renderBreadcrumb(extraLabel) {
    var html = '';
    if (selectedCategory) {
        // Show selected category as root crumb
        var catName = selectedCategory.node.name;
        if (navStack.length > 0 || extraLabel) {
            html += '<a class="crumb" onclick="navigateTo([])">' + escapeHtml(catName) + '</a>';
        } else {
            html += '<span class="crumb current">' + escapeHtml(catName) + '</span>';
        }

        for (var i = 0; i < navStack.length; i++) {
            var path = navStack.slice(0, i + 1);
            var node = getCategoryNodeAtPath(path);
            html += '<span class="separator">&#x203A;</span>';
            if (!extraLabel && i === navStack.length - 1) {
                html += '<span class="crumb current">' + escapeHtml(node.name) + '</span>';
            } else {
                html += '<a class="crumb" onclick="navigateTo(' + JSON.stringify(path) + ')">' + escapeHtml(node.name) + '</a>';
            }
        }
        if (extraLabel) {
            html += '<span class="separator">&#x203A;</span>';
            html += '<span class="crumb current">' + escapeHtml(extraLabel) + '</span>';
        }
    } else {
        html += '<span class="crumb current">' + escapeHtml(appLabels.home) + '</span>';
    }
    breadcrumb.innerHTML = html;
}

// -- Actions --
function doAction(action, moduleName) {
    // Disable buttons visually
    var buttons = document.querySelectorAll(".btn");
    for (var i = 0; i < buttons.length; i++) {
        buttons[i].disabled = true;
    }
    toScilab({type: action, name: moduleName});
}

function doAutoload(moduleName, checked) {
    var mod = findModule(moduleName);
    if (mod) mod.autoloaded = checked;
    toScilab({type: "autoload", name: moduleName, enable: checked});
}

function doSystemUpdate() {
    var btn = document.getElementById("header-refresh");
    if (btn) btn.classList.add("spinning");
    toScilab({type: "systemUpdate"});
}

function goBackFromDetail() {
    // Re-render the parent view (browse list, sub-category list, or installed list).
    // render() picks the right view based on currentTab/selectedCategory/navStack.
    render();
    resetContentScroll();
}

// -- Data normalization --
// Scilab JSON serialization quirks:
//   - 1-element string vectors become a plain string instead of an array
//   - string column vectors become arrays of 1-element arrays [["a"],["b"]]
//   - list() becomes an array, which is correct

function toArray(val) {
    if (!val) return [];
    if (typeof val === "string") return [val];
    if (Array.isArray(val)) return val;
    return [val];
}

function flattenStringArray(val) {
    // Flatten [["a"],["b"]] to ["a","b"] and "a" to ["a"]
    if (!val) return [];
    if (typeof val === "string") return [val];
    if (!Array.isArray(val)) return [val];
    var result = [];
    for (var i = 0; i < val.length; i++) {
        if (Array.isArray(val[i])) {
            for (var j = 0; j < val[i].length; j++) {
                result.push(val[i][j]);
            }
        } else {
            result.push(val[i]);
        }
    }
    return result;
}

function normalizeModules(modules) {
    if (!Array.isArray(modules)) modules = [modules];
    for (var i = 0; i < modules.length; i++) {
        var m = modules[i];
        if (m.urls) m.urls = flattenStringArray(m.urls);
        if (m.categories) m.categories = toArray(m.categories);
        // Decode HTML entities in text fields
        if (m.title) m.title = decodeHtmlEntities(m.title);
        if (m.summary) m.summary = decodeHtmlEntities(m.summary);
        if (m.description) m.description = decodeHtmlEntities(m.description);
        if (m.authors) m.authors = decodeHtmlEntities(m.authors);
    }
    return modules;
}

function normalizeCategories(cats) {
    if (!Array.isArray(cats)) cats = [cats];
    for (var i = 0; i < cats.length; i++) {
        var cat = cats[i];
        if (cat.modules) cat.modules = flattenStringArray(cat.modules);
        if (cat.children) {
            cat.children = normalizeCategories(toArray(cat.children));
        }
    }
    return cats;
}

// -- Helpers --
function getInitial(name) {
    var trimmed = name.replace(/^\s+/, "");
    return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : "?";
}

function formatLabel(template) {
    var args = Array.prototype.slice.call(arguments, 1);
    var i = 0;
    return template.replace(/%[ds]/g, function() { return args[i++]; });
}

function escapeHtml(str) {
    if (!str) return "";
    var div = document.createElement("div");
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
}

function escapeAttr(str) {
    return escapeHtml(str).replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

function linkifyText(str) {
    if (!str) return "";
    // Escape HTML first, then convert URLs to clickable links
    var escaped = escapeHtml(str);
    return escaped.replace(/(https?:\/\/[^\s<&]+)/g, '<a href="$1" target="_blank">$1</a>');
}

function decodeHtmlEntities(str) {
    if (!str) return "";
    var div = document.createElement("div");
    div.innerHTML = str;
    return div.textContent || div.innerText || "";
}

function highlightMatch(text, query) {
    if (!text) return "";
    var lower = text.toLowerCase();
    var idx = lower.indexOf(query.toLowerCase());
    if (idx < 0) return escapeHtml(text);
    var before = text.substring(0, idx);
    var match = text.substring(idx, idx + query.length);
    var after = text.substring(idx + query.length);
    return escapeHtml(before) + '<span class="search-highlight">' + escapeHtml(match) + '</span>' + escapeHtml(after);
}

function renderEmpty() {
    content.innerHTML = '<div class="empty-state"><p>' + escapeHtml(appLabels.no_result) + '</p></div>';
}

// -- Keyboard shortcuts --
document.addEventListener("keydown", function(e) {
    if ((e.ctrlKey && e.key === "f") || (e.key === "/" && document.activeElement !== searchInput)) {
        e.preventDefault();
        searchInput.focus();
        searchInput.select();
    }
    if (e.key === "Backspace" && document.activeElement !== searchInput && navStack.length > 0) {
        navigateTo(navStack.slice(0, -1));
    }
});

// -- Init: request data from Scilab --
if (typeof toScilab === "function") {
    toScilab("loaded");
}
