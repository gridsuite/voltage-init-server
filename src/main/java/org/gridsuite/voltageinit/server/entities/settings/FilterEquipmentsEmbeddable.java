/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.entities.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import org.gridsuite.voltageinit.server.dto.settings.FilterEquipments;

import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class FilterEquipmentsEmbeddable {

    @Column(name = "filterId")
    private UUID filterId;

    @Column(name = "filterName")
    private String filterName;

    public static List<FilterEquipmentsEmbeddable> toEmbeddableFilterEquipments(List<FilterEquipments> filters) {
        return filters == null ? null :
                filters.stream()
                        .map(filter -> new FilterEquipmentsEmbeddable(filter.getFilterId(), filter.getFilterName()))
                        .toList();
    }

    public static List<FilterEquipments> fromEmbeddableFilterEquipments(List<FilterEquipmentsEmbeddable> filters) {
        return filters == null ? null :
                filters.stream()
                        .map(filter -> new FilterEquipments(filter.getFilterId(), filter.getFilterName()))
                        .toList();
    }
}
