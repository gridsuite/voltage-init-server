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
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;

import java.util.List;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Data
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

    private List<FilterEquipments> filters;

    public VoltageLimitEntity toEntity(VoltageLimitParameterType voltageLimitParameterType) {
        return new VoltageLimitEntity(null, lowVoltageLimit, highVoltageLimit, priority, voltageLimitParameterType,
                FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(filters));
    }
}
