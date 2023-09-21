/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.repository;

import org.gridsuite.voltageinit.server.entities.GlobalStatusEntity;
import org.gridsuite.voltageinit.server.entities.ReactiveSlackEmbeddable;
import org.gridsuite.voltageinit.server.entities.VoltageInitResultEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.powsybl.openreac.parameters.output.OpenReacResult;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Repository
public class VoltageInitResultRepository {

    private GlobalStatusRepository globalStatusRepository;
    private ResultRepository resultRepository;

    public VoltageInitResultRepository(GlobalStatusRepository globalStatusRepository,
                                       ResultRepository resultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
    }

    private static VoltageInitResultEntity toVoltageInitResultEntity(UUID resultUuid, OpenReacResult result, UUID modificationsGroupUuid) {
        Map<String, String> indicators = result.getIndicators();
        List<ReactiveSlackEmbeddable> reactiveSlacks = result.getReactiveSlacks().stream().map(rs ->
                new ReactiveSlackEmbeddable(rs.getBusId(), rs.getSlack()))
            .collect(Collectors.toList());
        return new VoltageInitResultEntity(resultUuid, ZonedDateTime.now(), indicators, reactiveSlacks, modificationsGroupUuid);
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        resultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
                .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional(readOnly = true)
    public String findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return globalEntity.getStatus();
        } else {
            return null;
        }
    }

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
    public void insert(UUID resultUuid, OpenReacResult result, UUID modificationsGroupUuid, String status) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            resultRepository.save(toVoltageInitResultEntity(resultUuid, result, modificationsGroupUuid));
        }
        globalStatusRepository.save(toStatusEntity(resultUuid, status));
    }

    @Transactional
    public void insertErrorResult(UUID resultUuid, Map<String, String> errorIndicators) {
        Objects.requireNonNull(resultUuid);
        resultRepository.save(new VoltageInitResultEntity(resultUuid, ZonedDateTime.now(), errorIndicators, List.of(), null));
    }
}
