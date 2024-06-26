/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VoltageInitResult {

    private UUID resultUuid;

    private Instant writeTimeStamp;

    private Map<String, String> indicators;

    private List<ReactiveSlack> reactiveSlacks;

    private List<BusVoltage> busVoltages;

    private UUID modificationsGroupUuid;

    private boolean reactiveSlacksOverThreshold;

    private Double reactiveSlacksThreshold;
}
