/*
* Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
* Copyright (C) 2011 - DIGITEO - Karim Mamode
*
 * Copyright (C) 2012 - 2016 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
*/

#include <termios.h>
#include <curses.h>
#include <term.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include "sci_malloc.h"
#include "cliDisplayManagement.h"
#include "initConsoleMode.h"
#include "scilines.h"

static void canonicMode(struct termios *t)
{
    t->c_lflag |= ICANON;       /* Set CANON flag */
    t->c_lflag |= ECHO;         /* Print character when a key is pressed */
}

static void rawMode(struct termios *t)
{
    t->c_lflag &= ~ICANON;      /* take off CANON flag */
    t->c_lflag &= ~ECHO;        /* Do not print character when a key is pressed */
    t->c_cc[VMIN] = 1;          /* Wait 1 charater before leaving getwchar */
    t->c_cc[VTIME] = 0;         /* Wait 0 second before leaving getwchar */
}

/* Save Shell Attribute To reset it when exit */
static void saveAndResetShellAttr(struct termios *shellAttr)
{
    static struct termios *savedAttr = NULL;

    if (savedAttr == NULL && shellAttr != NULL)
    {
        savedAttr = MALLOC(sizeof(*savedAttr));
        *savedAttr = *shellAttr;
    }
    else if (shellAttr == NULL && savedAttr != NULL)
    {
        if (tcsetattr(0, TCSAFLUSH, savedAttr) == -1)
        {
            fprintf(stderr, "Cannot reset the shell attributes: %s\n", strerror(errno));
        }
        FREE(savedAttr);
        savedAttr = NULL;
    }
}

static void saveShellAttr(struct termios *shellAttr)
{
    saveAndResetShellAttr(shellAttr);
}

static void resetShellAttr(void)
{
    saveAndResetShellAttr(NULL);
}

/* Set Raw mode or Canonic Mode */
int setAttr(int bin)
{
    struct termios shellAttr;

    if (getCLIColor() == FALSE)
    {
        return 0;
    }

    if (bin == ATTR_RESET)
    {
        resetShellAttr();
        return 0;
    }
    if (tcgetattr(0, &shellAttr) == -1)
    {
        fprintf(stderr, "Cannot access to the term attributes: %s\n", strerror(errno));
        return -1;
    }
    saveShellAttr(&shellAttr);
    if (bin == CANON)
    {
        canonicMode(&shellAttr);
    }
    else if (bin == RAW)
    {
        rawMode(&shellAttr);
    }
    if (tcsetattr(0, TCSAFLUSH, &shellAttr) == -1)
    {
        fprintf(stderr, "Cannot change the term attributes: %s\n", strerror(errno));
        return -1;
    }
    return 0;
}

/* Initialise console mode */
int initConsoleMode(int bin)
{
    /* TODO: Check more term */
    if (tgetent(NULL, getenv("TERM")) == ERR && tgetent(NULL, "xterm") == ERR)
    {
        fprintf(stderr, "Cannot initialise termcaps.\nPlease check your variable TERM in your environment.\n");
        return -1;
    }

    /* STDIN_FILENO, not fileno(stdin): fileno() takes the FILE's internal lock,
     * and the console reader thread HOLDS that lock for as long as it sits in
     * fgets() waiting for input (fgets locks the FILE, then blocks in read()).
     * So on shutdown the main thread reached this very check -- the one whose
     * whole job is to answer "not a tty, nothing to do" -- and deadlocked
     * against a reader that would never return, because its stdin was a pipe
     * nobody was going to write to or close.
     *
     * Observed as an intermittent hang of `scilab -quit -f script` with an open
     * stdin: main thread in StopScilabEngine -> initConsoleMode -> fileno ->
     * flockfile, reader thread in getPipeLine -> fgets -> read. It only bit when
     * the reader happened to be inside fgets at that moment, which is why it
     * reproduced roughly one run in three.
     *
     * The file descriptor is what is actually wanted here, and the rest of this
     * file already talks to fd 0 directly (tcgetattr/tcsetattr), so this also
     * makes the check consistent with its neighbours. Answering it takes no
     * locks and cannot block. */
    if (!isatty(STDIN_FILENO))
    {
        /* We are in a pipe, no need to init the console */
        return 0;
    }

    // set default lines at startup
    scilinesdefault();

    return setAttr(bin);
}
