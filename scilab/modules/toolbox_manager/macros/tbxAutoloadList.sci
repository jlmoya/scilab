// Loader paths for autoload, ORDERED SO DEPENDENCIES LOAD FIRST, and CLOSED
// OVER THOSE DEPENDENCIES.
//
// Two things this does that the previous version did not:
//
//   ORDER IS COMPUTED, not stored. It used to return manifest FILE order, and
//   tbxInstall appends — so a dependency was satisfied only by the luck of
//   insertion order, and the only repair was to hand-edit the manifest. That is
//   not dependency resolution; it is a human being the resolver, once, until the
//   next install breaks it. tbx_toposort now computes the order from declared
//   dependencies (tbx_deps: DESCRIPTION's `Depends:`, plus the manifest's
//   optional deps column for toolboxes shipping no DESCRIPTION). Manifest order
//   survives only as the stable tie-break between independent toolboxes, so a
//   dependency-free manifest loads exactly as it always did.
//
//   THE SET IS CLOSED. If a toolbox is autoload-enabled, everything it depends
//   on is loaded too — transitively — even when that dependency is installed
//   with autoload switched off. Enabling a toolbox whose dependency is disabled
//   used to produce a half-loaded toolbox that failed later, at call time, with
//   an undefined-variable error naming a function the user never heard of. The
//   user asked for one toolbox; wanting its dependencies is implied, so the
//   system supplies them rather than reporting that it could have.
//
// What is left to REPORT is only what cannot be resolved automatically: a
// dependency that is not installed at all, and dependency cycles. Both name the
// dependent and the fix. That reporting is why this exists — the missing-guimaker
// defect sat in plain sight for months because nothing checked, and two more
// (helptbx, gui2bitmap) fell out of the declared data the moment anything did.
function paths = tbxAutoloadList()
    M = tbx_manifest_read();
    paths = [];
    nAll = size(M.name, "*");
    if nAll == 0 then return; end

    // dependencies of every INSTALLED toolbox (not just the enabled ones — the
    // closure below may pull a disabled one in)
    allDeps = list();
    for i = 1:nAll
        d = "";
        if isfield(M, "deps") & size(M.deps, "*") >= i then d = M.deps(i); end
        allDeps(i) = tbx_deps(M.path(i), d);
    end

    // MANIFEST PATHS MUST BE THE CANONICAL TOOLBOX ROOT.
    //
    // tbx_resolve() only ever yields <projects>/<name> or <tbxdir>/<name>, so a
    // path that is neither is a stale or hand-edited entry. Such an entry is NOT
    // necessarily broken -- and that is exactly what makes it dangerous.
    //
    // sciQuantLib was recorded as .../sciQuantLib/quantlib-swig/Scilab/toolbox,
    // a NESTED directory that has a perfectly good loader.sce. The toolbox loaded
    // and every gateway function worked, so nothing looked wrong. What silently
    // did not happen was the toolbox ROOT's own loader.sce ever running -- the
    // delegator that registers the Demonstrations menu entry. The demo was simply
    // absent, with no error anywhere to connect it to a path recorded months
    // earlier. A check for "does the loader load" would never have caught it.
    //
    // So: when the canonical root exists AND carries a loader.sce, it is
    // authoritative (it is what a fresh tbxInstall would record) and the manifest
    // is repaired in place. When it does not, the entry is left alone -- it may be
    // a deliberate custom location -- and only reported.
    cfg = tbx_cfg();
    fixed = %f;
    for i = 1:nAll
        canon  = fullfile(cfg.projects, M.name(i));
        remote = fullfile(cfg.tbxdir,   M.name(i));
        if M.path(i) == canon | M.path(i) == remote then continue; end
        if isfile(fullfile(canon, "loader.sce")) then
            mprintf("[toolbox-manager] %s: manifest path was %s, which is not the " + ..
                    "toolbox root. Repaired to %s (the root loader registers demos " + ..
                    "and help, and was being skipped).\n", M.name(i), M.path(i), canon);
            M.path(i) = canon;
            fixed = %t;
        else
            mprintf("[toolbox-manager] %s: manifest path %s is not the canonical " + ..
                    "root and %s has no loader.sce — left as-is. If this is not " + ..
                    "deliberate, re-register with tbxInstall(""%s"").\n", ..
                    M.name(i), M.path(i), canon, M.name(i));
        end
    end
    if fixed then tbx_manifest_write(M); end

    // seed: autoload-enabled and actually loadable
    want = zeros(nAll, 1);
    for i = 1:nAll
        if M.autoload(i) == 1 & isfile(fullfile(M.path(i), "loader.sce")) then
            want(i) = 1;
        end
    end

    // close over dependencies: keep pulling in installed deps until steady state
    missing = [];
    changed = %t;
    while changed
        changed = %f;
        for i = 1:nAll
            if want(i) <> 1 then continue; end
            d = allDeps(i);
            for k = 1:size(d, "*")
                j = find(M.name == d(k));
                if isempty(j) then
                    if isempty(missing) | isempty(find(missing(:, 1) == M.name(i) & ..
                                                      missing(:, 2) == d(k))) then
                        missing = [missing ; M.name(i), d(k)];
                    end
                    continue;
                end
                j = j(1);
                if want(j) <> 1 & isfile(fullfile(M.path(j), "loader.sce")) then
                    want(j) = 1;
                    changed = %t;
                    mprintf("[toolbox-manager] loading %s: required by %s.\n", ..
                            M.name(j), M.name(i));
                end
            end
        end
    end

    idx = find(want == 1);
    if isempty(idx) then return; end
    names = M.name(idx); ldrs = [];
    deps = list();
    for k = 1:size(idx, "*")
        ldrs = [ldrs ; fullfile(M.path(idx(k)), "loader.sce")];
        deps(k) = allDeps(idx(k));
    end

    [order, miss2, cycles] = tbx_toposort(names, deps);

    // miss2 re-finds the same not-installed deps; report each once
    for i = 1:size(missing, "r")
        mprintf("[toolbox-manager] %s needs %s, which is NOT INSTALLED. " + ..
                "Install it with tbxInstall(""%s"").\n", ..
                missing(i, 1), missing(i, 2), missing(i, 2));
    end
    if ~isempty(cycles) then
        mprintf("[toolbox-manager] dependency CYCLE involving: %s — these load in " + ..
                "manifest order, which may be wrong. Break the cycle in their " + ..
                "DESCRIPTION Depends or the manifest deps column.\n", ..
                strcat(cycles', ", "));
    end

    for i = 1:size(order, "*")
        paths = [paths ; ldrs(order(i))];
    end
    // cycle members still load, just unordered — better than not loading at all
    for i = 1:size(names, "*")
        if isempty(find(order == i)) then paths = [paths ; ldrs(i)]; end
    end
endfunction
