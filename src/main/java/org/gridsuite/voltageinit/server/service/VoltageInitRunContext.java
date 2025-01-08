/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.ws.commons.computation.dto.ReportInfos;
import com.powsybl.ws.commons.computation.service.AbstractComputationRunContext;
import lombok.Getter;

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

    public VoltageInitRunContext(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String reportType, String userId, UUID parametersUuid, Map<String, Double> voltageLevelsIdsRestricted, boolean debug) {
        super(networkUuid, variantId, receiver, new ReportInfos(reportUuid, reporterId, reportType), userId, null, null, debug);
        this.parametersUuid = parametersUuid;
        this.voltageLevelsIdsRestricted = voltageLevelsIdsRestricted;
    }

    public VoltageInitRunContext(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String reportType, String userId, UUID parametersUuid, boolean debug) {
        this(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid, new HashMap<>(), debug);
    }
}
