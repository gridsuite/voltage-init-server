/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto.parameters;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Voltage init parameters")
public class VoltageInitParametersInfos {
    @Schema(description = "parameters id")
    private UUID uuid;

    @Schema(description = "parameters date")
    private ZonedDateTime date;

    @Schema(description = "parameters name")
    private String name;

    private List<VoltageLimitInfos> voltageLimitsModification;

    private List<VoltageLimitInfos> voltageLimitsDefault;

    private List<FilterEquipments> constantQGenerators;

    private List<FilterEquipments> variableTwoWindingsTransformers;

    private List<FilterEquipments> variableShuntCompensators;

    public VoltageInitParametersEntity toEntity() {
        return new VoltageInitParametersEntity(this);
    }
}
