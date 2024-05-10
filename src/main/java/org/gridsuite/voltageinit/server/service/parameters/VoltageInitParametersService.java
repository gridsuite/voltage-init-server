/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import com.google.common.annotations.VisibleForTesting;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride.VoltageLimitType;
import com.powsybl.openreac.parameters.input.algo.ReactiveSlackBusesMode;
import org.apache.commons.lang3.ObjectUtils;
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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Service
public class VoltageInitParametersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitParametersService.class);
    private static final DecimalFormat VOLTAGE_FORMAT = new DecimalFormat("0.0\u202FkV", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final FilterService filterService;

    private final VoltageInitParametersRepository voltageInitParametersRepository;

    public static final double DEFAULT_REACTIVE_SLACKS_THRESHOLD = 500.;

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

    @Transactional
    public double getReactiveSlacksThreshold(UUID parametersUuid) {
        return Optional.ofNullable(parametersUuid)
            .flatMap(voltageInitParametersRepository::findById)
            .map(VoltageInitParametersEntity::toVoltageInitParametersInfos)
            .map(VoltageInitParametersInfos::getReactiveSlacksThreshold)
            .orElse(DEFAULT_REACTIVE_SLACKS_THRESHOLD);
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
                                                  final MutableInt missingVoltageLimitsCounter,
                                                  final MutableInt voltageLimitModificationsCounter,
                                                  Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                                  Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                                  VoltageLevel voltageLevel,
                                                  Map<String, Double> voltageLevelsIdsRestricted) {
        final CounterToIncrement counterToIncrementLow = generateLowVoltageLimit(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, voltageLevel, voltageLevelsIdsRestricted);
        final CounterToIncrement counterToIncrementHigh = generateHighVoltageLimit(specificVoltageLimits, voltageLevelModificationLimits, voltageLevelDefaultLimits, voltageLevel);
        if (counterToIncrementLow == CounterToIncrement.DEFAULT || counterToIncrementLow == CounterToIncrement.BOTH ||
            counterToIncrementHigh == CounterToIncrement.DEFAULT || counterToIncrementHigh == CounterToIncrement.BOTH) {
            missingVoltageLimitsCounter.increment();
        }
        if (counterToIncrementLow == CounterToIncrement.MODIFICATION || counterToIncrementLow == CounterToIncrement.BOTH ||
            counterToIncrementHigh == CounterToIncrement.MODIFICATION || counterToIncrementHigh == CounterToIncrement.BOTH) {
            voltageLimitModificationsCounter.increment();
        }
    }

    private static CounterToIncrement generateLowVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
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
            return CounterToIncrement.MODIFICATION;

        } else if (Double.isNaN(lowVoltageLimit) && lowVoltageDefaultLimit != null) {
            double voltageLimit = lowVoltageDefaultLimit + (lowVoltageModificationLimit != null ? lowVoltageModificationLimit : 0.0);
            if (voltageLimit < 0) {
                newLowVoltageLimit = 0.0;
                voltageLevelsIdsRestricted.put(voltageLevelId, newLowVoltageLimit);
            } else {
                newLowVoltageLimit = voltageLimit;
            }
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevelId, VoltageLimitType.LOW_VOLTAGE_LIMIT, false, newLowVoltageLimit));
            if (lowVoltageModificationLimit != null) {
                return CounterToIncrement.BOTH;
            }
            return CounterToIncrement.DEFAULT;
        }
        return CounterToIncrement.NONE;
    }

    private static CounterToIncrement generateHighVoltageLimit(List<VoltageLimitOverride> specificVoltageLimits,
                                                               Map<String, VoltageLimitEntity> voltageLevelModificationLimits,
                                                               Map<String, VoltageLimitEntity> voltageLevelDefaultLimits,
                                                               VoltageLevel voltageLevel) {
        final String voltageLevelId = voltageLevel.getId();
        final double highVoltageLimit = voltageLevel.getHighVoltageLimit();
        final Double highVoltageModificationLimit = voltageLevelModificationLimits.containsKey(voltageLevelId) ? voltageLevelModificationLimits.get(voltageLevelId).getHighVoltageLimit() : null;
        final Double highVoltageDefaultLimit = voltageLevelDefaultLimits.containsKey(voltageLevelId) ? voltageLevelDefaultLimits.get(voltageLevelId).getHighVoltageLimit() : null;
        if (!Double.isNaN(highVoltageLimit) && highVoltageModificationLimit != null) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevelId, VoltageLimitType.HIGH_VOLTAGE_LIMIT, true, highVoltageModificationLimit));
            return CounterToIncrement.MODIFICATION;
        } else if (Double.isNaN(highVoltageLimit) && highVoltageDefaultLimit != null) {
            specificVoltageLimits.add(new VoltageLimitOverride(voltageLevelId, VoltageLimitType.HIGH_VOLTAGE_LIMIT, false, highVoltageDefaultLimit + (highVoltageModificationLimit != null ? highVoltageModificationLimit : 0.0)));
            if (highVoltageModificationLimit != null) {
                return CounterToIncrement.BOTH;
            } else {
                return CounterToIncrement.DEFAULT;
            }
        } else {
            return CounterToIncrement.NONE;
        }
    }

    @Transactional(readOnly = true)
    public OpenReacParameters buildOpenReacParameters(VoltageInitRunContext context, Network network) {
        final long startTime = System.nanoTime();
        final ReportNode reportNode = context.getReportNode().newReportNode()
                                        .withMessageTemplate("VoltageInitParameters", "VoltageInit parameters")
                                        .withTypedValue("parameters_id", Objects.toString(context.getParametersUuid()), "ID")
                                        .add();
        OpenReacParameters parameters = new OpenReacParameters();
        final MutableInt missingVoltageLimitsCounter = new MutableInt(0);
        final MutableInt voltageLimitModificationsCounter = new MutableInt(0);

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
                    .forEach(voltageLevel -> fillSpecificVoltageLimits(specificVoltageLimits,
                        missingVoltageLimitsCounter, voltageLimitModificationsCounter,
                        voltageLevelModificationLimits, voltageLevelDefaultLimits,
                        voltageLevel, context.getVoltageLevelsIdsRestricted()));
                parameters.addSpecificVoltageLimits(specificVoltageLimits);
                logRestrictedVoltageLevels(reportNode, context.getVoltageLevelsIdsRestricted());
            }

            parameters.addConstantQGenerators(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getConstantQGenerators()))
                    .addVariableTwoWindingsTransformers(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getVariableTwoWindingsTransformers()))
                    .addVariableShuntCompensators(toEquipmentIdsList(context.getNetworkUuid(), context.getVariantId(), voltageInitParameters.getVariableShuntCompensators()));
        });

        logVoltageLimitsModifications(reportNode, network, parameters.getSpecificVoltageLimits());
        logVoltageLimitsModificationCounters(reportNode, missingVoltageLimitsCounter, voltageLimitModificationsCounter);

        //The optimizer will attach reactive slack variables to all buses
        parameters.setReactiveSlackBusesMode(ReactiveSlackBusesMode.ALL);

        LOGGER.info("Parameters built in {}s", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime));
        return parameters;
    }

    private List<String> toEquipmentIdsList(UUID networkUuid, String variantId, List<FilterEquipmentsEmbeddable> filters) {
        if (filters == null || filters.isEmpty()) {
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

    private static void logRestrictedVoltageLevels(final ReportNode reportNode, final Map<String, Double> voltageLevelsIdsRestricted) {
        if (!voltageLevelsIdsRestricted.isEmpty()) {
            reportNode.newReportNode()
                    .withMessageTemplate("restrictedVoltageLevels", "The modifications to the low limits for certain voltage levels have been restricted to avoid negative voltage limits: ${joinedVoltageLevelsIds}")
                    .withUntypedValue("joinedVoltageLevelsIds", voltageLevelsIdsRestricted
                            .entrySet()
                            .stream()
                            .map(entry -> entry.getKey() + " : " + VOLTAGE_FORMAT.format(ObjectUtils.defaultIfNull(entry.getValue(), Double.NaN)))
                            .collect(Collectors.joining(", ")))
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .add();
        }
    }

    private static void logVoltageLimitsModificationCounters(final ReportNode reportNode,
                                                             final MutableInt counterMissingVoltageLimits,
                                                             final MutableInt counterVoltageLimitModifications) {
        reportNode.newReportNode()
                .withMessageTemplate("missingVoltageLimits", "Missing voltage limits of ${nbMissingVoltageLimits} voltage levels have been replaced with user-defined default values.")
                .withUntypedValue("nbMissingVoltageLimits", counterMissingVoltageLimits.longValue())
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        reportNode.newReportNode()
                .withMessageTemplate("voltageLimitModifications", "Voltage limits of ${nbVoltageLimitModifications} voltage levels have been modified according to user input.")
                .withUntypedValue("nbVoltageLimitModifications", counterVoltageLimitModifications.longValue())
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    private static void logVoltageLimitsModifications(final ReportNode reporter,
                                                      final Network network,
                                                      final List<VoltageLimitOverride> specificVoltageLimits) {
        specificVoltageLimits
            .stream()
            .collect(HashMap<String, EnumMap<VoltageLimitType, VoltageLimitOverride>>::new,
                (map, voltageLimitOverride) -> map
                    .computeIfAbsent(voltageLimitOverride.getVoltageLevelId(), key -> new EnumMap<>(VoltageLimitType.class))
                    .put(voltageLimitOverride.getVoltageLimitType(), voltageLimitOverride),
                (map, map2) -> map2.forEach((id, newLimits) -> map.merge(id, newLimits, (newLimit1, newLimit2) -> {
                    newLimit1.putAll(newLimit2);
                    return newLimit1;
                })
            ))
            .forEach((id, voltageLimits) -> {
                final VoltageLevel voltageLevel = network.getVoltageLevel(id);
                final double initialLowVoltageLimit = voltageLevel.getLowVoltageLimit();
                final double initialHighVoltage = voltageLevel.getHighVoltageLimit();
                reporter.newReportNode()
                        .withMessageTemplate("voltageLimitModified", "Voltage limits of ${voltageLevelId} modified: low voltage limit = ${lowVoltageLimit}, high voltage limit = ${highVoltageLimit}")
                        .withTypedValue("voltageLevelId", voltageLevel.getId(), TypedValue.VOLTAGE_LEVEL)
                        .withTypedValue("lowVoltageLimit", computeRelativeVoltageLevel(initialLowVoltageLimit, voltageLimits.get(VoltageLimitType.LOW_VOLTAGE_LIMIT)), TypedValue.VOLTAGE)
                        .withTypedValue("highVoltageLimit", computeRelativeVoltageLevel(initialHighVoltage, voltageLimits.get(VoltageLimitType.HIGH_VOLTAGE_LIMIT)), TypedValue.VOLTAGE)
                        .withSeverity(TypedValue.TRACE_SEVERITY)
                        .add();
            });
    }

    private static String computeRelativeVoltageLevel(final double initialVoltageLimit, @Nullable final VoltageLimitOverride override) {
        if (override == null) {
            return VOLTAGE_FORMAT.format(initialVoltageLimit);
        } else {
            double voltage = (override.isRelative() ? initialVoltageLimit : 0.0) + override.getLimit();
            return VOLTAGE_FORMAT.format(initialVoltageLimit) + " → " + VOLTAGE_FORMAT.format(voltage);
        }
    }

    /**
     * We count modifications per substation only once in {@link #filterService}, not twice
     */
    @VisibleForTesting
    enum CounterToIncrement {
        NONE, DEFAULT, MODIFICATION, BOTH
    }
}
