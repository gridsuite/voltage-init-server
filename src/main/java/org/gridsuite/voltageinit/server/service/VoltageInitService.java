/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.repository.VoltageInitResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitService.class);

    @Autowired
    NotificationService notificationService;

    private UuidGeneratorService uuidGeneratorService;

    private VoltageInitResultRepository resultRepository;

    private ObjectMapper objectMapper;

    public VoltageInitService(NotificationService notificationService, UuidGeneratorService uuidGeneratorService, VoltageInitResultRepository resultRepository, ObjectMapper objectMapper) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public UUID runAndSaveResult(VoltageInitRunContext runContext) {
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), VoltageInitStatus.RUNNING.name());
        notificationService.sendRunMessage(new VoltageInitResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    public void deleteResult(UUID resultUuid) {
        resultRepository.delete(resultUuid);
    }

    public void deleteResults() {
//        resultRepository.deleteAll();
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
}
