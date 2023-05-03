/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import org.springframework.stereotype.Service;
import org.gridsuite.voltageinit.server.repository.VoltageInitRepository;

import java.util.Objects;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitService {
    private VoltageInitRepository voltageInitRepository;


    public VoltageInitService(VoltageInitRepository voltageInitRepository) {
        this.voltageInitRepository = Objects.requireNonNull(voltageInitRepository);
    }
}
