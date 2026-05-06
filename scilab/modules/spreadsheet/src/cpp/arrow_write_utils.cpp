/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
 * Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
 *
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

#include <arrow/api.h>
#include <cstring>
#include <cmath>
#include "arrow_write_utils.h"
#include "string.hxx"
#include "double.hxx"
#include "int.hxx"
#include "bool.hxx"
#include "sciprint.h"

// ---------------------------------------------------------------------------
// - ArrowBuilder : builder Arrow (ex: arrow::Int32Builder)
// - ScilabType   : type Scilab source (ex: types::Int32)
// - srcType      : native C type target (ex: int32_t)
// - checkNaN     : if true, NaN/Inf are converted to null (pour double)
// ---------------------------------------------------------------------------
template<typename ArrowBuilder, typename ScilabType, typename srcType, bool checkNaN = false>
static arrow::Result<std::shared_ptr<arrow::Array>> appendData(types::InternalType* pCol)
{
    ArrowBuilder builder;
    ScilabType* pData = pCol->getAs<ScilabType>();
    auto* src = pData->get();
    int rows = pData->getRows();

    for (int row = 0; row < rows; row++)
    {
        srcType value = static_cast<srcType>(src[row]);
        if constexpr (checkNaN)
        {
            double dval = static_cast<double>(src[row]);
            if (std::isnan(dval) || std::isinf(dval))
            {
                ARROW_RETURN_NOT_OK(builder.AppendNull());
            }
            else
            {
                ARROW_RETURN_NOT_OK(builder.Append(value));
            }
        }
        else
        {
            ARROW_RETURN_NOT_OK(builder.Append(value));
        }
    }
    return builder.Finish();
}

arrow::Result<std::shared_ptr<arrow::Array>> createColumn(wchar_t* wcsType, types::InternalType* pCol)
{
    if (wcscmp(wcsType, L"double") == 0)
    {
        return appendData<arrow::DoubleBuilder, types::Double, double, true>(pCol);
    }
    else if (wcscmp(wcsType, L"int8") == 0)
    {
        return appendData<arrow::Int8Builder, types::Int8, int8_t>(pCol);
    }
    else if (wcscmp(wcsType, L"int16") == 0)
    {
        return appendData<arrow::Int16Builder, types::Int16, int16_t>(pCol);
    }
    else if (wcscmp(wcsType, L"int32") == 0)
    {
        return appendData<arrow::Int32Builder, types::Int32, int32_t>(pCol);
    }
    else if (wcscmp(wcsType, L"int64") == 0)
    {
        return appendData<arrow::Int64Builder, types::Int64, int64_t>(pCol);
    }
    else if (wcscmp(wcsType, L"uint8") == 0)
    {
        return appendData<arrow::UInt8Builder, types::UInt8, uint8_t>(pCol);
    }
    else if (wcscmp(wcsType, L"uint16") == 0)
    {
        return appendData<arrow::UInt16Builder, types::UInt16, uint16_t>(pCol);
    }
    else if (wcscmp(wcsType, L"uint32") == 0)
    {
        return appendData<arrow::UInt32Builder, types::UInt32, uint32_t>(pCol);
    }
    else if (wcscmp(wcsType, L"uint64") == 0)
    {
        return appendData<arrow::UInt64Builder, types::UInt64, uint64_t>(pCol);
    }
    else if (wcscmp(wcsType, L"bool") == 0)
    {
        arrow::BooleanBuilder builder;
        types::Bool* pdata = pCol->getAs<types::Bool>();
        int* data = pdata->get();
        int rows = pdata->getRows();
        for (int i = 0; i < rows; i++)
        {
            ARROW_RETURN_NOT_OK(builder.Append(data[i] != 0));
        }
        return builder.Finish();
    }
    else if (wcscmp(wcsType, L"string") == 0)
    {
        arrow::StringBuilder builder;
        types::String* pdata = pCol->getAs<types::String>();
        wchar_t** data = pdata->get();
        int rows = pdata->getRows();
        for (int i = 0; i < rows; i++)
        {
            char* str = wide_string_to_UTF8(data[i]);
            if (str && strlen(str) > 0)
            {
                ARROW_RETURN_NOT_OK(builder.Append(str));
            }
            else
            {
                ARROW_RETURN_NOT_OK(builder.AppendNull());
            }
            if (str)
            {
                FREE(str);
            }
        }
        return builder.Finish();
    }
    else if (wcscmp(wcsType, L"datetime") == 0)
    {
        arrow::TimestampBuilder builder(arrow::timestamp(arrow::TimeUnit::MICRO), arrow::default_memory_pool());
        types::Double* pdata = pCol->getAs<types::Double>();
        double* data = pdata->get();
        int rows = pdata->getRows();
        for (int i = 0; i < rows; i++)
        {
            double value = data[i];
            if (std::isnan(value) || std::isinf(value))
            {
                ARROW_RETURN_NOT_OK(builder.AppendNull());
            }
            else
            {
                ARROW_RETURN_NOT_OK(builder.Append(static_cast<int64_t>(value)));
            }
        }
        return builder.Finish();
    }
    else if (wcscmp(wcsType, L"duration") == 0)
    {
        arrow::DurationBuilder builder(arrow::duration(arrow::TimeUnit::MICRO), arrow::default_memory_pool());
        types::Double* pdata = pCol->getAs<types::Double>();
        double* data = pdata->get();
        int rows = pdata->getRows();
        for (int i = 0; i < rows; i++)
        {
            double value = data[i];
            if (std::isnan(value) || std::isinf(value))
            {
                ARROW_RETURN_NOT_OK(builder.AppendNull());
            }
            else
            {
                ARROW_RETURN_NOT_OK(builder.Append(static_cast<int64_t>(value * 1e3)));
            }
        }
        return builder.Finish();
    }
    else
    {
        return arrow::Status::Invalid("Unsupported Scilab type.\n");
    }
}

arrow::Result<std::shared_ptr<arrow::Table>> createArrowTable(ArrowData* data)
{
    std::vector<std::shared_ptr<arrow::Field>> fields;
    std::vector<std::shared_ptr<arrow::Array>> arrays;

    types::List* pData = data->pData;
    int cols = pData->getSize();
    types::String* pType = data->pColTypes;
    types::String* pNames = data->pColNames;

    for (int col = 0; col < cols; col++)
    {
        wchar_t* wcsType = pType->get(col);
        wchar_t* wstrName = pNames->get(col);

        ARROW_ASSIGN_OR_RAISE(auto array, createColumn(wcsType, pData->get(col)));

        fields.push_back(arrow::field(wide_string_to_UTF8(wstrName), array->type()));
        arrays.push_back(array);
    }

    return arrow::Table::Make(arrow::schema(fields), arrays);
}
