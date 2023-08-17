/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.settings.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.gridsuite.voltageinit.settings.entities.VoltageInitParametersEntity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@ToString(callSuper = true)
@Schema(description = "Voltage init setting")
public class VoltageInitParametersInfos {
    @Schema(description = "Setting id")
    private UUID uuid;

    @Schema(description = "Setting date")
    private ZonedDateTime date;

    @Schema(description = "Setting name")
    private String name;

    List<VoltageInitVoltageLimitsParameterInfos> voltageLimits;

    List<FilterEquipments> constantQGenerators;

    List<FilterEquipments> variableTwoWindingsTransformers;

    List<FilterEquipments> variableShuntCompensators;

    public VoltageInitParametersEntity toEntity() {
        return new VoltageInitParametersEntity(this);
    }
}
