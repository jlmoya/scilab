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

package org.scilab.modules.guibuilder.model;

/** The uicontrol styles Scilab supports, plus axes. */
public enum WidgetStyle {

    PUSHBUTTON("pushbutton"),
    EDIT("edit"),
    TEXT("text"),
    CHECKBOX("checkbox"),
    RADIOBUTTON("radiobutton"),
    LISTBOX("listbox"),
    POPUPMENU("popupmenu"),
    SLIDER("slider"),
    SPINNER("spinner"),
    TABLE("table"),
    IMAGE("image"),
    FRAME("frame"),
    AXES("axes");

    private final String scilabName;

    WidgetStyle(String scilabName) {
        this.scilabName = scilabName;
    }

    public String scilabName() {
        return scilabName;
    }

    /** The style with this Scilab spelling, or null when it is not one we model. */
    public static WidgetStyle fromScilab(String name) {
        if (name == null) {
            return null;
        }
        for (WidgetStyle s : values()) {
            if (s.scilabName.equals(name)) {
                return s;
            }
        }
        return null;
    }
}
