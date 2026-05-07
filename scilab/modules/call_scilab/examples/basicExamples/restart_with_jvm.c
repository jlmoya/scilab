/*
* Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
* Copyright (C) 2026 - Dassault Systèmes S.E. - Clément DAVID
*
* This file is released under the 3-clause BSD license. See COPYING-BSD.
*
* This example shows how to stop and restart Scilab with the JVM. It is
* important to note that the JVM is not stopped when Scilab is terminated,
* so it can be restarted without any issue.
*/
#ifdef _MSC_VER
#pragma comment(lib, "api_scilab.lib")
#pragma comment(lib, "call_scilab.lib")
#endif
/*------------------------------------------------------------*/
#include <math.h>
#include <stdio.h>
#include <stdlib.h> /* malloc */
#include "api_scilab.h"
#include "call_scilab.h" /* Provide functions to call Scilab engine */
/*------------------------------------------------------------*/
int main(void)
{
    fprintf(stdout, "Enter...\n");
    if (StartScilab(NULL, NULL, NULL) == FALSE)
    {
        fprintf(stderr, "Error while calling StartScilab\n");
        return -1;
    }
    fprintf(stdout, "Scilab Started...\n");
    
    if (TerminateScilab(NULL) == FALSE) {
        fprintf(stderr, "Error while calling TerminateScilab\n");
        return -2;
    }
    fprintf(stdout, "Scilab Terminated...\n");
    
    if (StartScilab(NULL, NULL, NULL) == FALSE)
    {
        fprintf(stderr, "Error while calling StartScilab\n");
        return -1;
    }
    fprintf(stdout, "Scilab Started...\n");
    if (TerminateScilab(NULL) == FALSE) {
        fprintf(stderr, "Error while calling TerminateScilab\n");
        return -2;
    }
    fprintf(stdout, "Scilab Terminated...\n");
    return 0;
}
