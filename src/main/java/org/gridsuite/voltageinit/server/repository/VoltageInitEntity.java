/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "user_infos", indexes = {@Index(name = "voltage_init_index", columnList = "TODO")})
public class VoltageInitEntity {

    public VoltageInitEntity() {
        this(UUID.randomUUID());
    }

    @Id
    @Column(name = "id")
    private UUID id;

}
