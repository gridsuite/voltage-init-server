/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.gridsuite.voltageinit.utils.assertions.Assertions.*;

import java.util.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.IdentifiableAttributes;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageLimitInfos;
import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.service.VoltageInitService;
import org.gridsuite.voltageinit.server.service.parameters.FilterService;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class VoltageInitParametersTest {

    private static final String URI_PARAMETERS_BASE = "/v1/parameters";

    private static final String URI_PARAMETERS_GET_PUT = URI_PARAMETERS_BASE + "/";

    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final String VARIANT_ID_1 = "variant_1";
    private static final UUID FILTER_UUID_1 = UUID.fromString("1a3d23a6-7a4c-11ee-b962-0242ac120002");
    private static final UUID FILTER_UUID_2 = UUID.fromString("f5c30082-7a4f-11ee-b962-0242ac120002");
    private static final String FILTER_1 = "FILTER_1";
    private static final String FILTER_2 = "FILTER_2";

    private Network network;

    @Autowired
    VoltageInitService voltageInitService;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    private VoltageInitParametersRepository parametersRepository;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private FilterService filterService;

    @Before
    public void setup() {
        network = EurostagTutorialExample1Factory.create(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID_1);
        network.getVariantManager().setWorkingVariant(VARIANT_ID_1);
        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        network.getVoltageLevel("VLGEN").setLowVoltageLimit(10.);
        network.getVoltageLevel("VLGEN").setHighVoltageLimit(20.);
        network.getVoltageLevel("VLHV1").setHighVoltageLimit(20.);
        network.getVoltageLevel("VLHV2").setLowVoltageLimit(10.);
        List<FilterEquipments> equipmentsList1 = new ArrayList<>();
        List<IdentifiableAttributes> identifiableAttributes = new ArrayList<>();
        identifiableAttributes.add(new IdentifiableAttributes("VLGEN", IdentifiableType.VOLTAGE_LEVEL, null));
        identifiableAttributes.add(new IdentifiableAttributes("VLHV1", IdentifiableType.VOLTAGE_LEVEL, null));
        identifiableAttributes.add(new IdentifiableAttributes("VLHV2", IdentifiableType.VOLTAGE_LEVEL, null));
        identifiableAttributes.add(new IdentifiableAttributes("VLLOAD", IdentifiableType.VOLTAGE_LEVEL, null));
        equipmentsList1.add(new FilterEquipments(FILTER_UUID_1, FILTER_1, identifiableAttributes, List.of()));

        List<FilterEquipments> equipmentsList2 = new ArrayList<>();
        List<IdentifiableAttributes> identifiableAttributes2 = new ArrayList<>();
        identifiableAttributes2.add(new IdentifiableAttributes("VLLOAD", IdentifiableType.VOLTAGE_LEVEL, null));
        equipmentsList2.add(new FilterEquipments(FILTER_UUID_2, FILTER_2, identifiableAttributes2, List.of()));
        given(filterService.exportFilters(List.of(FILTER_UUID_1), NETWORK_UUID, VARIANT_ID_1)).willReturn(equipmentsList1);
        given(filterService.exportFilters(List.of(FILTER_UUID_2), NETWORK_UUID, VARIANT_ID_1)).willReturn(equipmentsList2);

        parametersRepository.deleteAll();
    }

    @After
    public void tearOff() {
        parametersRepository.deleteAll();
    }

    @Test
    public void testCreate() throws Exception {

        VoltageInitParametersInfos parametersToCreate = buildParameters();
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);

        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        VoltageInitParametersInfos createdParameters = parametersRepository.findAll().get(0).toVoltageInitParametersInfos();

        assertThat(createdParameters).recursivelyEquals(parametersToCreate);
    }

    @Test
    public void testRead() throws Exception {

        VoltageInitParametersInfos parametersToRead = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToRead);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        VoltageInitParametersInfos receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedParameters).recursivelyEquals(parametersToRead);
    }

    @Test
    public void testUpdate() throws Exception {

        VoltageInitParametersInfos parametersToUpdate = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToUpdate);

        parametersToUpdate = buildParametersUpdate();

        String parametersToUpdateJson = mapper.writeValueAsString(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).content(parametersToUpdateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        VoltageInitParametersInfos updatedParameters = parametersRepository.findById(parametersUuid).get().toVoltageInitParametersInfos();

        assertThat(updatedParameters).recursivelyEquals(parametersToUpdate);
    }

    @Test
    public void testDelete() throws Exception {

        VoltageInitParametersInfos parametersToDelete = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();

        List<VoltageInitParametersEntity> storedParameters = parametersRepository.findAll();

        assertTrue(storedParameters.isEmpty());
    }

    @Test
    public void testGetAll() throws Exception {
        VoltageInitParametersInfos parameters1 = buildParameters();

        VoltageInitParametersInfos parameters2 = buildParametersUpdate();

        saveAndRetunId(parameters1);

        saveAndRetunId(parameters2);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_BASE))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<VoltageInitParametersInfos> receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedParameters).hasSize(2);
    }

    @Test
    public void testDuplicate() throws Exception {

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
    protected UUID saveAndRetunId(VoltageInitParametersInfos parametersInfos) {
        parametersRepository.save(parametersInfos.toEntity());
        return parametersRepository.findAll().get(0).getId();
    }

    protected VoltageInitParametersInfos buildParameters() {
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

    protected VoltageInitParametersInfos buildParametersUpdate() {
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

    @Test
    public void testBuildSpecificVoltageLimits() {
        VoltageLimitEntity voltageLimit = new VoltageLimitEntity(UUID.randomUUID(), 5., 10., 0, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        VoltageLimitEntity voltageLimit2 = new VoltageLimitEntity(UUID.randomUUID(), 44., 88., 1, VoltageLimitParameterType.DEFAULT, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_2, FILTER_2)));

        Optional<VoltageInitParametersEntity> voltageInitParameters = Optional.of(new VoltageInitParametersEntity(UUID.randomUUID(), null, "", List.of(voltageLimit, voltageLimit2), null, null, null));
        OpenReacParameters openReacParameters = voltageInitService.buildOpenReacParameters(voltageInitParameters, NETWORK_UUID, VARIANT_ID_1);

        //No override should be relative since there are no voltage limit modification
        assertThat(openReacParameters.getSpecificVoltageLimits().stream().allMatch(voltageLimitOverride -> !voltageLimitOverride.isRelative())).isTrue();

        assertEquals(4, openReacParameters.getSpecificVoltageLimits().size());
        //VLHV1, VLHV2 and VLLOAD should be applied default voltage limits since those are missing one or both limits
        assertThat(openReacParameters.getSpecificVoltageLimits().stream().anyMatch(voltageLimitOverride -> "VLHV1".equals(voltageLimitOverride.getVoltageLevelId()))).isTrue();
        assertEquals(1, openReacParameters.getSpecificVoltageLimits().stream().filter(voltageLimitOverride -> "VLHV1".equals(voltageLimitOverride.getVoltageLevelId()) && VoltageLimitOverride.VoltageLimitType.LOW_VOLTAGE_LIMIT.equals(voltageLimitOverride.getVoltageLimitType())).count());

        assertThat(openReacParameters.getSpecificVoltageLimits().stream().anyMatch(voltageLimitOverride -> "VLHV2".equals(voltageLimitOverride.getVoltageLevelId()))).isTrue();
        assertEquals(1, openReacParameters.getSpecificVoltageLimits().stream().filter(voltageLimitOverride -> "VLHV2".equals(voltageLimitOverride.getVoltageLevelId()) && VoltageLimitOverride.VoltageLimitType.HIGH_VOLTAGE_LIMIT.equals(voltageLimitOverride.getVoltageLimitType())).count());

        assertThat(openReacParameters.getSpecificVoltageLimits().stream().anyMatch(voltageLimitOverride -> "VLLOAD".equals(voltageLimitOverride.getVoltageLevelId()))).isTrue();
        assertEquals(1, openReacParameters.getSpecificVoltageLimits().stream().filter(voltageLimitOverride -> "VLLOAD".equals(voltageLimitOverride.getVoltageLevelId()) && VoltageLimitOverride.VoltageLimitType.LOW_VOLTAGE_LIMIT.equals(voltageLimitOverride.getVoltageLimitType())).count());
        assertEquals(1, openReacParameters.getSpecificVoltageLimits().stream().filter(voltageLimitOverride -> "VLLOAD".equals(voltageLimitOverride.getVoltageLevelId()) && VoltageLimitOverride.VoltageLimitType.HIGH_VOLTAGE_LIMIT.equals(voltageLimitOverride.getVoltageLimitType())).count());

        //The voltage limits attributed to VLLOAD should respectively be 44. and 88. since the priority related to VLLOAD's filter, FILTER_2, is higher than FILTER_1
        assertEquals(44., openReacParameters.getSpecificVoltageLimits().stream().filter(voltageLimitOverride -> "VLLOAD".equals(voltageLimitOverride.getVoltageLevelId()) && VoltageLimitOverride.VoltageLimitType.LOW_VOLTAGE_LIMIT.equals(voltageLimitOverride.getVoltageLimitType())).findAny().get().getLimit());
        assertEquals(88., openReacParameters.getSpecificVoltageLimits().stream().filter(voltageLimitOverride -> "VLLOAD".equals(voltageLimitOverride.getVoltageLevelId()) && VoltageLimitOverride.VoltageLimitType.HIGH_VOLTAGE_LIMIT.equals(voltageLimitOverride.getVoltageLimitType())).findAny().get().getLimit());

        //We now add limit modifications
        VoltageLimitEntity voltageLimit3 = new VoltageLimitEntity(UUID.randomUUID(), -1., -2., 0, VoltageLimitParameterType.MODIFICATION, List.of(new FilterEquipmentsEmbeddable(FILTER_UUID_1, FILTER_1)));
        voltageInitParameters = Optional.of(new VoltageInitParametersEntity(UUID.randomUUID(), null, "", List.of(voltageLimit, voltageLimit2, voltageLimit3), null, null, null));
        openReacParameters = voltageInitService.buildOpenReacParameters(voltageInitParameters, NETWORK_UUID, VARIANT_ID_1);

        //Limits that were set are now impacted
        assertEquals(8, openReacParameters.getSpecificVoltageLimits().size());
    }
}

