// Copyright (C) 2026 - Scilab macOS/2027 modernization
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

// Which build system builds toolbox gateways. Step 6 of
// docs/design/dynamic-link-cmake-migration.md: CMake is the default; the
// autotools skeleton survives one release as a deliberate opt-out.
//
// Shared by ilib_gen_Make_unix (which generator to run) and ilib_compile (what
// to invoke), because those two MUST agree -- a CMakeLists.txt built by `make`,
// or a Makefile built by `cmake`, fails in a way that reads like a compiler
// problem.
//
//   SCILAB_GATEWAY_BUILD unset / "cmake"      -> CMake (default)
//   SCILAB_GATEWAY_BUILD "autotools" / "make" -> the deprecated skeleton
//
// The opt-out is an explicit choice, never a fallback that engages when CMake
// errors: a silent fallback would hide CMake bugs behind a path nobody is
// testing, which is exactly how the skeleton outlived autotools everywhere else
// in this project.

// warn (optional, default %t): the resolution is needed TWICE per build -- once
// to pick the generator and once to pick the build command -- and a deprecation
// notice printed twice for one gateway reads like two problems. The generator
// warns; ilib_compile passes %f and stays quiet.
function useCMake = ilib_gateway_use_cmake(warn)

    [lhs, rhs] = argn(0);
    if rhs < 1 then warn = %t; end

    mode = convstr(stripblanks(getenv("SCILAB_GATEWAY_BUILD", "")), "l");

    select mode
    case ""          then useCMake = %t;
    case "cmake"     then useCMake = %t;
    case "autotools" then useCMake = %f;
    // "make" is the spelling used while the switch was a development-time A/B
    // flag. Kept working so anything scripted against it does not break, but
    // "autotools" is the documented name -- it says what you are opting into.
    case "make"      then useCMake = %f;
    else
        // Unknown value: do NOT guess quietly. Say so and take the default,
        // because a typo like SCILAB_GATEWAY_BUILD=camke silently selecting the
        // deprecated path is the worst of both worlds.
        if warn then
            warning(msprintf(_("%s: unknown SCILAB_GATEWAY_BUILD value ''%s''; using the default (cmake). Valid: cmake, autotools.\n"), ..
                             "ilib_build", mode));
        end
        useCMake = %t;
    end

    if ~useCMake & warn then
        // Every use warns, and the warning names the RELEASE it is removed in.
        // "Deprecated" with no date is how a temporary path survives a decade --
        // precisely this skeleton's own history.
        warning(msprintf(_("the autotools build path for toolbox gateways is DEPRECATED and will be REMOVED in Scilab 2027.1. Your gateway built, but rebuild it with the default (CMake) path before upgrading.\n")));
    end

endfunction
