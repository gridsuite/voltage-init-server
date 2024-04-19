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

import java.util.List;
import java.util.UUID;

import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.service.parameters.VoltageInitParametersService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + VoltageInitApi.API_VERSION + "/parameters")
@Tag(name = "Voltage init parameters")
public class VoltageInitParametersController {

    private final VoltageInitParametersService parametersService;

    public VoltageInitParametersController(VoltageInitParametersService parametersService) {
        this.parametersService = parametersService;
    }

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "parameters were created")})
    public ResponseEntity<UUID> createParameters(
            @RequestBody VoltageInitParametersInfos parametersInfos) {
        return ResponseEntity.ok().body(parametersService.createParameters(parametersInfos));
    }

    @PostMapping(value = "/{sourceParametersUuid}")
    @Operation(summary = "Duplicate parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "parameters were duplicated"),
        @ApiResponse(responseCode = "404", description = "source parameters were not found")})
    public ResponseEntity<UUID> duplicateParameters(
        @Parameter(description = "source parameters UUID") @PathVariable("sourceParametersUuid") UUID sourceParametersUuid) {
        return parametersService.duplicateParameters(sourceParametersUuid).map(duplicatedParametersUuid -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(duplicatedParametersUuid))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "parameters were returned"),
        @ApiResponse(responseCode = "404", description = "parameters were not found")})
    public ResponseEntity<VoltageInitParametersInfos> getParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid) {
        VoltageInitParametersInfos parameters = parametersService.getParameters(parametersUuid);
        return parameters != null ? ResponseEntity.ok().body(parametersService.getParameters(parametersUuid))
                : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all parameters")
    @ApiResponse(responseCode = "200", description = "The list of all parameters was returned")
    public ResponseEntity<List<VoltageInitParametersInfos>> getAllParameters() {
        return ResponseEntity.ok().body(parametersService.getAllParameters());
    }

    @PutMapping(value = "/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update parameters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "parameters were updated")})
    public ResponseEntity<Void> updateParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid,
            @RequestBody VoltageInitParametersInfos parametersInfos) {
        parametersService.updateParameters(parametersUuid, parametersInfos);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete parameters")
    @ApiResponse(responseCode = "200", description = "parameters were deleted")
    public ResponseEntity<Void> deleteParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid) {
        parametersService.deleteParameters(parametersUuid);
        return ResponseEntity.ok().build();
    }
}
