/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

import org.gridsuite.voltageinit.settings.dto.VoltageInitParametersInfos;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + SettingsApi.API_VERSION)
@Tag(name = "Voltage init settings")
public class VoltageInitParametersController {

    private final VoltageInitParametersService settingsService;

    public VoltageInitParametersController(VoltageInitParametersService settingsService) {
        this.settingsService = settingsService;
    }

    @PostMapping(value = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a setting")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The setting was created"),
        @ApiResponse(responseCode = "404", description = "The setting was not found")})
    public ResponseEntity<VoltageInitParametersInfos> createSetting(
            @RequestBody VoltageInitParametersInfos settingInfos) {
        return ResponseEntity.ok().body(settingsService.createSetting(settingInfos));
    }

    @GetMapping(value = "/settings/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a setting")
    @ApiResponse(responseCode = "200", description = "The setting was returned")
    public ResponseEntity<VoltageInitParametersInfos> getSetting(
            @Parameter(description = "Setting UUID") @PathVariable("uuid") UUID settingUuid) {
        return ResponseEntity.ok().body(settingsService.getSetting(settingUuid));
    }

    @GetMapping(value = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all settings")
    @ApiResponse(responseCode = "200", description = "The list of all settings was returned")
    public ResponseEntity<List<VoltageInitParametersInfos>> getAllSettings() {
        return ResponseEntity.ok().body(settingsService.getAllSettings());
    }

    @PutMapping(value = "/settings/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a setting")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The setting was updated")})
    public ResponseEntity<Void> updateSetting(
            @Parameter(description = "Setting UUID") @PathVariable("uuid") UUID settingUuid,
            @RequestBody VoltageInitParametersInfos settingInfos) {
        settingsService.updateSetting(settingUuid, settingInfos);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/settings/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a setting")
    @ApiResponse(responseCode = "200", description = "The setting was deleted")
    public ResponseEntity<Void> deleteSetting(
            @Parameter(description = "Setting UUID") @PathVariable("uuid") UUID settingUuid) {
        settingsService.deleteSetting(settingUuid);
        return ResponseEntity.ok().build();
    }
}
