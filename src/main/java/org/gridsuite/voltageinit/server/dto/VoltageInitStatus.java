/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
public enum VoltageInitStatus {
    NOT_DONE,
    RUNNING,
    COMPLETED,

    /**
     * {@link com.powsybl.openreac.parameters.output.OpenReacStatus#OK}
     */
    OK,

    /**
     * {@link com.powsybl.openreac.parameters.output.OpenReacStatus#NOT_OK}
     */
    NOT_OK;
}
