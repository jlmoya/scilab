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

import org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel;
import org.scilab.modules.gui.tabfactory.AbstractScilabTabFactory;
import org.scilab.modules.gui.tabfactory.ScilabTabFactory;

/**
 * Registers the guidesigner tab with {@link ScilabTabFactory}, mirroring
 * {@code org.scilab.modules.scinotes.tabfactory.CodeNavigatorTabFactory} --
 * the smallest existing example of this pattern.
 *
 * <p>Restoring a tab from nothing but its uuid is not supported yet:
 * {@code CodeNavigatorTabFactory} can do it because {@code SciNotes} keeps a
 * {@code ConfigSciNotesManager} record mapping a navigator's uuid back to the
 * editor it belongs to; this module persists no such record of which file a
 * tab had open. {@link #getTab(String)} therefore always returns null and
 * {@link #isAValidUUID(String)} always returns false, which leaves a
 * guidesigner tab still open when a session ends coming back as an empty
 * tab on the next restore -- the same fallback {@code ScilabTabFactory}
 * already gives any uuid that no registered factory claims -- rather than
 * failing to restore at all. Registering this factory regardless keeps the
 * class discoverable by name and package, and gives a future phase that does
 * persist enough state somewhere to implement real restoration.
 */
public class GuiDesignerTabFactory extends AbstractScilabTabFactory {

    public static final String APPLICATION = "GuiDesigner";
    public static final String PACKAGE = "GuiDesigner";
    public static final String CLASS = "org.scilab.modules.guibuilder.ui.GuiDesignerTabFactory";

    private static GuiDesignerTabFactory instance;

    /**
     * Default constructor.
     */
    public GuiDesignerTabFactory() {
        if (instance == null) {
            instance = this;
        }
    }

    /**
     * {@inheritDoc}
     */
    public SwingScilabDockablePanel getTab(String uuid) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String getPackage() {
        return PACKAGE;
    }

    /**
     * {@inheritDoc}
     */
    public String getClassName() {
        return CLASS;
    }

    /**
     * {@inheritDoc}
     */
    public String getApplication() {
        return APPLICATION;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAValidUUID(String uuid) {
        return false;
    }

    /**
     * @return an instance of this factory
     */
    public static GuiDesignerTabFactory getInstance() {
        new GuiDesignerTabFactory();

        return instance;
    }
}
