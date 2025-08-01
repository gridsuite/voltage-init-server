/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.repository;

import org.gridsuite.voltageinit.server.entities.VoltageInitResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Repository
public interface ResultRepository extends JpaRepository<VoltageInitResultEntity, UUID> {

    Optional<VoltageInitResultEntity> findByResultUuid(UUID resultUuid);

    void deleteByResultUuid(UUID resultUuid);

    @Modifying
    @Query("UPDATE VoltageInitResultEntity r SET r.debugFileLocation = :debugFileLocation WHERE r.resultUuid = :resultUuid")
    int updateDebugFileLocation(@Param("resultUuid") UUID resultUuid, @Param("debugFileLocation") String debugFileLocation);
}
