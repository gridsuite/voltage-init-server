/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.entities.parameters;

import lombok.*;

import javax.persistence.*;
import javax.transaction.Transactional;

import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageLimitInfos;

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
    @JoinColumn(name = "voltageInitParametersId")
    private List<VoltageLimitEntity> voltageLimits;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityConstantQGenerators",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_constantQGenerators_fk")),
            indexes = {@Index(name = "VoltageInitParametersEntity_constantQGenerators_index", columnList = "voltageInitParametersId")}
    )
    private List<FilterEquipmentsEmbeddable> constantQGenerators;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableTwoWt",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableTwoWt_fk")),
            indexes = {@Index(name = "VoltageInitParametersEntity_variableTwoWTransformers_index", columnList = "voltageInitParametersId")}
    )
    private List<FilterEquipmentsEmbeddable> variableTwoWindingsTransformers;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableShuntCompensators",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableShuntCompensators_fk")),
            indexes = {@Index(name = "VoltageInitParametersEntity_variableShuntCompensators_index", columnList = "voltageInitParametersId")}
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
        List<VoltageLimitEntity> voltageLimitsEntities = null;
        if (voltageInitParametersInfos.getVoltageLimits() != null) {
            voltageLimitsEntities = voltageInitParametersInfos.getVoltageLimits().stream().map(VoltageLimitInfos::toEntity).toList();
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

    private List<VoltageLimitInfos> toVoltageLimits(List<VoltageLimitEntity> voltageLimits) {
        List<VoltageLimitInfos> voltageInitVoltageLimits = null;
        if (voltageLimits != null) {
            voltageInitVoltageLimits = voltageLimits.stream()
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
                .voltageLimits(toVoltageLimits(this.getVoltageLimits()))
                .constantQGenerators(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getConstantQGenerators()))
                .variableTwoWindingsTransformers(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableTwoWindingsTransformers()))
                .variableShuntCompensators(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableShuntCompensators())).build();
    }

}




