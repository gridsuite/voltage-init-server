/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import com.powsybl.openreac.parameters.input.algo.ReactiveSlackBusesMode;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.IdentifiableAttributes;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.service.VoltageInitRunContext;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Service
public class VoltageInitParametersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitParametersService.class);

    private final FilterService filterService;

    private final VoltageInitParametersRepository voltageInitParametersRepository;

    public VoltageInitParametersService(VoltageInitParametersRepository voltageInitParametersRepository, FilterService filterService) {
        this.voltageInitParametersRepository = voltageInitParametersRepository;
        this.filterService = filterService;
    }

    public UUID createParameters(VoltageInitParametersInfos parametersInfos) {
        return voltageInitParametersRepository.save(parametersInfos.toEntity()).getId();
    }

    public Optional<UUID> duplicateParameters(UUID sourceParametersId) {
        Optional<VoltageInitParametersInfos> sourceVoltageInitParametersInfos = voltageInitParametersRepository.findById(sourceParametersId).map(VoltageInitParametersEntity::toVoltageInitParametersInfos);
        if (sourceVoltageInitParametersInfos.isPresent()) {
            VoltageInitParametersEntity entity = new VoltageInitParametersEntity(sourceVoltageInitParametersInfos.get());
            voltageInitParametersRepository.save(entity);
            return Optional.of(entity.getId());
        }
        return Optional.empty();
    }

    @Transactional
    public VoltageInitParametersInfos getParameters(UUID parametersUuid) {
        return voltageInitParametersRepository.findById(parametersUuid).map(VoltageInitParametersEntity::toVoltageInitParametersInfos).orElse(null);
    }

    public List<VoltageInitParametersInfos> getAllParameters() {
        return voltageInitParametersRepository.findAll().stream().map(VoltageInitParametersEntity::toVoltageInitParametersInfos).toList();
    }

    @Transactional
    public void updateParameters(UUID parametersUuid, VoltageInitParametersInfos parametersInfos) {
        voltageInitParametersRepository.findById(parametersUuid).orElseThrow().update(parametersInfos);
    }

    public void deleteParameters(UUID parametersUuid) {
        voltageInitParametersRepository.deleteById(parametersUuid);
    }

    private Map<String, VoltageLimitEntity> resolveVoltageLevelLimits(VoltageInitRunContext context, List<VoltageLimitEntity> voltageLimits) {
        Map<String, VoltageLimitEntity> voltageLevelLimits = new HashMap<>();
        //each voltage level is associated to a voltage limit setting
        //if a voltage level is resolved by multiple filters the highest priority setting will be kept
        voltageLimits.stream().forEach(voltageLimit ->
            filterService.exportFilters(voltageLimit.getFilters().stream().map(FilterEquipmentsEmbeddable::getFilterId).toList(), context.getNetworkUuid(), context.getVariantId())
                .forEach(filterEquipment -> filterEquipment.getIdentifiableAttributes().stream().map(IdentifiableAttributes::getId)
                    .forEach(voltageLevelsId -> voltageLevelLimits.put(voltageLevelsId, voltageLimit))
                )
        );
        return voltageLevelLimits;
    }

    private void fillSpecificVoltageLimits(List<VoltageLimitOverride> specificVoltageLimits, Map<String, VoltageLimitEntity> voltageLevelModificationLimits, Map<String, VoltageLimitEntity> voltageLevelDefaultLimits, VoltageLevel voltageLevel, Map<String, Double> voltageLevelsIdsRestricted) {
        boolean isLowVoltageLimitModificationSet = voltageLevelModificationLimits.containsKey(voltageLevel.getId()) && voltageLevelModificationLimits.get(voltageLevel.getId()).getLowVoltageLimit() != null;
        boolean isHighVoltageLimitModificationSet = voltageLevelModificationLimits.containsKey(voltageLevel.getId()) && voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit() != null;
        boolean isLowVoltageLimitDefaultSet = voltageLevelDefaultLimits.containsKey(voltageLevel.getId()) && voltageLevelDefaultLimits.get(voltageLevel.getId()).getLowVoltageLimit() != null;
        boolean isHighVoltageLimitDefaultSet = voltageLevelDefaultLimits.containsKey(voltageLevel.getId()) && voltageLevelDefaultLimits.get(voltageLevel.getId()).getHighVoltageLimit() != null;

        setLowVoltageLimit(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, isLowVoltageLimitModificationSet, isLowVoltageLimitDefaultSet, voltageLevel, voltageLevelsIdsRestricted);
        setHighVoltageLimit(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, isHighVoltageLimitModificationSet, isHighVoltageLimitDefaultSet, voltageLevel);
    }

    private void setLowVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
                                    Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                    Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                    boolean isLowVoltageLimitModificationSet,
                                    boolean isLowVoltageLimitDefaultSet,
                                    VoltageLevel voltageLevel,
                                    Map<String, Double> voltageLevelsIdsRestricted) {
        double newLowVoltageLimit;
        double lowVoltageLimit = voltageLevel.getLowVoltageLimit();
        if (!Double.isNaN(lowVoltageLimit) && isLowVoltageLimitModificationSet) {
            double lowVoltageLimitModification = voltageLevelModificationLimits.get(voltageLevel.getId()).getLowVoltageLimit();

            if (lowVoltageLimit + lowVoltageLimitModification < 0) {
                newLowVoltageLimit = lowVoltageLimit * -1;
                voltageLevelsIdsRestricted.put(voltageLevel.getId(), newLowVoltageLimit);
            } else {
                newLowVoltageLimit = lowVoltageLimitModification;
            }
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitOverride.VoltageLimitType.LOW_VOLTAGE_LIMIT, true, newLowVoltageLimit));

        } else if (Double.isNaN(lowVoltageLimit) && isLowVoltageLimitDefaultSet) {
            double voltageLimit = voltageLevelDefaultLimits.get(voltageLevel.getId()).getLowVoltageLimit() + (isLowVoltageLimitModificationSet ? voltageLevelModificationLimits.get(voltageLevel.getId()).getLowVoltageLimit() : 0.);
            if (voltageLimit < 0) {
                newLowVoltageLimit = 0.0;
                voltageLevelsIdsRestricted.put(voltageLevel.getId(), newLowVoltageLimit);
            } else {
                newLowVoltageLimit = voltageLimit;
            }
            specificVoltageLimits.add(
                new VoltageLimitOverride(voltageLevel.getId(),
                    VoltageLimitOverride.VoltageLimitType.LOW_VOLTAGE_LIMIT,
                    false,
                    newLowVoltageLimit
                ));
        }
    }

    private void setHighVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits, Map<String, VoltageLimitEntity> voltageLevelModificationLimits, Map<String, VoltageLimitEntity> voltageLevelDefaultLimits, boolean isHighVoltageLimitModificationSet, boolean isHighVoltageLimitDefaultSet, VoltageLevel voltageLevel) {
        if (!Double.isNaN(voltageLevel.getHighVoltageLimit()) && isHighVoltageLimitModificationSet) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitOverride.VoltageLimitType.HIGH_VOLTAGE_LIMIT, true, voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit()));
        } else if (Double.isNaN(voltageLevel.getHighVoltageLimit()) && isHighVoltageLimitDefaultSet) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitOverride.VoltageLimitType.HIGH_VOLTAGE_LIMIT, false, voltageLevelDefaultLimits.get(voltageLevel.getId()).getHighVoltageLimit() + (isHighVoltageLimitModificationSet ? voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit() : 0.)));
        }
    }

    @Transactional(readOnly = true)
    public OpenReacParameters buildOpenReacParameters(VoltageInitRunContext context, Network network) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());

        Optional<VoltageInitParametersEntity> voltageInitParametersEntity = Optional.empty();
        if (context.getParametersUuid() != null) {
            voltageInitParametersEntity = voltageInitParametersRepository.findById(context.getParametersUuid());
        }

        OpenReacParameters parameters = new OpenReacParameters();
        List<VoltageLimitOverride> specificVoltageLimits = new ArrayList<>();
        List<String> constantQGenerators = new ArrayList<>();
        List<String> variableTwoWindingsTransformers = new ArrayList<>();
        List<String> variableShuntCompensators = new ArrayList<>();

        voltageInitParametersEntity.ifPresent(voltageInitParameters -> {
            if (voltageInitParameters.getVoltageLimits() != null) {
                Map<String, VoltageLimitEntity> voltageLevelDefaultLimits = resolveVoltageLevelLimits(context, voltageInitParameters.getVoltageLimits().stream()
                    .filter(voltageLimit -> VoltageLimitParameterType.DEFAULT.equals(voltageLimit.getVoltageLimitParameterType()))
                    .toList());

                Map<String, VoltageLimitEntity> voltageLevelModificationLimits = resolveVoltageLevelLimits(context, voltageInitParameters.getVoltageLimits().stream()
                    .filter(voltageLimit -> VoltageLimitParameterType.MODIFICATION.equals(voltageLimit.getVoltageLimitParameterType()))
                    .toList());

                network.getVoltageLevelStream()
                    .filter(voltageLevel -> voltageLevelDefaultLimits.keySet().contains(voltageLevel.getId()) || voltageLevelModificationLimits.keySet().contains(voltageLevel.getId()))
                    .forEach(voltageLevel -> fillSpecificVoltageLimits(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, voltageLevel, context.getVoltageLevelsIdsRestricted()));
            }

            constantQGenerators.addAll(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getConstantQGenerators()));
            variableTwoWindingsTransformers.addAll(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getVariableTwoWindingsTransformers()));
            variableShuntCompensators.addAll(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getVariableShuntCompensators()));
        });
        parameters.addSpecificVoltageLimits(specificVoltageLimits)
            .addConstantQGenerators(constantQGenerators)
            .addVariableTwoWindingsTransformers(variableTwoWindingsTransformers)
            .addVariableShuntCompensators(variableShuntCompensators);

        //The optimizer will attach reactive slack variables to all buses
        parameters.setReactiveSlackBusesMode(ReactiveSlackBusesMode.ALL);

        long nanoTime = System.nanoTime();
        LOGGER.info("Parameters built in {}s", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.getAndSet(nanoTime)));
        return parameters;
    }

    private List<String> toEquipmentIdsList(UUID networkUuid, String variantId, List<FilterEquipmentsEmbeddable> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        List<FilterEquipments> equipments = filterService.exportFilters(filters.stream().map(FilterEquipmentsEmbeddable::getFilterId).toList(), networkUuid, variantId);
        Set<String> ids = new HashSet<>();
        equipments.forEach(filterEquipment ->
            filterEquipment.getIdentifiableAttributes().forEach(identifiableAttribute ->
                ids.add(identifiableAttribute.getId())
            )
        );
        return new ArrayList<>(ids);
    }
}
