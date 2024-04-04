/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride.VoltageLimitType;
import com.powsybl.openreac.parameters.input.algo.ReactiveSlackBusesMode;
import org.apache.commons.lang3.mutable.MutableInt;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.IdentifiableAttributes;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.service.VoltageInitRunContext;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;
import org.jetbrains.annotations.Nullable;
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

    private static void fillSpecificVoltageLimits(List<VoltageLimitOverride> specificVoltageLimits,
                                                  final MutableInt counterMissingVoltageLimits,
                                                  final MutableInt counterVoltageLimitModifications,
                                                  Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                                  Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                                  VoltageLevel voltageLevel,
                                                  Map<String, Double> voltageLevelsIdsRestricted) {
        boolean isLowVoltageLimitModificationSet = voltageLevelModificationLimits.containsKey(voltageLevel.getId()) && voltageLevelModificationLimits.get(voltageLevel.getId()).getLowVoltageLimit() != null;
        boolean isHighVoltageLimitModificationSet = voltageLevelModificationLimits.containsKey(voltageLevel.getId()) && voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit() != null;
        boolean isLowVoltageLimitDefaultSet = voltageLevelDefaultLimits.containsKey(voltageLevel.getId()) && voltageLevelDefaultLimits.get(voltageLevel.getId()).getLowVoltageLimit() != null;
        boolean isHighVoltageLimitDefaultSet = voltageLevelDefaultLimits.containsKey(voltageLevel.getId()) && voltageLevelDefaultLimits.get(voltageLevel.getId()).getHighVoltageLimit() != null;

        setLowVoltageLimit(specificVoltageLimits, counterMissingVoltageLimits, counterVoltageLimitModifications, voltageLevelModificationLimits, voltageLevelDefaultLimits, isLowVoltageLimitModificationSet, isLowVoltageLimitDefaultSet, voltageLevel, voltageLevelsIdsRestricted);
        setHighVoltageLimit(specificVoltageLimits, counterMissingVoltageLimits, counterVoltageLimitModifications, voltageLevelModificationLimits, voltageLevelDefaultLimits, isHighVoltageLimitModificationSet, isHighVoltageLimitDefaultSet, voltageLevel);
    }

    private static void setLowVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
                                           final MutableInt counterMissingVoltageLimits,
                                           final MutableInt counterVoltageLimitModifications,
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
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.LOW_VOLTAGE_LIMIT, true, newLowVoltageLimit));
            counterVoltageLimitModifications.increment();

        } else if (Double.isNaN(lowVoltageLimit) && isLowVoltageLimitDefaultSet) {
            double voltageLimit = voltageLevelDefaultLimits.get(voltageLevel.getId()).getLowVoltageLimit() + (isLowVoltageLimitModificationSet ? voltageLevelModificationLimits.get(voltageLevel.getId()).getLowVoltageLimit() : 0.);
            if (voltageLimit < 0) {
                newLowVoltageLimit = 0.0;
                voltageLevelsIdsRestricted.put(voltageLevel.getId(), newLowVoltageLimit);
            } else {
                newLowVoltageLimit = voltageLimit;
            }
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.LOW_VOLTAGE_LIMIT, false, newLowVoltageLimit));
            counterMissingVoltageLimits.increment();
            if (isLowVoltageLimitModificationSet) {
                counterVoltageLimitModifications.increment();
            }
        }
    }

    private static void setHighVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
                                            final MutableInt counterMissingVoltageLimits,
                                            final MutableInt counterVoltageLimitModifications,
                                            Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                            Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                            boolean isHighVoltageLimitModificationSet,
                                            boolean isHighVoltageLimitDefaultSet,
                                            VoltageLevel voltageLevel) {
        if (!Double.isNaN(voltageLevel.getHighVoltageLimit()) && isHighVoltageLimitModificationSet) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.HIGH_VOLTAGE_LIMIT, true, voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit()));
            counterVoltageLimitModifications.increment();
        } else if (Double.isNaN(voltageLevel.getHighVoltageLimit()) && isHighVoltageLimitDefaultSet) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.HIGH_VOLTAGE_LIMIT, false, voltageLevelDefaultLimits.get(voltageLevel.getId()).getHighVoltageLimit() + (isHighVoltageLimitModificationSet ? voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit() : 0.)));
            counterMissingVoltageLimits.increment();
            if (isHighVoltageLimitModificationSet) {
                counterVoltageLimitModifications.increment();
            }
        }
    }

    @Transactional(readOnly = true)
    public OpenReacParameters buildOpenReacParameters(VoltageInitRunContext context, Network network) {
        final long startTime = System.nanoTime();
        final Reporter reporter = context.getRootReporter().createSubReporter("OpenReacParameters", "OpenReac parameters", Map.of(
                "parameters_id", new TypedValue(Objects.toString(context.getParametersUuid()), "ID")
        ));

        Optional<VoltageInitParametersEntity> voltageInitParametersEntity = Optional.empty();
        if (context.getParametersUuid() != null) {
            voltageInitParametersEntity = voltageInitParametersRepository.findById(context.getParametersUuid());
        }

        OpenReacParameters parameters = new OpenReacParameters();
        List<VoltageLimitOverride> specificVoltageLimits = new ArrayList<>();
        List<String> constantQGenerators = new ArrayList<>();
        List<String> variableTwoWindingsTransformers = new ArrayList<>();
        List<String> variableShuntCompensators = new ArrayList<>();
        final MutableInt counterMissingVoltageLimits = new MutableInt(0);
        final MutableInt counterVoltageLimitModifications = new MutableInt(0);

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
                    .forEach(voltageLevel -> fillSpecificVoltageLimits(specificVoltageLimits,
                        counterMissingVoltageLimits, counterVoltageLimitModifications,
                        voltageLevelModificationLimits, voltageLevelDefaultLimits,
                        voltageLevel, context.getVoltageLevelsIdsRestricted()));

                if (!context.getVoltageLevelsIdsRestricted().isEmpty()) {
                    reporter.report(Report.builder()
                        .withKey("restrictedVoltageLevels")
                        .withDefaultMessage("The modifications to the low limits for certain voltage levels have been restricted to avoid negative voltage limits: ${joinedVoltageLevelsIds}")
                        .withValue("joinedVoltageLevelsIds", context.getVoltageLevelsIdsRestricted()
                            .entrySet()
                            .stream()
                            .map(entry -> entry.getKey() + ":" + entry.getValue())
                            .collect(Collectors.joining(", ")))
                        .withSeverity(TypedValue.WARN_SEVERITY)
                        .build());
                }
            }

            constantQGenerators.addAll(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getConstantQGenerators()));
            variableTwoWindingsTransformers.addAll(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getVariableTwoWindingsTransformers()));
            variableShuntCompensators.addAll(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getVariableShuntCompensators()));
        });
        parameters.addSpecificVoltageLimits(specificVoltageLimits)
            .addConstantQGenerators(constantQGenerators)
            .addVariableTwoWindingsTransformers(variableTwoWindingsTransformers)
            .addVariableShuntCompensators(variableShuntCompensators);

        parameters.getSpecificVoltageLimits()
            .stream()
            .collect(HashMap<String, EnumMap<VoltageLimitType, VoltageLimitOverride>>::new,
                (map, voltageLimitOverride) -> map
                    .computeIfAbsent(voltageLimitOverride.getVoltageLevelId(), key -> new EnumMap<>(VoltageLimitType.class))
                    .put(voltageLimitOverride.getVoltageLimitType(), voltageLimitOverride),
                (map, map2) -> map2.forEach((id, newLimits) -> map.merge(id, newLimits, (nl1, nl2) -> {
                    nl1.putAll(nl2);
                    return nl1;
                })
            ))
            .forEach((id, voltageLimits) -> {
                final VoltageLevel voltageLevel = network.getVoltageLevel(id);
                final double initialLowVoltageLimit = voltageLevel.getLowVoltageLimit();
                final double initialHighVoltage = voltageLevel.getHighVoltageLimit();
                reporter.report(Report.builder()
                        .withKey("voltageLimitModified")
                        .withDefaultMessage("One or two voltage limits of voltage level ${voltageLevelId} have been replaced and/or modified: low voltage limit = ${newLowVoltageLimit}\u202FkV, high voltage limit = ${newHighVoltageLimit}\u202FkV (initial values: low voltage limit = ${initialLowVoltageLimit}\u202FkV, high voltage limit = ${initialHighVoltage}\u202FkV).")
                        .withTypedValue("voltageLevelId", voltageLevel.getId(), TypedValue.VOLTAGE_LEVEL)
                        .withTypedValue("newLowVoltageLimit", computeRelativeVoltageLevel(initialLowVoltageLimit, voltageLimits.get(VoltageLimitType.LOW_VOLTAGE_LIMIT)), TypedValue.VOLTAGE)
                        .withTypedValue("newHighVoltageLimit", computeRelativeVoltageLevel(initialHighVoltage, voltageLimits.get(VoltageLimitType.HIGH_VOLTAGE_LIMIT)), TypedValue.VOLTAGE)
                        .withTypedValue("initialLowVoltageLimit", initialLowVoltageLimit, TypedValue.VOLTAGE)
                        .withTypedValue("initialHighVoltage", initialHighVoltage, TypedValue.VOLTAGE)
                        .withSeverity(TypedValue.TRACE_SEVERITY)
                        .build());
            });
        reporter.report(Report.builder()
                              .withKey("missingVoltageLimits")
                              .withDefaultMessage("Missing voltage limits of ${nbMissingVoltageLimits} voltage levels have been replaced with user-defined default values.")
                              .withValue("nbMissingVoltageLimits", counterMissingVoltageLimits.longValue())
                              .withSeverity(TypedValue.INFO_SEVERITY)
                              .build());
        reporter.report(Report.builder()
                              .withKey("voltageLimitModifications")
                              .withDefaultMessage("Voltage limits of ${nbVoltageLimitModifications} voltage levels have been modified according to user input.")
                              .withValue("nbVoltageLimitModifications", counterVoltageLimitModifications.longValue())
                              .withSeverity(TypedValue.INFO_SEVERITY)
                              .build());

        //The optimizer will attach reactive slack variables to all buses
        parameters.setReactiveSlackBusesMode(ReactiveSlackBusesMode.ALL);

        LOGGER.info("Parameters built in {}s", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime));
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

    private double computeRelativeVoltageLevel(final double initialVoltageLimit, @Nullable final VoltageLimitOverride override) {
        if (override == null) {
            return initialVoltageLimit;
        } else {
            return (override.isRelative() ? initialVoltageLimit : 0.0) + override.getLimit();
        }
    }
}
