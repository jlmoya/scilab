/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.completion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import com.artenum.rosetta.interfaces.core.CompletionItem;

import org.scilab.modules.completion.AbstractSciCompletionWindow.CompletionItemListModel;

/**
 * Hermetic tests for {@link AbstractSciCompletionWindow.CompletionItemListModel},
 * the {@code protected static} inner list model of the (otherwise Swing/GUI)
 * completion window.
 *
 * <p>The model itself is pure data logic: it keeps a list of
 * {@link CompletionItem}s, {@code Collections.sort}s them on
 * {@link #updateData}, and fires {@link ListDataEvent}s on change. None of that
 * needs a live display, so it is exercised directly with a tiny in-package fake
 * item. The enclosing window (JPanel/JList/focus plumbing) is deliberately left
 * untested — it needs an event loop and a real text component.</p>
 */
public class AbstractSciCompletionWindowModelTest {

    /**
     * Minimal {@link CompletionItem}, ordered by {@code methodProfile} so the
     * model's {@code Collections.sort} has a deterministic, test-controlled key.
     */
    private static final class FakeItem implements CompletionItem {
        private String type;
        private String methodProfile;
        private String returnValue;
        private String help;

        FakeItem(String methodProfile) {
            this.methodProfile = methodProfile;
            this.returnValue = methodProfile;
            this.type = "Function";
        }

        @Override public String getType() {
            return type;
        }
        @Override public String getMethodProfile() {
            return methodProfile;
        }
        @Override public String getReturnValue() {
            return returnValue;
        }
        @Override public String getHelp() {
            return help;
        }
        @Override public void setType(String t) {
            this.type = t;
        }
        @Override public void setMethodProfile(String m) {
            this.methodProfile = m;
        }
        @Override public void setReturnValue(String r) {
            this.returnValue = r;
        }
        @Override public void setHelp(String h) {
            this.help = h;
        }
        @Override public int compareTo(CompletionItem o) {
            return this.methodProfile.compareTo(o.getMethodProfile());
        }
    }

    /** Captures the model's ListDataEvent notifications. */
    private static final class RecordingListener implements ListDataListener {
        int contentsChanged = 0;
        int intervalAdded = 0;
        int intervalRemoved = 0;
        ListDataEvent last;

        @Override public void contentsChanged(ListDataEvent e) {
            contentsChanged++;
            last = e;
        }
        @Override public void intervalAdded(ListDataEvent e) {
            intervalAdded++;
            last = e;
        }
        @Override public void intervalRemoved(ListDataEvent e) {
            intervalRemoved++;
            last = e;
        }
    }

    private static List<CompletionItem> items(String... profiles) {
        List<CompletionItem> l = new ArrayList<CompletionItem>();
        for (String p : profiles) {
            l.add(new FakeItem(p));
        }
        return l;
    }

    @Test
    void freshModelIsEmpty() {
        CompletionItemListModel model = new CompletionItemListModel();
        assertEquals(0, model.getSize());
    }

    @Test
    void getElementAtOnEmptyModelThrows() {
        CompletionItemListModel model = new CompletionItemListModel();
        // Backed by an ArrayList; no bounds guard in the model.
        assertThrows(IndexOutOfBoundsException.class, () -> model.getElementAt(0));
    }

    @Test
    void updateDataSetsSizeToTheListSize() {
        CompletionItemListModel model = new CompletionItemListModel();
        model.updateData(items("a", "b", "c"));
        assertEquals(3, model.getSize());
    }

    @Test
    void updateDataSortsItemsByCompareTo() {
        CompletionItemListModel model = new CompletionItemListModel();
        model.updateData(items("charlie", "alpha", "bravo"));
        assertEquals("alpha", ((CompletionItem) model.getElementAt(0)).getMethodProfile());
        assertEquals("bravo", ((CompletionItem) model.getElementAt(1)).getMethodProfile());
        assertEquals("charlie", ((CompletionItem) model.getElementAt(2)).getMethodProfile());
    }

    @Test
    void updateDataStoresItemReferencesNotCopies() {
        CompletionItemListModel model = new CompletionItemListModel();
        FakeItem only = new FakeItem("only");
        List<CompletionItem> list = new ArrayList<CompletionItem>();
        list.add(only);
        model.updateData(list);
        assertSame(only, model.getElementAt(0));
    }

    @Test
    void updateDataReplacesPreviousContents() {
        CompletionItemListModel model = new CompletionItemListModel();
        model.updateData(items("first", "second"));
        assertEquals(2, model.getSize());

        // A second update must clear() before addAll(), not append.
        model.updateData(items("solo"));
        assertEquals(1, model.getSize());
        assertEquals("solo", ((CompletionItem) model.getElementAt(0)).getMethodProfile());
    }

    @Test
    void updateDataWithEmptyListEmptiesTheModel() {
        CompletionItemListModel model = new CompletionItemListModel();
        model.updateData(items("x", "y"));
        model.updateData(new ArrayList<CompletionItem>());
        assertEquals(0, model.getSize());
    }

    @Test
    void updateDataIsIndependentOfTheCallerList() {
        // Because updateData copies via addAll, mutating the source afterward
        // must not change the model.
        CompletionItemListModel model = new CompletionItemListModel();
        List<CompletionItem> src = items("a", "b");
        model.updateData(src);
        src.clear();
        assertEquals(2, model.getSize());
    }

    @Test
    void updateDataFiresOneContentsChangedEventSizedToTheData() {
        CompletionItemListModel model = new CompletionItemListModel();
        RecordingListener listener = new RecordingListener();
        model.addListDataListener(listener);

        model.updateData(items("a", "b", "c"));

        // updateData ends with setFilter(null), which fires exactly one
        // contentsChanged(this, 0, getSize()).
        assertEquals(1, listener.contentsChanged);
        assertEquals(0, listener.intervalAdded);
        assertEquals(0, listener.intervalRemoved);
        assertNotNull(listener.last);
        assertEquals(0, listener.last.getIndex0());
        assertEquals(3, listener.last.getIndex1());
        assertEquals(ListDataEvent.CONTENTS_CHANGED, listener.last.getType());
    }

    @Test
    void setFilterFiresContentsChangedCoveringTheWholeRange() {
        CompletionItemListModel model = new CompletionItemListModel();
        model.updateData(items("a", "b", "c", "d"));

        RecordingListener listener = new RecordingListener();
        model.addListDataListener(listener);

        model.setFilter("anything");

        assertEquals(1, listener.contentsChanged);
        assertEquals(0, listener.last.getIndex0());
        assertEquals(4, listener.last.getIndex1());
    }

    @Test
    void setFilterNullDoesNotThrowAndStillFires() {
        CompletionItemListModel model = new CompletionItemListModel();
        RecordingListener listener = new RecordingListener();
        model.addListDataListener(listener);

        assertDoesNotThrow(() -> model.setFilter(null));
        assertEquals(1, listener.contentsChanged);
    }

    @Test
    void setFilterEmptyStringBehavesLikeNullAndFires() {
        // Defect-characterisation: the `filter` field is write-only (never read
        // by any method), so the null/empty coalescing has no observable effect
        // beyond not throwing. We can still assert the event is fired for the
        // empty-string branch, pinning current behaviour.
        CompletionItemListModel model = new CompletionItemListModel();
        RecordingListener listener = new RecordingListener();
        model.addListDataListener(listener);

        assertDoesNotThrow(() -> model.setFilter(""));
        assertEquals(1, listener.contentsChanged);
    }

    @Test
    void setFilterOnEmptyModelFiresRangeZeroToZero() {
        CompletionItemListModel model = new CompletionItemListModel();
        RecordingListener listener = new RecordingListener();
        model.addListDataListener(listener);

        model.setFilter("x");

        assertEquals(0, listener.last.getIndex0());
        assertEquals(0, listener.last.getIndex1());
    }

    @Test
    void removedListenerStopsReceivingEvents() {
        CompletionItemListModel model = new CompletionItemListModel();
        RecordingListener listener = new RecordingListener();
        model.addListDataListener(listener);
        model.setFilter("one");
        assertEquals(1, listener.contentsChanged);

        model.removeListDataListener(listener);
        model.setFilter("two");
        assertEquals(1, listener.contentsChanged, "no further events after removal");
    }

    @Test
    void duplicateProfilesAreAllKept() {
        CompletionItemListModel model = new CompletionItemListModel();
        model.updateData(items("same", "same", "same"));
        assertEquals(3, model.getSize());
        assertEquals("same", ((CompletionItem) model.getElementAt(2)).getMethodProfile());
    }
}
