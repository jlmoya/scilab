// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) ENPC
// Copyright (C) 2007-2008 - INRIA - Sylvestre LEDRU (rewrite to use autotools)
// Copyright (C) 2009-2010 - DIGITEO - Sylvestre LEDRU
//
// Copyright (C) 2012 - 2016 - Scilab Enterprises
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

//=============================================================================
// Generate a Makefile which can be used by ilib_*
//=============================================================================

function ilib_gen_Make_unix(names,   ..
    files,   ..
    libs,    ..
    libname, ..
    ldflags, ..
    cflags,  ..
    fflags,  ..
    cc,      ..
    tables   ..
    )

    if getos() == "Windows" then
        return
    end

    if libname == "" then libname = names(1);end

    if ( strncpy(libname,3) == "lib") then
        l       = strsplit(libname,3);
        libname = l(2);
        clear l;
    end

    if isdef("tables") then

        /// Check tables ... the second element should be the file name
        if typeof(tables)<>"list" then
            tables=list(tables)
        end
        L=length(tables);

        // for each element in tables
        for it=1:L
            table = tables(it)
            [mt,nt]=size(table);
            for i=1:mt ;
                // mex files to be added to the build process
                if table(i,3)=="cmex" | table(i,3)=="fmex" | table(i,3)=="Fmex" then
                    if isempty(find(basename(files)==table(i,2))) then // If not already in the array
                        files=[files, table(i,2)] // add it to the file list
                    end
                end
            end
        end

    end // isdef('tables')


    originPath  = pwd();
    linkBuildDir    = TMPDIR+"/"+libname;
    mkdir(linkBuildDir);
    commandpath = SCI+"/modules/dynamic_link/src/scripts";
    usercommandpath = SCIHOME+"/modules/dynamic_link/src/scripts";

    // Seed (or repair) the shared flagless configure cache, so that the ~11 s
    // ./configure below runs once per installation instead of once per library.
    //
    // This used to be guarded on `isdir(usercommandpath) == %F` and to call
    // generateConfigure with 1 of its 5 arguments, which raises "Undefined
    // variable: ldflags". The directory had already been mkdir'd on the line
    // above, so the guard never let it retry: the cache directory existed,
    // Makefile.orig and libtool never appeared in it, and the reuse test below
    // could not succeed on any installation. Every gateway build re-ran the
    // full compiler detection and then wrote a cache key nobody would read.
    //
    // Guard on the artifacts, not on the directory, so an installation left in
    // that state repairs itself.
    if ~isdir(usercommandpath) then
        mkdir(usercommandpath);
    end
    if ~isfile(usercommandpath+"/Makefile.orig") | ~isfile(usercommandpath+"/libtool") then
        // Deliberately flagless: this cache is shared by every library, so it
        // may only ever hold a build that depends on no caller's flags. The
        // reuse test below enforces the matching half of that contract.
        //
        // errcatch because seeding is an optimisation, not a precondition. If
        // it fails we fall through to the per-build configure, which is exactly
        // what happens today -- a broken cache must not break the build.
        ie_seed = execstr("copyMandatoryFiles(commandpath, usercommandpath); " + ..
                          "generateConfigure(usercommandpath, """", """", """", """");", "errcatch");
        if ie_seed <> 0 & ilib_verbose() == 2 then
            mprintf(gettext("   %s: Could not seed the shared compiler detection (%s); continuing without it.\n"), ..
                    "ilib_gen_Make", lasterror()(1));
        end
    end

    // Copy files => linkBuildDir
    chdir(linkBuildDir);

    if ( ilib_verbose() <> 0 ) then
        mprintf(gettext("   %s: Copy compilation files (Makefile*, libtool...) to TMPDIR\n"),"ilib_gen_Make");
    end

    copyMandatoryFiles(usercommandpath,linkBuildDir);

    filelist = "";

    for x = files(:)' ;
        // Pre added file in the list ... don't really know why

        if (x <> "csci") then
            // Old way: to compile a fun.c file, the user had to provide fun.o
            filename = strsubst(x,".o","");

            chdir(originPath); // Switch back to the source dir in order to have only the filename
            filesMatching = ls(filename+".*");

            // Two cases here:
            // * The user provided the real filename. Then, take if straight
            // * The user provided a file ending by .o (example: myfile.o)
            // We stripped the ending .o and looked for all files
            if filesMatching == [] | fileinfo(x) <> [] then

                [pathFrom, file_name, file_extension]=fileparts(x); // Retrieve the path of the file
                if length(pathFrom) == 0 then // Empty => it should be PWD
                    pathFrom=pwd();
                end

                if pathFrom <> linkBuildDir then
                    if ( ilib_verbose() <> 0 ) then
                        mprintf(gettext("   %s: Copy %s to TMPDIR\n"),"ilib_gen_Make",x);
                    end
                    copyfile(x, linkBuildDir);
                else
                    if ( ilib_verbose() <> 0 ) then
                        mprintf(gettext("   %s: Did not copy %s: Source and target directories are the same (%s).\n"),"ilib_gen_Make",x,pathFrom);
                    end
                end

                if ~isfile(pathFrom + "/" + file_name + file_extension) then
                    error(msprintf(_("%s: Wrong value for input argument #%d: existing file(s) expected. Provided: %s\n"), "ilib_gen_Make_unix", 2, pathFrom + "/" + file_name + file_extension));
                end

                filelist = filelist + " " + file_name + file_extension;

            else

                // Or copy the file matching to what we were looking for
                // (this stuff could lead to bug if you have fun.c fun.f
                // or fun.cxx but it was already the case before ...

                // Not that we don't want to copy working files
                ignoredFileExtension=[".lo",".la",".lai"]
                for f=filesMatching(:)'

                    if ~isfile(f) then
                        error(msprintf(_("%s: Wrong value for input argument #%d: existing file(s) expected.\n"), "ilib_gen_Make_unix", 2));
                    end

                    if strindex(f,ignoredFileExtension) == [] then
                        if ( ilib_verbose() <> 0 ) then
                            mprintf(gettext("   %s: Copy %s to TMPDIR\n"),"ilib_gen_Make",f);
                        end

                        copyfile(f, linkBuildDir);
                        filelist = filelist + " " + f;
                    else
                        if ( ilib_verbose() <> 0 ) then
                            mprintf(gettext("   %s: File %s ignored.\n"),"ilib_gen_Make",f);
                        end
                    end
                end
            end
            chdir(linkBuildDir);
        end
    end

    // Step 3 of the CMake migration: emit the declarative CMakeLists.txt.
    // SCILAB_GATEWAY_BUILD selects which generator runs --
    //   unset / "make"  the autotools skeleton alone (unchanged default)
    //   "cmake"         CMakeLists.txt alone
    //   "both"          both, so the two can be diffed on a real toolbox
    //
    // Placed here, ABOVE the compiler detection, on purpose: everything below
    // exists only to produce the skeleton's Makefile, and ./configure is the
    // ~11 s that the migration is meant to delete. Emitting CMake and then
    // running configure anyway would hide the win the switch exists to measure.
    // filelist and linkBuildDir are both final at this point.
    //
    // The switch lives in the generator rather than in ilib_compile because
    // this is where those two are in scope; ilib_compile reads the same
    // variable to decide what to invoke (step 4).
    gwBuild = getenv("SCILAB_GATEWAY_BUILD", "make");
    if gwBuild == "cmake" | gwBuild == "both" then
        ilib_gen_cmake_unix(libname, filelist, ldflags, cflags, fflags, cc, linkBuildDir);
    end
    if gwBuild == "cmake" then
        chdir(originPath);
        return;
    end

    // Reuse the shared detection only for a build that asked for no flags of its
    // own, because there is exactly ONE shared Makefile.orig for every library
    // on the installation and it was generated flagless.
    //
    // The previous test also accepted a match against a per-library
    // <libname>.md5 key. That is unsound and only stayed harmless because the
    // cache never populated: the key is per library, the cached artifact is
    // shared, so library A built with -DFOO would record its key, and a later
    // A build would then reuse whatever flagless Makefile.orig some unrelated
    // library had left behind -- silently dropping -DFOO. Fixing the cache
    // without removing that branch would have turned a dead optimisation into a
    // wrong-output bug. Flagged builds simply run their own configure, which is
    // what they already do today.
    flagless = strcat([ldflags,cflags,fflags,cc]) == ""
    if flagless && ...
        isfile(usercommandpath+"/Makefile.orig") && ...
        isfile(usercommandpath+"/libtool")
        // Reuse existing Makefile.orig: this build contributed no flags of its own
        [status,msg]=copyfile(usercommandpath+"/Makefile.orig",linkBuildDir);

        if ( ilib_verbose() == 2 ) then
            mprintf(gettext("   %s: Use the previous detection of compiler.\n"),"ilib_gen_Make");
        end

        if (status <> 1)
            error(msprintf(gettext("%s: An error occurred: %s\n"), "ilib_gen_Make",msg));
        end

        // We just copied the configure script, so its modification time is "now".
        // But Makefile timestamp is also "now", since we just created it. Since
        // Makefile depends on configure, "make" re-run the configure script, and
        // hence rebuild Makefile from Makefile.in, overwriting the result of
        // scicompile.sh. We want to avoid this, so we have to force Makefile's
        // timestamp to one second later.
        // (just try "touch configure Makefile; make" on any autoconf project)
        sleep(1000);
        host("touch Makefile");
    else
        // This build carries its own flags, so the shared detection cannot
        // represent it: run ./configure with those flags, in this build's own
        // directory. (No cache key is written -- see the note above on why a
        // per-library key over a shared artifact is unsound. The old code wrote
        // one on every build and nothing ever read it, which is why an
        // installation accumulates dozens of stale <libname>.md5 files.)

        if ( ilib_verbose() == 2 ) then
            mprintf(gettext("   %s: Need to run the compiler detection (configure).\n"),"ilib_gen_Make");
        end

        mdelete(linkBuildDir+"/Makefile.orig");
        generateConfigure(linkBuildDir, ldflags, cflags, fflags, cc)
    end

    // Alter the Makefile in order to compile the right files
    if ( ilib_verbose() <> 0 ) then
        mprintf(gettext("   %s: Modification of the Makefile in TMPDIR.\n"),"ilib_gen_Make");
    end

    cmd=commandpath + "/scicompile.sh " + libname + " " + filelist

    [ierr, msg, stderr] = host(cmd);

    if ( ilib_verbose() == 2 ) then
        mprintf(gettext("   %s: Substitute the reference by the actual file.\n"),"ilib_gen_Make");
        mprintf(gettext("   Command: %s\n"),cmd);
        if (length(msg)) then
            mprintf(gettext("Output: %s\n"),msg);
        end
        mprintf(gettext("stderr: %s\n"),stderr);
    end

    if ierr <> 0 then
        if ( ilib_verbose() <> 0 ) then
            mprintf(gettext("%s: Error while modifying the reference Makefile:\n"),"ilib_gen_Make")
            mprintf(gettext("Output: %s\n"),msg);
            mprintf(gettext("stderr: %s\n"),stderr);
        end
        return;
    end

    chdir(originPath);

endfunction


function generateConfigure(workingPath, ..
    ldflags, ..
    cflags, ..
    fflags, ..
    cc)

    // We launch ./configure in order to produce a "generic" Makefile
    // for this computer

    if ( ilib_verbose() <> 0 ) then
        mprintf(gettext("   %s: configure : Generate Makefile.\n"),"ilib_gen_Make");
    end
    cmd = gencompilationflags_unix(ldflags, cflags, fflags, cc, "configure")
    cmd = workingPath+"/compilerDetection.sh "+cmd

    [ierr, msg, stderr] = host(cmd);

    if ( ilib_verbose() == 2 ) then
        mprintf(gettext("   %s: Command: %s\n"),"ilib_gen_Make",cmd);
        mprintf(gettext("   Output: %s\n"),msg);
        mprintf(gettext("   stderr: %s\n"),stderr);
    end

    if ierr <> 0 then
        if ( ilib_verbose() <> 0 ) then
            mprintf(gettext("Output: %s\n"),msg);
            mprintf(gettext("stderr: %s\n"),stderr);
        end
        error(msprintf(gettext("%s: An error occurred during the detection of the compiler(s). Set ilib_verbose(2) for more information.\n"), "ilib_gen_Make"));
        return;
    end

endfunction

function copyMandatoryFiles(commandpath,workingPath)
    // List of the files mandatory to generate a lib with the detection of the env
    mandatoryFiles = ["compilerDetection.sh", ..
    "configure.ac", ..
    "configure", ..
    "compile", ..
    "Makefile.am", ..
    "Makefile.in", ..
    "config.sub", ..
    "libtool", ..
    "config.guess", ..
    "config.status", ..
    "depcomp", ..
    "install-sh", ..
    "ltmain.sh", ..
    "missing", ..
    "aclocal.m4"];

    // Copy files to the working path
    for x = mandatoryFiles(:)' ;
        fullPath=commandpath+"/"+x;
        if (isfile(fullPath)) then
            [status,msg]=copyfile(fullPath,workingPath);
            if (status <> 1)
                error(msprintf(gettext("%s: An error occurred: %s\n"), "ilib_gen_Make",msg));
            end
        end
    end

endfunction
//=============================================================================
