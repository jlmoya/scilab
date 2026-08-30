/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.guibuilder.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.scilab.modules.commons.ScilabConstants;

import org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel;
import org.scilab.modules.gui.bridge.window.SwingScilabWindow;
import org.scilab.modules.gui.messagebox.ScilabModalDialog;
import org.scilab.modules.gui.messagebox.ScilabModalDialog.IconType;
import org.scilab.modules.gui.tab.SimpleTab;
import org.scilab.modules.gui.tabfactory.ScilabTabFactory;
import org.scilab.modules.gui.utils.ClosingOperationsManager;
import org.scilab.modules.gui.utils.Size;
import org.scilab.modules.gui.utils.WindowsConfigurationManager;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.PropertyValue;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;
import org.scilab.modules.guibuilder.write.DesignWriter;
import org.scilab.modules.guibuilder.write.Macr2TreeValidator;
import org.scilab.modules.guibuilder.write.SourceDocument;
import org.scilab.modules.guibuilder.write.SourceValidator;
import org.scilab.modules.guibuilder.write.WriteRefusedException;

/**
 * The read-only designer tab: opens a {@code .sce}, shows what
 * {@link ScilabGuiParser} understood as a tree of widgets and what it could
 * not as locked entries beside their reasons, and saves back without
 * disturbing a byte it did not touch.
 *
 * <p>Structurally this mirrors {@code org.scilab.modules.scinotes.utils.
 * NavigatorWindow} and {@code org.scilab.modules.ui_data.newsfeed.
 * NewsFeedTab}: a {@link SwingScilabDockablePanel} built with a plain content
 * pane, shown in its own window via {@link SwingScilabWindow#createWindow},
 * with a closing operation registered so the docking framework knows how to
 * close it and {@link GuiDesignerTabFactory} registered so
 * {@link ScilabTabFactory} recognises the class, exactly as
 * {@code CodeNavigatorTabFactory} is registered from {@code NavigatorWindow}.
 *
 * <p>Locked entries -- both a widget's own locked properties and every
 * {@link UnmodelledRegion} -- surface inside the {@link JTree} itself (see
 * {@link DesignTreeModel} and {@link DesignTreeCellRenderer}), not only in
 * the properties table or the unmodelled-regions list beside it. A user who
 * never looks at a side panel would otherwise never learn what the tool
 * refuses to touch, which defeats the point of the degradation contract.
 *
 * <p>No colour is set anywhere below: every component inherits its
 * foreground, background and selection colours from the active Look and
 * Feel (FlatLaf), light or dark. Hardcoding one is exactly what later left
 * the graphic editor needing a bespoke themed-refresh mechanism.
 */
@SuppressWarnings("serial")
final class GuiDesignerTab extends SwingScilabDockablePanel {

    private static final String APPLICATION = "GUI Designer";

    static {
        ScilabTabFactory.getInstance().addTabFactory(GuiDesignerTabFactory.getInstance());
    }

    private final String path;
    private final Design design;

    /**
     * The charset {@code path} decoded with. Remembered rather than assumed
     * so the save writes the file's own encoding back -- see {@link
     * SourceFile} for what assuming UTF-8 costs.
     */
    private final Charset charset;

    private final JTree tree;
    private final PropertiesTableModel propertiesModel;
    private final JTable regionsTable;
    private final JButton saveButton;

    /**
     * Opens {@code path} (or an empty designer, when it is null or empty) in
     * a new tab of its own window.
     *
     * @param path a .sce to open, or the empty string for an empty designer
     * @return true when the tab was opened
     */
    static boolean openOn(String path) {
        try {
            SourceFile source = readSource(path);
            Design design = ScilabGuiParser.parse(source.text());
            return showTab(path, design, source.charset());
        } catch (IOException e) {
            report("could not open " + path + ": " + e.getMessage());
            return false;
        } catch (RuntimeException e) {
            // Defensive, as Task 7's placeholder was: this must never let an
            // exception cross back over the JNI bridge (GuiDesigner.hxx),
            // which would otherwise see nothing but a bare "false" and a
            // stderr trace to explain it.
            report("could not open " + path + ": " + e);
            return false;
        }
    }

    private static SourceFile readSource(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            return SourceFile.empty();
        }
        return SourceFile.read(Paths.get(path));
    }

    /**
     * Builds and shows the tab on the event dispatch thread, marshalling
     * onto it first when needed -- {@code openOn} runs on whatever thread
     * called into the JNI bridge, which is never the EDT, mirroring how
     * {@code SciNotes.endedRestoration} and its siblings dispatch their own
     * Swing work.
     */
    private static boolean showTab(String path, Design design, Charset charset) {
        if (SwingUtilities.isEventDispatchThread()) {
            new GuiDesignerTab(path, design, charset).display();
            return true;
        }
        final boolean[] opened = {false};
        try {
            SwingUtilities.invokeAndWait(() -> {
                new GuiDesignerTab(path, design, charset).display();
                opened[0] = true;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            report("interrupted while opening " + path);
            return false;
        } catch (InvocationTargetException e) {
            report("could not open " + path + ": " + e.getCause());
            return false;
        }
        return opened[0];
    }

    private static void report(String message) {
        System.err.println("[guidesigner] " + message);
        ScilabModalDialog.show((SimpleTab) null, message, APPLICATION, IconType.ERROR_ICON);
    }

    private GuiDesignerTab(String path, Design design, Charset charset) {
        super(title(path), UUID.randomUUID().toString());
        this.path = path;
        this.design = design;
        this.charset = charset;

        DesignTreeModel treeModel = new DesignTreeModel(design);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DesignTreeCellRenderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(this::onTreeSelectionChanged);
        tree.expandRow(0);

        propertiesModel = new PropertiesTableModel();
        JTable propertiesTable = new JTable(propertiesModel);
        propertiesTable.getColumnModel().getColumn(2).setMaxWidth(60);

        regionsTable = new JTable(new RegionsTableModel(design));
        regionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        saveButton = new JButton("Save");
        saveButton.setEnabled(path != null && !path.isEmpty());
        saveButton.addActionListener(e -> onSave());

        setContentPane(buildContentPane(propertiesTable));
        tree.setSelectionPath(new TreePath(design.root()));

        registerClosingOperation();
        WindowsConfigurationManager.restorationFinished(this);
    }

    private static String title(String path) {
        if (path == null || path.isEmpty()) {
            return APPLICATION;
        }
        return APPLICATION + " - " + Paths.get(path).getFileName();
    }

    /**
     * A tree beside a properties table needs room. SwingScilabWindow defaults to
     * 500x500, which truncated every label in the tree -- including the
     * unmodelled-region reasons, which are the whole point of this read-only
     * view: an entry reading "code we do not model: hand..." tells the user
     * nothing about what the tool will refuse to touch.
     *
     * Clamped to the display rather than fixed, so it stays usable on a screen
     * smaller than the size we would prefer.
     */
    private static final int PREFERRED_WIDTH = 1100;
    private static final int PREFERRED_HEIGHT = 700;

    /** Initial width of the tree pane, which sets where the divider starts. */
    private static final int TREE_PANE_WIDTH = 380;

    private static Size defaultWindowSize() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(PREFERRED_WIDTH, (int) (screen.width * 0.8));
        int height = Math.min(PREFERRED_HEIGHT, (int) (screen.height * 0.8));
        return new Size(width, height);
    }

    private void display() {
        SwingScilabWindow window = SwingScilabWindow.createWindow(true);
        window.addTab(this);
        window.setDims(defaultWindowSize());
        window.setVisible(true);
    }

    private JPanel buildContentPane(JTable propertiesTable) {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(saveButton);

        JPanel propertiesPanel = new JPanel(new BorderLayout());
        propertiesPanel.add(new JLabel("Properties"), BorderLayout.NORTH);
        propertiesPanel.add(new JScrollPane(propertiesTable), BorderLayout.CENTER);

        JPanel regionsPanel = new JPanel(new BorderLayout());
        regionsPanel.add(new JLabel("Unmodelled regions"), BorderLayout.NORTH);
        regionsPanel.add(new JScrollPane(regionsTable), BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, propertiesPanel, regionsPanel);
        rightSplit.setResizeWeight(0.5);

        // A JSplitPane starts its divider at the left component's preferred width,
        // so this is what stops the tree opening too narrow to read.
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setPreferredSize(new Dimension(TREE_PANE_WIDTH, PREFERRED_HEIGHT));
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, rightSplit);
        mainSplit.setResizeWeight(0.35);
        mainSplit.setOneTouchExpandable(true);

        JPanel content = new JPanel(new BorderLayout());
        content.add(top, BorderLayout.NORTH);
        content.add(mainSplit, BorderLayout.CENTER);
        return content;
    }

    /**
     * Selecting a widget shows its properties; selecting a locked region
     * clears the properties table (a region has none) and highlights that
     * region in the list beside it, so the two panels stay in step with
     * whatever the tree has selected.
     */
    private void onTreeSelectionChanged(TreeSelectionEvent event) {
        TreePath selectedPath = event.getNewLeadSelectionPath();
        Object selected = selectedPath == null ? null : selectedPath.getLastPathComponent();
        if (selected instanceof Node) {
            propertiesModel.showProperties(((Node) selected).properties());
            regionsTable.clearSelection();
        } else if (selected instanceof UnmodelledRegion) {
            propertiesModel.showProperties(Collections.emptyMap());
            int index = design.unmodelled().indexOf(selected);
            if (index >= 0) {
                regionsTable.setRowSelectionInterval(index, index);
                regionsTable.scrollRectToVisible(regionsTable.getCellRect(index, 0, true));
            }
        } else {
            propertiesModel.showProperties(Collections.emptyMap());
            regionsTable.clearSelection();
        }
    }

    /**
     * Writes the design back out. Phase 1 never edits it -- there is no
     * editing UI yet -- so {@code document} carries no edits and
     * {@link DesignWriter#write} can only either return {@link
     * Design#source()} unchanged or refuse; either way the file on disk is
     * never left in a broken state.
     *
     * <p><b>Off the event thread.</b> The validation oracle is not a library
     * call: {@link Macr2TreeValidator} launches a whole packaged Scilab and
     * {@code waitFor}s it with a sixty-second timeout. Run from the button's
     * own listener that blocks the EDT for up to a minute -- no repaint, no
     * menu, no window close, the beach ball. So the work goes to a {@link
     * SwingWorker} and the Save control is disabled while it runs, which is
     * also what stops a second save being started on top of the first.
     *
     * <p>The worker returns the message to show rather than throwing, so
     * every outcome -- refusal, I/O failure, or an unexpected runtime
     * failure -- arrives at {@code done()} the same way and is reported from
     * the EDT. {@link RuntimeException} is caught explicitly: without it a
     * runtime failure would be swallowed into the worker's own
     * {@code ExecutionException} and Save would appear to do nothing at all.
     */
    private void onSave() {
        if (path == null || path.isEmpty()) {
            return;
        }
        saveButton.setEnabled(false);
        new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() {
                SourceValidator validator = new Macr2TreeValidator(scilabLauncher());
                try {
                    String rendered =
                        DesignWriter.write(design, new SourceDocument(design.source()), validator);
                    AtomicFileWriter.write(Paths.get(path), SourceFile.encode(rendered, charset));
                    return null;
                } catch (WriteRefusedException e) {
                    return e.getMessage();
                } catch (IOException e) {
                    return "could not write " + path + ": " + e.getMessage();
                } catch (RuntimeException e) {
                    return "could not save " + path + ": " + e;
                }
            }

            @Override
            protected void done() {
                saveButton.setEnabled(true);
                String failure;
                try {
                    failure = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure = "interrupted while saving " + path;
                } catch (ExecutionException e) {
                    // Only an Error can get here now that doInBackground
                    // catches RuntimeException; reported rather than lost.
                    failure = "could not save " + path + ": " + e.getCause();
                }
                if (failure != null) {
                    ScilabModalDialog.show(GuiDesignerTab.this, failure, APPLICATION, IconType.ERROR_ICON);
                }
            }
        }.execute();
    }

    /**
     * The launcher of the very Scilab this code is running inside of,
     * derived from {@link ScilabConstants#SCI} rather than any hardcoded
     * install location. {@code <SCI>/bin/scilab} is the standard entry
     * point documented at the top of that script itself, and resolves
     * correctly whether {@code SCI} names a packaged app's bundled copy or
     * an in-tree development build. Nothing here needs to pre-check that
     * the result is actually runnable: {@link Macr2TreeValidator} already
     * degrades to "cannot confirm" on its own -- see its class javadoc --
     * when it is handed a path that is not.
     */
    private static String scilabLauncher() {
        return new File(ScilabConstants.SCI, "bin/scilab").getPath();
    }

    private void registerClosingOperation() {
        ClosingOperationsManager.registerClosingOperation(this, new ClosingOperationsManager.ClosingOperation() {

            @Override
            public int canClose() {
                return 1;
            }

            @Override
            public void destroy() {
            }

            @Override
            public String askForClosing(final List<SwingScilabDockablePanel> list) {
                return null;
            }

            @Override
            public void updateDependencies(List<SwingScilabDockablePanel> list,
                                           ListIterator<SwingScilabDockablePanel> it) {
            }
        });
        ClosingOperationsManager.addDependencyWithRoot(this);
    }

    /** name / value / Locked / reason, in the widget's own source order. */
    private static final class PropertiesTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Property", "Value", "Locked", "Reason"};

        private List<String> names = new ArrayList<>();
        private List<PropertyValue> values = new ArrayList<>();

        void showProperties(Map<String, PropertyValue> properties) {
            names = new ArrayList<>(properties.keySet());
            values = new ArrayList<>(properties.values());
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return names.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PropertyValue value = values.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return names.get(rowIndex);
                case 1:
                    return formatValue(value);
                case 2:
                    return value.isLocked();
                case 3:
                    return value.isLocked() ? value.reason() : "";
                default:
                    throw new IllegalArgumentException("no column " + columnIndex);
            }
        }

        private static String formatValue(PropertyValue value) {
            if (value.isLocked()) {
                return value.sourceText();
            }
            Object v = value.value();
            if (v instanceof double[]) {
                return java.util.Arrays.toString((double[]) v);
            }
            return String.valueOf(v);
        }
    }

    /**
     * The unmodelled regions of one design, in SOURCE order -- {@link
     * Design#addUnmodelled} re-sorts by start offset on every insertion, so
     * the list reads top-of-file first regardless of the order the parser
     * happened to find them in.
     */
    private static final class RegionsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Code", "Reason"};
        private static final int PREVIEW_LIMIT = 60;

        private final Design design;

        RegionsTableModel(Design design) {
            this.design = design;
        }

        @Override
        public int getRowCount() {
            return design.unmodelled().size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            UnmodelledRegion region = design.unmodelled().get(rowIndex);
            return columnIndex == 0 ? preview(region) : region.reason();
        }

        private String preview(UnmodelledRegion region) {
            String text = design.source().substring(region.range().start(), region.range().end());
            String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
            return oneLine.length() > PREVIEW_LIMIT ? oneLine.substring(0, PREVIEW_LIMIT) + "..." : oneLine;
        }
    }

    /**
     * Labels a {@link Node} with its tag and style, and every unmodelled
     * region with its reason verbatim -- the reason is already written as a
     * sentence for a user to read (see {@link ScilabGuiParser}'s own
     * javadoc), so it needs no further formatting to serve as the tree
     * label itself. A locked node is marked and carries the same reasons in
     * its tooltip, for the rare case it has more than fits on one line.
     *
     * <p>Deliberately touches no colour: {@code super.getTreeCellRendererComponent}
     * already applies whatever the active Look and Feel provides, and
     * overriding it here would be the one mistake this class exists to
     * avoid repeating.
     */
    private static final class DesignTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof UnmodelledRegion) {
                String reason = ((UnmodelledRegion) value).reason();
                setText(reason);
                setToolTipText(reason);
            } else if (value instanceof Node) {
                Node node = (Node) value;
                String text = node.tag() + " (" + node.style().scilabName() + ")";
                List<String> reasons = lockedReasonsOf(node);
                if (!reasons.isEmpty()) {
                    text += " -- locked";
                    setToolTipText(String.join("; ", reasons));
                } else {
                    setToolTipText(null);
                }
                setText(text);
            }
            return this;
        }

        private static List<String> lockedReasonsOf(Node node) {
            List<String> reasons = new ArrayList<>();
            for (PropertyValue v : node.properties().values()) {
                if (v.isLocked()) {
                    reasons.add(v.reason());
                }
            }
            return reasons;
        }
    }
}
