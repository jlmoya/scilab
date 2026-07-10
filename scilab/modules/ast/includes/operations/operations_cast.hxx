/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

#ifndef __OPERATIONS_CAST_HXX__
#define __OPERATIONS_CAST_HXX__

#include <cmath>
#include <type_traits>

// (O)v where v is a floating scalar and O a narrow integer is undefined behaviour for an
// out-of-range / inf / nan v. The historical operator code relied on the compiler's
// int-intermediate lowering (saturate to int32, then narrow to O); reproduce that explicitly so
// every result is bit-for-bit unchanged. Identity for every other type pair (a plain static_cast),
// so wrapping the element casts in +, -, ... is behaviour-preserving for double/int/... operands.
template<typename O, typename V>
inline static O castVal(V v)
{
    if constexpr (std::is_floating_point<V>::value && std::is_integral<O>::value)
    {
        double d = static_cast<double>(v);
        int i = std::isnan(d) ? 0
                : (d >= 2147483647.0 ? 2147483647
                   : (d <= -2147483648.0 ? (-2147483647 - 1) : static_cast<int>(d)));
        return static_cast<O>(i);
    }
    else
    {
        return static_cast<O>(v);
    }
}

#endif /* !__OPERATIONS_CAST_HXX__ */
