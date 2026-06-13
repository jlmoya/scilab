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

package org.scilab.modules.scinotes;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.scilab.modules.commons.filemonitor.FileSystemMonitor;
import org.scilab.modules.gui.messagebox.ScilabModalDialog;
import org.scilab.modules.gui.messagebox.ScilabModalDialog.AnswerOption;
import org.scilab.modules.gui.messagebox.ScilabModalDialog.ButtonType;
import org.scilab.modules.gui.messagebox.ScilabModalDialog.IconType;
import org.scilab.modules.scinotes.utils.SciNotesMessages;

/**
 * Proactive, conflict-aware reload of SciNotes tabs whose backing file is changed
 * by another program (e.g. a build, git, or Claude running in the embedded
 * terminal). Unlike the legacy focus-only timestamp check, this reacts live via
 * the shared {@link FileSystemMonitor} (native FSEvents):
 * <ul>
 *   <li>clean buffer (no unsaved edits) -&gt; silent auto-reload;</li>
 *   <li>unsaved local edits -&gt; ask the user (reload / keep).</li>
 * </ul>
 *
 * The watched set is the parent directories of all currently-open files; it is
 * recomputed by {@link #refresh()} on open / save (self-correcting, so a missed
 * close hook only leaves a harmless idle watch). {@link #suppress(File)} drops the
 * event from SciNotes' own save so it does not bounce back as an external change.
 *
 * @author Jose Moya
 */
public final class SciNotesFileWatcher {

    private static final SciNotesFileWatcher INSTANCE = new SciNotesFileWatcher();

    private final FileSystemMonitor monitor = FileSystemMonitor.getInstance();
    private final Set<Path> watchedDirs = new HashSet<Path>();
    private final FileSystemMonitor.FileSystemListener listener;

    private SciNotesFileWatcher() {
        listener = new FileSystemMonitor.FileSystemListener() {
            @Override
            public void fileChanged(FileSystemMonitor.FileChangeEvent event) {
                if (event.getType() == FileSystemMonitor.ChangeType.MODIFY) {
                    onFileModified(event.getPath());
                }
            }
        };
    }

    public static SciNotesFileWatcher getInstance() {
        return INSTANCE;
    }

    /**
     * Recompute the watched directory set from every open SciNotes tab, then
     * subscribe the new directories and unsubscribe those no longer needed.
     */
    public synchronized void refresh() {
        Set<Path> wanted = new HashSet<Path>();
        for (SciNotes ed : SciNotes.getSciNotesList()) {
            int n = ed.getTabPane().getTabCount();
            for (int i = 0; i < n; i++) {
                ScilabEditorPane pane = ed.getTextPane(i);
                if (pane != null && pane.getName() != null) {
                    File parent = new File(pane.getName()).getParentFile();
                    if (parent != null && parent.isDirectory()) {
                        wanted.add(parent.toPath().toAbsolutePath().normalize());
                    }
                }
            }
        }
        for (Path d : wanted) {
            if (!watchedDirs.contains(d)) {
                monitor.subscribe(d, listener);
            }
        }
        for (Path d : new ArrayList<Path>(watchedDirs)) {
            if (!wanted.contains(d)) {
                monitor.unsubscribe(d, listener);
            }
        }
        watchedDirs.clear();
        watchedDirs.addAll(wanted);
    }

    /**
     * Suppress the next monitor event for {@code f}: call this right before
     * SciNotes writes the file itself so its own save is not reported back.
     */
    public void suppress(File f) {
        if (f != null) {
            monitor.suppress(f.toPath());
        }
    }

    private void onFileModified(final Path changed) {
        final String path = changed.toString();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                for (SciNotes ed : SciNotes.getSciNotesList()) {
                    int n = ed.getTabPane().getTabCount();
                    for (int i = 0; i < n; i++) {
                        ScilabEditorPane pane = ed.getTextPane(i);
                        if (pane != null && path.equals(pane.getName())) {
                            reloadOrPrompt(ed, pane);
                            return; // a path is open in at most one tab
                        }
                    }
                }
            }
        });
    }

    private void reloadOrPrompt(SciNotes ed, ScilabEditorPane pane) {
        File f = new File(pane.getName());
        if (!f.exists()) {
            return; // deletion is handled by the existing close/save paths
        }
        int index = ed.getTabPane().indexOfComponent(pane.getEditorComponent());
        if (index < 0) {
            return;
        }
        ScilabDocument doc = (ScilabDocument) pane.getDocument();
        if (!doc.isContentModified()) {
            // clean buffer -> silent reload
            ed.reload(index);
        } else {
            // unsaved local edits -> ask before discarding them
            ed.getInfoBar().setText(SciNotesMessages.EXTERNAL_MODIFICATION_INFO);
            if (ScilabModalDialog.show(ed, String.format(SciNotesMessages.ASK_TO_RELOAD, f.getName()),
                    SciNotesMessages.RELOAD, IconType.QUESTION_ICON, ButtonType.YES_NO) == AnswerOption.YES_OPTION) {
                ed.reload(index);
            }
        }
    }
}
