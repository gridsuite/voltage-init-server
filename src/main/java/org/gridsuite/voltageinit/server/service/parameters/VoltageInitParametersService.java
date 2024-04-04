/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride.VoltageLimitType;
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

import java.util.*;
import java.util.concurrent.TimeUnit;

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
        //if a voltage level is resolved by multiple filters, the highest priority setting will be kept
        voltageLimits.forEach(voltageLimit ->
            filterService.exportFilters(voltageLimit.getFilters().stream().map(FilterEquipmentsEmbeddable::getFilterId).toList(), context.getNetworkUuid(), context.getVariantId())
                         .stream()
                         .map(FilterEquipments::getIdentifiableAttributes)
                         .flatMap(List::stream)
                         .map(IdentifiableAttributes::getId)
                         .forEach(voltageLevelsId -> voltageLevelLimits.put(voltageLevelsId, voltageLimit))
        );
        return voltageLevelLimits;
    }

    private static void fillSpecificVoltageLimits(List<VoltageLimitOverride> specificVoltageLimits,
                                                  Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                                  Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                                  VoltageLevel voltageLevel,
                                                  Map<String, Double> voltageLevelsIdsRestricted) {
        setLowVoltageLimit(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, voltageLevel, voltageLevelsIdsRestricted);
        setHighVoltageLimit(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, voltageLevel);
    }

    private static void setLowVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
                                           Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                           Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                           VoltageLevel voltageLevel,
                                           Map<String, Double> voltageLevelsIdsRestricted) {
        final String voltageLevelId = voltageLevel.getId();
        final Double lowVoltageModificationLimit = voltageLevelModificationLimits.containsKey(voltageLevelId) ? voltageLevelModificationLimits.get(voltageLevelId).getLowVoltageLimit() : null;
        final Double lowVoltageDefaultLimit = voltageLevelDefaultLimits.containsKey(voltageLevelId) ? voltageLevelDefaultLimits.get(voltageLevelId).getLowVoltageLimit() : null;
        final double lowVoltageLimit = voltageLevel.getLowVoltageLimit();
        double newLowVoltageLimit;

        if (!Double.isNaN(lowVoltageLimit) && lowVoltageModificationLimit != null) {
            if (lowVoltageLimit + lowVoltageModificationLimit < 0) {
                newLowVoltageLimit = lowVoltageLimit * -1;
                voltageLevelsIdsRestricted.put(voltageLevelId, newLowVoltageLimit);
            } else {
                newLowVoltageLimit = lowVoltageModificationLimit;
            }
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevelId, VoltageLimitType.LOW_VOLTAGE_LIMIT, true, newLowVoltageLimit));

        } else if (Double.isNaN(lowVoltageLimit) && lowVoltageDefaultLimit != null) {
            double voltageLimit = lowVoltageDefaultLimit + (lowVoltageModificationLimit != null ? lowVoltageModificationLimit : 0.0);
            if (voltageLimit < 0) {
                newLowVoltageLimit = 0.0;
                voltageLevelsIdsRestricted.put(voltageLevelId, newLowVoltageLimit);
            } else {
                newLowVoltageLimit = voltageLimit;
            }
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevelId, VoltageLimitType.LOW_VOLTAGE_LIMIT, false, newLowVoltageLimit));
        }
    }

    private static void setHighVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
                                            Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                            Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                            VoltageLevel voltageLevel) {
        final String voltageLevelId = voltageLevel.getId();
        final double highVoltageLimit = voltageLevel.getHighVoltageLimit();
        final Double highVoltageModificationLimit = voltageLevelModificationLimits.containsKey(voltageLevelId) ? voltageLevelModificationLimits.get(voltageLevelId).getHighVoltageLimit() : null;
        final Double highVoltageDefaultLimit = voltageLevelDefaultLimits.containsKey(voltageLevelId) ? voltageLevelDefaultLimits.get(voltageLevelId).getHighVoltageLimit() : null;
        if (!Double.isNaN(highVoltageLimit) && highVoltageModificationLimit != null) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevelId, VoltageLimitType.HIGH_VOLTAGE_LIMIT, true, highVoltageModificationLimit));
        } else if (Double.isNaN(highVoltageLimit) && highVoltageDefaultLimit != null) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevelId, VoltageLimitType.HIGH_VOLTAGE_LIMIT, false, highVoltageDefaultLimit + (highVoltageModificationLimit != null ? highVoltageModificationLimit : 0.0)));
        }
    }

    @Transactional(readOnly = true)
    public OpenReacParameters buildOpenReacParameters(VoltageInitRunContext context, Network network) {
        final long startTime = System.nanoTime();
        OpenReacParameters parameters = new OpenReacParameters();

        Optional.ofNullable(context.getParametersUuid()).flatMap(voltageInitParametersRepository::findById).ifPresent(voltageInitParameters -> {
            if (voltageInitParameters.getVoltageLimits() != null) {
                Map<String, VoltageLimitEntity> voltageLevelDefaultLimits = resolveVoltageLevelLimits(context, voltageInitParameters.getVoltageLimits().stream()
                    .filter(voltageLimit -> VoltageLimitParameterType.DEFAULT.equals(voltageLimit.getVoltageLimitParameterType()))
                    .toList());

                Map<String, VoltageLimitEntity> voltageLevelModificationLimits = resolveVoltageLevelLimits(context, voltageInitParameters.getVoltageLimits().stream()
                    .filter(voltageLimit -> VoltageLimitParameterType.MODIFICATION.equals(voltageLimit.getVoltageLimitParameterType()))
                    .toList());

                List<VoltageLimitOverride> specificVoltageLimits = new LinkedList<>();
                network.getVoltageLevelStream()
                    .filter(voltageLevel -> voltageLevelDefaultLimits.containsKey(voltageLevel.getId()) || voltageLevelModificationLimits.containsKey(voltageLevel.getId()))
                    .forEach(voltageLevel -> fillSpecificVoltageLimits(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, voltageLevel, context.getVoltageLevelsIdsRestricted()));
                parameters.addSpecificVoltageLimits(specificVoltageLimits);
            }

            parameters.addConstantQGenerators(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getConstantQGenerators()))
                    .addVariableTwoWindingsTransformers(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getVariableTwoWindingsTransformers()))
                    .addVariableShuntCompensators(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getVariableShuntCompensators()));
        });

        //The optimizer will attach reactive slack variables to all buses
        parameters.setReactiveSlackBusesMode(ReactiveSlackBusesMode.ALL);

        LOGGER.info("Parameters built in {}s", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime));
        return parameters;
    }

    private List<String> toEquipmentIdsList(UUID networkUuid, String variantId, List<FilterEquipmentsEmbeddable> filters) {
        if (filters == null) {
            return List.of();
        }
        return filterService.exportFilters(filters.stream().map(FilterEquipmentsEmbeddable::getFilterId).toList(), networkUuid, variantId)
                            .stream()
                            .map(FilterEquipments::getIdentifiableAttributes)
                            .flatMap(List::stream)
                            .map(IdentifiableAttributes::getId)
                            .distinct()
                            .toList();
    }
}
