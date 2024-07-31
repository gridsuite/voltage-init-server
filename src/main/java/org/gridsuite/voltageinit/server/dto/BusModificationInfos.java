/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@SuperBuilder
@Getter
@Setter
public class BusModificationInfos {
    private String voltageLevelId;

    private String busId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double v;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double angle;
}
