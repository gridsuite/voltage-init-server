/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride.VoltageLimitType;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Condition;
import org.assertj.core.api.ListAssert;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.IdentifiableAttributes;
import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.service.VoltageInitRunContext;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;
import org.gridsuite.voltageinit.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.condition.NestableCondition.nestable;
import static org.assertj.core.condition.VerboseCondition.verboseCondition;
import static org.mockito.BDDMockito.given;

@ExtendWith({ MockitoExtension.class })
@SpringBootTest
@DirtiesContext
@AutoConfigureTestEntityManager
@Transactional(propagation = Propagation.REQUIRES_NEW)
@Slf4j
class ParametersTest {
    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final String VARIANT_ID_1 = "variant_1";
    private static final UUID FILTER_UUID_1 = UUID.fromString("1a3d23a6-7a4c-11ee-b962-0242ac120002");
    private static final UUID FILTER_UUID_2 = UUID.fromString("f5c30082-7a4f-11ee-b962-0242ac120002");
    private static final String FILTER_1 = "FILTER_1";
    private static final String FILTER_2 = "FILTER_2";
    private static final UUID REPORT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final JSONComparator REPORTER_COMPARATOR = new CustomComparator(JSONCompareMode.STRICT,
        // ignore field having uuid changing each run
        new Customization("reportTree.subReporters[*].taskValues.parameters_id.value", (o1, o2) -> (o1 == null) == (o2 == null))
    );

    private Network network;

    @Autowired
    private VoltageInitParametersService voltageInitParametersService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private FilterService filterService;

    @BeforeEach
    public void setup() {
        network = EurostagTutorialExample1Factory.create(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID_1);
        network.getVariantManager().setWorkingVariant(VARIANT_ID_1);
        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);

        network.getVoltageLevel("VLGEN").setLowVoltageLimit(10.);
        network.getVoltageLevel("VLGEN").setHighVoltageLimit(20.);
        network.getVoltageLevel("VLHV1").setHighVoltageLimit(20.);
        network.getVoltageLevel("VLHV2").setLowVoltageLimit(10.);
        given(filterService.exportFilters(List.of(FILTER_UUID_1), NETWORK_UUID, VARIANT_ID_1)).willReturn(List.of(
            new FilterEquipments(FILTER_UUID_1, FILTER_1, List.of(
                new IdentifiableAttributes("VLGEN", IdentifiableType.VOLTAGE_LEVEL, null),
                new IdentifiableAttributes("VLHV1", IdentifiableType.VOLTAGE_LEVEL, null),
                new IdentifiableAttributes("VLHV2", IdentifiableType.VOLTAGE_LEVEL, null),
                new IdentifiableAttributes("VLLOAD", IdentifiableType.VOLTAGE_LEVEL, null)
            ), List.of())
        ));
        given(filterService.exportFilters(List.of(FILTER_UUID_2), NETWORK_UUID, VARIANT_ID_1)).willReturn(List.of(
            new FilterEquipments(FILTER_UUID_2, FILTER_2, List.of(new IdentifiableAttributes("VLLOAD", IdentifiableType.VOLTAGE_LEVEL, null)), List.of())
        ));
    }

    private static Consumer<VoltageLimitOverride> assertVoltageLimitOverride(final String levelId, final VoltageLimitType limitType) {
        return assertVoltageLimitOverride(levelId, limitType, null);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<VoltageLimitOverride> assertVoltageLimitOverride(final String levelId, final VoltageLimitType limitType, final Double limit) {
        return voltageLimitOverride -> assertThat(voltageLimitOverride).as("voltageLimit override")
            .is(nestable("VoltageLimitOverride", Stream.<Condition<VoltageLimitOverride>>of(
            verboseCondition(actual -> levelId.equals(actual.getVoltageLevelId()),
                "to have voltageLevelId=\"" + Objects.toString(levelId, "<null>") + "\"",
                actual -> " but is actually \"" + Objects.toString(actual.getVoltageLevelId(), "<null>") + "\""),
            verboseCondition(actual -> limitType.equals(actual.getVoltageLimitType()),
                "to have voltageLimitType=\"" + Objects.toString(limitType, "<null>") + "\"",
                actual -> " but is actually \"" + Objects.toString(actual.getVoltageLimitType(), "<null>") + "\""),
            limit == null ? null : verboseCondition(actual -> limit.equals(actual.getLimit()),
                "to have limit=" + limit, actual -> " but is actually " + actual.getLimit())
        ).filter(Objects::nonNull).toArray(Condition[]::new)));
    }

    private ListAssert<VoltageLimitOverride> testsBuildSpecificVoltageLimitsCommon(List<VoltageLimitEntity> voltageLimits, String reportFilename) throws Exception {
        final VoltageInitParametersEntity voltageInitParameters = entityManager.persistFlushFind(
            new VoltageInitParametersEntity(null, null, "", voltageLimits, null, null, null)
        );
        final VoltageInitRunContext context = new VoltageInitRunContext(NETWORK_UUID, VARIANT_ID_1, null, REPORT_UUID, null, "", "", voltageInitParameters.getId());
        final OpenReacParameters openReacParameters = voltageInitParametersService.buildOpenReacParameters(context, network);
        log.debug("openReac build parameters report: {}", mapper.writeValueAsString(context.getRootReporter()));
        JSONAssert.assertEquals("build parameters logs", TestUtils.resourceToString(reportFilename), mapper.writeValueAsString(context.getRootReporter()), REPORTER_COMPARATOR);
        return assertThat(openReacParameters.getSpecificVoltageLimits()).as("SpecificVoltageLimits");
    }

    @DisplayName("buildSpecificVoltageLimits: No voltage limit modification")
    @Test
    void testsBuildSpecificVoltageLimitsSimple() throws Exception {
        final VoltageLimitEntity voltageLimit = new VoltageLimitEntity(null, 5., 10., 0, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        final VoltageLimitEntity voltageLimit2 = new VoltageLimitEntity(null, 44., 88., 1, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_2, FILTER_2)));
        testsBuildSpecificVoltageLimitsCommon(List.of(voltageLimit, voltageLimit2), "reporter_buildOpenReacParameters.json")
            .hasSize(4)
            //No override should be relative since there is no voltage limit modification
            .noneMatch(VoltageLimitOverride::isRelative)
            //VLHV1, VLHV2 and VLLOAD should be applied default voltage limits since those are missing one or both limits
            .satisfiesExactlyInAnyOrder(
                assertVoltageLimitOverride("VLHV1", VoltageLimitType.LOW_VOLTAGE_LIMIT),
                assertVoltageLimitOverride("VLHV2", VoltageLimitType.HIGH_VOLTAGE_LIMIT),
                //The voltage limits attributed to VLLOAD should respectively be 44. and 88. since the priority of FILTER_2, related to VLLOAD, is higher than FILTER_1
                assertVoltageLimitOverride("VLLOAD", VoltageLimitType.LOW_VOLTAGE_LIMIT, 44.),
                assertVoltageLimitOverride("VLLOAD", VoltageLimitType.HIGH_VOLTAGE_LIMIT, 88.)
            );
    }

    @DisplayName("buildSpecificVoltageLimits: With voltage limit modifications")
    @Test
    void testsBuildSpecificVoltageLimitsWithLimitModifications() throws Exception {
        final VoltageLimitEntity voltageLimit = new VoltageLimitEntity(null, 5., 10., 0, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        final VoltageLimitEntity voltageLimit2 = new VoltageLimitEntity(null, 44., 88., 1, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_2, FILTER_2)));
        final VoltageLimitEntity voltageLimit3 = new VoltageLimitEntity(null, -1., -2., 0, VoltageLimitParameterType.MODIFICATION, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        //We now add limit modifications in additions to defaults settings
        testsBuildSpecificVoltageLimitsCommon(List.of(voltageLimit, voltageLimit2, voltageLimit3), "reporter_buildOpenReacParameters_withLimitModifications.json")
            //Limits that weren't impacted by default settings are now impacted by modification settings
            .hasSize(8)
            //There should (not?) be relative overrides since voltage limit modification are applied
            .anyMatch(VoltageLimitOverride::isRelative)
            //VLGEN has both it limits set so it should now be impacted by modifications override
            .satisfiesOnlyOnce(assertVoltageLimitOverride("VLGEN", VoltageLimitType.LOW_VOLTAGE_LIMIT, -1.))
            .satisfiesOnlyOnce(assertVoltageLimitOverride("VLGEN", VoltageLimitType.HIGH_VOLTAGE_LIMIT, -2.))
            //Because of the modification setting the voltage limits attributed to VLLOAD should now respectively be 43. and 86.
            .satisfiesOnlyOnce(assertVoltageLimitOverride("VLLOAD", VoltageLimitType.LOW_VOLTAGE_LIMIT, 43.))
            .satisfiesOnlyOnce(assertVoltageLimitOverride("VLLOAD", VoltageLimitType.HIGH_VOLTAGE_LIMIT, 86.));
    }

    @DisplayName("buildSpecificVoltageLimits: Case relative true overrides")
    @Test
    void testsBuildSpecificVoltageLimitsCaseRelativeTrue() throws Exception {
        final VoltageLimitEntity voltageLimit4 = new VoltageLimitEntity(null, -20.0, 10.0, 0, VoltageLimitParameterType.MODIFICATION, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        // We need to check for the case of relative = true with the modification less than 0 => the new low voltage limit = low voltage limit * -1
        testsBuildSpecificVoltageLimitsCommon(List.of(voltageLimit4), "reporter_buildOpenReacParameters_caseRelativeTrue.json")
            .hasSize(4)
            // isRelative: There should have relative true overrides since voltage limit modification are applied for VLGEN
            // getLimit: The low voltage limit must be impacted by the modification of the value
            .containsOnlyOnce(new VoltageLimitOverride("VLGEN", VoltageLimitType.LOW_VOLTAGE_LIMIT, true, -10.0));
        //note: VoltageLimitOverride implement equals() correctly, so we can use it
    }

    @DisplayName("buildSpecificVoltageLimits: Case relative false overrides")
    @Test
    void testsBuildSpecificVoltageLimitsCaseRelativeFalse() throws Exception {
        final VoltageLimitEntity voltageLimit4 = new VoltageLimitEntity(null, -20.0, 10.0, 0, VoltageLimitParameterType.MODIFICATION, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        final VoltageLimitEntity voltageLimit5 = new VoltageLimitEntity(null, 10.0, 10.0, 0, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        // We need to check for the case of relative = false with the modification less than 0 => the new low voltage limit = 0
        testsBuildSpecificVoltageLimitsCommon(List.of(voltageLimit4, voltageLimit5), "reporter_buildOpenReacParameters_caseRelativeFalse.json")
            .hasSize(8)
            // isRelative: There should have relative false overrides since voltage limit modification are applied for VLHV1
            // getLimit: The low voltage limit must be impacted by the modification of the value
            .containsOnlyOnce(new VoltageLimitOverride("VLHV1", VoltageLimitType.LOW_VOLTAGE_LIMIT, false, 0.0));
    }
}