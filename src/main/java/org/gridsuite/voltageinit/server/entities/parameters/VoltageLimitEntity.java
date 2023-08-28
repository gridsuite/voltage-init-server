/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.entities.parameters;

import lombok.*;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "voltageLimit")
public class VoltageLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "lowVoltageLimit")
    private double lowVoltageLimit;

    @Column(name = "highVoltageLimit")
    private double highVoltageLimit;

    @Column(name = "priority")
    private int priority;

    @ElementCollection
    @CollectionTable(
            name = "VoltageLimitEntityFilters",
            joinColumns = @JoinColumn(name = "voltageLimitId", foreignKey = @ForeignKey(name = "VoltageLimitsEntity_filters_fk")),
            indexes = {@Index(name = "VoltageLimitEntity_filters_index", columnList = "voltageLimitId")}
    )
    private List<FilterEquipmentsEmbeddable> filters;
}
