// Scilab toolbox manager — tbxUpdate
//
// WHY THIS FILE IS SO DEFENSIVE ABOUT THE NETWORK
// -----------------------------------------------
// A `./package-macos.sh --rebuild-toolboxes` run was killed after 24+ HOURS with
// no output. It was not slow -- it was wedged. tbxUpdate() called
// `git pull --ff-only` on every registered toolbox through tbx_sh(), which is a
// bare unix_g() with no timeout; 52 of the 54 registered toolboxes have SSH
// remotes, and gitlab.com:22 was black-holing that day. One unreachable remote,
// or one credential prompt (invisible, because unix_g captures output), stops
// the whole run for as long as you let it.
//
// Three changes came out of that:
//   1. The network pull is now OPT-IN. Rebuilding is not the same operation as
//      updating from upstream, and the common case -- "the engine ABI changed,
//      rebuild the native gateways" -- needs no network at all.
//   2. When you do ask for it, every git invocation is bounded and
//      non-interactive: ConnectTimeout, BatchMode (never prompt, just fail) and
//      a hard `timeout` wrapper when one is available.
//   3. Progress prints per toolbox with an index and elapsed seconds, so
//      "still working" and "stalled" look different from the outside. That was
//      the actual complaint: not that it was slow, but that there was no way to
//      tell which.
//
//   tbxUpdate()            // all toolboxes, no network
//   tbxUpdate(name)        // one toolbox, no network
//   tbxUpdate(name, %t)    // one toolbox, git pull first
//   tbxUpdate([], %t)      // all toolboxes, git pull first

// The third argument is internal: the "update all" driver below passes %f to
// build a toolbox without loading it, because it does the loading itself.

function ok = tbxUpdate(name, dopull, doload)

    // argn(2) is the INPUT count; argn(1) is the OUTPUT count and is
    // always 1 here. The original code tested argn(1) < 1, which is never
    // true, so its no-argument "update all" branch never actually fired.
    if argn(2) < 3 then doload = %t; end
    if argn(2) < 2 then dopull = %f; end
    if argn(2) < 1 then name = []; end

    // ---- update every registered toolbox ----------------------------------
    if isempty(name) then
        M = tbx_manifest_read();
        nm = M.name;
        n  = size(nm, "*");
        ok = %t;

        // Build in DEPENDENCY order, and load each toolbox HERE rather than inside
        // the per-toolbox call.
        //
        // Both halves are needed, and the second one is the subtle one: a macro
        // library exists only in the scope that exec'd its loader. Loading inside
        // the recursive tbxUpdate(nm(i)) call therefore threw every library away
        // the instant that call returned, so a toolbox built later never saw its
        // dependency -- guimaker aborts with "apifun is required but not loaded"
        // immediately after apifun itself built and loaded successfully. Ordering
        // alone cannot fix that; the loads have to share one scope that outlives
        // the individual toolboxes, which is this loop.
        //
        // tbx_toposort keeps the ready-set in the caller's original order, so a
        // dependency-free manifest is ordered exactly as before.
        depsList = list();
        for i = 1:n
            dcol = "";
            if isfield(M, "deps") then dcol = M.deps(i); end
            depsList(i) = tbx_deps(M.path(i), dcol);
        end
        [ord, missdep, cyc] = tbx_toposort(nm, depsList);
        if ~isempty(missdep) then
            for i = 1:size(missdep, "r")
                mprintf("  note: %s declares %s, which is not installed\n", ..
                        missdep(i, 1), missdep(i, 2));
            end
        end
        if ~isempty(cyc) then
            mprintf("  note: %d toolbox(es) in a dependency cycle, built last\n", size(cyc, "*"));
        end
        // Anything the sort could not place still gets built -- appended, never dropped.
        rest = [];
        for i = 1:n
            if isempty(find(ord == i)) then rest = [rest ; i]; end
        end
        ord = [matrix(ord, -1, 1) ; rest];
        if dopull then netmsg = "ON (bounded)"; else netmsg = "OFF"; end
        mprintf("tbxUpdate: %d toolbox(es), network pull %s\n", n, netmsg);
        mprintf("  each native toolbox runs ./configure && make -- minutes each is normal\n");
        // Loaders are exec'd in THIS scope -- that is the whole point -- so they
        // can and do assign ordinary names like i, k and path. Every value the
        // loop needs to survive a loader lives under a tbx__ prefix, and the
        // counter is explicit rather than a for-loop variable.
        tbx__nm    = nm;
        tbx__ord   = ord;
        tbx__n     = n;
        tbx__paths = M.path;
        tbx__pull  = dopull;
        tbx__t0all = getdate("s");
        tbx__nfail = 0;
        tbx__ok    = %t;
        tbx__k     = 1;
        while tbx__k <= tbx__n
            tbx__i = tbx__ord(tbx__k);
            mprintf("[%d/%d] %s: starting\n", tbx__k, tbx__n, tbx__nm(tbx__i));
            tbx__t0  = getdate("s");
            tbx__oki = tbxUpdate(tbx__nm(tbx__i), tbx__pull, %f);   // build only
            if tbx__oki then
                tbx__ldr = fullfile(tbx__paths(tbx__i), "loader.sce");
                if isfile(tbx__ldr) then
                    mprintf("      loading\n");
                    try
                        exec(tbx__ldr, -1);
                    catch
                        mprintf("      LOAD FAILED: %s\n", lasterror());
                        tbx__oki = %f;
                    end
                else
                    mprintf("      LOAD FAILED: no loader.sce\n");
                    tbx__oki = %f;
                end
            end
            tbx__dt = getdate("s") - tbx__t0;
            if tbx__oki then
                tbx__verdict = "ok";
            else
                tbx__verdict = "FAILED";
                tbx__nfail   = tbx__nfail + 1;
            end
            mprintf("[%d/%d] %s: %s in %ds (elapsed %ds)\n", ..
                    tbx__k, tbx__n, tbx__nm(tbx__i), tbx__verdict, tbx__dt, ..
                    getdate("s") - tbx__t0all);
            tbx__ok = tbx__oki & tbx__ok;
            tbx__k  = tbx__k + 1;
        end
        mprintf("tbxUpdate: done -- %d ok, %d failed, %ds total\n", ..
                tbx__n - tbx__nfail, tbx__nfail, getdate("s") - tbx__t0all);
        ok = tbx__ok;
        return;
    end

    // ---- update one toolbox -----------------------------------------------
    M = tbx_manifest_read();
    k = tbx_find(M, name);
    if k == 0 then
        mprintf("tbxUpdate: %s not installed\n", name);
        ok = %f;
        return;
    end
    path = M.path(k);

    if dopull & isdir(fullfile(path, ".git")) then
        // Bounded and non-interactive on every axis that can block:
        //   GIT_TERMINAL_PROMPT=0  -> never ask for a username/password
        //   BatchMode=yes          -> never ask for an SSH passphrase, just fail
        //   ConnectTimeout=10      -> do not wait out a black-holed TCP connect
        //   timeout <n>            -> covers the connected-then-stalled transfer,
        //                             which the ssh options alone do not
        netcap = 90;
        [tok, tout] = tbx_sh("command -v timeout");
        if tok then
            pre = "timeout " + string(netcap) + " ";
        else
            pre = "";   // ssh options still bound the common failure modes
            mprintf("      (no timeout binary; relying on ssh ConnectTimeout only)\n");
        end
        mprintf("      git pull (cap %ds)\n", netcap);
        cmd = "cd """ + path + """ && GIT_TERMINAL_PROMPT=0 " + ..
              "GIT_SSH_COMMAND=""ssh -o BatchMode=yes -o ConnectTimeout=10"" " + ..
              pre + "git pull --ff-only";
        tp0 = getdate("s");
        [pok, pout] = tbx_sh(cmd);
        if ~pok then
            // Never fatal: a rebuild from the local working tree is still valid,
            // and being offline must not fail the whole run.
            mprintf("      pull skipped after %ds (unreachable, auth needed, or not fast-forwardable)\n", ..
                    getdate("s") - tp0);
            mprintf("      continuing with the local source tree\n");
        end
    elseif dopull then
        mprintf("      (not a git checkout; nothing to pull)\n");
    end

    mprintf("      building\n");
    tbx__built = tbx_build(path);

    // native-arm64 gate: a rebuild may have produced non-arm64 native libs; don't load them
    [archok, bad] = tbx_arch_check(path);
    if ~archok then
        mprintf("      ARCH GATE: %s ships non-arm64 native libs; not loading (needs an arm64 rebuild):\n", name);
        for i = 1:size(bad, "*")
            mprintf("        %s\n", bad(i));
        end
        ok = %f;
        return;
    end

    // The "update all" driver loads in its own scope, so that libraries stay
    // visible to the toolboxes built after them; it tells us to skip loading here.
    if ~doload then
        ok = tbx__built;
        return;
    end

    mprintf("      loading\n");
    // Same scope hazard as in tbx_build: the loader is exec'd at level -1, so its
    // variables land in THIS scope. A loader that happens to assign `ok` would
    // overwrite the build verdict -- including turning a failed build into a pass.
    tbx__ldr    = fullfile(path, "loader.sce");   // inline exec (see tbxInstall note)
    tbx__loaded = %t;
    try, exec(tbx__ldr, -1); catch, tbx__loaded = %f; end
    ok = tbx__built & tbx__loaded;

endfunction
