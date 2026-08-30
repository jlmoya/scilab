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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One widget in a design. */
public class Node {

    private final String id = UUID.randomUUID().toString();
    private final String tag;
    private final WidgetStyle style;
    private final SourceRange sourceRange;
    private final Map<String, PropertyValue> properties = new LinkedHashMap<>();
    private Frame parent;

    public Node(String tag, WidgetStyle style, SourceRange sourceRange) {
        ScilabIdentifier.requireValid(tag);
        if (style == null) {
            throw new IllegalArgumentException("style must not be null");
        }
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
        this.tag = tag;
        this.style = style;
        this.sourceRange = sourceRange;
    }

    public String id() {
        return id;
    }

    public String tag() {
        return tag;
    }

    public WidgetStyle style() {
        return style;
    }

    public SourceRange sourceRange() {
        return sourceRange;
    }

    /** Insertion-ordered, so the tab and the writer see properties as the file has them. */
    public Map<String, PropertyValue> properties() {
        return Collections.unmodifiableMap(properties);
    }

    public void putProperty(String name, PropertyValue value) {
        properties.put(name, value);
    }

    public Frame parent() {
        return parent;
    }

    void setParent(Frame parent) {
        this.parent = parent;
    }

    /** True when any property could not be modelled. */
    public boolean isLocked() {
        for (PropertyValue v : properties.values()) {
            if (v.isLocked()) {
                return true;
            }
        }
        return false;
    }
}
