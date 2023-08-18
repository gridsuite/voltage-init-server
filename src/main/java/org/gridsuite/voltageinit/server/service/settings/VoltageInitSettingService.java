/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.settings;

import java.util.List;
import java.util.UUID;

import org.gridsuite.voltageinit.server.dto.settings.VoltageInitSettingInfos;
import org.gridsuite.voltageinit.server.entities.settings.VoltageInitSettingEntity;
import org.gridsuite.voltageinit.server.repository.settings.VoltageInitSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Service
public class VoltageInitSettingService {

    private final VoltageInitSettingRepository voltageInitSettingRepository;

    public VoltageInitSettingService(VoltageInitSettingRepository voltageInitSettingRepository) {
        this.voltageInitSettingRepository = voltageInitSettingRepository;
    }

    public VoltageInitSettingInfos createSetting(VoltageInitSettingInfos settingInfos) {
        return voltageInitSettingRepository.save(settingInfos.toEntity()).toVoltageInitSettingInfos();
    }

    public VoltageInitSettingInfos getSetting(UUID settingUuid) {
        return voltageInitSettingRepository.findById(settingUuid).orElseThrow().toVoltageInitSettingInfos();
    }

    public List<VoltageInitSettingInfos> getAllSettings() {
        return voltageInitSettingRepository.findAll().stream().map(VoltageInitSettingEntity::toVoltageInitSettingInfos).toList();
    }

    @Transactional
    public void updateSetting(UUID settingUuid, VoltageInitSettingInfos settingInfos) {
        voltageInitSettingRepository.findById(settingUuid).orElseThrow().update(settingInfos);
    }

    public void deleteSetting(UUID settingUuid) {
        voltageInitSettingRepository.deleteById(settingUuid);
    }
}
