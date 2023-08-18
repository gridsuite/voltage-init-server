/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.entities.settings;

import lombok.*;

import javax.persistence.*;
import javax.transaction.Transactional;

import org.gridsuite.voltageinit.server.dto.settings.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.settings.VoltageInitSettingInfos;
import org.gridsuite.voltageinit.server.dto.settings.VoltageLimitsParameterInfos;

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
@Table(name = "voltage_init_setting")
public class VoltageInitSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "date")
    private ZonedDateTime date;

    @Column(name = "name")
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "voltage_init_setting_id")
    private List<VoltageLimitsParameterEntity> voltageLimits;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitSettingEntityConstantQGenerators",
            joinColumns = @JoinColumn(name = "voltageInitSettingId", foreignKey = @ForeignKey(name = "voltageInitSettingEntity_constantQGenerators_fk"))
    )
    private List<FilterEquipmentsEmbeddable> constantQGenerators;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitSettingEntityVariableTwoWt",
            joinColumns = @JoinColumn(name = "voltageInitSettingId", foreignKey = @ForeignKey(name = "voltageInitSettingEntity_variableTwoWt_fk"))
    )
    private List<FilterEquipmentsEmbeddable> variableTwoWindingsTransformers;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitSettingEntityVariableShuntCompensators",
            joinColumns = @JoinColumn(name = "voltageInitSettingId", foreignKey = @ForeignKey(name = "voltageInitSettingEntity_variableShuntCompensators_fk"))
    )
    private List<FilterEquipmentsEmbeddable> variableShuntCompensators;

    public VoltageInitSettingEntity(@NonNull VoltageInitSettingInfos voltageInitSettingInfos) {
        this.date = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        assignAttributes(voltageInitSettingInfos);
    }

    public void update(@NonNull VoltageInitSettingInfos voltageInitSettingInfos) {
        assignAttributes(voltageInitSettingInfos);
    }

    public void assignAttributes(@NonNull VoltageInitSettingInfos voltageInitSettingInfos) {
        List<VoltageLimitsParameterEntity> voltageLimitsEntities = null;
        if (voltageInitSettingInfos.getVoltageLimits() != null) {
            voltageLimitsEntities = voltageInitSettingInfos.getVoltageLimits().stream().map(VoltageLimitsParameterInfos::toEntity).toList();
        }
        if (voltageLimits == null) {
            voltageLimits = voltageLimitsEntities;
        } else {
            voltageLimits.clear();
            if (voltageLimitsEntities != null) {
                voltageLimits.addAll(voltageLimitsEntities);
            }
        }
        constantQGenerators = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitSettingInfos.getConstantQGenerators());
        variableTwoWindingsTransformers = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitSettingInfos.getVariableTwoWindingsTransformers());
        variableShuntCompensators = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageInitSettingInfos.getVariableShuntCompensators());
        name = voltageInitSettingInfos.getName();
    }

    private List<VoltageLimitsParameterInfos> toVoltageLimitsParameters(List<VoltageLimitsParameterEntity> voltageLimits) {
        List<VoltageLimitsParameterInfos> voltageInitVoltageLimits = null;
        if (voltageLimits != null) {
            voltageInitVoltageLimits = voltageLimits.stream()
                    .map(voltageLimit -> {
                        List<FilterEquipments> filters = FilterEquipmentsEmbeddable
                                .fromEmbeddableFilterEquipments(voltageLimit.getFilters());
                        return new VoltageLimitsParameterInfos(voltageLimit.getPriority(),
                                voltageLimit.getLowVoltageLimit(),
                                voltageLimit.getHighVoltageLimit(), filters);
                    }).toList();
        }
        return voltageInitVoltageLimits;
    }

    public VoltageInitSettingInfos toVoltageInitSettingInfos() {
        return toVoltageInitSettingInfosBuilder().build();
    }

    @Transactional
    private VoltageInitSettingInfos.VoltageInitSettingInfosBuilder<?, ?> toVoltageInitSettingInfosBuilder() {
        return VoltageInitSettingInfos.builder()
                .uuid(this.getId())
                .date(this.getDate())
                .name(this.getName())
                .voltageLimits(toVoltageLimitsParameters(this.getVoltageLimits()))
                .constantQGenerators(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getConstantQGenerators()))
                .variableTwoWindingsTransformers(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableTwoWindingsTransformers()))
                .variableShuntCompensators(FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(this.getVariableShuntCompensators()));
    }
}




