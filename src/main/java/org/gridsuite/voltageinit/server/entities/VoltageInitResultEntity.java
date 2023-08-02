/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.entities;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.time.ZonedDateTime;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Getter
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

    @Column
    private UUID modificationsGroupUuid;
}
