/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static org.gridsuite.voltageinit.server.VoltageInitException.Type.FORBIDDEN;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler {

    @ExceptionHandler(value = { VoltageInitException.class })
    protected ResponseEntity<Object> handleException(RuntimeException exception) {
        if (exception instanceof VoltageInitException) {
            VoltageInitException voltageInitException = (VoltageInitException) exception;
            if (voltageInitException.getType().equals(FORBIDDEN)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(voltageInitException.getType());
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
