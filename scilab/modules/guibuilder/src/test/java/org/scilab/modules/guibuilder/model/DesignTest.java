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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DesignTest {

    private static SourceRange at(int a, int b) {
        return new SourceRange(a, b);
    }

    private static Design emptyDesign() {
        return new Design("source text", new Frame("root", WidgetStyle.FRAME, at(0, 11)));
    }

    @Test
    public void addingAChildLinksItBothWays() {
        Design d = emptyDesign();
        Node n = new Node("okButton", WidgetStyle.PUSHBUTTON, at(0, 5));
        d.add(d.root(), n);
        assertTrue(d.root().children().contains(n));
        assertSame(d.root(), n.parent());
        assertSame(n, d.byTag("okButton"));
    }

    @Test
    public void duplicateTagsAreRejected() {
        Design d = emptyDesign();
        d.add(d.root(), new Node("okButton", WidgetStyle.PUSHBUTTON, at(0, 5)));
        assertThrows(IllegalArgumentException.class,
                     () -> d.add(d.root(), new Node("okButton", WidgetStyle.EDIT, at(6, 9))));
    }

    @Test
    public void invalidTagsAreRejectedAtTheModelBoundary() {
        Design d = emptyDesign();
        assertThrows(IllegalArgumentException.class,
                     () -> d.add(d.root(), new Node("has space", WidgetStyle.EDIT, at(0, 5))));
    }

    @Test
    public void unmodelledRegionsAreKeptInSourceOrder() {
        // The tab lists them, and the writer walks them to detect collisions,
        // so an unordered list would make both jobs harder than they need to be.
        Design d = emptyDesign();
        d.addUnmodelled(new UnmodelledRegion(at(50, 60), "loop creates controls"));
        d.addUnmodelled(new UnmodelledRegion(at(10, 20), "unrecognised call"));
        assertEquals(10, d.unmodelled().get(0).range().start());
        assertEquals(50, d.unmodelled().get(1).range().start());
    }

    @Test
    public void allNodesWalksTheWholeTree() {
        Design d = emptyDesign();
        Frame panel = new Frame("panel", WidgetStyle.FRAME, at(0, 5));
        d.add(d.root(), panel);
        d.add(panel, new Node("inner", WidgetStyle.TEXT, at(6, 9)));
        assertEquals(2, d.allNodes().size());
    }
}
