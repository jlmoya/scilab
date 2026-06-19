/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) INRIA - Allan CORNET , Cong WU
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

/* desc : Let  s[k]  stands for the  k  character of Input_StringMatrixings
  ( or the  white space character if  k >length(s) ).
  part  returns  c , a matrix of character Input_StringMatrixings, such that
  c(i,j)  is the Input_StringMatrixing  "s[v(1)]...s[v(n)]"  (  s=mp(i,j)  ).
                                                                          */
/*------------------------------------------------------------------------*/
#include "double.hxx"
#include "funcmanager.hxx"
#include "function.hxx"
#include "overload.hxx"
#include "string.hxx"
#include "polynom.hxx"
#include "implicitlist.hxx"
#include "string_gw.hxx"

#include <limits>

extern "C"
{
#include "Scierror.h"
#include "freeArrayOfString.h"
#include "localization.h"
#include "partfunction.h"
#include <stdio.h>
#include <string.h>
}
/*--------------------------------------------------------------------------*/
inline double evaluate_poly(int dollar, types::SinglePoly* pSP)
{
    double oR, oC;
    pSP->evaluate(dollar, 0, &oR, &oC);
    return oR;
};
inline double evaluate_poly(types::String* pS, types::Polynom* pP)
{
    if(!pP->isScalar())
        return std::numeric_limits<double>::quiet_NaN();
    if(pP->getVariableName() != L"$")
        return std::numeric_limits<double>::signaling_NaN();
    types::SinglePoly* pSP = pP->get(0);

    int dollar = (int) wcslen(pS->get(0));
    return evaluate_poly(dollar, pSP);
};
/*--------------------------------------------------------------------------*/
types::Function::ReturnValue sci_part(types::typed_list& in, int _iRetCount, types::typed_list& out)
{
    if (in.size() != 2)
    {
        Scierror(999, _("%s: Wrong number of input argument(s): %d expected.\n"), "part", 2);
        return types::Function::Error;
    }

    if (_iRetCount > 1)
    {
        Scierror(78, _("%s: Wrong number of output argument(s): %d expected.\n"), "part", 1);
        return types::Function::Error;
    }

    //part([], ...
    if (in[0]->isDouble() && in[0]->getAs<types::Double>()->isEmpty())
    {
        out.push_back(types::Double::Empty());
        return types::Function::OK;
    }

    if (in[0]->isString() == false)
    {
        std::wstring wstFuncName = L"%" + in[0]->getShortTypeStr() + L"_part";
        return Overload::call(wstFuncName, in, _iRetCount, out);
    }

    types::String* pS = in[0]->getAs<types::String>();
    std::vector<int> index;

    // corner case: for '$' on a single string we don't need to call the overload
    if (in[1]->isPoly() &&
        in[1]->getAs<types::Polynom>()->getVariableName() == L"$" &&
        pS->isScalar())
    {
        int dollar = (int) wcslen(pS->get(0));
        types::Polynom* pP = in[1]->getAs<types::Polynom>();
        for (int pos = 0; pos < pP->getSize(); pos++)
        {
            double acc = evaluate_poly(dollar, pP->get(pos));
            if (acc > 0) // handle negative values, %inf and %nan
            {
                index.push_back((int) acc);
            }
        }
    }

    // corner case: for '1:$' on a single string we don't need to call the overload
    if (index.empty() && in[1]->isImplicitList() && pS->isScalar())
    {
        types::ImplicitList* pIL = in[1]->getAs<types::ImplicitList>();
        if (pIL->getStartType() == types::InternalType::ScilabPolynom)
        {
            types::Polynom* pP = pIL->getStart()->getAs<types::Polynom>();
            double acc = evaluate_poly(pS, pP);
            if (acc > 0) // handle negative values, %inf and %nan
            {
                pIL->setStart(new types::Double(acc));
            }
        }
        if (pIL->getStepType() == types::InternalType::ScilabPolynom)
        {
            types::Polynom* pP = pIL->getStep()->getAs<types::Polynom>();
            double acc = evaluate_poly(pS, pP);
            if (acc > 0) // handle negative values, %inf and %nan
            {
                pIL->setStep(new types::Double(acc));
            }
        }
        if (pIL->getEndType() == types::InternalType::ScilabPolynom)
        {
            types::Polynom* pP = pIL->getEnd()->getAs<types::Polynom>();
            double acc = evaluate_poly(pS, pP);
            if (acc > 0) // handle negative values, %inf and %nan
            {
                pIL->setEnd(new types::Double(acc));
            }
        }

        if (pIL->compute())
        {
            types::Double* temp = new types::Double(0);
            for (int i = 0; i < pIL->getSize(); i++)
            {
                pIL->extractValueAsDouble(i, temp);
                index.push_back((int) temp->get(0));
            }
            temp->killMe();
        }
    }

    if (index.empty() && in[1]->isDouble())
    {
        types::Double* pD = in[1]->getAs<types::Double>();
        if (pD->isVector() == false && pD->isEmpty() == false)
        {
            //non vector
            Scierror(999, _("%s: Wrong size for input argument #%d: A vector expected.\n"), "part", 2);
            return types::Function::Error;
        }

        int i_len = pD->getSize();
        index.resize(i_len);
        const double* pdata = pD->get();
        for (int i = 0; i < i_len; i++)
        {
            int idx = static_cast<int>(pdata[i]);
            if (idx < 1)
            {
                Scierror(36, _("%s: Wrong values for input argument #%d: Must be >= 1.\n"), "part", 2);
                return types::Function::Error;
            }
            index[i] = idx;
        }
    }
    else if (index.empty())
    {
        std::wstring wstFuncName = L"%" + in[1]->getShortTypeStr() + L"_part";
        return Overload::call(wstFuncName, in, _iRetCount, out);
    }

    types::String* pOut = new types::String(pS->getRows(), pS->getCols());

    // Get direct access to the data arrays
    wchar_t** out_data = pOut->get();
    wchar_t** in_data = pS->get();

    // allocate the default output string and fill it with spaces
    wchar_t* defaultValue = new wchar_t[index.size() + 1];
    std::wmemset(defaultValue, L' ', index.size());
    defaultValue[index.size()] = L'\0';

    for (int i = 0; i < pS->getSize(); ++i)
    {
        wchar_t* wcs_in = in_data[i];
        size_t s_len = wcslen(wcs_in);
        int wcs_len = (s_len > std::numeric_limits<int>::max())
                    ? std::numeric_limits<int>::max()
                    : static_cast<int>(s_len);

        // initialize output string
        pOut->set(i, defaultValue);

        // copy the input characters
        for (size_t j = 0; j < index.size(); ++j)
        {
            if (index[j] <= wcs_len) [[likely]]
            {
                out_data[i][j] = wcs_in[index[j] - 1];
            }
        }
    }

    delete[] defaultValue;

    out.push_back(pOut);
    return types::Function::OK;
}
/*--------------------------------------------------------------------------*/
