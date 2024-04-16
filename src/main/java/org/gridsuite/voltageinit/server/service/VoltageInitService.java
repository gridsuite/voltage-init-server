/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.network.store.client.NetworkStoreService;

import org.gridsuite.voltageinit.server.dto.BusVoltage;
import org.gridsuite.voltageinit.server.dto.ReactiveSlack;
import org.gridsuite.voltageinit.server.dto.VoltageInitResult;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.entities.VoltageInitResultEntity;
import org.gridsuite.voltageinit.server.repository.VoltageInitResultRepository;
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

    private final VoltageInitResultRepository resultRepository;

    public VoltageInitService(NotificationService notificationService,
                              NetworkModificationService networkModificationService,
                              UuidGeneratorService uuidGeneratorService,
                              VoltageInitResultRepository resultRepository) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.networkModificationService = Objects.requireNonNull(networkModificationService);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
    }

    public UUID runAndSaveResult(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String userId, String reportType, UUID parametersUuid) {
        VoltageInitRunContext runContext = new VoltageInitRunContext(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid);
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), VoltageInitStatus.RUNNING.name());
        notificationService.sendRunMessage(new VoltageInitResultContext(resultUuid, runContext).toMessage());
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
        List<BusVoltage> busVoltages = resultEntity.getBusVoltages().stream()
            .map(bv -> new BusVoltage(bv.getBusId(), bv.getV(), bv.getAngle()))
            .toList();
        return new VoltageInitResult(resultEntity.getResultUuid(), resultEntity.getWriteTimeStamp(), sortedIndicators,
            reactiveSlacks, busVoltages, resultEntity.getModificationsGroupUuid(), resultEntity.isReactiveSlacksOverThreshold(),
            resultEntity.getReactiveSlacksThreshold());
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

    @Transactional
    public void resetModificationsGroupUuid(UUID resultUuid) {
        Optional<VoltageInitResultEntity> result = resultRepository.find(resultUuid);
        result.ifPresent(entity -> entity.setModificationsGroupUuid(null));
    }
}
