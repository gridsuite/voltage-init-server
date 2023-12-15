/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.openreac.parameters.input.OpenReacParameters;

import org.gridsuite.voltageinit.server.dto.ReactiveSlack;
import org.gridsuite.voltageinit.server.dto.VoltageInitResult;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.entities.VoltageInitResultEntity;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.repository.VoltageInitResultRepository;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.service.parameters.VoltageInitParametersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UuidGeneratorService uuidGeneratorService;

    private final VoltageInitParametersService voltageInitParametersService;

    private final VoltageInitResultRepository resultRepository;

    private final VoltageInitParametersRepository voltageInitParametersRepository;

    private final ObjectMapper objectMapper;

    public VoltageInitService(NotificationService notificationService,
                              NetworkModificationService networkModificationService,
                              UuidGeneratorService uuidGeneratorService,
                              VoltageInitParametersService voltageInitParametersService,
                              VoltageInitResultRepository resultRepository,
                              VoltageInitParametersRepository voltageInitParametersRepository,
                              ObjectMapper objectMapper) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.networkModificationService = Objects.requireNonNull(networkModificationService);
        this.voltageInitParametersService = Objects.requireNonNull(voltageInitParametersService);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.voltageInitParametersRepository = Objects.requireNonNull(voltageInitParametersRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public UUID runAndSaveResult(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String userId, String reportType, UUID parametersUuid) {
        Optional<VoltageInitParametersEntity> voltageInitParametersEntity = Optional.empty();
        HashMap<String, Double> voltageLevelsIdsRestricted = new HashMap<>();
        if (parametersUuid != null) {
            voltageInitParametersEntity = voltageInitParametersRepository.findById(parametersUuid);
        }

        OpenReacParameters parameters = voltageInitParametersService.buildOpenReacParameters(voltageInitParametersEntity, networkUuid, variantId, voltageLevelsIdsRestricted);
        VoltageInitRunContext runContext = new VoltageInitRunContext(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parameters, voltageLevelsIdsRestricted);

        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), VoltageInitStatus.RUNNING.name());
        notificationService.sendRunMessage(new VoltageInitResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
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
