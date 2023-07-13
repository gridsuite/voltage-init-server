/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type"
)
@JsonTypeName("TABLE_EQUIPMENT_MODIFICATION")
public class TableEquipmentModificationInfos {
    private List<EquipmentModificationInfos> modifications = new ArrayList<>();

    public void addModification(EquipmentModificationInfos modificationInfos) {
        modifications.add(modificationInfos);
    }
}
