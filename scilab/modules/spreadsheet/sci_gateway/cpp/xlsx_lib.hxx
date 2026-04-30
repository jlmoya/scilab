// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

#ifndef XLSX_LIB_HXX
#define XLSX_LIB_HXX

#include <string>
#include <vector>

#define MS_BY_DAY 86400000 //24 * 60 * 60 * 1000

extern "C" {

// data struct
enum XlsxDataType_t
{
    XLSX_TYPE_DOUBLE = 0,
    XLSX_TYPE_INT64,
    XLSX_TYPE_INT32,
    XLSX_TYPE_BOOL,
    XLSX_TYPE_STRING,
    XLSX_TYPE_TIMESTAMP,
    XLSX_TYPE_DURATION,
    XLSX_TYPE_UNKNOWN // not use for the moment
};

struct XlsxCell
{
    double dblValue;
    std::string value;
    XlsxDataType_t type;
    int row;
    int col;
};

struct XlsxSheet
{
    std::string name;
    std::vector<std::vector<XlsxCell>> cells;
    int max_row;
    int max_col;
};

struct XlsxData
{
    std::string filename;
    std::vector<XlsxSheet> sheets;
    int sheet_count;
    bool is_loaded;
};

struct XlsxMetadata
{
    std::string filename;
    int total_sheets;
    std::vector<std::string> sheet_names;
    std::string creator;
    std::string last_modified_by;
    std::string created_date;
    std::string modified_date;
    std::string title;
    std::string subject;
    std::string description;
    long file_size;

    struct XlsxSheetInfo
    {
        bool is_sheet_info;
        std::string sheet_name;
        int sheet_index;
        bool is_empty;
        int max_row;
        int max_col;
        int cell_count;
    } sheet_info;
};

struct XlsxWriteOptions
{
    std::string sheet_name;
    int sheet_index;
    std::string range;
    bool clear_sheet;
    bool append_mode;

    XlsxWriteOptions() : sheet_index(-1), clear_sheet(false), append_mode(false) {}
};

enum XlsxSheetOperation_t
{
    XLSX_SHEET_CREATE = 0,
    XLSX_SHEET_DELETE,
    XLSX_SHEET_RENAME,
    XLSX_SHEET_INFO
};

struct XlsxSheetOptions
{
    XlsxSheetOperation_t operation;
    std::string sheet_name;
    int sheet_index;
    bool use_index;
    std::string new_sheet_name;

    XlsxSheetOptions() : operation(XLSX_SHEET_INFO), sheet_index(-1), use_index(false) {}
};

// data functions
int xlsx_read(const char* filename, XlsxData* data, const XlsxWriteOptions* options);
void xlsx_free_data(XlsxData* data);
int xlsx_write(const char* filename, const XlsxData* data, const XlsxMetadata* metadata, const XlsxWriteOptions* options = nullptr);

// metadata functions
int xlsx_read_metadata(const char* filename, XlsxMetadata* metadata, const char* sheet_name = nullptr, int sheet_index = -1);
int xlsx_sheet(const char* filename, const XlsxSheetOptions* options);

// file validation
int xlsx_valid_file(const char* filename);

} // extern "C"

#endif /* XLSX_LIB_HXX */
