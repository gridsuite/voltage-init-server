/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto.parameters;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import org.gridsuite.voltageinit.server.util.EquipmentsSelectionType;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Voltage init parameters")
public class VoltageInitParametersInfos {
    @Schema(description = "parameters id")
    private UUID uuid;

    @Schema(description = "parameters date")
    private Instant date;

    @Schema(description = "parameters name")
    private String name;

    List<VoltageLimitInfos> voltageLimitsModification;

    List<VoltageLimitInfos> voltageLimitsDefault;

    List<FilterEquipments> variableQGenerators;

    EquipmentsSelectionType generatorsSelectionType;

    List<FilterEquipments> variableTwoWindingsTransformers;

    EquipmentsSelectionType twoWindingsTransformersSelectionType;

    List<FilterEquipments> variableShuntCompensators;

    EquipmentsSelectionType shuntCompensatorsSelectionType;

    double reactiveSlacksThreshold;

    double shuntCompensatorActivationThreshold;

    boolean updateBusVoltage;

    public VoltageInitParametersEntity toEntity() {
        return new VoltageInitParametersEntity(this);
    }
}
