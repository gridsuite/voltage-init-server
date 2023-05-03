/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import com.powsybl.openreac.parameters.input.OpenReacParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.voltageinit.server.service.VoltageInitRunContext;
import org.gridsuite.voltageinit.server.service.VoltageInitService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
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
public class VoltageIniController {
    private final VoltageInitService voltageInitService;

    public VoltageIniController(VoltageInitService voltageInitService) {
        this.voltageInitService = voltageInitService;
    }

    @PostMapping(value = "/networks/{networkUuid}/run-and-save", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a voltage init on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200",
            description = "The voltage init analysis has been performed")})
    public ResponseEntity<UUID> runAndSave(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                           @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                           @Parameter(description = "Other networks UUID (to merge with main one))") @RequestParam(name = "networkUuid", required = false) List<UUID> otherNetworkUuids,
                                           @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                           @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                           @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reporterId,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        List<UUID> nonNullOtherNetworkUuids = getNonNullOtherNetworkUuids(otherNetworkUuids);
        UUID resultUuid = voltageInitService.runAndSaveResult(new VoltageInitRunContext(networkUuid, variantId, nonNullOtherNetworkUuids, receiver, new OpenReacParameters(), reportUuid, reporterId, userId));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resultUuid);
    }

    private static List<UUID> getNonNullOtherNetworkUuids(List<UUID> otherNetworkUuids) {
        return otherNetworkUuids != null ? otherNetworkUuids : Collections.emptyList();
    }

//    @GetMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
//    @Operation(summary = "Get a short circuit analysis result from the database")
//    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis result"),
//            @ApiResponse(responseCode = "404", description = "Short circuit analysis result has not been found")})
//    public ResponseEntity<ShortCircuitAnalysisResult> getResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
//                                                                @Parameter(description = "Full or summary result") @RequestParam(name = "full", required = false, defaultValue = "false") Boolean full) {
//        ShortCircuitAnalysisResult result = shortCircuitService.getResult(resultUuid, full);
//        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
//                : ResponseEntity.notFound().build();
//    }

    @DeleteMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a voltage init result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init result has been deleted")})
    public ResponseEntity<Void> deleteResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        voltageInitService.deleteResult(resultUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete all short circuit analysis results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All short circuit analysis results have been deleted")})
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

//    @PutMapping(value = "/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
//    @Operation(summary = "Invalidate the short circuit analysis status from the database")
//    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis status has been invalidated")})
//    public ResponseEntity<Void> invalidateStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
//        shortCircuitService.setStatus(resultUuids, ShortCircuitAnalysisStatus.NOT_DONE.name());
//        return ResponseEntity.ok().build();
//    }
//
    @PutMapping(value = "/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a voltage init computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has been stopped")})
    public ResponseEntity<Void> stop(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                     @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver) {
        voltageInitService.stop(resultUuid, receiver);
        return ResponseEntity.ok().build();
    }
}
