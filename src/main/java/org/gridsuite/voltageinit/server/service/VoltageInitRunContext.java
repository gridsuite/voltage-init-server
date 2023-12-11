/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.openreac.parameters.input.OpenReacParameters;
import lombok.Getter;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class VoltageInitRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final String receiver;

    private final UUID reportUuid;

    private final String reporterId;

    private final String userId;

    private final UUID parametersUuid;

    public VoltageInitRunContext(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String userId, UUID parametersUuid) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.receiver = receiver;
        this.reportUuid = reportUuid;
        this.reporterId = reporterId;
        this.userId = userId;
        this.parametersUuid = parametersUuid;
    }
}
