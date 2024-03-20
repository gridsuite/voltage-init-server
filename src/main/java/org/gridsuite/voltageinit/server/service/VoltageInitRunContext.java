/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Data
@AllArgsConstructor
public class VoltageInitRunContext {

    @NonNull
    private final UUID networkUuid;

    private final String variantId;

    private final String receiver;

    private final UUID reportUuid;

    private final String reporterId;

    private final String reportType;

    private final String userId;

    private final UUID parametersUuid;

    private final Map<String, Double> voltageLevelsIdsRestricted;

    public VoltageInitRunContext(@NonNull UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String reportType, String userId, UUID parametersUuid) {
        this(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid, new HashMap<>());
    }
}
