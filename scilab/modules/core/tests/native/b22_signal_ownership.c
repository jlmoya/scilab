/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 * ---------------------------------------------------------------------------
 * Acceptance probe for deferred-fixes-register.md B22 defect (2):
 * "an embedded engine must not seize the host process's fatal-signal handlers".
 *
 * base_error_init() may install the fatal-signal handler only when somebody
 * armed ScilabJmpEnv with setjmp(), because longjmp'ing to it is the only thing
 * sig_fatal() can do at the end. Exactly one caller arms it: the standalone
 * executable's main (modules/startup/src/cpp/scilab.cpp). Embedders -- javasci
 * and every other call_scilab host -- do not, and must keep their own handlers.
 *
 * The probe is two-sided so one binary observes both behaviours, which is what
 * makes it a real guard rather than a test that has never been seen to fail:
 *
 *   b22_signal_ownership embedded    no armScilabJmpEnv(): the host's SIGSEGV
 *                                    handler must SURVIVE StartScilab().
 *                                    exit 0 on success.
 *   b22_signal_ownership standalone  armScilabJmpEnv() first: Scilab's handler
 *                                    must REPLACE the host's, proving the REPL
 *                                    did not lose its crash reporting.
 *                                    exit 1 (== "Scilab took it") on success.
 *
 * Build and run with run_b22_signal_ownership.sh in this directory.
 * ---------------------------------------------------------------------------
 */
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <stdlib.h>

extern void armScilabJmpEnv(void);

/* call_scilab surface, declared by hand: including the real headers would drag
   in the module's whole include graph for three prototypes. */
extern void DisableInteractiveMode(void);
extern int StartScilab(char* SCIpath, char* ScilabStartup, int Stacksize);
extern int TerminateScilab(char* ScilabQuit);

static void host_segv_handler(int signum, siginfo_t* info, void* ctx)
{
    (void)signum;
    (void)info;
    (void)ctx;
}

int main(int argc, char** argv)
{
    struct sigaction mine, seen;
    const char* mode = (argc > 1) ? argv[1] : "embedded";
    int armed = (strcmp(mode, "standalone") == 0);
    int survived;

    /* The host process establishes its signal policy first, as a JVM does. */
    memset(&mine, 0, sizeof(mine));
    mine.sa_sigaction = host_segv_handler;
    mine.sa_flags = SA_SIGINFO;
    sigemptyset(&mine.sa_mask);
    if (sigaction(SIGSEGV, &mine, NULL) != 0)
    {
        fprintf(stderr, "PROBE ERROR: could not install the host handler\n");
        return 2;
    }

    if (armed)
    {
        armScilabJmpEnv();
    }

    DisableInteractiveMode();
    if (StartScilab(getenv("SCI"), NULL, 0) == 0)
    {
        fprintf(stderr, "PROBE ERROR: StartScilab failed (is SCI set?)\n");
        return 2;
    }

    memset(&seen, 0, sizeof(seen));
    sigaction(SIGSEGV, NULL, &seen);
    survived = (seen.sa_sigaction == host_segv_handler);

    printf("PROBE mode=%s armed=%d host=%p installed=%p verdict=%s\n",
           mode, armed,
           (void*)(size_t)host_segv_handler,
           (void*)(size_t)seen.sa_sigaction,
           survived ? "HOST-SURVIVED" : "SCILAB-TOOK-IT");
    fflush(stdout);

    TerminateScilab(NULL);

    /* The verdict is the exit code so the driver can assert on it. */
    return survived ? 0 : 1;
}
