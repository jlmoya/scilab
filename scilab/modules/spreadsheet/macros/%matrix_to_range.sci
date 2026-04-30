// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Valentin MULLER
//
// For more information, see the COPYING file which you should have received
// along with this program.

function range_str = %matrix_to_range(range_matrix, fname)

    if or(range_matrix <> int(range_matrix)) then
        error(msprintf(_("%s: Wrong value for ""%s"" argument: Must be integers.\n"), fname, "range"));
    end
    if or(range_matrix < 1) then
        error(msprintf(_("%s: Wrong value for ""%s"" argument: Must be positive (>=1).\n"), fname, "range"));
    end
    
    row1 = range_matrix(1, 1);
    col1 = range_matrix(1, 2);
    row2 = range_matrix(2, 1);
    col2 = range_matrix(2, 2);

    // invert row/col for excel compared to scilab
    col1_letter = column_number_to_letter(col1);
    col2_letter = column_number_to_letter(col2);

    range_str = col1_letter + string(row1) + ":" + col2_letter + string(row2);
endfunction

function col_letter = column_number_to_letter(col_num)
    col_letter = "";
    while col_num > 0
        remainder = modulo(col_num - 1, 26);
        col_letter = ascii(65 + remainder) + col_letter;
        col_num = int((col_num - remainder - 1) / 26);
    end
endfunction