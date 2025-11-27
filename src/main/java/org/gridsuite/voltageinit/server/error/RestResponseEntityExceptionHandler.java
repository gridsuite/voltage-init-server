/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessExceptionHandler;
import com.powsybl.ws.commons.error.ServerNameProvider;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends AbstractBusinessExceptionHandler<VoltageInitException, VoltageInitBusinessErrorCode> {
    protected RestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected @NonNull VoltageInitBusinessErrorCode getBusinessCode(VoltageInitException e) {
        return e.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(VoltageInitBusinessErrorCode businessErrorCode) {
        return switch (businessErrorCode) {
            case MISSING_FILTER -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
