/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class ReactiveSlackEmbeddable {
    @Column
    private String voltageLevelId;

    @Column
    private String busId;

    @Column
    private double slack;
}
