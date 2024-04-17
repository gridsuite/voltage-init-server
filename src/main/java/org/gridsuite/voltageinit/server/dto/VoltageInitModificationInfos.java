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
@JsonTypeName("VOLTAGE_INIT_MODIFICATION")
public class VoltageInitModificationInfos {
    private List<GeneratorModificationInfos> generators = new ArrayList<>();

    private List<TransformerModificationInfos> transformers = new ArrayList<>();

    private List<StaticVarCompensatorModificationInfos> staticVarCompensators = new ArrayList<>();

    private List<VscConverterStationModificationInfos> vscConverterStations = new ArrayList<>();

    private List<ShuntCompensatorModificationInfos> shuntCompensators = new ArrayList<>();

    private List<BusModificationInfos> buses = new ArrayList<>();

    public void addGeneratorModification(GeneratorModificationInfos generatorModificationInfos) {
        generators.add(generatorModificationInfos);
    }

    public void addTransformerModification(TransformerModificationInfos transformerModificationInfos) {
        transformers.add(transformerModificationInfos);
    }

    public void addStaticVarCompensatorModification(StaticVarCompensatorModificationInfos staticVarCompensatorModificationInfos) {
        staticVarCompensators.add(staticVarCompensatorModificationInfos);
    }

    public void addVscConverterStationModification(VscConverterStationModificationInfos vscConverterStationModificationInfos) {
        vscConverterStations.add(vscConverterStationModificationInfos);
    }

    public void addShuntCompensatorModification(ShuntCompensatorModificationInfos shuntCompensatorModificationInfos) {
        shuntCompensators.add(shuntCompensatorModificationInfos);
    }

    public void addBusModification(BusModificationInfos busModificationInfos) {
        buses.add(busModificationInfos);
    }
}
