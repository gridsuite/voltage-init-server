/**
 * Copyright (c) 2017-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.gridsuite.voltageinit.server.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public enum VoltageInitBusinessErrorCode implements BusinessErrorCode {
    MISSING_FILTER("voltageInit.missingFilter");

    private final String code;

    VoltageInitBusinessErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}
