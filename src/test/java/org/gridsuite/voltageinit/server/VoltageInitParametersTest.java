/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageLimitInfos;
import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.service.VoltageInitRunContext;
import org.gridsuite.voltageinit.server.service.parameters.FilterService;
import org.gridsuite.voltageinit.server.service.parameters.VoltageInitParametersService;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;
import org.gridsuite.voltageinit.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.function.ThrowingBiFunction;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.condition.NestableCondition.nestable;
import static org.assertj.core.condition.VerboseCondition.verboseCondition;
import static org.gridsuite.voltageinit.utils.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@SpringBootTest
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@Transactional
@Slf4j
class VoltageInitParametersTest {
    private static final String URI_PARAMETERS_BASE = "/v1/parameters";
    private static final String URI_PARAMETERS_GET_PUT = URI_PARAMETERS_BASE + "/";

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private VoltageInitParametersRepository parametersRepository;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private FilterService filterService;

    @BeforeEach
    public void setup() {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

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

    @Test
    void testCreate() throws Exception {
        VoltageInitParametersInfos parametersToCreate = buildParameters();
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);

        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        VoltageInitParametersInfos createdParameters = parametersRepository.findAll().get(0).toVoltageInitParametersInfos();

        assertThat(createdParameters).recursivelyEquals(parametersToCreate);
    }

    @Test
    void testRead() throws Exception {
        VoltageInitParametersInfos parametersToRead = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToRead);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        VoltageInitParametersInfos receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedParameters).recursivelyEquals(parametersToRead);
    }

    @Test
    void testUpdate() throws Exception {
        VoltageInitParametersInfos parametersToUpdate = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToUpdate);

        parametersToUpdate = buildParametersUpdate();

        String parametersToUpdateJson = mapper.writeValueAsString(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).content(parametersToUpdateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        VoltageInitParametersInfos updatedParameters = parametersRepository.findById(parametersUuid).get().toVoltageInitParametersInfos();

        assertThat(updatedParameters).recursivelyEquals(parametersToUpdate);
    }

    @Test
    void testDelete() throws Exception {
        VoltageInitParametersInfos parametersToDelete = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();
        assertThat(parametersRepository.count()).as("parameters repository items count").isZero();
    }

    @Test
    void testGetAll() throws Exception {
        VoltageInitParametersInfos parameters1 = buildParameters();

        VoltageInitParametersInfos parameters2 = buildParametersUpdate();

        saveAndReturnId(parameters1);

        saveAndReturnId(parameters2);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_BASE))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<VoltageInitParametersInfos> receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedParameters).hasSize(2);
    }

    @Test
    void testDuplicate() throws Exception {
        VoltageInitParametersInfos parametersToCreate = buildParameters();
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);
        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        VoltageInitParametersInfos createdParameters = parametersRepository.findAll().get(0).toVoltageInitParametersInfos();

        mockMvc.perform(post(URI_PARAMETERS_BASE)
                        .param("duplicateFrom", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(URI_PARAMETERS_BASE)
                        .param("duplicateFrom", createdParameters.getUuid().toString()))
                .andExpect(status().isOk());

        VoltageInitParametersInfos duplicatedParameters = parametersRepository.findAll().get(1).toVoltageInitParametersInfos();
        assertThat(duplicatedParameters).recursivelyEquals(createdParameters);
    }

    /** Save parameters into the repository and return its UUID. */
    private UUID saveAndReturnId(VoltageInitParametersInfos parametersInfos) {
        return parametersRepository.save(parametersInfos.toEntity()).getId();
    }

    private static VoltageInitParametersInfos buildParameters() {
        return VoltageInitParametersInfos.builder()
            .voltageLimitsDefault(List.of())
            .voltageLimitsModification(List.of())
            .constantQGenerators(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("qgenFilter1")
                    .build(), FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("qgenFilter2")
                    .build()))
            .variableTwoWindingsTransformers(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter1")
                    .build(), FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter2")
                    .build()))
            .build();
    }

    private static VoltageInitParametersInfos buildParametersUpdate() {
        return VoltageInitParametersInfos.builder()
            .voltageLimitsModification(List.of(VoltageLimitInfos.builder()
                .priority(0)
                .lowVoltageLimit(2.0)
                .highVoltageLimit(20.0)
                .filters(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("filterName")
                    .build()))
                .build()))
            .voltageLimitsDefault(List.of(VoltageLimitInfos.builder()
                .priority(0)
                .lowVoltageLimit(2.0)
                .highVoltageLimit(20.0)
                .filters(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("filterName")
                    .build()))
                .build()))
            .variableShuntCompensators(List.of(FilterEquipments.builder()
                .filterId(UUID.randomUUID())
                .filterName("vscFilter1")
                .build()))
            .variableTwoWindingsTransformers(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter1Modified")
                    .build(), FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter2Modified")
                    .build()))
            .build();
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

    @TestFactory
    List<DynamicTest> dynamicTestsBuildSpecificVoltageLimits() {
        final ThrowingBiFunction<List<VoltageLimitEntity>, String, ListAssert<VoltageLimitOverride>> initTestEnv = (voltageLimits, reportFilename) -> {
            final VoltageInitParametersEntity voltageInitParameters = parametersRepository.save(
                new VoltageInitParametersEntity(UUID.randomUUID(), null, "", voltageLimits, null, null, null)
            );
            final VoltageInitRunContext context = new VoltageInitRunContext(NETWORK_UUID, VARIANT_ID_1, null, REPORT_UUID, null, "", "", voltageInitParameters.getId());
            final OpenReacParameters openReacParameters = voltageInitParametersService.buildOpenReacParameters(context, network);
            log.debug("openReac build parameters report: {}", mapper.writeValueAsString(context.getRootReporter()));
            JSONAssert.assertEquals("build parameters logs", TestUtils.resourceToString(reportFilename), mapper.writeValueAsString(context.getRootReporter()), REPORTER_COMPARATOR);
            return assertThat(openReacParameters.getSpecificVoltageLimits()).as("SpecificVoltageLimits");
        };
        final VoltageLimitEntity voltageLimit = new VoltageLimitEntity(UUID.randomUUID(), 5., 10., 0, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        final VoltageLimitEntity voltageLimit2 = new VoltageLimitEntity(UUID.randomUUID(), 44., 88., 1, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_2, FILTER_2)));
        final VoltageLimitEntity voltageLimit3 = new VoltageLimitEntity(UUID.randomUUID(), -1., -2., 0, VoltageLimitParameterType.MODIFICATION, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        final VoltageLimitEntity voltageLimit4 = new VoltageLimitEntity(UUID.randomUUID(), -20.0, 10.0, 0, VoltageLimitParameterType.MODIFICATION, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        final VoltageLimitEntity voltageLimit5 = new VoltageLimitEntity(UUID.randomUUID(), 10.0, 10.0, 0, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        return List.of(
            DynamicTest.dynamicTest("No voltage limit modification", () -> initTestEnv.apply(List.of(voltageLimit, voltageLimit2), "reporter_buildOpenReacParameters.json")
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
                )),
            //We now add limit modifications in additions to defaults settings
            DynamicTest.dynamicTest("With voltage limit modifications", () -> initTestEnv.apply(List.of(voltageLimit, voltageLimit2, voltageLimit3), "reporter_buildOpenReacParameters_withLimitModifications.json")
                //Limits that weren't impacted by default settings are now impacted by modification settings
                .hasSize(8)
                //There should (not?) be relative overrides since voltage limit modification are applied
                .anyMatch(VoltageLimitOverride::isRelative)
                //VLGEN has both it limits set so it should now be impacted by modifications override
                .satisfiesOnlyOnce(assertVoltageLimitOverride("VLGEN", VoltageLimitType.LOW_VOLTAGE_LIMIT, -1.))
                .satisfiesOnlyOnce(assertVoltageLimitOverride("VLGEN", VoltageLimitType.HIGH_VOLTAGE_LIMIT, -2.))
                //Because of the modification setting the voltage limits attributed to VLLOAD should now respectively be 43. and 86.
                .satisfiesOnlyOnce(assertVoltageLimitOverride("VLLOAD", VoltageLimitType.LOW_VOLTAGE_LIMIT, 43.))
                .satisfiesOnlyOnce(assertVoltageLimitOverride("VLLOAD", VoltageLimitType.HIGH_VOLTAGE_LIMIT, 86.))),
            //note: VoltageLimitOverride implement equals() correctly, so we can use it
            // We need to check for the case of relative = true with the modification less than 0 => the new low voltage limit = low voltage limit * -1
            DynamicTest.dynamicTest("Case relative true overrides", () -> initTestEnv.apply(List.of(voltageLimit4), "reporter_buildOpenReacParameters_caseRelativeTrue.json")
                .hasSize(4)
                // isRelative: There should have relative true overrides since voltage limit modification are applied for VLGEN
                // getLimit: The low voltage limit must be impacted by the modification of the value
                .containsOnlyOnce(new VoltageLimitOverride("VLGEN", VoltageLimitType.LOW_VOLTAGE_LIMIT, true, -10.0))),
            // We need to check for the case of relative = false with the modification less than 0 => the new low voltage limit = 0
            DynamicTest.dynamicTest("Case relative false overrides", () -> initTestEnv.apply(List.of(voltageLimit4, voltageLimit5), "reporter_buildOpenReacParameters_caseRelativeFalse.json")
                .hasSize(8)
                // isRelative: There should have relative false overrides since voltage limit modification are applied for VLHV1
                // getLimit: The low voltage limit must be impacted by the modification of the value
                .containsOnlyOnce(new VoltageLimitOverride("VLHV1", VoltageLimitType.LOW_VOLTAGE_LIMIT, false, 0.0)))
        );
    }
}
