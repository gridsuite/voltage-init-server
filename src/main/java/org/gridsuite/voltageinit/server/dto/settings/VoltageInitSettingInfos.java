/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto.settings;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.gridsuite.voltageinit.server.entities.settings.VoltageInitSettingEntity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Voltage init setting")
public class VoltageInitSettingInfos {
    @Schema(description = "Setting id")
    private UUID uuid;

    @Schema(description = "Setting date")
    private ZonedDateTime date;

    @Schema(description = "Setting name")
    private String name;

    List<VoltageLimitsParameterInfos> voltageLimits;

    List<FilterEquipments> constantQGenerators;

    List<FilterEquipments> variableTwoWindingsTransformers;

    List<FilterEquipments> variableShuntCompensators;

    public VoltageInitSettingEntity toEntity() {
        return new VoltageInitSettingEntity(this);
    }
}
