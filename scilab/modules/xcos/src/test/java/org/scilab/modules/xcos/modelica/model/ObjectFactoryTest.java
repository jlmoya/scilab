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

package org.scilab.modules.xcos.modelica.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB {@link ObjectFactory} of the Modelica model
 * package.
 *
 * <p>The factory has two kinds of method. The plain {@code createXxx()} builders
 * just {@code new} a schema-derived bean, so the tests assert they hand back a
 * non-null, freshly-allocated instance each call (never a shared singleton). The
 * {@code @XmlElementDecl} builders wrap a value in a {@link JAXBElement}; those
 * tests pin the element wiring the marshaller relies on: the QName
 * (namespace + local part), the declared type, the declaration scope
 * (global vs. a specific {@code @XmlType} owner) and value pass-through.
 */
public class ObjectFactoryTest {

    private final ObjectFactory factory = new ObjectFactory();

    @Test
    public void constructorDoesNotThrow() {
        assertNotNull(new ObjectFactory());
    }

    // ---- plain createXxx() builders: non-null and always a fresh instance ----

    @Test
    public void createModelReturnsFreshModel() {
        Model a = factory.createModel();
        Model b = factory.createModel();
        assertNotNull(a);
        assertNotNull(b);
        assertNotSame(a, b);
    }

    @Test
    public void createModelElementsReturnsFreshInstance() {
        assertNotNull(factory.createModelElements());
        assertNotSame(factory.createModelElements(), factory.createModelElements());
    }

    @Test
    public void createModelWhenClausesReturnsFreshInstance() {
        assertNotNull(factory.createModelWhenClauses());
        assertNotSame(factory.createModelWhenClauses(), factory.createModelWhenClauses());
    }

    @Test
    public void createModelicaValueReturnsFreshInstanceWithEmptyDefault() {
        ModelicaValue v = factory.createModelicaValue();
        assertNotNull(v);
        // ModelicaValue defaults its required attribute to "" (non-null for
        // xml2modelica); the factory must not alter that.
        assertEquals("", v.getValue());
        assertNotSame(factory.createModelicaValue(), factory.createModelicaValue());
    }

    @Test
    public void createStructSubnodesReturnsFreshInstance() {
        assertNotNull(factory.createStructSubnodes());
        assertNotSame(factory.createStructSubnodes(), factory.createStructSubnodes());
    }

    @Test
    public void createTerminalReturnsFreshInstance() {
        Terminal t = factory.createTerminal();
        assertNotNull(t);
        // a freshly-built terminal carries no data
        assertNull(t.getName());
        assertNotSame(factory.createTerminal(), factory.createTerminal());
    }

    @Test
    public void createOutputDependenciesReturnsFreshInstance() {
        assertNotNull(factory.createOutputDependencies());
        assertNotSame(factory.createOutputDependencies(), factory.createOutputDependencies());
    }

    @Test
    public void createInfoReturnsFreshInstance() {
        assertNotNull(factory.createInfo());
        assertNotSame(factory.createInfo(), factory.createInfo());
    }

    @Test
    public void createTerminalOutputReturnsFreshInstance() {
        Terminal.Output out = factory.createTerminalOutput();
        assertNotNull(out);
        assertNotSame(factory.createTerminalOutput(), factory.createTerminalOutput());
    }

    @Test
    public void createOutputReturnsFreshInstance() {
        assertNotNull(factory.createOutput());
        assertNotSame(factory.createOutput(), factory.createOutput());
    }

    @Test
    public void createModelOutputsReturnsFreshInstance() {
        assertNotNull(factory.createModelOutputs());
        assertNotSame(factory.createModelOutputs(), factory.createModelOutputs());
    }

    @Test
    public void createModelExplicitRelationsReturnsFreshInstance() {
        assertNotNull(factory.createModelExplicitRelations());
        assertNotSame(factory.createModelExplicitRelations(), factory.createModelExplicitRelations());
    }

    @Test
    public void createModelEquationsReturnsFreshInstance() {
        assertNotNull(factory.createModelEquations());
        assertNotSame(factory.createModelEquations(), factory.createModelEquations());
    }

    @Test
    public void createModelImplicitRelationsReturnsFreshInstance() {
        assertNotNull(factory.createModelImplicitRelations());
        assertNotSame(factory.createModelImplicitRelations(), factory.createModelImplicitRelations());
    }

    @Test
    public void createModelIdentifiersReturnsFreshInstance() {
        assertNotNull(factory.createModelIdentifiers());
        assertNotSame(factory.createModelIdentifiers(), factory.createModelIdentifiers());
    }

    @Test
    public void createModelExplicitRelationsExplicitRelationReturnsFreshInstance() {
        assertNotNull(factory.createModelExplicitRelationsExplicitRelation());
        assertNotSame(factory.createModelExplicitRelationsExplicitRelation(),
                      factory.createModelExplicitRelationsExplicitRelation());
    }

    @Test
    public void createStructReturnsFreshInstance() {
        assertNotNull(factory.createStruct());
        assertNotSame(factory.createStruct(), factory.createStruct());
    }

    @Test
    public void createModelImplicitRelationsImplicitRelationReturnsFreshInstance() {
        assertNotNull(factory.createModelImplicitRelationsImplicitRelation());
        assertNotSame(factory.createModelImplicitRelationsImplicitRelation(),
                      factory.createModelImplicitRelationsImplicitRelation());
    }

    // ---- @XmlElementDecl builders: JAXBElement wiring ----

    @Test
    public void createModelWrapsValueInGloballyScopedElement() {
        Model value = new Model();
        JAXBElement<Model> element = factory.createModel(value);

        assertNotNull(element);
        assertEquals("", element.getName().getNamespaceURI());
        assertEquals("model", element.getName().getLocalPart());
        assertEquals(Model.class, element.getDeclaredType());
        assertTrue(element.isGlobalScope(), "createModel(Model) declares a global element");
        assertSame(value, element.getValue());
        assertFalse(element.isNil());
    }

    @Test
    public void createModelToleratesANullValue() {
        // The JAXBElement contract rejects a null name/type but permits a null
        // value; the factory forwards it untouched. Per JAXBElement.isNil()
        // (return (value == null) || nil), a null value reports nil == true even
        // though setNil(true) was never called.
        JAXBElement<Model> element = factory.createModel((Model) null);

        assertNotNull(element);
        assertEquals("model", element.getName().getLocalPart());
        assertNull(element.getValue());
        assertTrue(element.isNil());
    }

    @Test
    public void createModelIdentifiersInputIsScopedToIdentifiers() {
        JAXBElement<String> element = factory.createModelIdentifiersInput("u1");

        assertQName("", "input", element.getName());
        assertEquals(String.class, element.getDeclaredType());
        assertFalse(element.isGlobalScope());
        assertEquals(Model.Identifiers.class, element.getScope());
        assertSame("u1", element.getValue());
    }

    @Test
    public void createModelIdentifiersExplicitVariableIsScopedToIdentifiers() {
        JAXBElement<String> element = factory.createModelIdentifiersExplicitVariable("x");

        assertQName("", "explicit_variable", element.getName());
        assertEquals(String.class, element.getDeclaredType());
        assertEquals(Model.Identifiers.class, element.getScope());
        assertSame("x", element.getValue());
    }

    @Test
    public void createModelIdentifiersParameterIsScopedToIdentifiers() {
        JAXBElement<String> element = factory.createModelIdentifiersParameter("p");

        assertQName("", "parameter", element.getName());
        assertEquals(String.class, element.getDeclaredType());
        assertEquals(Model.Identifiers.class, element.getScope());
        assertSame("p", element.getValue());
    }

    @Test
    public void createModelIdentifiersImplicitVariableIsScopedToIdentifiers() {
        JAXBElement<String> element = factory.createModelIdentifiersImplicitVariable("z");

        assertQName("", "implicit_variable", element.getName());
        assertEquals(String.class, element.getDeclaredType());
        assertEquals(Model.Identifiers.class, element.getScope());
        assertSame("z", element.getValue());
    }

    @Test
    public void createModelImplicitRelationsImplicitRelationInputIsScopedToImplicitRelation() {
        JAXBElement<String> element =
            factory.createModelImplicitRelationsImplicitRelationInput("u2");

        assertQName("", "input", element.getName());
        assertEquals(String.class, element.getDeclaredType());
        assertEquals(Model.ImplicitRelations.ImplicitRelation.class, element.getScope());
        assertSame("u2", element.getValue());
    }

    @Test
    public void createModelImplicitRelationsImplicitRelationImplicitVariableIsScopedToImplicitRelation() {
        JAXBElement<String> element =
            factory.createModelImplicitRelationsImplicitRelationImplicitVariable("w");

        assertQName("", "implicit_variable", element.getName());
        assertEquals(String.class, element.getDeclaredType());
        assertEquals(Model.ImplicitRelations.ImplicitRelation.class, element.getScope());
        assertSame("w", element.getValue());
    }

    @Test
    public void inputElementSharesLocalPartButNotScopeAcrossTheTwoDeclarations() {
        // "input" is declared under two different @XmlType scopes; the local
        // part matches but the scope must differ so the marshaller keeps them apart.
        JAXBElement<String> identifiersInput = factory.createModelIdentifiersInput("a");
        JAXBElement<String> relationInput =
            factory.createModelImplicitRelationsImplicitRelationInput("b");

        assertEquals(identifiersInput.getName().getLocalPart(),
                     relationInput.getName().getLocalPart());
        assertNotSame(identifiersInput.getScope(), relationInput.getScope());
        assertEquals(Model.Identifiers.class, identifiersInput.getScope());
        assertEquals(Model.ImplicitRelations.ImplicitRelation.class, relationInput.getScope());
    }

    private static void assertQName(String namespace, String localPart, QName actual) {
        assertEquals(namespace, actual.getNamespaceURI());
        assertEquals(localPart, actual.getLocalPart());
    }
}
