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
#include <arrow/ipc/writer.h>
#include "arrow_lib.h"
#include "arrow_write_utils.h"

int arrow_write(const char* filename, ArrowData* raw_data)
{
    if (!filename || !raw_data)
    {
        // Invalid arguments
        return -1;
    }

    auto file_result = arrow::io::FileOutputStream::Open(filename);
    if (!file_result.ok())
    {
        // Failed to open file
        return -2;
    }

    auto table_result = createArrowTable(raw_data);
    if (!table_result.ok())
    {
        // Failed to create table
        return -3;
    }

    auto outfile = file_result.ValueOrDie();
    auto table = table_result.ValueOrDie();

    auto writer_result = arrow::ipc::MakeFileWriter(outfile, table->schema());
    if (!writer_result.ok())
    {
        // Failed to create file writer
        return -4;
    }
    auto writer = writer_result.ValueOrDie();

    auto record_batch_result = table->CombineChunksToBatch(arrow::default_memory_pool());
    if (!record_batch_result.ok())
    {
        // Failed to combine chunks into a single batch
        return -5;
    }

    auto batch = record_batch_result.ValueOrDie();
    if (!writer->WriteRecordBatch(*batch).ok())
    {
        // Failed to write batch
        return -5;
    }

    if (!writer->Close().ok())
    {
        // Failed to close writer
        return -6;
    }

    if (!outfile->Close().ok())
    {
        // Failed to close file
        return -7;
    }

    return 0;
}
