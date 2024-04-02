/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.gridsuite.voltageinit.server.dto.VoltageInitResult;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.service.VoltageInitService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.voltageinit.server.service.NotificationService.HEADER_USER_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + VoltageInitApi.API_VERSION)
@Tag(name = "Voltage init server")
public class VoltageInitController {
    private final VoltageInitService voltageInitService;

    public VoltageInitController(VoltageInitService voltageInitService) {
        this.voltageInitService = voltageInitService;
    }

    @PostMapping(value = "/networks/{networkUuid}/run-and-save", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a voltage init on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200",
            description = "The voltage init analysis has been performed")})
    public ResponseEntity<UUID> runAndSave(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                           @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                           @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                           @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                           @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reporterId,
                                           @Parameter(description = "The type name for the report") @RequestParam(name = "reportType", required = false, defaultValue = "VoltageInit") String reportType,
                                           @Parameter(description = "parametersUuid") @RequestParam(name = "parametersUuid", required = false) UUID parametersUuid,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        UUID resultUuid = voltageInitService.runAndSaveResult(networkUuid, variantId, receiver, reportUuid, reporterId, userId, reportType, parametersUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resultUuid);
    }

    @GetMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a voltage init result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init result"),
        @ApiResponse(responseCode = "404", description = "Voltage init result has not been found")})
    public ResponseEntity<VoltageInitResult> getResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                       @Parameter(description = "parametersUuid") @RequestParam(name = "parametersUuid", required = false) UUID parametersUuid) {
        VoltageInitResult result = voltageInitService.getResult(resultUuid, parametersUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a voltage init result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init result has been deleted")})
    public ResponseEntity<Void> deleteResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        voltageInitService.deleteResult(resultUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete all voltage init results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All voltage init results have been deleted")})
    public ResponseEntity<Void> deleteResults() {
        voltageInitService.deleteResults();
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the voltage init status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init status")})
    public ResponseEntity<String> getStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        String result = voltageInitService.getStatus(resultUuid);
        return ResponseEntity.ok().body(result);
    }

    @PutMapping(value = "/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Invalidate the voltage init status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init status has been invalidated")})
    public ResponseEntity<Void> invalidateStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
        voltageInitService.setStatus(resultUuids, VoltageInitStatus.NOT_DONE.name());
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a voltage init computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has been stopped")})
    public ResponseEntity<Void> stop(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                     @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver) {
        voltageInitService.stop(resultUuid, receiver);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/modifications-group-uuid", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the modifications group uuid associated to a result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modifications group uuid"),
        @ApiResponse(responseCode = "404", description = "The result has not been found")})
    public ResponseEntity<UUID> getModificationsGroupUuid(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        UUID modificationsGroupUuid = voltageInitService.getModificationsGroupUuid(resultUuid);
        return modificationsGroupUuid != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(modificationsGroupUuid)
                : ResponseEntity.notFound().build();
    }

    @PutMapping(value = "/results/{resultUuid}/modifications-group-uuid", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Reset the modifications group uuid associated to a result")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modifications group uuid has been resetted")})
    public ResponseEntity<Void> resetModificationsGroupUuidInResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        voltageInitService.resetModificationsGroupUuid(resultUuid);
        return ResponseEntity.ok().build();
    }
}
