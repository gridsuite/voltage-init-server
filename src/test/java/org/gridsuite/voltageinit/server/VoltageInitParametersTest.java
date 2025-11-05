/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageLimitInfos;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.service.parameters.FilterService;
import org.gridsuite.voltageinit.server.util.EquipmentsSelectionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.voltageinit.server.service.parameters.VoltageInitParametersService.DEFAULT_REACTIVE_SLACKS_THRESHOLD;
import static org.gridsuite.voltageinit.utils.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VoltageInitParametersTest {

    private static final String URI_PARAMETERS_BASE = "/v1/parameters";

    private static final String URI_PARAMETERS_GET_PUT = URI_PARAMETERS_BASE + "/";

    private static final String DUPLICATE_FROM_PARAMETER = "duplicateFrom";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private VoltageInitParametersRepository parametersRepository;

    @MockitoBean
    private FilterService filterService;

    @BeforeEach
    void setUpFilterMock() {
        when(filterService.getFiltersExistence(anySet()))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Set<UUID> ids = (Set<UUID>) inv.getArgument(0);
                return ids.stream().collect(Collectors.toMap(id -> id, id -> true));
            });
    }

    @AfterEach
    void cleanDB() {
        parametersRepository.deleteAll();
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
    void testCreateDefault() throws Exception {
        mockMvc.perform(post(URI_PARAMETERS_BASE)).andExpect(status().isOk()).andReturn();
        VoltageInitParametersInfos createdParameters = parametersRepository.findAll().get(0).toVoltageInitParametersInfos();
        assertNotNull(createdParameters);
        assertTrue(createdParameters.getVoltageLimitsDefault().isEmpty());
        assertNull(createdParameters.getVariableShuntCompensators());
        assertEquals(EquipmentsSelectionType.NONE_EXCEPT, createdParameters.getShuntCompensatorsSelectionType());
        assertNull(createdParameters.getVariableTwoWindingsTransformers());
        assertEquals(EquipmentsSelectionType.NONE_EXCEPT, createdParameters.getTwoWindingsTransformersSelectionType());
        assertNull(createdParameters.getVariableQGenerators());
        assertEquals(EquipmentsSelectionType.ALL_EXCEPT, createdParameters.getGeneratorsSelectionType());
        assertTrue(createdParameters.getVoltageLimitsModification().isEmpty());
        assertEquals(DEFAULT_REACTIVE_SLACKS_THRESHOLD, createdParameters.getReactiveSlacksThreshold());
        assertEquals(0., createdParameters.getShuntCompensatorActivationThreshold());
        assertFalse(createdParameters.isUpdateBusVoltage());
    }

    @Test
    void testRead() throws Exception {

        VoltageInitParametersInfos parametersToRead = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToRead);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        VoltageInitParametersInfos receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(receivedParameters).recursivelyEquals(parametersToRead);
    }

    @Test
    void testUpdate() throws Exception {

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
    void testDelete() throws Exception {

        VoltageInitParametersInfos parametersToDelete = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();

        List<VoltageInitParametersEntity> storedParameters = parametersRepository.findAll();

        assertTrue(storedParameters.isEmpty());
    }

    @Test
    void testGetAll() throws Exception {
        VoltageInitParametersInfos parameters1 = buildParameters();

        VoltageInitParametersInfos parameters2 = buildParametersUpdate();

        saveAndRetunId(parameters1);

        saveAndRetunId(parameters2);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_BASE))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<VoltageInitParametersInfos> receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(receivedParameters).hasSize(2);
    }

    @Test
    void testDuplicate() throws Exception {

        VoltageInitParametersInfos parametersToCreate = buildParameters();
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);
        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();
        VoltageInitParametersInfos createdParameters = parametersRepository.findAll().get(0).toVoltageInitParametersInfos();

        mockMvc.perform(post(URI_PARAMETERS_BASE).queryParam(DUPLICATE_FROM_PARAMETER, UUID.randomUUID().toString()))
            .andExpect(status().isNotFound());

        mockMvc.perform(post(URI_PARAMETERS_BASE).queryParam(DUPLICATE_FROM_PARAMETER, createdParameters.getUuid().toString()))
            .andExpect(status().isOk());

        VoltageInitParametersInfos duplicatedParameters = parametersRepository.findAll().get(1).toVoltageInitParametersInfos();
        assertThat(duplicatedParameters).recursivelyEquals(createdParameters);
    }

    /** Save parameters into the repository and return its UUID. */
    private UUID saveAndRetunId(VoltageInitParametersInfos parametersInfos) {
        return parametersRepository.save(parametersInfos.toEntity()).getId();
    }

    private static VoltageInitParametersInfos buildParameters() {
        return VoltageInitParametersInfos.builder()
            .voltageLimitsDefault(List.of())
            .voltageLimitsModification(List.of())
            .variableQGenerators(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("qgenFilter1")
                    .build(), FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("qgenFilter2")
                    .build()))
            .generatorsSelectionType(EquipmentsSelectionType.ALL_EXCEPT)
            .variableTwoWindingsTransformers(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter1")
                    .build(), FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter2")
                    .build()))
            .twoWindingsTransformersSelectionType(EquipmentsSelectionType.NONE_EXCEPT)
            .shuntCompensatorsSelectionType(EquipmentsSelectionType.NONE_EXCEPT)
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
            .generatorsSelectionType(EquipmentsSelectionType.ALL_EXCEPT)
            .variableShuntCompensators(List.of(FilterEquipments.builder()
                .filterId(UUID.randomUUID())
                .filterName("vscFilter1")
                .build()))
            .shuntCompensatorsSelectionType(EquipmentsSelectionType.NONE_EXCEPT)
            .variableTwoWindingsTransformers(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter1Modified")
                    .build(), FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter2Modified")
                    .build()))
            .twoWindingsTransformersSelectionType(EquipmentsSelectionType.ALL_EXCEPT)
            .build();
    }
}
