/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2011 - DIGITEO - Sylvestre LEDRU
 *
 * Copyright (C) 2012 - 2016 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */
#ifndef __SIGNAL_MGMT_H__
#define __SIGNAL_MGMT_H__

/**
 * Declare that ScilabJmpEnv has been armed with setjmp() by the caller, so the
 * fatal-signal handler has somewhere to longjmp() back to.
 *
 * Only the standalone startup (modules/startup/src/cpp/scilab.cpp) does that.
 * An EMBEDDER -- javasci, or any call_scilab host -- calls StartScilabEngine
 * directly and never arms it, so a fatal handler that longjmps would jump into
 * a zero-initialised jmp_buf. Call this immediately after setjmp() and before
 * StartScilabEngine(); base_error_init() consults it to decide whether
 * installing the handler is safe. See deferred-fixes-register.md B22.
 */
void armScilabJmpEnv(void);

/**
 * Initialize the signal management system.
 *
 * Installs the fatal-signal handler ONLY when armScilabJmpEnv() has been
 * called; otherwise the host process keeps its own handlers.
 */
void base_error_init(void);

/**
 * Install a timeout on the process (watchdog behavior)
 * @param timeoutDelay delay in seconds (0 will disable the timeout)
 */
void timeout_process_after(int timeoutDelay);

#endif /* __SIGNAL_MGMT_H__ */
