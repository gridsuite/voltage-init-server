/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import lombok.Getter;
import org.gridsuite.computation.dto.ReportInfos;
import org.gridsuite.computation.service.AbstractComputationRunContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class VoltageInitRunContext extends AbstractComputationRunContext<Void> {

    private final UUID parametersUuid;

    private final Map<String, Double> voltageLevelsIdsRestricted;

    public VoltageInitRunContext(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId,
             String reportType, String userId, UUID parametersUuid, Map<String, Double> voltageLevelsIdsRestricted, Boolean debug) {
        super(networkUuid, variantId, receiver, new ReportInfos(reportUuid, reporterId, reportType), userId,
            "default-provider",  // TODO : replace with null when fix in powsybl-ws-commons will handle null provider
            null, debug);
        this.parametersUuid = parametersUuid;
        this.voltageLevelsIdsRestricted = voltageLevelsIdsRestricted;
    }

    public VoltageInitRunContext(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String reportType, String userId, UUID parametersUuid) {
        this(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid, new HashMap<>(), null);
    }
}
