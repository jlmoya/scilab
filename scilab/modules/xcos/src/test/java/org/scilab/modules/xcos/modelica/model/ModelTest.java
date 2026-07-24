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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB data-binding class {@link Model}, the root of
 * the modelica tree, and all of its nested wrapper types. No native runtime is
 * required — these exercise only the generated POJO accessors.
 */
public class ModelTest {

    /**
     * Asserts the lazy-live-list contract shared by every generated list
     * accessor: never null, initially empty, and the identical instance is
     * returned on repeated calls (so there is deliberately no setter).
     */
    private static void assertLazyLiveList(List<?> first, List<?> second) {
        assertNotNull(first, "list accessor must never return null");
        assertTrue(first.isEmpty(), "list must start empty");
        assertSame(first, second, "accessor must return the same live list instance");
    }

    // ----- top-level Model -------------------------------------------------

    @Test
    public void newModelHasAllPropertiesNull() {
        Model model = new Model();

        assertNull(model.getName());
        assertNull(model.getModelInfo());
        assertNull(model.getIdentifiers());
        assertNull(model.getImplicitRelations());
        assertNull(model.getExplicitRelations());
        assertNull(model.getOutputs());
        assertNull(model.getElements());
        assertNull(model.getEquations());
        assertNull(model.getWhenClauses());
    }

    @Test
    public void nameRoundTrips() {
        Model model = new Model();

        model.setName("BouncingBall");

        assertEquals("BouncingBall", model.getName());
    }

    @Test
    public void modelInfoRoundTripsPreservingIdentity() {
        Model model = new Model();
        Info info = new Info();

        model.setModelInfo(info);

        assertSame(info, model.getModelInfo());
    }

    /**
     * Sets every complex property to a distinct instance and reads each one
     * back. Distinct instances guard against a getter/setter wired to the wrong
     * field (a copy/paste hazard in generated code).
     */
    @Test
    public void allComplexSettersRoundTripIndependently() {
        Model model = new Model();
        Info info = new Info();
        Model.Identifiers identifiers = new Model.Identifiers();
        Model.ImplicitRelations implicitRelations = new Model.ImplicitRelations();
        Model.ExplicitRelations explicitRelations = new Model.ExplicitRelations();
        Model.Outputs outputs = new Model.Outputs();
        Model.Elements elements = new Model.Elements();
        Model.Equations equations = new Model.Equations();
        Model.WhenClauses whenClauses = new Model.WhenClauses();

        model.setModelInfo(info);
        model.setIdentifiers(identifiers);
        model.setImplicitRelations(implicitRelations);
        model.setExplicitRelations(explicitRelations);
        model.setOutputs(outputs);
        model.setElements(elements);
        model.setEquations(equations);
        model.setWhenClauses(whenClauses);

        assertSame(info, model.getModelInfo());
        assertSame(identifiers, model.getIdentifiers());
        assertSame(implicitRelations, model.getImplicitRelations());
        assertSame(explicitRelations, model.getExplicitRelations());
        assertSame(outputs, model.getOutputs());
        assertSame(elements, model.getElements());
        assertSame(equations, model.getEquations());
        assertSame(whenClauses, model.getWhenClauses());
    }

    @Test
    public void settersAcceptNullClearingValues() {
        Model model = new Model();
        model.setName("m");
        model.setModelInfo(new Info());

        model.setName(null);
        model.setModelInfo(null);

        assertNull(model.getName());
        assertNull(model.getModelInfo());
    }

    // ----- Model.Elements --------------------------------------------------

    @Test
    public void elementsStructListIsLazyAndLive() {
        Model.Elements elements = new Model.Elements();

        assertLazyLiveList(elements.getStruct(), elements.getStruct());
    }

    @Test
    public void elementsStructListHoldsStructAndPersists() {
        Model.Elements elements = new Model.Elements();
        Struct struct = new Struct();

        elements.getStruct().add(struct);

        assertEquals(1, elements.getStruct().size());
        assertSame(struct, elements.getStruct().get(0));
    }

    // ----- Model.Equations -------------------------------------------------

    @Test
    public void equationsListIsLazyAndLive() {
        Model.Equations equations = new Model.Equations();

        assertLazyLiveList(equations.getEquation(), equations.getEquation());
    }

    @Test
    public void equationsListHoldsModelicaValueAndPersists() {
        Model.Equations equations = new Model.Equations();
        ModelicaValue eq = new ModelicaValue();
        eq.setValue("x = 0");

        equations.getEquation().add(eq);

        assertEquals(1, equations.getEquation().size());
        assertEquals("x = 0", equations.getEquation().get(0).getValue());
    }

    // ----- Model.WhenClauses -----------------------------------------------

    @Test
    public void whenClausesListIsLazyAndLive() {
        Model.WhenClauses whenClauses = new Model.WhenClauses();

        assertLazyLiveList(whenClauses.getWhenClause(), whenClauses.getWhenClause());
    }

    @Test
    public void whenClausesListHoldsModelicaValueAndPersists() {
        Model.WhenClauses whenClauses = new Model.WhenClauses();
        ModelicaValue clause = new ModelicaValue();

        whenClauses.getWhenClause().add(clause);

        assertEquals(1, whenClauses.getWhenClause().size());
        assertSame(clause, whenClauses.getWhenClause().get(0));
    }

    // ----- Model.Outputs ---------------------------------------------------

    @Test
    public void outputsListIsLazyAndLive() {
        Model.Outputs outputs = new Model.Outputs();

        assertLazyLiveList(outputs.getOutput(), outputs.getOutput());
    }

    @Test
    public void outputsListHoldsOutputAndPersists() {
        Model.Outputs outputs = new Model.Outputs();
        Output output = new Output();
        output.setName("y");

        outputs.getOutput().add(output);

        assertEquals(1, outputs.getOutput().size());
        assertEquals("y", outputs.getOutput().get(0).getName());
    }

    // ----- Model.ExplicitRelations (+ nested ExplicitRelation) --------------

    @Test
    public void explicitRelationsListIsLazyAndLive() {
        Model.ExplicitRelations explicitRelations = new Model.ExplicitRelations();

        assertLazyLiveList(explicitRelations.getExplicitRelation(),
                           explicitRelations.getExplicitRelation());
    }

    @Test
    public void explicitRelationVariableListIsLazyLiveAndHoldsStrings() {
        Model.ExplicitRelations.ExplicitRelation relation =
            new Model.ExplicitRelations.ExplicitRelation();

        assertLazyLiveList(relation.getExplicitVariable(), relation.getExplicitVariable());

        relation.getExplicitVariable().add("v1");
        relation.getExplicitVariable().add("v2");

        assertEquals(List.of("v1", "v2"), relation.getExplicitVariable());
    }

    @Test
    public void explicitRelationsNestingIsNavigable() {
        Model.ExplicitRelations explicitRelations = new Model.ExplicitRelations();
        Model.ExplicitRelations.ExplicitRelation relation =
            new Model.ExplicitRelations.ExplicitRelation();
        relation.getExplicitVariable().add("v1");
        explicitRelations.getExplicitRelation().add(relation);

        assertEquals(1, explicitRelations.getExplicitRelation().size());
        assertEquals("v1",
            explicitRelations.getExplicitRelation().get(0).getExplicitVariable().get(0));
    }

    // ----- Model.ImplicitRelations (+ nested ImplicitRelation) --------------

    @Test
    public void implicitRelationsListIsLazyAndLive() {
        Model.ImplicitRelations implicitRelations = new Model.ImplicitRelations();

        assertLazyLiveList(implicitRelations.getImplicitRelation(),
                           implicitRelations.getImplicitRelation());
    }

    @Test
    public void implicitRelationVariableOrInputListIsLazyLiveAndHoldsJaxbElements() {
        Model.ImplicitRelations.ImplicitRelation relation =
            new Model.ImplicitRelations.ImplicitRelation();

        assertLazyLiveList(relation.getImplicitVariableOrInput(),
                           relation.getImplicitVariableOrInput());

        JAXBElement<String> input =
            new JAXBElement<>(new QName("input"), String.class, "u1");
        relation.getImplicitVariableOrInput().add(input);

        assertEquals(1, relation.getImplicitVariableOrInput().size());
        assertEquals("u1", relation.getImplicitVariableOrInput().get(0).getValue());
        assertEquals("input", relation.getImplicitVariableOrInput().get(0).getName().getLocalPart());
    }

    // ----- Model.Identifiers -----------------------------------------------

    @Test
    public void identifiersListIsLazyAndLive() {
        Model.Identifiers identifiers = new Model.Identifiers();

        assertLazyLiveList(identifiers.getParameterOrExplicitVariableOrImplicitVariable(),
                           identifiers.getParameterOrExplicitVariableOrImplicitVariable());
    }

    @Test
    public void identifiersListHoldsHeterogeneousJaxbElementsInOrder() {
        Model.Identifiers identifiers = new Model.Identifiers();
        JAXBElement<String> parameter =
            new JAXBElement<>(new QName("parameter"), String.class, "k");
        JAXBElement<String> input =
            new JAXBElement<>(new QName("input"), String.class, "u");

        identifiers.getParameterOrExplicitVariableOrImplicitVariable().add(parameter);
        identifiers.getParameterOrExplicitVariableOrImplicitVariable().add(input);

        List<JAXBElement<String>> ids =
            identifiers.getParameterOrExplicitVariableOrImplicitVariable();
        assertEquals(2, ids.size());
        assertEquals("parameter", ids.get(0).getName().getLocalPart());
        assertEquals("k", ids.get(0).getValue());
        assertEquals("input", ids.get(1).getName().getLocalPart());
        assertEquals("u", ids.get(1).getValue());
    }
}
