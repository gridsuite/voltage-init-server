/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride.VoltageLimitType;

import org.gridsuite.voltageinit.server.dto.ReactiveSlack;
import org.gridsuite.voltageinit.server.dto.VoltageInitResult;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.IdentifiableAttributes;
import org.gridsuite.voltageinit.server.entities.VoltageInitResultEntity;
import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.repository.VoltageInitResultRepository;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.service.parameters.FilterService;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
public class VoltageInitService {
    @Autowired
    NotificationService notificationService;

    @Autowired
    NetworkModificationService networkModificationService;

    private NetworkStoreService networkStoreService;

    private final FilterService filterService;

    private final UuidGeneratorService uuidGeneratorService;

    private final VoltageInitResultRepository resultRepository;

    private final VoltageInitParametersRepository voltageInitParametersRepository;

    private final ObjectMapper objectMapper;

    public VoltageInitService(NotificationService notificationService,
                              NetworkModificationService networkModificationService,
                              FilterService filterService,
                              UuidGeneratorService uuidGeneratorService,
                              NetworkStoreService networkStoreService,
                              VoltageInitResultRepository resultRepository,
                              VoltageInitParametersRepository voltageInitParametersRepository,
                              ObjectMapper objectMapper) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.networkModificationService = Objects.requireNonNull(networkModificationService);
        this.filterService = filterService;
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.voltageInitParametersRepository = Objects.requireNonNull(voltageInitParametersRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    private Network getNetwork(UUID networkUuid, PreloadingStrategy strategy, String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, strategy);
            if (variantId != null) {
                network.getVariantManager().setWorkingVariant(variantId);
            }
            return network;
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public UUID runAndSaveResult(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String userId, UUID parametersUuid) {
        Optional<VoltageInitParametersEntity> voltageInitParametersEntity = Optional.empty();
        if (parametersUuid != null) {
            voltageInitParametersEntity = voltageInitParametersRepository.findById(parametersUuid);
        }
        OpenReacParameters parameters = buildOpenReacParameters(voltageInitParametersEntity, networkUuid, variantId);
        VoltageInitRunContext runContext = new VoltageInitRunContext(networkUuid, variantId, receiver, reportUuid, reporterId, userId, parameters);
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), VoltageInitStatus.RUNNING.name());
        notificationService.sendRunMessage(new VoltageInitResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    private Map<String, VoltageLimitEntity> resolveVoltageLevelLimits(List<VoltageLimitEntity> voltageLimits, UUID networkUuid, String networkVariant) {
        Map<String, VoltageLimitEntity> voltageLevelLimits = new HashMap<>();
        voltageLimits.stream().forEach(voltageLimit -> {
            var filterEquipments = filterService.exportFilters(voltageLimit.getFilters().stream().map(FilterEquipmentsEmbeddable::getFilterId).toList(), networkUuid, networkVariant);
            filterEquipments.forEach(filterEquipment -> {
                    List<String> voltageLevelsIds = filterEquipment.getIdentifiableAttributes().stream().map(IdentifiableAttributes::getId).toList();
                    voltageLevelsIds.forEach(voltageLevelsId -> voltageLevelLimits.put(voltageLevelsId, voltageLimit));
                }
            );
        });
        return voltageLevelLimits;
    }

    private void fillSpecificVoltageLimits(List<VoltageLimitOverride> specificVoltageLimits, VoltageLevel voltageLevel, Double lowVoltageLimitModification, Double lowVoltageLimitDefault, Double highVoltageLimitModification, Double highVoltageLimitDefault) {
        if (!Double.isNaN(voltageLevel.getLowVoltageLimit()) && lowVoltageLimitModification != null) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.LOW_VOLTAGE_LIMIT, true, lowVoltageLimitModification));
        } else if (Double.isNaN(voltageLevel.getLowVoltageLimit()) && lowVoltageLimitDefault != null) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.LOW_VOLTAGE_LIMIT, false, lowVoltageLimitDefault + (lowVoltageLimitModification != null ? lowVoltageLimitModification : 0)));
        }
        if (!Double.isNaN(voltageLevel.getHighVoltageLimit()) && highVoltageLimitModification != null) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.HIGH_VOLTAGE_LIMIT, true, highVoltageLimitModification));
        } else if (Double.isNaN(voltageLevel.getHighVoltageLimit()) && highVoltageLimitDefault != null) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.HIGH_VOLTAGE_LIMIT, false, highVoltageLimitDefault + (highVoltageLimitModification != null ? highVoltageLimitModification : 0)));
        }
    }

    public OpenReacParameters buildOpenReacParameters(Optional<VoltageInitParametersEntity> voltageInitParametersEntity, UUID networkUuid, String variantId) {
        OpenReacParameters parameters = new OpenReacParameters();
        List<VoltageLimitOverride> specificVoltageLimits = new ArrayList<>();
        List<String> constantQGenerators = new ArrayList<>();
        List<String> variableTwoWindingsTransformers = new ArrayList<>();
        List<String> variableShuntCompensators = new ArrayList<>();

        voltageInitParametersEntity.ifPresent(voltageInitParameters -> {
            if (voltageInitParameters.getVoltageLimits() != null) {
                Network network = getNetwork(networkUuid, PreloadingStrategy.COLLECTION, variantId);

                Map<String, VoltageLimitEntity> voltageLevelDefaultLimits = resolveVoltageLevelLimits(voltageInitParameters.getVoltageLimits().stream().filter(voltageLimit -> VoltageLimitParameterType.DEFAULT.equals(voltageLimit.getVoltageLimitParameterType())).toList(), networkUuid, network.getVariantManager().getWorkingVariantId());
                Map<String, VoltageLimitEntity> voltageLevelModificationLimits = resolveVoltageLevelLimits(voltageInitParameters.getVoltageLimits().stream().filter(voltageLimit -> VoltageLimitParameterType.MODIFICATION.equals(voltageLimit.getVoltageLimitParameterType())).toList(), networkUuid, network.getVariantManager().getWorkingVariantId());
                List<VoltageLevel> voltageLevels = network.getVoltageLevelStream()
                    .filter(voltageLevel -> voltageLevelDefaultLimits.keySet().contains(voltageLevel.getId()) || voltageLevelModificationLimits.keySet().contains(voltageLevel.getId()))
                    .toList();
                voltageLevels.forEach(voltageLevel -> {
                    Double lowVoltageLimitModification = voltageLevelModificationLimits.containsKey(voltageLevel.getId()) ? voltageLevelModificationLimits.get(voltageLevel.getId()).getLowVoltageLimit() : null;
                    Double highVoltageLimitModification = voltageLevelModificationLimits.containsKey(voltageLevel.getId()) ? voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit() : null;
                    Double lowVoltageLimitDefault = voltageLevelDefaultLimits.containsKey(voltageLevel.getId()) ? voltageLevelDefaultLimits.get(voltageLevel.getId()).getLowVoltageLimit() : null;
                    Double highVoltageLimitDefault = voltageLevelDefaultLimits.containsKey(voltageLevel.getId()) ? voltageLevelDefaultLimits.get(voltageLevel.getId()).getHighVoltageLimit() : null;
                    fillSpecificVoltageLimits(specificVoltageLimits, voltageLevel, lowVoltageLimitModification, lowVoltageLimitDefault, highVoltageLimitModification, highVoltageLimitDefault);
                });
            }
            constantQGenerators.addAll(toEquipmentIdsList(voltageInitParameters.getConstantQGenerators(), networkUuid, variantId));
            variableTwoWindingsTransformers.addAll(toEquipmentIdsList(voltageInitParameters.getVariableTwoWindingsTransformers(), networkUuid, variantId));
            variableShuntCompensators.addAll(toEquipmentIdsList(voltageInitParameters.getVariableShuntCompensators(), networkUuid, variantId));
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
        List<FilterEquipments> equipments = filterService.exportFilters(filters.stream().map(FilterEquipmentsEmbeddable::getFilterId).toList(), networkUuid, variantId);
        Set<String> ids = new HashSet<>();
        equipments.forEach(filterEquipment ->
                filterEquipment.getIdentifiableAttributes().forEach(identifiableAttribute ->
                        ids.add(identifiableAttribute.getId())
                )
        );
        return new ArrayList<>(ids);
    }

    @Transactional(readOnly = true)
    public VoltageInitResult getResult(UUID resultUuid) {
        Optional<VoltageInitResultEntity> result = resultRepository.find(resultUuid);
        return result.map(VoltageInitService::fromEntity).orElse(null);
    }

    private static VoltageInitResult fromEntity(VoltageInitResultEntity resultEntity) {
        LinkedHashMap<String, String> sortedIndicators = resultEntity.getIndicators().entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (collisionValue1, collisionValue2) -> collisionValue1, LinkedHashMap::new));
        List<ReactiveSlack> reactiveSlacks = resultEntity.getReactiveSlacks().stream()
                .map(slack -> new ReactiveSlack(slack.getBusId(), slack.getSlack()))
                .toList();
        return new VoltageInitResult(resultEntity.getResultUuid(), resultEntity.getWriteTimeStamp(), sortedIndicators, reactiveSlacks);
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
