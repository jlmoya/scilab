/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) INRIA - Allan CORNET
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
#include <stdio.h>
#include <string.h>
#include "sciprint.h"
#include "diary.h"
#include "configvariable_interface.h"
#include "ConsolePrintf.h"
#include "machine.h" /* bsiz */
#ifdef _MSC_VER
#include "TermPrintf.h"
#endif
#include "sci_malloc.h"
#include "charEncoding.h"
#include "scilabWrite.hxx"
/*--------------------------------------------------------------------------*/
#ifdef _MSC_VER
#define vsnprintf _vsnprintf
#define vsnwprintf _vsnwprintf
#endif
#define MAXPRINTF bsiz /* bsiz size of internal chain buf */
/*--------------------------------------------------------------------------*/
/* sciprint uses scivprint */
/* scivprint uses stdlib vsprintf */
/*--------------------------------------------------------------------------*/
void sciprint(const char* fmt, ...)
{
    va_list ap;

    va_start(ap, fmt);
    scivprint(fmt, ap);
    va_end (ap);
}
/*--------------------------------------------------------------------------*/
//void sciprintW(wchar_t* fmt,...)
//{
//	va_list ap;
//
//	va_start(ap,fmt);
//	scivprintW(fmt,ap);
//	va_end (ap);
//}
/*--------------------------------------------------------------------------*/
//int scivprintW(wchar_t* fmt,va_list args)
//{
//	static wchar_t s_buf[MAXPRINTF];
//	int count=0;
//
//	va_list savedargs;
//	va_copy(savedargs, args);
//
//#ifdef _MSC_VER
//	count= vsnwprintf(s_buf, MAXPRINTF - 1, fmt, args );
//#else
//	count= vswprintf(s_buf, MAXPRINTF - 1, fmt, args );
//#endif
//	if(count == -1)
//    {
//        s_buf[MAXPRINTF - 1]= L'\0';
//    }
//
//	scilabWriteW(s_buf);
//
//	va_end(savedargs);
//
//	return count;
//}
/*--------------------------------------------------------------------------*/
int scivprint(const char *fmt, va_list args)
{
    static char s_buf[MAXPRINTF];
    int count = 0;

    va_list savedargs;
    va_copy(savedargs, args);

    // Bounded on EVERY platform. This used to call vsnprintf only under
    // _MSC_VER and plain vsprintf everywhere else, so on macOS and Linux any
    // message longer than bsiz (4096) overflowed this static buffer. It was
    // reachable from ordinary use: addinter() on a path that does not exist
    // makes scilabLink sciprint the path into its error message, and a long
    // enough path trapped in __chk_fail_overflow -- SIGTRAP, uncatchable by
    // errcatch, with the process gone. Without _FORTIFY_SOURCE the same input
    // would have silently corrupted whatever follows s_buf instead.
    //
    // vsnprintf is C99 and has been in MSVC since 2015, so the guard bought
    // nothing on any platform Scilab still supports.
    count = vsnprintf(s_buf, MAXPRINTF, fmt, args);

    // Negative means an encoding error; >= MAXPRINTF means truncation, and
    // count is then what WOULD have been written. vsnprintf always terminates,
    // but pin the terminator anyway so this stays correct if the MSVC
    // _vsnprintf alias (which does not) is ever reinstated above.
    if (count < 0 || count >= MAXPRINTF)
    {
        s_buf[MAXPRINTF - 1] = '\0';
    }

    scilabForcedWrite(s_buf);

    va_end(savedargs);

    return count;
}
/*--------------------------------------------------------------------------*/
