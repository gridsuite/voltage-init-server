/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.repository;

import org.gridsuite.voltageinit.server.entities.GlobalStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Repository
public interface GlobalStatusRepository extends JpaRepository<GlobalStatusEntity, UUID> {
    GlobalStatusEntity findByResultUuid(UUID resultUuid);

    List<GlobalStatusEntity> findByResultUuidIn(List<UUID> resultUuids);

    void deleteByResultUuid(UUID resultUuid);
}
