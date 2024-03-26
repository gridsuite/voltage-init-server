/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "voltage_init_result")
public class VoltageInitResultEntity {
    @Id
    private UUID resultUuid;

    @Column
    private ZonedDateTime writeTimeStamp;

    @ElementCollection
    @CollectionTable
    private Map<String, String> indicators;

    @ElementCollection
    @CollectionTable
    private List<ReactiveSlackEmbeddable> reactiveSlacks;

    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "voltageInitResultEntity_busVoltages_fk1"), indexes = {@Index(name = "voltageInitResultEntity_busVoltages_idx1", columnList = "voltage_init_result_entity_result_uuid")})
    private List<BusVoltageEmbeddable> busVoltages;

    @Column
    private UUID modificationsGroupUuid;
}
