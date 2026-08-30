package org.scilab.modules.guibuilder.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ScilabIdentifierTest {

    @Test
    public void ordinaryNamesAreValid() {
        assertTrue(ScilabIdentifier.isValid("okButton"));
        assertTrue(ScilabIdentifier.isValid("btn_2"));
        assertTrue(ScilabIdentifier.isValid("A"));
    }

    @Test
    public void namesThatWouldNotSurviveBecomingAStructFieldAreRejected() {
        assertFalse(ScilabIdentifier.isValid(""));
        assertFalse(ScilabIdentifier.isValid("2fast"));
        assertFalse(ScilabIdentifier.isValid("has space"));
        assertFalse(ScilabIdentifier.isValid("has-dash"));
        assertFalse(ScilabIdentifier.isValid(null));
    }

    @Test
    public void scilabKeywordsAreRejected() {
        // A tag becomes both a variable and a struct field in generated code,
        // so a keyword here produces a file that will not parse.
        assertFalse(ScilabIdentifier.isValid("function"));
        assertFalse(ScilabIdentifier.isValid("end"));
        assertFalse(ScilabIdentifier.isValid("select"));
    }

    @Test
    public void requireValidNamesTheOffendingValue() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> ScilabIdentifier.requireValid("has space"));
        assertTrue(e.getMessage().contains("has space"));
    }
}
