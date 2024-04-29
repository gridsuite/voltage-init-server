/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import org.gridsuite.voltageinit.server.computation.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Service
public class VoltageInitNotificationService extends NotificationService {

    public static final String HEADER_REACTIVE_SLACKS_OVER_THRESHOLD = "REACTIVE_SLACKS_OVER_THRESHOLD";
    public static final String HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE = "reactiveSlacksThreshold";

    @Autowired
    public VoltageInitNotificationService(StreamBridge publisher) {
        super(publisher);
    }

}
