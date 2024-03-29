/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto.parameters;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class VoltageLimitInfos {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Integer priority;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Double lowVoltageLimit;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Double highVoltageLimit;

    List<FilterEquipments> filters;

    public VoltageLimitEntity toEntity(VoltageLimitParameterType voltageLimitParameterType) {
        return new VoltageLimitEntity(null, lowVoltageLimit, highVoltageLimit, priority, voltageLimitParameterType,
                FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(filters));
    }
}
