/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessExceptionHandler;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import com.powsybl.ws.commons.error.ServerNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class VoltageInitExceptionHandler extends AbstractBusinessExceptionHandler<VoltageInitException, VoltageInitBusinessErrorCode> {
    protected VoltageInitExceptionHandler(ServerNameProvider serverNameProvider) {
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

    @ExceptionHandler(VoltageInitException.class)
    protected ResponseEntity<PowsyblWsProblemDetail> handleVoltageInitException(
            VoltageInitException exception, HttpServletRequest request) {
        return super.handleDomainException(exception, request);
    }
}
