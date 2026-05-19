/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
 * Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
 *
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

#ifndef ARROW_WRITE_UTILS_H
#define ARROW_WRITE_UTILS_H

#include <arrow/api.h>
#include "arrow_lib.h"


arrow::Result<std::shared_ptr<arrow::Array>> createColumn(wchar_t* wcsType, types::InternalType* pCol);
arrow::Result<std::shared_ptr<arrow::Table>> createArrowTable(ArrowData* data);

#endif /* !ARROW_WRITE_UTILS_H */
