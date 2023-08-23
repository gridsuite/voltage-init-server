/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.gridsuite.voltageinit.utils.assertions.Assertions.*;
import java.util.List;
import java.util.UUID;

import org.gridsuite.voltageinit.server.dto.settings.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.settings.VoltageInitSettingInfos;
import org.gridsuite.voltageinit.server.dto.settings.VoltageLimitsParameterInfos;
import org.gridsuite.voltageinit.server.entities.settings.VoltageInitSettingEntity;
import org.gridsuite.voltageinit.server.repository.settings.VoltageInitSettingRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
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
public class VoltageInitSettingsTest {

    private static final String URI_SETTINGS_BASE = "/v1/settings";

    private static final String URI_SETTINGS_GET_PUT = URI_SETTINGS_BASE + "/";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    private VoltageInitSettingRepository settingsRepository;

    @Before
    public void setup() {
        settingsRepository.deleteAll();
    }

    @After
    public void tearOff() {
        settingsRepository.deleteAll();
    }

    @Test
    public void testCreate() throws Exception {

        VoltageInitSettingInfos settingToCreate = buildSetting();
        String settingToCreateJson = mapper.writeValueAsString(settingToCreate);

        mockMvc.perform(post(URI_SETTINGS_BASE).content(settingToCreateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        VoltageInitSettingInfos createdSetting = settingsRepository.findAll().get(0).toVoltageInitSettingInfos();

        assertThat(createdSetting).recursivelyEquals(settingToCreate);
    }

    @Test
    public void testRead() throws Exception {

        VoltageInitSettingInfos settingToRead = buildSetting();

        UUID settingUuid = saveAndRetunId(settingToRead);

        MvcResult mvcResult = mockMvc.perform(get(URI_SETTINGS_GET_PUT + settingUuid))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        VoltageInitSettingInfos receivedSetting = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedSetting).recursivelyEquals(settingToRead);
    }

    @Test
    public void testUpdate() throws Exception {

        VoltageInitSettingInfos settingToUpdate = buildSetting();

        UUID settingUuid = saveAndRetunId(settingToUpdate);

        settingToUpdate = buildSettingUpdate();

        String settingToUpdateJson = mapper.writeValueAsString(settingToUpdate);

        mockMvc.perform(put(URI_SETTINGS_GET_PUT + settingUuid).content(settingToUpdateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        VoltageInitSettingInfos updatedSetting = settingsRepository.findById(settingUuid).get().toVoltageInitSettingInfos();

        assertThat(updatedSetting).recursivelyEquals(settingToUpdate);
    }

    @Test
    public void testDelete() throws Exception {

        VoltageInitSettingInfos settingToDelete = buildSetting();

        UUID settingUuid = saveAndRetunId(settingToDelete);

        mockMvc.perform(delete(URI_SETTINGS_GET_PUT + settingUuid)).andExpect(status().isOk()).andReturn();

        List<VoltageInitSettingEntity> storedSettings = settingsRepository.findAll();

        assertTrue(storedSettings.isEmpty());
    }

    @Test
    public void testGetAll() throws Exception {
        VoltageInitSettingInfos setting1 = buildSetting();

        VoltageInitSettingInfos setting2 = buildSettingUpdate();

        saveAndRetunId(setting1);

        saveAndRetunId(setting2);

        MvcResult mvcResult = mockMvc.perform(get(URI_SETTINGS_BASE))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<VoltageInitSettingInfos> receivedSettings = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedSettings).hasSize(2);
    }

    /** Save a setting into the repository and return its UUID. */
    protected UUID saveAndRetunId(VoltageInitSettingInfos settingInfos) {
        settingsRepository.save(settingInfos.toEntity());
        return settingsRepository.findAll().get(0).getId();
    }

    protected VoltageInitSettingInfos buildSetting() {
        return VoltageInitSettingInfos.builder()
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

    protected VoltageInitSettingInfos buildSettingUpdate() {
        return VoltageInitSettingInfos.builder()
            .voltageLimits(List.of(VoltageLimitsParameterInfos.builder()
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

