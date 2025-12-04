/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.error;

import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.voltageinit.server.error.VoltageInitBusinessErrorCode.MISSING_FILTER;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
class VoltageInitExceptionHandlerTest {

    private VoltageInitExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new VoltageInitExceptionHandler(() -> "voltageInit");
    }

    @Test
    void mapsInteralErrorBusinessErrorToStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/results-endpoint/uuid");
        VoltageInitException exception = new VoltageInitException(MISSING_FILTER, "Bus out of voltage");
        ResponseEntity<PowsyblWsProblemDetail> response = handler.handleVoltageInitException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertEquals("voltageInit.missingFilter", response.getBody().getBusinessErrorCode());
    }
}
