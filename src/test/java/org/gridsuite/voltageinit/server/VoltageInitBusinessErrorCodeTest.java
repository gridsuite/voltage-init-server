/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import org.gridsuite.voltageinit.server.error.VoltageInitBusinessErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.gridsuite.voltageinit.utils.assertions.Assertions.assertThat;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
class VoltageInitBusinessErrorCodeTest {
    @ParameterizedTest
    @EnumSource(VoltageInitBusinessErrorCode.class)
    void valueMatchesEnumName(VoltageInitBusinessErrorCode code) {
        assertThat(code.value()).startsWith("voltageInit.");
    }
}
