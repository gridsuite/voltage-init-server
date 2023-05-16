/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class VoltageInitRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final List<UUID> otherNetworkUuids;

    private final String receiver;

    private final UUID reportUuid;

    private final String reporterId;

    private final String userId;

    public VoltageInitRunContext(UUID networkUuid, String variantId, List<UUID> otherNetworkUuids, String receiver, UUID reportUuid, String reporterId, String userId) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.otherNetworkUuids = Objects.requireNonNull(otherNetworkUuids);
        this.receiver = receiver;
        this.reportUuid = reportUuid;
        this.reporterId = reporterId;
        this.userId = userId;
    }
}
