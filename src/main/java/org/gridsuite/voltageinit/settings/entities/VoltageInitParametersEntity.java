/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.settings.entities;

import lombok.*;

import javax.persistence.*;
import javax.transaction.Transactional;

import org.gridsuite.voltageinit.settings.dto.FilterEquipments;
import org.gridsuite.voltageinit.settings.dto.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.settings.dto.VoltageInitVoltageLimitsParameterInfos;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
@Table(name = "voltageInitParameters")
public class VoltageInitParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "date")
    private ZonedDateTime date;

    @Column(name = "name")
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "voltage_init_parameters_id")
    private List<VoltageInitParametersVoltageLimitsEntity> voltageLimits;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityConstantQGenerators",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_constantQGenerators_fk"))
    )
    private List<FilterEquipmentsEmbeddable> constantQGenerators;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableTwoWt",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableTwoWt_fk"))
    )
    private List<FilterEquipmentsEmbeddable> variableTwoWindingsTransformers;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableShuntCompensators",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableShuntCompensators_fk"))
    )
    private List<FilterEquipmentsEmbeddable> variableShuntCompensators;

    public VoltageInitParametersEntity(@NonNull VoltageInitParametersInfos voltageInitParametersInfos) {
        this.date = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        assignAttributes(voltageInitParametersInfos);
    }

    public void update(@NonNull VoltageInitParametersInfos voltageInitParametersInfos) {
        assignAttributes(voltageInitParametersInfos);
    }

    public void assignAttributes(@NonNull VoltageInitParametersInfos voltageInitParametersInfos) {
        List<VoltageInitParametersVoltageLimitsEntity> voltageLimitsEntities = null;
        if (voltageInitParametersInfos.getVoltageLimits() != null) {
            voltageLimitsEntities = voltageInitParametersInfos.getVoltageLimits().stream().map(voltageLimit -> voltageLimit.toEntity()).toList();
        }
        if (voltageLimits == null) {
            voltageLimits = voltageLimitsEntities;
        } else {
            voltageLimits.clear();
            if (voltageLimitsEntities != null) {
                voltageLimits.addAll(voltageLimitsEntities);
            }
        }
        constantQGenerators = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitParametersInfos.getConstantQGenerators());
        variableTwoWindingsTransformers = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitParametersInfos.getVariableTwoWindingsTransformers());
        variableShuntCompensators = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitParametersInfos.getVariableShuntCompensators());
        name = voltageInitParametersInfos.getName();
    }

    private List<VoltageInitVoltageLimitsParameterInfos> toVoltageInitVoltageLimits(List<VoltageInitParametersVoltageLimitsEntity> voltageLimits) {
        List<VoltageInitVoltageLimitsParameterInfos> voltageInitVoltageLimits = null;
        if (voltageLimits != null) {
            voltageInitVoltageLimits = voltageLimits.stream()
                    .map(voltageLimit -> {
                        List<FilterEquipments> filters = FilterEquipmentsEmbeddable
                                .fromEmbeddableFilterEquipments(voltageLimit.getFilters());
                        return new VoltageInitVoltageLimitsParameterInfos(voltageLimit.getPriority(),
                                voltageLimit.getLowVoltageLimit(),
                                voltageLimit.getHighVoltageLimit(), filters);
                    }).toList();
        }
        return voltageInitVoltageLimits;
    }

    public VoltageInitParametersInfos toVoltageInitParametersInfos() {
        return toVoltageInitParametersInfosBuilder().build();
    }

    @Transactional
    private VoltageInitParametersInfos.VoltageInitParametersInfosBuilder<?, ?> toVoltageInitParametersInfosBuilder() {
        return VoltageInitParametersInfos.builder()
                .uuid(this.getId())
                .date(this.getDate())
                .name(this.getName())
                .voltageLimits(toVoltageInitVoltageLimits(this.getVoltageLimits()))
                .constantQGenerators(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getConstantQGenerators()))
                .variableTwoWindingsTransformers(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableTwoWindingsTransformers()))
                .variableShuntCompensators(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableShuntCompensators()));
    }
}




