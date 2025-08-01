/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.iidm.network.Bus;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import org.gridsuite.computation.service.AbstractComputationResultService;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.entities.BusVoltageEmbeddable;
import org.gridsuite.voltageinit.server.entities.GlobalStatusEntity;
import org.gridsuite.voltageinit.server.entities.ReactiveSlackEmbeddable;
import org.gridsuite.voltageinit.server.entities.VoltageInitResultEntity;
import org.gridsuite.voltageinit.server.repository.GlobalStatusRepository;
import org.gridsuite.voltageinit.server.repository.ResultRepository;
import org.jgrapht.alg.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Service
public class VoltageInitResultService extends AbstractComputationResultService<VoltageInitStatus> {

    private GlobalStatusRepository globalStatusRepository;
    private ResultRepository resultRepository;

    public VoltageInitResultService(GlobalStatusRepository globalStatusRepository,
                                    ResultRepository resultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
    }

    private static VoltageInitResultEntity toVoltageInitResultEntity(UUID resultUuid, OpenReacResult result, Map<String, Bus> networkBuses, UUID modificationsGroupUuid,
                                                                     boolean isReactiveSlacksOverThreshold, Double reactiveSlacksThreshold) {
        Map<String, String> indicators = result.getIndicators();
        List<ReactiveSlackEmbeddable> reactiveSlacks = result.getReactiveSlacks().stream().map(rs ->
                new ReactiveSlackEmbeddable(rs.getVoltageLevelId(), rs.getBusId(), rs.getSlack()))
            .collect(Collectors.toList());
        Map<String, Pair<Double, Double>> voltageProfile = result.getVoltageProfile();
        List<BusVoltageEmbeddable> busVoltages = voltageProfile.entrySet().stream()
            .map(vp -> {
                Bus b = networkBuses.get(vp.getKey());
                if (b != null) {
                    return new BusVoltageEmbeddable(b.getVoltageLevel().getId(), vp.getKey(),
                        vp.getValue().getFirst() * b.getVoltageLevel().getNominalV(),
                        Math.toDegrees(vp.getValue().getSecond()));
                } else {
                    return null;
                }
            }
        ).filter(Objects::nonNull).toList();
        return new VoltageInitResultEntity(resultUuid, Instant.now(), indicators, reactiveSlacks, busVoltages, modificationsGroupUuid,
                                           isReactiveSlacksOverThreshold, reactiveSlacksThreshold, null);
    }

    @Override
    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        resultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional
    @Override
    public void insertStatus(List<UUID> resultUuids, VoltageInitStatus status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
                .map(uuid -> toStatusEntity(uuid, status.name())).collect(Collectors.toList()));
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional(readOnly = true)
    @Override
    public VoltageInitStatus findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return VoltageInitStatus.valueOf(globalEntity.getStatus());
        } else {
            return null;
        }
    }

    @Transactional
    @Override
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        resultRepository.deleteAll();
    }

    public List<VoltageInitResultEntity> findAll() {
        return resultRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<VoltageInitResultEntity> find(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultRepository.findByResultUuid(resultUuid);
    }

    @Transactional
    public void insert(UUID resultUuid, OpenReacResult result, Map<String, Bus> networkBuses, UUID modificationsGroupUuid,
                       String status, boolean isReactiveSlacksOverThreshold, Double reactiveSlacksThreshold) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            resultRepository.save(toVoltageInitResultEntity(resultUuid, result, networkBuses, modificationsGroupUuid, isReactiveSlacksOverThreshold, reactiveSlacksThreshold));
        }
        globalStatusRepository.save(toStatusEntity(resultUuid, status));
    }

    @Transactional
    public void insertErrorResult(UUID resultUuid, Map<String, String> errorIndicators) {
        Objects.requireNonNull(resultUuid);
        resultRepository.save(new VoltageInitResultEntity(resultUuid, Instant.now(), errorIndicators, List.of(), List.of(), null, false, null, null));
    }

    @Override
    @Transactional
    public void saveDebugFileLocation(UUID resultUuid, String debugFilePath) {
        resultRepository.findById(resultUuid).ifPresentOrElse(
                (var resultEntity) -> resultRepository.updateDebugFileLocation(resultUuid, debugFilePath),
                () -> resultRepository.save(new VoltageInitResultEntity(resultUuid, null, null, null, null, null,
                        false, null, debugFilePath))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String findDebugFileLocation(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultRepository.findById(resultUuid)
                .map(VoltageInitResultEntity::getDebugFileLocation)
                .orElse(null);
    }
}
