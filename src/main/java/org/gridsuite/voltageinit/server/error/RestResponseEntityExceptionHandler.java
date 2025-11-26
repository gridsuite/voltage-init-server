/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.error;

import com.powsybl.ws.commons.error.ServerNameProvider;
import org.gridsuite.computation.error.AbstractTypedComputationRestResponseEntityExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends AbstractTypedComputationRestResponseEntityExceptionHandler<VoltageInitBusinessErrorCode> {
    protected RestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider, VoltageInitBusinessErrorCode.class);
    }

    @Override
    protected HttpStatus mapSpecificStatus(VoltageInitBusinessErrorCode businessErrorCode) {
        return switch (businessErrorCode) {
            case MISSING_FILTER -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
