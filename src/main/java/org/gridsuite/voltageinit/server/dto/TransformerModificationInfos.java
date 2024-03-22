/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.powsybl.iidm.network.ThreeSides;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuperBuilder
@Getter
@Setter
public class TransformerModificationInfos {
    private String transformerId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer ratioTapChangerPosition;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double ratioTapChangerTargetV;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ThreeSides legSide;
}
