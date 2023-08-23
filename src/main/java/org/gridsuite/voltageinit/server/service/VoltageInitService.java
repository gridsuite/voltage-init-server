/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;

import org.gridsuite.voltageinit.server.dto.ReactiveSlack;
import org.gridsuite.voltageinit.server.dto.VoltageInitResult;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.dto.settings.FilterEquipments;
import org.gridsuite.voltageinit.server.entities.VoltageInitResultEntity;
import org.gridsuite.voltageinit.server.entities.settings.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.settings.VoltageInitSettingEntity;
import org.gridsuite.voltageinit.server.repository.VoltageInitResultRepository;
import org.gridsuite.voltageinit.server.repository.settings.VoltageInitSettingRepository;
import org.gridsuite.voltageinit.server.service.settings.FilterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitService {
    @Autowired
    NotificationService notificationService;

    @Autowired
    NetworkModificationService networkModificationService;

    private final FilterService filterService;

    private UuidGeneratorService uuidGeneratorService;

    private VoltageInitResultRepository resultRepository;

    private VoltageInitSettingRepository voltageInitSettingRepository;

    private ObjectMapper objectMapper;

    public VoltageInitService(NotificationService notificationService,
                              NetworkModificationService networkModificationService,
                              FilterService filterService,
                              UuidGeneratorService uuidGeneratorService,
                              VoltageInitResultRepository resultRepository,
                              VoltageInitSettingRepository voltageInitSettingRepository,
                              ObjectMapper objectMapper) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.networkModificationService = Objects.requireNonNull(networkModificationService);
        this.filterService = filterService;
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.voltageInitSettingRepository = Objects.requireNonNull(voltageInitSettingRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public UUID runAndSaveResult(UUID networkUuid, String variantId, List<UUID> nonNullOtherNetworkUuids, String receiver, UUID reportUuid, String reporterId, String userId, UUID settingUuid) {
        Optional<VoltageInitSettingEntity> voltageInitSettingEntity = Optional.empty();
        if (settingUuid != null) {
            voltageInitSettingEntity = voltageInitSettingRepository.findById(settingUuid);
        }
        OpenReacParameters parameters = buildOpenReacParameters(voltageInitSettingEntity, networkUuid, variantId);
        VoltageInitRunContext runContext = new VoltageInitRunContext(networkUuid, variantId, nonNullOtherNetworkUuids, receiver, reportUuid, reporterId, userId, parameters);
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), VoltageInitStatus.RUNNING.name());
        notificationService.sendRunMessage(new VoltageInitResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    private OpenReacParameters buildOpenReacParameters(Optional<VoltageInitSettingEntity> voltageInitSettingEntity, UUID networkUuid, String variantId) {
        OpenReacParameters parameters = new OpenReacParameters();
        Map<String, VoltageLimitOverride> specificVoltageLimits = new HashMap<>();
        List<String> constantQGenerators = new ArrayList<>();
        List<String> variableTwoWindingsTransformers = new ArrayList<>();
        List<String> variableShuntCompensators = new ArrayList<>();
        voltageInitSettingEntity.ifPresent(voltageInitSetting -> {
            if (voltageInitSetting.getVoltageLimits() != null) {
                voltageInitSetting.getVoltageLimits().forEach(voltageLimit -> {
                    var filterEquipments = filterService.exportFilters(voltageLimit.getFilters().stream().map(filter -> filter.getFilterId()).collect(Collectors.toList()), networkUuid, variantId);
                    filterEquipments.forEach(filterEquipment ->
                            filterEquipment.getIdentifiableAttributes().forEach(idenfiableAttribute ->
                                    specificVoltageLimits.put(idenfiableAttribute.getId(), new VoltageLimitOverride(voltageLimit.getLowVoltageLimit(), voltageLimit.getHighVoltageLimit()))
                            )
                    );
                });
                constantQGenerators.addAll(toEquipmentIdsList(voltageInitSetting.getConstantQGenerators(), networkUuid, variantId));
                variableTwoWindingsTransformers.addAll(toEquipmentIdsList(voltageInitSetting.getVariableTwoWindingsTransformers(), networkUuid, variantId));
                variableShuntCompensators.addAll(toEquipmentIdsList(voltageInitSetting.getVariableShuntCompensators(), networkUuid, variantId));
            }
        });
        parameters.addSpecificVoltageLimits(specificVoltageLimits)
                .addConstantQGenerators(constantQGenerators)
                .addVariableTwoWindingsTransformers(variableTwoWindingsTransformers)
                .addVariableShuntCompensators(variableShuntCompensators);

        return parameters;
    }

    private List<String> toEquipmentIdsList(List<FilterEquipmentsEmbeddable> filters, UUID networkUuid, String variantId) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        List<FilterEquipments> equipments = filterService.exportFilters(filters.stream().map(filter -> filter.getFilterId()).collect(Collectors.toList()), networkUuid, variantId);
        Set<String> ids = new HashSet<>();
        equipments.forEach(filterEquipment ->
                filterEquipment.getIdentifiableAttributes().forEach(identifiableAttribute ->
                        ids.add(identifiableAttribute.getId())
                )
        );
        return ids.stream().collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VoltageInitResult getResult(UUID resultUuid) {
        Optional<VoltageInitResultEntity> result = resultRepository.find(resultUuid);
        return result.map(r -> fromEntity(r)).orElse(null);
    }

    private static VoltageInitResult fromEntity(VoltageInitResultEntity resultEntity) {
        List<ReactiveSlack> reactiveSlacks = resultEntity.getReactiveSlacks().stream()
                .map(slack -> new ReactiveSlack(slack.getBusId(), slack.getSlack()))
                .collect(Collectors.toList());
        return new VoltageInitResult(resultEntity.getResultUuid(), resultEntity.getWriteTimeStamp(), resultEntity.getIndicators(), reactiveSlacks);
    }

    public void deleteResult(UUID resultUuid) {
        Optional<VoltageInitResultEntity> result = resultRepository.find(resultUuid);
        result.ifPresent(r -> {
            if (r.getModificationsGroupUuid() != null) {
                CompletableFuture.runAsync(() -> networkModificationService.deleteModificationsGroup(r.getModificationsGroupUuid()));
            }
        });
        resultRepository.delete(resultUuid);
    }

    public void deleteResults() {
        resultRepository.findAll().forEach(r -> {
            if (r.getModificationsGroupUuid() != null) {
                networkModificationService.deleteModificationsGroup(r.getModificationsGroupUuid());
            }
        });
        resultRepository.deleteAll();
    }

    public String getStatus(UUID resultUuid) {
        return resultRepository.findStatus(resultUuid);
    }

    public void setStatus(List<UUID> resultUuids, String status) {
        resultRepository.insertStatus(resultUuids, status);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new VoltageInitCancelContext(resultUuid, receiver).toMessage());
    }

    @Transactional(readOnly = true)
    public UUID getModificationsGroupUuid(UUID resultUuid) {
        Optional<VoltageInitResultEntity> result = resultRepository.find(resultUuid);
        return result.map(VoltageInitResultEntity::getModificationsGroupUuid).orElse(null);
    }

}
