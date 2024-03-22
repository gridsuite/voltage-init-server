/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Data
public class VoltageInitRunContext {
    private static final String VOLTAGE_INIT_TYPE_REPORT = "VoltageInit";

    private final UUID networkUuid;
    private final String variantId;
    private final String receiver;
    private final UUID reportUuid;
    private final String reporterId;
    private final String reportType;
    private final String userId;
    private final UUID parametersUuid;
    private final Map<String, Double> voltageLevelsIdsRestricted;
    private final Reporter rootReporter;

    public VoltageInitRunContext(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String reportType, String userId, UUID parametersUuid, Map<String, Double> voltageLevelsIdsRestricted) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.receiver = receiver;
        this.reportUuid = reportUuid;
        this.reporterId = reporterId;
        this.reportType = reportType;
        this.userId = userId;
        this.parametersUuid = parametersUuid;
        this.voltageLevelsIdsRestricted = voltageLevelsIdsRestricted;
        if (this.reportUuid == null) {
            this.rootReporter = Reporter.NO_OP;
        } else {
            final String rootReporterId = reporterId == null ? VOLTAGE_INIT_TYPE_REPORT : reporterId + "@" + reportType;
            this.rootReporter = new ReporterModel(rootReporterId, rootReporterId);
        }
    }

    public VoltageInitRunContext(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String reportType, String userId, UUID parametersUuid) {
        this(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid, new HashMap<>());
    }
}
