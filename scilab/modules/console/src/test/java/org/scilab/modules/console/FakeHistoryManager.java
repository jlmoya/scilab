/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.console;

import com.artenum.rosetta.interfaces.core.HistoryManager;

/**
 * Hermetic test double for the rosetta {@link HistoryManager} interface.
 *
 * <p>Holds the two pieces of state the console history-browsing actions read and
 * write ({@code inHistory} and {@code tmpEntry}) and lets a test script what the
 * next/previous lookups return, so the {@code GetNextAction} / {@code
 * GetPreviousAction} state machine can be exercised without the native Scilab
 * history store.
 */
class FakeHistoryManager implements HistoryManager {

    boolean inHistory = false;
    String tmpEntry = null;

    /** Value the next {@link #getNextEntry(String)} call returns. */
    String nextEntryReturn = null;
    /** Value the next {@link #getPreviousEntry(String)} call returns. */
    String previousEntryReturn = null;

    String lastNextArg = null;
    String lastPreviousArg = null;

    public boolean isInHistory() {
        return inHistory;
    }

    public void setInHistory(boolean status) {
        inHistory = status;
    }

    public String getTmpEntry() {
        return tmpEntry;
    }

    public void setTmpEntry(String entry) {
        tmpEntry = entry;
    }

    public String getNextEntry(String beg) {
        lastNextArg = beg;
        return nextEntryReturn;
    }

    public String getPreviousEntry(String beg) {
        lastPreviousArg = beg;
        return previousEntryReturn;
    }

    public String getEntry(int index) {
        return null;
    }

    public void addEntry(String entry) {
    }

    public void display() {
    }

    public void setMaxEntryNumber(int n) {
    }

    public void reset() {
    }

    public void load() {
    }

    public void save() {
    }
}
