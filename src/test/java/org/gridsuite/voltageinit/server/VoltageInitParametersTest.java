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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.voltageinit.utils.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private VoltageInitParametersRepository parametersRepository;

    @Before
    @After
    public void cleanDB() {
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
    private UUID saveAndRetunId(VoltageInitParametersInfos parametersInfos) {
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
}
