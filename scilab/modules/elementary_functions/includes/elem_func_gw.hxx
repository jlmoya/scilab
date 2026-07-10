/*
*  Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
*  Copyright (C) 2008-2008 - DIGITEO - Antoine ELIAS
*  Copyright (C) 2010-2011 - DIGITEO - Bruno JOFRET
*  Copyright (C) 2018 - UTC - Stéphane MOTTELET
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

#ifndef __ELEM_FUNC_GW_HXX__
#define __ELEM_FUNC_GW_HXX__

#include <cmath>
#include <limits>
#include <cstdint>

#include "cpp_gateway_prototype.hxx"
#include "double.hxx"
#include "complex"
#include "function.hxx"

extern "C"
{
#include "dynlib_elementary_functions_gw.h"
}

class ElemFuncModule
{
private :
    ElemFuncModule() {};
    ~ElemFuncModule() {};
public :
    EXTERN_EF_GW static int Load();
    EXTERN_EF_GW static int Unload()
    {
        return 1;
    }
};

CPP_GATEWAY_PROTOTYPE(sci_abs);
CPP_GATEWAY_PROTOTYPE(sci_acos);
CPP_GATEWAY_PROTOTYPE(sci_acosh);
CPP_GATEWAY_PROTOTYPE(sci_asin);
CPP_GATEWAY_PROTOTYPE(sci_asinh);
CPP_GATEWAY_PROTOTYPE(sci_atan);
CPP_GATEWAY_PROTOTYPE(sci_atanh);
CPP_GATEWAY_PROTOTYPE(sci_base2dec);
CPP_GATEWAY_PROTOTYPE(sci_bitstring);
CPP_GATEWAY_PROTOTYPE(sci_ceil);
CPP_GATEWAY_PROTOTYPE(sci_clean);
CPP_GATEWAY_PROTOTYPE(sci_conj);
CPP_GATEWAY_PROTOTYPE(sci_cos);
CPP_GATEWAY_PROTOTYPE(sci_cosh);
CPP_GATEWAY_PROTOTYPE(sci_cumprod);
CPP_GATEWAY_PROTOTYPE(sci_cumsum);
CPP_GATEWAY_PROTOTYPE(sci_dec2base);
CPP_GATEWAY_PROTOTYPE(sci_diag);
CPP_GATEWAY_PROTOTYPE(sci_dsearch);
CPP_GATEWAY_PROTOTYPE(sci_exp);
CPP_GATEWAY_PROTOTYPE(sci_expm);
CPP_GATEWAY_PROTOTYPE(sci_eye);
CPP_GATEWAY_PROTOTYPE(sci_floor);
CPP_GATEWAY_PROTOTYPE(sci_frexp);
CPP_GATEWAY_PROTOTYPE(sci_gallery);
CPP_GATEWAY_PROTOTYPE(sci_gsort);
CPP_GATEWAY_PROTOTYPE(sci_imag);
CPP_GATEWAY_PROTOTYPE(sci_imult);
CPP_GATEWAY_PROTOTYPE(sci_int);
CPP_GATEWAY_PROTOTYPE(sci_isequal);
CPP_GATEWAY_PROTOTYPE(sci_isreal);
CPP_GATEWAY_PROTOTYPE(sci_issquare);
CPP_GATEWAY_PROTOTYPE(sci_isvector);
CPP_GATEWAY_PROTOTYPE(sci_kron);
CPP_GATEWAY_PROTOTYPE(sci_linspace);
CPP_GATEWAY_PROTOTYPE(sci_log);
CPP_GATEWAY_PROTOTYPE(sci_log10);
CPP_GATEWAY_PROTOTYPE(sci_log1p);
CPP_GATEWAY_PROTOTYPE(sci_matrix);
CPP_GATEWAY_PROTOTYPE(sci_max); // Old name sci_maxi
CPP_GATEWAY_PROTOTYPE(sci_min); // Old name sci_mini
CPP_GATEWAY_PROTOTYPE(sci_nearfloat);
CPP_GATEWAY_PROTOTYPE(sci_ones);
CPP_GATEWAY_PROTOTYPE(sci_permute);
CPP_GATEWAY_PROTOTYPE(sci_prod);
CPP_GATEWAY_PROTOTYPE(sci_rand);
CPP_GATEWAY_PROTOTYPE(sci_rat);
CPP_GATEWAY_PROTOTYPE(sci_real);
CPP_GATEWAY_PROTOTYPE(sci_round);
CPP_GATEWAY_PROTOTYPE(sci_sign);
CPP_GATEWAY_PROTOTYPE(sci_sin);
CPP_GATEWAY_PROTOTYPE(sci_sinh);
CPP_GATEWAY_PROTOTYPE(sci_size);
CPP_GATEWAY_PROTOTYPE(sci_sqrt);
CPP_GATEWAY_PROTOTYPE(sci_sum);
CPP_GATEWAY_PROTOTYPE(sci_tan);
CPP_GATEWAY_PROTOTYPE(sci_tanh);
CPP_GATEWAY_PROTOTYPE(sci_tril);
CPP_GATEWAY_PROTOTYPE(sci_triu);
CPP_GATEWAY_PROTOTYPE(sci_vander);
CPP_GATEWAY_PROTOTYPE(sci_zeros);
CPP_GATEWAY_PROTOTYPE(sci_isempty);
CPP_GATEWAY_PROTOTYPE(sci_percent_gallery);
CPP_GATEWAY_PROTOTYPE(sci_ishermitian);
CPP_GATEWAY_PROTOTYPE(sci_issymmetric);


bool getDimsFromArguments(types::typed_list& in, const std::string& _pstName, int* _iDims, int** _piDims, bool* _alloc);

template <class T>
types::Double* getAsDouble(T* _val)
{
    types::Double* dbl = new types::Double(_val->getDims(), _val->getDimsArray());
    double* pOut = dbl->get();
    typename T::type* pIn = _val->get();
    int size = dbl->getSize();
    for (int i = 0; i < size; i++)
    {
        pOut[i] = static_cast<double>(pIn[i]);
    }

    return dbl;
}

// Convert a double to a narrow integer type with Scilab's documented int8()/uint8()/... semantics
// while avoiding UB: nan -> 0, inf -> saturate to the type's bounds, and any finite out-of-range
// value wraps modulo 2^N. A straight static_cast<IntType>(double) of an out-of-range value is UB;
// arm64 fcvtz* saturates instead of wrapping; and routing through int64_t/uint64_t still traps at
// the 2^63/2^64 boundary. So reduce modulo 2^N with fmod first — exact for the integer-valued
// doubles here — which is portable and correct at every width. Mirrors doubleToInt() in
// integer/sci_gateway/cpp/sci_int.cpp.
template <class IntType>
static inline IntType dblToInt(double d)
{
    if (std::isnan(d))
    {
        return 0;
    }
    if (std::isinf(d))
    {
        return d > 0 ? std::numeric_limits<IntType>::max() : std::numeric_limits<IntType>::min();
    }
    if (d >= static_cast<double>(std::numeric_limits<IntType>::min()) &&
        d <  static_cast<double>(std::numeric_limits<IntType>::max()) + 1.0)
    {
        return static_cast<IntType>(d);
    }
    // out of range, finite. Narrow with EXACT integer routing where possible; reducing in double
    // (fmod) would lose precision at the 64-bit top (e.g. -1 -> uint64: 2^64-1 is unrepresentable
    // and rounds up to 2^64, whose cast is UB). A value in [-2^63, 2^63) fits int64_t exactly and
    // one in [2^63, 2^64) fits uint64_t exactly; the final narrowing cast to IntType then wraps.
    if (d >= -9223372036854775808.0 && d < 9223372036854775808.0)
    {
        return static_cast<IntType>(static_cast<int64_t>(d));
    }
    if (d >= 9223372036854775808.0 && d < 18446744073709551616.0)
    {
        return static_cast<IntType>(static_cast<uint64_t>(d));
    }
    // |d| beyond the 64-bit range: reduce mod 2^64 with fmod (such doubles are multiples of >= 2^11,
    // so the result is exact and never rounds up to 2^64).
    double m = std::fmod(d, 18446744073709551616.0);
    if (m < 0.0)
    {
        m += 18446744073709551616.0;
    }
    return static_cast<IntType>(static_cast<uint64_t>(m));
}

template <class T>
T* toInt(types::Double* _dbl)
{
    T* pI = new T(_dbl->getDims(), _dbl->getDimsArray());
    typename T::type* p = pI->get();
    double* pdbl = _dbl->get();
    int size = _dbl->getSize();
    for (int i = 0; i < size; i++)
    {
        p[i] = dblToInt<typename T::type>(pdbl[i]);
    }

    return pI;
}

typedef double(*func_real)(double);
typedef std::complex<double>(*func_complex)(const std::complex<double>&);

types::Double* trigo(types::Double* in, func_real func_r, func_complex func_c, bool forceComplex = false);
types::Function::ReturnValue zerosOrOnesFromValue(types::typed_list& in, int _iRetCount, types::typed_list& out, bool value);

#endif /* __ELEM_FUNC_GW_HXX__ */
