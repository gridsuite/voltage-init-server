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

import com.google.common.annotations.VisibleForTesting;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride.VoltageLimitType;
import com.powsybl.openreac.parameters.input.algo.ReactiveSlackBusesMode;
import lombok.NonNull;
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

    @SuppressWarnings({"EnumSwitchStatementWhichMissesCases", "java:S131"})
    // it's wanted to not have a case for NONE in switch, as we do nothing in this case
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

        final CountVoltageLimit counterToIncrement1 = generateLowVoltageLimit(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, isLowVoltageLimitModificationSet, isLowVoltageLimitDefaultSet, voltageLevel, voltageLevelsIdsRestricted);
        final CountVoltageLimit counterToIncrement2 = generateHighVoltageLimit(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, isHighVoltageLimitModificationSet, isHighVoltageLimitDefaultSet, voltageLevel);
        if ((counterToIncrement1 == CountVoltageLimit.DEFAULT || counterToIncrement1 == CountVoltageLimit.BOTH) ||
            (counterToIncrement2 == CountVoltageLimit.DEFAULT || counterToIncrement2 == CountVoltageLimit.BOTH)) {
            counterMissingVoltageLimits.increment();
        }
        if ((counterToIncrement1 == CountVoltageLimit.MODIFICATION || counterToIncrement1 == CountVoltageLimit.BOTH) ||
            (counterToIncrement2 == CountVoltageLimit.MODIFICATION || counterToIncrement2 == CountVoltageLimit.BOTH)) {
            counterVoltageLimitModifications.increment();
        }
    }

    private static CountVoltageLimit generateLowVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
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
            return CountVoltageLimit.MODIFICATION;

        } else if (Double.isNaN(lowVoltageLimit) && isLowVoltageLimitDefaultSet) {
            double voltageLimit = voltageLevelDefaultLimits.get(voltageLevel.getId()).getLowVoltageLimit() + (isLowVoltageLimitModificationSet ? voltageLevelModificationLimits.get(voltageLevel.getId()).getLowVoltageLimit() : 0.);
            if (voltageLimit < 0) {
                newLowVoltageLimit = 0.0;
                voltageLevelsIdsRestricted.put(voltageLevel.getId(), newLowVoltageLimit);
            } else {
                newLowVoltageLimit = voltageLimit;
            }
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.LOW_VOLTAGE_LIMIT, false, newLowVoltageLimit));
            if (isLowVoltageLimitModificationSet) {
                return CountVoltageLimit.DEFAULT;
            } else {
                return CountVoltageLimit.BOTH;
            }
        } else {
            return CountVoltageLimit.NONE;
        }
    }

    private static CountVoltageLimit generateHighVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
                                                              Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                                              Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                                              boolean isHighVoltageLimitModificationSet,
                                                              boolean isHighVoltageLimitDefaultSet,
                                                              VoltageLevel voltageLevel) {
        if (!Double.isNaN(voltageLevel.getHighVoltageLimit()) && isHighVoltageLimitModificationSet) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.HIGH_VOLTAGE_LIMIT, true, voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit()));
            return CountVoltageLimit.MODIFICATION;
        } else if (Double.isNaN(voltageLevel.getHighVoltageLimit()) && isHighVoltageLimitDefaultSet) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevel.getId(), VoltageLimitType.HIGH_VOLTAGE_LIMIT, false, voltageLevelDefaultLimits.get(voltageLevel.getId()).getHighVoltageLimit() + (isHighVoltageLimitModificationSet ? voltageLevelModificationLimits.get(voltageLevel.getId()).getHighVoltageLimit() : 0.)));
            if (isHighVoltageLimitModificationSet) {
                return CountVoltageLimit.BOTH;
            } else {
                return CountVoltageLimit.DEFAULT;
            }
        } else {
            return CountVoltageLimit.NONE;
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
                            .map(entry -> entry.getKey() + " : " + entry.getValue())
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
                        .withDefaultMessage("Voltage limits of ${voltageLevelId} modified: low voltage limit = ${lowVoltageLimit}, high voltage limit = ${highVoltageLimit}")
                        .withTypedValue("voltageLevelId", voltageLevel.getId(), TypedValue.VOLTAGE_LEVEL)
                        .withTypedValue("lowVoltageLimit", computeRelativeVoltageLevel(initialLowVoltageLimit, voltageLimits.get(VoltageLimitType.LOW_VOLTAGE_LIMIT)), TypedValue.VOLTAGE)
                        .withTypedValue("highVoltageLimit", computeRelativeVoltageLevel(initialHighVoltage, voltageLimits.get(VoltageLimitType.HIGH_VOLTAGE_LIMIT)), TypedValue.VOLTAGE)
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

    private static String voltageToString(double voltage) {
        return Double.isNaN(voltage) ? Double.toString(voltage) : voltage + "\u202FkV";
    }
    private static String computeRelativeVoltageLevel(final double initialVoltageLimit, @Nullable final VoltageLimitOverride override) {
        if (override == null) {
            return voltageToString(initialVoltageLimit);
        } else {
            return voltageToString(initialVoltageLimit) + " → " + voltageToString((override.isRelative() ? initialVoltageLimit : 0.0) + override.getLimit());
        }
    }

    /**
     * We count modifications per substation only once in {@link #filterService}, not twice
     */
    @VisibleForTesting
    enum CountVoltageLimit {
        NONE, DEFAULT, MODIFICATION, BOTH;

        public CountVoltageLimit merge(@NonNull final CountVoltageLimit other) {
            if (this == BOTH || other == BOTH || this == DEFAULT && other == MODIFICATION || this == MODIFICATION && other == DEFAULT) {
                return BOTH;
            } else if (this == NONE) {
                return other;
            } else { // other == NONE
                return this;
            }
        }
    }
}
