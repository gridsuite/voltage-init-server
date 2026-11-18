/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import org.gridsuite.voltageinit.server.repository.GlobalStatusRepository;
import org.gridsuite.voltageinit.server.repository.ResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Service
public class SupervisionService {
    private final ResultRepository resultRepository;
    private final GlobalStatusRepository globalStatusRepository;
    private final VoltageInitService voltageInitService;

    public SupervisionService(ResultRepository resultRepository, GlobalStatusRepository globalStatusRepository, VoltageInitService voltageInitService) {
        this.resultRepository = resultRepository;
        this.globalStatusRepository = globalStatusRepository;
        this.voltageInitService = voltageInitService;
    }

    public Integer getResultsCount() {
        return (int) resultRepository.count();
    }

    @Transactional
    public void deleteAllResults(String userId) {
        voltageInitService.deleteModificationsGroups(resultRepository.findAll(), userId);
        globalStatusRepository.deleteAll();
        resultRepository.deleteAll();
    }
}
