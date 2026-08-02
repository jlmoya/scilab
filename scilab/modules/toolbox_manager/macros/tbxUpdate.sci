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

function ok = tbxUpdate(name, dopull)

    if argn(1) < 2 then dopull = %f; end
    if argn(1) < 1 then name = []; end

    // ---- update every registered toolbox ----------------------------------
    if isempty(name) then
        M = tbx_manifest_read();
        nm = M.name;
        n  = size(nm, "*");
        ok = %t;
        if dopull then netmsg = "ON (bounded)"; else netmsg = "OFF"; end
        mprintf("tbxUpdate: %d toolbox(es), network pull %s\n", n, netmsg);
        mprintf("  each native toolbox runs ./configure && make -- minutes each is normal\n");
        t0all = getdate("s");
        nfail = 0;
        for i = 1:n
            mprintf("[%d/%d] %s: starting\n", i, n, nm(i));
            t0 = getdate("s");
            oki = tbxUpdate(nm(i), dopull);
            dt  = getdate("s") - t0;
            if oki then
                verdict = "ok";
            else
                verdict = "FAILED";
                nfail = nfail + 1;
            end
            mprintf("[%d/%d] %s: %s in %ds (elapsed %ds)\n", ..
                    i, n, nm(i), verdict, dt, getdate("s") - t0all);
            ok = oki & ok;
        end
        mprintf("tbxUpdate: done -- %d ok, %d failed, %ds total\n", ..
                n - nfail, nfail, getdate("s") - t0all);
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
    ok = tbx_build(path);

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

    mprintf("      loading\n");
    ldr = fullfile(path, "loader.sce");        // inline exec (see tbxInstall note)
    try, exec(ldr, -1); catch, ok = %f; end

endfunction
