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

package org.scilab.modules.terminal;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Deliberately lightweight helper that schedules the GUI-startup terminal open.
 *
 * It is invoked (via {@code jimport}) from {@code etc/scilab.start}, i.e. on the
 * Scilab interpreter thread while startup is still running. Loading this class
 * must therefore be cheap and must NOT pull in the heavy GUI/FlexDock/Swing class
 * graph (or run a static initializer touching {@code ScilabTabFactory}) on the
 * interpreter thread - doing that previously raced with the EDT's window-layout
 * restoration and could deadlock startup.
 *
 * So this class references nothing heavy: it merely arms a one-shot Swing
 * {@link Timer}. The terminal itself ({@link ScilabTerminal}) is only loaded when
 * the timer fires, on the Event Dispatch Thread, long after the console layout has
 * finished restoring.
 *
 * @author Jose Moya
 */
public final class TerminalAutoOpen {

    /** Delay before opening, giving the console window layout time to restore. */
    private static final int DELAY_MS = 1500;

    private static boolean scheduled = false;

    private TerminalAutoOpen() { }

    /**
     * Arm a one-shot, EDT-based open of a terminal docked below the console.
     * Returns immediately; never loads {@link ScilabTerminal} on the calling
     * (interpreter) thread. Safe to call once at GUI startup.
     */
    public static synchronized void scheduleInitialTerminal() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Timer timer = new Timer(DELAY_MS, new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        // Runs on the EDT: loading ScilabTerminal here is correct
                        // (Swing work belongs on the EDT) and safely past startup.
                        ScilabTerminal.openTerminal();
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
    }
}
