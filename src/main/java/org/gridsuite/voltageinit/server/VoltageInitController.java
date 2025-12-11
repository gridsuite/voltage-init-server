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
import org.gridsuite.voltageinit.server.service.VoltageInitRunContext;
import org.gridsuite.voltageinit.server.service.VoltageInitService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.computation.service.NotificationService.HEADER_USER_ID;
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
                                           @Parameter(description = "Debug") @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
                                           @Parameter(description = "parametersUuid") @RequestParam(name = "parametersUuid", required = false) UUID parametersUuid,
                                           @Parameter(description = "rootNetworkName") @RequestParam(name = "rootNetworkName") String rootNetworkName,
                                           @Parameter(description = "nodeName") @RequestParam(name = "nodeName") String nodeName,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        VoltageInitRunContext runContext = new VoltageInitRunContext(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid, debug,
                                                                     rootNetworkName, nodeName);
        UUID resultUuid = voltageInitService.runAndSaveResult(runContext);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resultUuid);
    }

    @GetMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a voltage init result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init result"),
        @ApiResponse(responseCode = "404", description = "Voltage init result has not been found")})
    public ResponseEntity<VoltageInitResult> getResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                       @Parameter(description = "Global Filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                       @Parameter(description = "network Uuid") @RequestParam(name = "networkUuid", required = false) UUID networkUuid,
                                                       @Parameter(description = "variant Id") @RequestParam(name = "variantId", required = false) String variantId) {
        String decodedStringGlobalFilters = globalFilters != null ? URLDecoder.decode(globalFilters, StandardCharsets.UTF_8) : null;
        VoltageInitResult result = voltageInitService.getResult(resultUuid, decodedStringGlobalFilters, networkUuid, variantId);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete voltage init results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All voltage init results have been deleted")})
    public ResponseEntity<Void> deleteResults(@Parameter(description = "Results UUID") @RequestParam(value = "resultsUuids", required = false) List<UUID> resultsUuids) {
        voltageInitService.deleteResults(resultsUuids);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the voltage init status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init status")})
    public ResponseEntity<String> getStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        VoltageInitStatus result = voltageInitService.getStatus(resultUuid);
        return ResponseEntity.ok().body(result != null ? result.name() : null);
    }

    @PutMapping(value = "/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Invalidate the voltage init status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init status has been invalidated")})
    public ResponseEntity<Void> invalidateStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
        voltageInitService.setStatus(resultUuids, VoltageInitStatus.NOT_DONE);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a voltage init computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has been stopped")})
    public ResponseEntity<Void> stop(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                     @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                     @RequestHeader(HEADER_USER_ID) String userId) {
        voltageInitService.stop(resultUuid, receiver, userId);
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

    @GetMapping(value = "/results/{resultUuid}/download-debug-file", produces = "application/json")
    @Operation(summary = "Download a voltage init debug file")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Voltage init debug file"),
        @ApiResponse(responseCode = "404", description = "Voltage init debug file has not been found")})
    public ResponseEntity<Resource> downloadDebugFile(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        return voltageInitService.downloadDebugFile(resultUuid);
    }
}
