/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.settings;

import java.util.List;
import java.util.UUID;

import org.gridsuite.voltageinit.settings.dto.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.settings.entities.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.settings.repository.VoltageInitParametersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Service
public class VoltageInitParametersService {

    private final VoltageInitParametersRepository voltageInitParametersRepository;

    public VoltageInitParametersService(VoltageInitParametersRepository voltageInitParametersRepository) {
        this.voltageInitParametersRepository = voltageInitParametersRepository;
    }

    public VoltageInitParametersInfos createSetting(VoltageInitParametersInfos settingInfos) {
        return voltageInitParametersRepository.save(settingInfos.toEntity()).toVoltageInitParametersInfos();
    }

    public VoltageInitParametersInfos getSetting(UUID settingUuid) {
        return voltageInitParametersRepository.findById(settingUuid).orElseThrow().toVoltageInitParametersInfos();
    }

    public List<VoltageInitParametersInfos> getAllSettings() {
        return voltageInitParametersRepository.findAll().stream().map(VoltageInitParametersEntity::toVoltageInitParametersInfos).toList();
    }

    @Transactional
    public void updateSetting(UUID settingUuid, VoltageInitParametersInfos settingInfos) {
        voltageInitParametersRepository.findById(settingUuid).orElseThrow().update(settingInfos);
    }

    public void deleteSetting(UUID settingUuid) {
        voltageInitParametersRepository.deleteById(settingUuid);
    }
}
