/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.voltageinit.server.service.SupervisionService;
import org.gridsuite.voltageinit.server.service.VoltageInitService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.gridsuite.computation.service.NotificationService.HEADER_USER_ID;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + VoltageInitApi.API_VERSION + "/supervision")
@Tag(name = "Voltage init server - Supervision")
public class SupervisionController {
    private final SupervisionService supervisionService;
    private final VoltageInitService voltageInitService;

    public SupervisionController(SupervisionService supervisionService, VoltageInitService voltageInitService) {
        this.supervisionService = supervisionService;
        this.voltageInitService = voltageInitService;
    }

    @GetMapping(value = "/results-count")
    @Operation(summary = "Get results count")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The count of all results")})
    public ResponseEntity<Integer> getResultsCount() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.getResultsCount());
    }

    @DeleteMapping(value = "/results")
    @Operation(summary = "Delete all voltage init results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All voltage init results have been deleted")})
    public ResponseEntity<Void> deleteAllResults(@RequestHeader(HEADER_USER_ID) String userId) {
        voltageInitService.deleteAllResults(userId);
        return ResponseEntity.ok().build();
    }
}
