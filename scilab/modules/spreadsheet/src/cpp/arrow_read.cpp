/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
 * Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
 *
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

#include <arrow/api.h>
#include <arrow/io/api.h>
#include <parquet/arrow/reader.h>
#include <arrow/ipc/reader.h>
#include <arrow/ipc/writer.h>
#include <iostream>
#include <memory>
#include <vector>
#include <string>
#include <cstring>
#include "arrow_lib.h"
#include "string.hxx"
#include "double.hxx"
#include "int.hxx"
#include "UTF8.hxx"


template<typename destType, typename srcType>
void convertData(srcType* src, destType* dest, const std::shared_ptr<arrow::Array>& array, int length, int offset)
{
    for (int i = 0; i < length; i++)
    {
        if (array->IsNull(i))
        {
            dest[i] = std::numeric_limits<destType>::quiet_NaN();
        }
        else
        {
            dest[i] = static_cast<destType>(src[i + offset]);
        }
    }
}

template<typename ScilabType, typename ArrayType, typename CType, typename T> 
ScilabType* copyData(const std::shared_ptr<arrow::Array>& array, int length) {
    auto typed_array = std::static_pointer_cast<ArrayType>(array);
    int offset = static_cast<int>(array->offset());
    ScilabType* pData = new ScilabType(length, 1);
    if (array->null_count() == 0 && offset == 0)
    {
        memcpy(pData->get(), typed_array->raw_values(), length * sizeof(CType));
    }
    else
    {
        auto* pdata = pData->get();
        const CType* prawdata = typed_array->raw_values();
        convertData(prawdata, pdata, array, length, offset);
    }
    return pData;
}

static types::InternalType* copyArrayData(const std::shared_ptr<arrow::Array>& array, const std::shared_ptr<arrow::DataType>& type, std::string& coltype)
{
    const int length = (int)array->length();

    switch (type->id())
    {
        case arrow::Type::DOUBLE:
        {
            coltype = "double";
            return copyData<types::Double, arrow::DoubleArray, double, double>(array, length);
            // auto typed_array = std::static_pointer_cast<arrow::DoubleArray>(array);
            // types::Double* pDouble = new types::Double(length, 1);
            
            // if (array->null_count() == 0)
            // {
                // memcpy(pDouble->get(), typed_array->raw_values(), length * sizeof(double));
            // }
            // else
            // {
            //     double* pdata = pDouble->get();
            //     const double* prawdata = typed_array->raw_values();
            //     for (int i = 0; i < length; i++)
            //     {
            //         if (array->IsNull(i))
            //         {
            //             pdata[i] = std::numeric_limits<double>::quiet_NaN();
            //         }
            //         else
            //         {
            //             pdata[i] = prawdata[i];
            //         }
            //     }
            // }
            
            // return pDouble;
        }
        case arrow::Type::FLOAT:
        {
            coltype = "double";
            auto typed_array = std::static_pointer_cast<arrow::FloatArray>(array);
            types::Double* pDouble = new types::Double(length, 1);
            
            double* pdata = pDouble->get();
            const float* prawdata = typed_array->raw_values();
            int offset = static_cast<int>(typed_array->offset());
            convertData(prawdata, pdata, array, length, offset);
            // for (int i = 0; i < length; i++)
            // {
            //     if (array->IsNull(i))
            //     {
            //         pdata[i] = std::numeric_limits<double>::quiet_NaN();
            //     }
            //     else
            //     {
            //         pdata[i] = static_cast<double>(prawdata[i]);
            //     }
            // }
            
            return pDouble;            
        }
        case arrow::Type::INT64:
        {
            coltype = "int64";
            return copyData<types::Int64, arrow::Int64Array, int64_t, int64_t>(array, length);
        }
        case arrow::Type::INT32:
        {
            coltype = "int32";
            return copyData<types::Int32, arrow::Int32Array, int32_t, int32_t>(array, length);
        }
        case arrow::Type::INT16:
        {
            coltype = "int16";
            return copyData<types::Int16, arrow::Int16Array, int16_t, int16_t>(array, length);
        }
        case arrow::Type::INT8:
        {
            coltype = "int8";
            return copyData<types::Int8, arrow::Int8Array, int8_t, char>(array, length);
        }
        case arrow::Type::UINT64:
        {
            coltype = "uint64";
            return copyData<types::UInt64, arrow::UInt64Array, uint64_t, uint64_t>(array, length);
        }
        case arrow::Type::UINT32:
        {
            coltype = "uint32";
            return copyData<types::UInt32, arrow::UInt32Array, uint32_t, uint32_t>(array, length);
            // auto typed_array = std::static_pointer_cast<arrow::UInt32Array>(array);
            // types::UInt32* pInt = new types::UInt32(length, 1);

            // if (array->null_count() == 0)
            // {
            //     memcpy(pInt->get(), typed_array->raw_values(), length * sizeof(uint32_t));
            // }
            // else
            // {
            //     uint32_t* pdata = pInt->get();
            //     const uint32_t* prawdata = typed_array->raw_values();
            //     for (int i = 0; i < length; i++)
            //     {
            //         if (array->IsNull(i))
            //         {
            //             pdata[i] = 0;
            //         }
            //         else
            //         {
            //             pdata[i] = prawdata[i];
            //         }
            //     }
            // }

            // return pInt;
        }
        case arrow::Type::UINT16:
        {
            coltype = "uint16";
            return copyData<types::UInt16, arrow::UInt16Array, uint16_t, uint16_t>(array, length);
        }
        case arrow::Type::UINT8:
        {
            coltype = "uint8";
            return copyData<types::UInt8, arrow::UInt8Array, uint8_t, uint8_t>(array, length);
        }
        case arrow::Type::BOOL:
        {
            coltype = "bool";
            auto typed_array = std::static_pointer_cast<arrow::BooleanArray>(array);
            types::Bool* pBool = new types::Bool(length, 1);
            
            int* pdata = pBool->get();
            for (int i = 0; i < length; i++)
            {
                if (array->IsNull(i))
                {
                    pdata[i] = 0;
                }
                else
                {
                    pdata[i] = typed_array->Value(i);
                }
            }
            return pBool;       
        }
        case arrow::Type::STRING:
        {
            coltype = "string";
            auto typed_array = std::static_pointer_cast<arrow::StringArray>(array);
            types::String* pStr = new types::String(length, 1);

            for (int i = 0; i < length; i++)
            {
                if (array->IsNull(i))
                {
                    pStr->set(i, L"");
                }
                else
                {
                    std::string str = typed_array->GetString(i);
                    pStr->set(i, str.c_str());
                }
            }
            return pStr;
        }
        case arrow::Type::DICTIONARY:
        {
            coltype = "string";
            auto dict_array = std::static_pointer_cast<arrow::DictionaryArray>(array);
            auto dictionary = dict_array->dictionary();
            
            if (dictionary->type_id() == arrow::Type::STRING)
            {
                types::String* pStr = new types::String(length, 1);
                auto indices = dict_array->indices();
                auto string_dict = std::static_pointer_cast<arrow::StringArray>(dictionary);
                auto int32_indices = std::static_pointer_cast<arrow::Int32Array>(indices);

                for (int i = 0; i < length; i++)
                {
                    if (dict_array->IsNull(i))
                    {
                        pStr->set(i, L"");
                    }
                    else
                    {
                        int32_t index = int32_indices->Value(i);
                        std::string str_value = string_dict->GetString(index);
                        pStr->set(i, str_value.c_str());
                    }
                }
                return pStr;
            }
            else
            {
                return nullptr;
            }
        }
        case arrow::Type::TIMESTAMP:
        {
            coltype = "datetime";
            auto timestamp_array = std::static_pointer_cast<arrow::TimestampArray>(array);
            auto timestamp_type = std::static_pointer_cast<arrow::TimestampType>(timestamp_array->type());
            arrow::TimeUnit::type unit = timestamp_type->unit();
            types::Double* pDouble = new types::Double(length, 1);
            double* pdata = pDouble->get();
            const int64_t* prawdata = timestamp_array->raw_values();
            int offset = static_cast<int>(timestamp_array->offset());
            for (int i = 0; i < length; i++)
            {
                if (timestamp_array->IsNull(i))
                {
                    pdata[i] = std::numeric_limits<double>::quiet_NaN();
                }
                else
                {
                    double timestamp = static_cast<double>(prawdata[i + offset]);
                    double val = 0;
                    switch (unit)
                    {
                        case arrow::TimeUnit::SECOND:
                        {
                            val = timestamp * 1e6; 
                            break;
                        }
                        case arrow::TimeUnit::MILLI:
                        {
                            val = timestamp * 1e3;
                            break;
                        }
                        case arrow::TimeUnit::MICRO:
                        {
                            val = timestamp; 
                            break;
                        }
                        case arrow::TimeUnit::NANO:
                        {
                            val = timestamp / 1e3;
                            break;
                        }
                    }

                    pdata[i] = val;
                }
            }
            
            return pDouble;

            // for (int64_t i = 0; i < length; i++)
            // {
            //     if (timestamp_array->IsNull(i))
            //     {
            //         data_ptr[i] = 0;
            //     }
            //     else
            //     {
            //         int64_t t_value = timestamp_array->Value(i);
            //         int64_t microseconds = 0;
            //         switch (original_unit)
            //         {
            //             case arrow::TimeUnit::SECOND:
            //                 microseconds = t_value * 1000000LL;
            //                 break;
            //             case arrow::TimeUnit::MILLI:
            //                 microseconds = t_value * 1000LL;
            //                 break;
            //             case arrow::TimeUnit::MICRO:
            //                 microseconds = t_value;
            //                 break;
            //             case arrow::TimeUnit::NANO:
            //                 microseconds = t_value / 1000LL;
            //                 break;
            //             default:
            //                 microseconds = t_value;
            //                 break;
            //         }
            //         data_ptr[i] = microseconds;
            //     }
            // }
            // break;
        }
        case arrow::Type::DATE32:
        {
            coltype = "datetime";
            auto date32_array = std::static_pointer_cast<arrow::Date32Array>(array);
            types::Double* pDouble = new types::Double(length, 1);
            double* pdata = pDouble->get();
            for (int i = 0; i < length; i++)
            {
                if (date32_array->IsNull(i))
                {
                    pdata[i] = std::numeric_limits<double>::quiet_NaN();
                }
                else
                {
                    pdata[i] = static_cast<double>(date32_array->Value(i)) * 24 * 60 * 60 * 1000000;
                }
            }
            return pDouble;
        }
        case arrow::Type::DATE64:
        {
            coltype = "datetime";
            auto date64_array = std::static_pointer_cast<arrow::Date64Array>(array);
            types::Double* pDouble = new types::Double(length, 1);
            double* pdata = pDouble->get();
            for (int i = 0; i < length; i++)
            {
                if (date64_array->IsNull(i))
                {
                    pdata[i] = std::numeric_limits<double>::quiet_NaN();
                }
                else
                {
                    pdata[i] = static_cast<double>(date64_array->Value(i)) * 1000;
                }
            }
            return pDouble;
        }
        case arrow::Type::DURATION:
        {
            coltype = "duration";
            auto duration_array = std::static_pointer_cast<arrow::DurationArray>(array);
            auto duration_type = std::static_pointer_cast<arrow::DurationType>(duration_array->type());
            types::Double* pDouble = new types::Double(length, 1);
            double* pdata = pDouble->get();
            const int64_t* prawdata = duration_array->raw_values();
            int offset = static_cast<int>(duration_array->offset());
            for (int i = 0; i < length; i++)
            {
                if (duration_array->IsNull(i))
                {
                    pdata[i] = std::numeric_limits<double>::quiet_NaN();
                }
                else
                {
                    pdata[i] = static_cast<double>(prawdata[i + offset]) / 1e3;
                }
            }

            return pDouble;
        }
        default: {}
    }
    return nullptr;
}
    
    // switch (column->type)
    // {
    //     case ARROW_TYPE_DOUBLE:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::DoubleArray>(array);
    //         column->data = malloc(length * sizeof(double));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }   
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(double));
    //         break;
    //     }
    //     case ARROW_TYPE_FLOAT:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::FloatArray>(array);
    //         column->data = malloc(length * sizeof(double));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         float* src = (float*)typed_array->raw_values();
    //         double* dst = (double*)column->data;
    //         for (int64_t i = 0; i < length; i++)
    //         {
    //             dst[i] = static_cast<double>(src[i]);
    //         }
    //         break;
    //     }
    //     case ARROW_TYPE_INT64:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::Int64Array>(array);
    //         column->data = malloc(length * sizeof(int64_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(int64_t));
    //         break;
    //     }
    //     case ARROW_TYPE_INT32:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::Int32Array>(array);
    //         column->data = malloc(length * sizeof(int32_t));
    //         if (!column->data) 
    //         {
    //             return -1;
    //         }
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(int32_t));
    //         break;
    //     }
    //     case ARROW_TYPE_INT16:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::Int16Array>(array);
    //         column->data = malloc(length * sizeof(int16_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(int16_t));
    //         break;
    //     }
    //     case ARROW_TYPE_INT8:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::Int8Array>(array);
    //         column->data = malloc(length * sizeof(int8_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(int8_t));
    //         break;
    //     }
    //     case ARROW_TYPE_UINT64:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::UInt64Array>(array);
    //         column->data = malloc(length * sizeof(uint64_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(uint64_t));
    //         break;
    //     }
    //     case ARROW_TYPE_UINT32:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::UInt32Array>(array);
    //         column->data = malloc(length * sizeof(uint32_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(uint32_t));
    //         break;
    //     }
    //     case ARROW_TYPE_UINT16:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::UInt16Array>(array);
    //         column->data = malloc(length * sizeof(uint16_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(uint16_t));
    //         break;
    //     }
    //     case ARROW_TYPE_UINT8:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::UInt8Array>(array);
    //         column->data = malloc(length * sizeof(uint8_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         memcpy(column->data, typed_array->raw_values(), length * sizeof(uint8_t));
    //         break;
    //     }
    //     case ARROW_TYPE_BOOL:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::BooleanArray>(array);
    //         column->data = malloc(length * sizeof(int));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         int* bool_data = (int*)column->data;
    //         for (int64_t i = 0; i < length; i++)
    //         {
    //             bool_data[i] = typed_array->Value(i);
    //         }
    //         break;
    //     }
    //     case ARROW_TYPE_DICTIONARY: 
    //     {
    //         auto dict_array = std::static_pointer_cast<arrow::DictionaryArray>(array);
    //         auto dictionary = dict_array->dictionary();
    //         auto indices = dict_array->indices();
            
    //         if (dictionary->type_id() == arrow::Type::STRING)
    //         {
    //             column->data = malloc(length * sizeof(char*));
    //             if (!column->data)
    //             {
    //                 return -1;
    //             }
    //             char** string_data = (char**)column->data;
    //             auto string_dict = std::static_pointer_cast<arrow::StringArray>(dictionary);
    //             auto int32_indices = std::static_pointer_cast<arrow::Int32Array>(indices);
    //             for (int64_t i = 0; i < length; i++)
    //             {
    //                 if (dict_array->IsNull(i))
    //                 {
    //                     string_data[i] = nullptr;
    //                 }
    //                 else
    //                 {
    //                     int32_t index = int32_indices->Value(i);
    //                     std::string str_value = string_dict->GetString(index);
    //                     string_data[i] = (char*)malloc(str_value.length() + 1);
    //                     if (!string_data[i])
    //                     {
    //                         return -1;
    //                     }
    //                     strcpy(string_data[i], str_value.c_str());
    //                 }
    //             }
    //         }
    //         else
    //         {
    //             return -1;
    //         }
    //         break;
    //     }
    //     case ARROW_TYPE_STRING:
    //     {
    //         auto typed_array = std::static_pointer_cast<arrow::StringArray>(array);
    //         char** string_data = (char**)malloc(length * sizeof(char*));
    //         if (!string_data)
    //         {
    //             return -1;
    //         }
    //         column->data = string_data;
    //         for (int64_t i = 0; i < length; i++)
    //         {
    //             if (array->IsNull(i))
    //             {
    //                 string_data[i] = nullptr;
    //             }
    //             else
    //             {
    //                 std::string str = typed_array->GetString(i);
    //                 string_data[i] = (char*)malloc(str.length() + 1);
    //                 if (!string_data[i])
    //                 {
    //                     return -1;
    //                 }
    //                 strcpy(string_data[i], str.c_str());
    //             }
    //         }
    //         break;
    //     }
    //     case ARROW_TYPE_TIMESTAMP:
    //     {
    //         auto timestamp_array = std::static_pointer_cast<arrow::TimestampArray>(array);
    //         column->data = malloc(length * sizeof(int64_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         auto timestamp_type = std::static_pointer_cast<arrow::TimestampType>(timestamp_array->type());
    //         arrow::TimeUnit::type original_unit = timestamp_type->unit();
    //         int64_t* data_ptr = static_cast<int64_t*>(column->data);
    //         for (int64_t i = 0; i < length; i++)
    //         {
    //             if (timestamp_array->IsNull(i))
    //             {
    //                 data_ptr[i] = 0;
    //             }
    //             else
    //             {
    //                 int64_t t_value = timestamp_array->Value(i);
    //                 int64_t microseconds = 0;
    //                 switch (original_unit)
    //                 {
    //                     case arrow::TimeUnit::SECOND:
    //                         microseconds = t_value * 1000000LL;
    //                         break;
    //                     case arrow::TimeUnit::MILLI:
    //                         microseconds = t_value * 1000LL;
    //                         break;
    //                     case arrow::TimeUnit::MICRO:
    //                         microseconds = t_value;
    //                         break;
    //                     case arrow::TimeUnit::NANO:
    //                         microseconds = t_value / 1000LL;
    //                         break;
    //                     default:
    //                         microseconds = t_value;
    //                         break;
    //                 }
    //                 data_ptr[i] = microseconds;
    //             }
    //         }
    //         break;
    //     }
    //     case ARROW_TYPE_DATE32:
    //     {
    //         auto date32_array = std::static_pointer_cast<arrow::Date32Array>(array);
    //         column->data = malloc(length * sizeof(int64_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         column->type = ARROW_TYPE_TIMESTAMP;
    //         int64_t* data_ptr = static_cast<int64_t*>(column->data);
    //         for (int64_t i = 0; i < length; i++)
    //         {
    //             if (date32_array->IsNull(i))
    //             {
    //                 data_ptr[i] = 0;
    //             }
    //             else
    //             {
    //                 int32_t days_since_epoch = date32_array->Value(i);
    //                 int64_t microseconds = static_cast<int64_t>(days_since_epoch) * 24LL * 60LL * 60LL * 1000000LL;
    //                 data_ptr[i] = microseconds;
    //             }
    //         }
    //         break;
    //     }
    //     case ARROW_TYPE_DATE64:
    //     {
    //         auto date64_array = std::static_pointer_cast<arrow::Date64Array>(array);
    //         column->data = malloc(length * sizeof(int64_t));
    //         if (!column->data)
    //         {
    //             return -1;
    //         }
    //         column->type = ARROW_TYPE_TIMESTAMP;
    //         int64_t* data_ptr = static_cast<int64_t*>(column->data);
    //         for (int64_t i = 0; i < length; i++)
    //         {
    //             if (date64_array->IsNull(i))
    //             {
    //                 data_ptr[i] = 0;
    //             }
    //             else
    //             {
    //                 int64_t milliseconds = date64_array->Value(i);
    //                 int64_t microseconds = milliseconds * 1000LL;
    //                 data_ptr[i] = microseconds;
    //             }
    //         }
    //         break;
    //     }
    //     default:
    //         return -1;
    // }
    // return 0;
// }

static int _ConvertArrowTableToRaw(const std::shared_ptr<arrow::Table>& table, ArrowData &data)
{
    if (!table)
    {
        return -1;
    }

    const int num_columns = table->num_columns();

    types::String* pColNames = new types::String(1, num_columns);
    types::List* pList = new types::List();
    types::String* pColTypes = new types::String(1, num_columns);
    
    for (int i = 0; i < num_columns; i++)
    {
        auto column = table->column(i);
        auto field = table->schema()->field(i);

        std::string name = field->name();
        pColNames->set(i, name.c_str());

        auto combined_result = arrow::Concatenate(column->chunks());
        if (!combined_result.ok())
        {
            pColNames->killMe();
            pList->killMe();
            pColTypes->killMe();
            return -1;
        }

        auto combined_array = combined_result.ValueOrDie();      
        std::string coltype;
        types::InternalType* pData = copyArrayData(combined_array, field->type(), coltype);
        if (pData == nullptr)
        {
            pColNames->killMe();
            pList->killMe();
            pColTypes->killMe();
            return -1;
        }

        pList->append(pData);
        pColTypes->set(i, coltype.c_str());
    } 

    data.pColNames = pColNames;
    data.pData = pList;
    data.pColTypes = pColTypes;

    return 0;
}


ArrowData arrow_read(const char* filename)
{
    ArrowData st = {nullptr, nullptr, nullptr, 0};

    if (!filename)
    {
        // Invalid arguments
        st.status = -1;
        return st;
    }

    std::shared_ptr<arrow::io::ReadableFile> infile;
    auto file_result = arrow::io::ReadableFile::Open(filename);
    if (!file_result.ok())
    {
        // Failed to open file
        st.status = -1;
        return st;
    }

    infile = file_result.ValueOrDie();
    char magic_bytes[8] = {0};
    auto read_result = infile->Read(8, magic_bytes);
    if (!read_result.ok())
    {
        // Failed to read magic bytes
        // invalid file
        st.status = -2;
        return st;
    }

    auto seek_result = infile->Seek(0);
    if (!seek_result.ok())
    {
        st.status = -2;
        return st;
    }

    if (memcmp(magic_bytes, "PAR1", 4) == 0)
    {
        auto reader_result = parquet::arrow::OpenFile(infile, arrow::default_memory_pool());
        if (!reader_result.ok())
        {
            // Failed to open Parquet file
            // invalid file
            st.status = -2;
            return st;
        }
        auto reader = reader_result.MoveValueUnsafe();

        std::shared_ptr<arrow::Table> table;
        auto table_result = reader->ReadTable(&table);
        if (!table_result.ok())
        {
            // Failed to read table
            st.status = -3;
            return st;
        }

        int status = _ConvertArrowTableToRaw(table, st);
        st.status = status;

        return st;
    }
    else if (memcmp(magic_bytes, "ARROW1", 6) == 0)
    {
        auto file_reader_result = arrow::ipc::RecordBatchFileReader::Open(infile);
        if (file_reader_result.ok())
        {
            auto reader = file_reader_result.ValueOrDie();
            std::vector<std::shared_ptr<arrow::RecordBatch>> batches;
            for (int i = 0; i < reader->num_record_batches(); ++i)
            {
                auto batch_result = reader->ReadRecordBatch(i);
                if (!batch_result.ok())
                {
                    // Failed to read batch
                    st.status = -3;
                    return st;
                }
                batches.push_back(batch_result.ValueOrDie());
            }
            
            auto table_result = arrow::Table::FromRecordBatches(batches);
            if (!table_result.ok())
            {
                // Failed to create table
                st.status = -5;
                return st;
            }

            int status = _ConvertArrowTableToRaw(table_result.ValueOrDie(), st);
            st.status = status;

            return st;
        }

        seek_result = infile->Seek(0);
        if (!seek_result.ok())
        {
            st.status = -2;
            return st;
        }
        
        auto stream_reader_result = arrow::ipc::RecordBatchStreamReader::Open(infile);
        if (stream_reader_result.ok())
        {
            auto reader = stream_reader_result.ValueOrDie();
            std::vector<std::shared_ptr<arrow::RecordBatch>> batches;
            while (true)
            {
                auto batch_result = reader->Next();
                if (!batch_result.ok())
                {
                    // Failed to read record batch
                    st.status = -4;
                    return st;
                }
                auto batch = batch_result.ValueOrDie();
                if (!batch)
                {
                    break;
                }
                batches.push_back(batch);
            }
            
            if (batches.empty())
            {
                // No batches found
                st.status = -4;
                return st;
            }
            
            auto table_result = arrow::Table::FromRecordBatches(batches);
            if (!table_result.ok())
            {
                // Failed to create table
                st.status = -5;
                return st;
            }

            int status = _ConvertArrowTableToRaw(table_result.ValueOrDie(), st);
            st.status = status;

            return st;
        }
        // Failed to open as file or stream
        st.status = -2;
        return st;
    }
    else
    {
        // Unsupported file format (no recognized magic bytes)
        st.status = -1;
        return st;
    }
}
