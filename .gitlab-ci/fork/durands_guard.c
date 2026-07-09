/*
 * CI regression guard for the durands() UB-miscompile fix.
 *
 * Scilab's rand() (uniform) generator found its integer modulus with a loop
 * that terminated by overflowing a signed int — undefined behaviour that
 * modern clang/gcc miscompile at -O2: the loop left m2 = 0, so the scale
 * became 0.5/0 = +Inf and EVERY rand() value was Inf. This file carries the
 * FIXED algorithm (identical to modules/elementary_functions/src/c/
 * basic_functions.c) and asserts, at any optimisation level, that the
 * generator initialises correctly and produces finite values in [0, 1).
 * CI compiles it at -O0 and -O2 and diffs the output streams — any
 * divergence means an optimiser is again exploiting undefined behaviour.
 */
#include <stdio.h>
#include <math.h>
#include <limits.h>

static int nint(double x)
{
    return (int)(x < 0 ? x - 0.5 : x + 0.5);
}

static double durands(int* _iVal)
{
    static int ia = 0, ic = 0, itwo = 2, m2 = 0, mic = 0;
    static double halfm = 0, s = 0;

    if (m2 == 0)
    {
        /* largest power of two m2 with 2*m2 still in signed-int range —
           computed WITHOUT overflowing (the fix) */
        m2 = 1;
        while (m2 <= INT_MAX / itwo)
        {
            m2 = itwo * m2;
        }
        halfm = m2;

        ia = 8 * nint(halfm * atan(1.0) / 8) + 5;
        ic = 2 * nint(halfm * (0.5 - sqrt(3.0) / 6)) + 1;
        mic = (m2 - ic) + m2;

        s = 0.5 / halfm;
    }

    *_iVal *= ia;
    if (*_iVal > mic)
    {
        *_iVal = (*_iVal - m2) - m2;
    }
    *_iVal += ic;
    if (*_iVal / 2 > m2)
    {
        *_iVal = (*_iVal - m2) - m2;
    }
    if (*_iVal < 0)
    {
        *_iVal = (*_iVal + m2) + m2;
    }
    return (double)*_iVal * s;
}

int main(void)
{
    int seed = 0;
    int i;

    /* the known-good first value for seed 0: ic / 2^31 */
    const double expected0 = 453816693.0 / 2147483648.0;
    double v = durands(&seed);
    if (fabs(v - expected0) > 1e-15)
    {
        fprintf(stderr, "FAIL: first value %.17g != expected %.17g (generator misinitialised)\n",
                v, expected0);
        return 1;
    }
    printf("%.17g\n", v);

    for (i = 1; i < 1000; i++)
    {
        v = durands(&seed);
        if (!isfinite(v) || v < 0.0 || v >= 1.0)
        {
            fprintf(stderr, "FAIL: value #%d = %.17g out of [0,1) or non-finite\n", i, v);
            return 1;
        }
        printf("%.17g\n", v);
    }
    return 0;
}
