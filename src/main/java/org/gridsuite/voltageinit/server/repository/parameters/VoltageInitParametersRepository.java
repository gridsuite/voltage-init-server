/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.repository.parameters;

import java.util.UUID;

import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Repository
public interface VoltageInitParametersRepository extends JpaRepository<VoltageInitParametersEntity, UUID> {
}
