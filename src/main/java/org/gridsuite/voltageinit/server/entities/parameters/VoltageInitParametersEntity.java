/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.entities.parameters;

import lombok.*;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;

import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageLimitInfos;
import org.gridsuite.voltageinit.server.util.EquipmentsSelectionType;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    @Column(name = "date", columnDefinition = "timestamptz")
    private Instant date;

    @Column(name = "name")
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_voltageLimits_fk"))
    private List<VoltageLimitEntity> voltageLimits;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableQGenerators",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableQGenerators_fk")),
            indexes = {@Index(name = "VoltageInitParametersEntity_variableQGenerators_index", columnList = "voltageInitParametersId")}
    )
    private List<FilterEquipmentsEmbeddable> variableQGenerators;

    @Enumerated(EnumType.STRING)
    @Column(name = "generatorsSelectionType")
    private EquipmentsSelectionType generatorsSelectionType;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableTwoWt",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableTwoWt_fk")),
            indexes = {@Index(name = "VoltageInitParametersEntity_variableTwoWTransformers_index", columnList = "voltageInitParametersId")}
    )
    private List<FilterEquipmentsEmbeddable> variableTwoWindingsTransformers;

    @Enumerated(EnumType.STRING)
    @Column(name = "twoWindingsTransformersSelectionType")
    private EquipmentsSelectionType twoWindingsTransformersSelectionType;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableShuntCompensators",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableShuntCompensators_fk")),
            indexes = {@Index(name = "VoltageInitParametersEntity_variableShuntCompensators_index", columnList = "voltageInitParametersId")}
    )
    private List<FilterEquipmentsEmbeddable> variableShuntCompensators;

    @Enumerated(EnumType.STRING)
    @Column(name = "shuntCompensatorsSelectionType")
    private EquipmentsSelectionType shuntCompensatorsSelectionType;

    @Column(name = "reactiveSlacksThreshold")
    private double reactiveSlacksThreshold;

    @Column(name = "shuntCompensatorActivationThreshold")
    private double shuntCompensatorActivationThreshold;

    @Column(name = "updateBusVoltage")
    private boolean updateBusVoltage;

    public VoltageInitParametersEntity(@NonNull VoltageInitParametersInfos voltageInitParametersInfos) {
        this.date = Instant.now().truncatedTo(ChronoUnit.MICROS);
        assignAttributes(voltageInitParametersInfos);
    }

    public void update(@NonNull VoltageInitParametersInfos voltageInitParametersInfos) {
        assignAttributes(voltageInitParametersInfos);
    }

    public void assignAttributes(@NonNull VoltageInitParametersInfos voltageInitParametersInfos) {
        List<VoltageLimitEntity> voltageLimitsEntities = new ArrayList<>();
        if (voltageInitParametersInfos.getVoltageLimitsModification() != null) {
            voltageLimitsEntities.addAll(voltageInitParametersInfos.getVoltageLimitsModification().stream().map(voltageLimitInfos -> voltageLimitInfos.toEntity(VoltageLimitParameterType.MODIFICATION)).toList());
        }
        if (voltageInitParametersInfos.getVoltageLimitsDefault() != null) {
            voltageLimitsEntities.addAll(voltageInitParametersInfos.getVoltageLimitsDefault().stream().map(voltageLimitInfos -> voltageLimitInfos.toEntity(VoltageLimitParameterType.DEFAULT)).toList());
        }
        if (voltageLimits == null) {
            voltageLimits = voltageLimitsEntities;
        } else {
            voltageLimits.clear();
            if (!voltageLimitsEntities.isEmpty()) {
                voltageLimits.addAll(voltageLimitsEntities);
            }
        }
        variableQGenerators = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitParametersInfos.getVariableQGenerators());
        generatorsSelectionType = voltageInitParametersInfos.getGeneratorsSelectionType() != null ? voltageInitParametersInfos.getGeneratorsSelectionType() : EquipmentsSelectionType.ALL_EXCEPT;
        variableTwoWindingsTransformers = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitParametersInfos.getVariableTwoWindingsTransformers());
        twoWindingsTransformersSelectionType = voltageInitParametersInfos.getTwoWindingsTransformersSelectionType() != null ? voltageInitParametersInfos.getTwoWindingsTransformersSelectionType() : EquipmentsSelectionType.NONE_EXCEPT;
        variableShuntCompensators = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitParametersInfos.getVariableShuntCompensators());
        shuntCompensatorsSelectionType = voltageInitParametersInfos.getShuntCompensatorsSelectionType() != null ? voltageInitParametersInfos.getShuntCompensatorsSelectionType() : EquipmentsSelectionType.NONE_EXCEPT;
        name = voltageInitParametersInfos.getName();
        reactiveSlacksThreshold = voltageInitParametersInfos.getReactiveSlacksThreshold();
        shuntCompensatorActivationThreshold = voltageInitParametersInfos.getShuntCompensatorActivationThreshold();
        updateBusVoltage = voltageInitParametersInfos.isUpdateBusVoltage();
    }

    private List<VoltageLimitInfos> toVoltageLimits(List<VoltageLimitEntity> voltageLimits, VoltageLimitParameterType voltageLimitParameterType) {
        List<VoltageLimitInfos> voltageInitVoltageLimits = null;
        if (voltageLimits != null) {
            voltageInitVoltageLimits = voltageLimits.stream()
                    .filter(voltageLimit -> Objects.equals(voltageLimit.getVoltageLimitParameterType(), voltageLimitParameterType))
                    .map(voltageLimit -> {
                        List<FilterEquipments> filters = FilterEquipmentsEmbeddable
                                .fromEmbeddableFilterEquipments(voltageLimit.getFilters());
                        return new VoltageLimitInfos(voltageLimit.getPriority(),
                                voltageLimit.getLowVoltageLimit(),
                                voltageLimit.getHighVoltageLimit(), filters);
                    }).toList();
        }
        return voltageInitVoltageLimits;
    }

    @Transactional
    public VoltageInitParametersInfos toVoltageInitParametersInfos() {
        return VoltageInitParametersInfos.builder()
            .uuid(this.getId())
            .date(this.getDate())
            .name(this.getName())
            .voltageLimitsModification(toVoltageLimits(this.getVoltageLimits(), VoltageLimitParameterType.MODIFICATION))
            .voltageLimitsDefault(toVoltageLimits(this.getVoltageLimits(), VoltageLimitParameterType.DEFAULT))
            .variableQGenerators(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableQGenerators()))
            .generatorsSelectionType(this.getGeneratorsSelectionType() != null ? this.getGeneratorsSelectionType() : EquipmentsSelectionType.ALL_EXCEPT)
            .variableTwoWindingsTransformers(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableTwoWindingsTransformers()))
            .twoWindingsTransformersSelectionType(this.getTwoWindingsTransformersSelectionType() != null ? this.getTwoWindingsTransformersSelectionType() : EquipmentsSelectionType.NONE_EXCEPT)
            .variableShuntCompensators(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableShuntCompensators()))
            .shuntCompensatorsSelectionType(this.getShuntCompensatorsSelectionType() != null ? this.getShuntCompensatorsSelectionType() : EquipmentsSelectionType.NONE_EXCEPT)
            .reactiveSlacksThreshold(this.getReactiveSlacksThreshold())
            .shuntCompensatorActivationThreshold(this.getShuntCompensatorActivationThreshold())
            .updateBusVoltage(this.isUpdateBusVoltage())
            .build();
    }
}




