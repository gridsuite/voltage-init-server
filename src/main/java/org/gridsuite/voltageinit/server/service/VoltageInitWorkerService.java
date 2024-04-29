/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.openreac.OpenReacConfig;
import com.powsybl.openreac.OpenReacRunner;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import com.powsybl.openreac.parameters.output.OpenReacStatus;
import org.gridsuite.voltageinit.server.computation.service.AbstractResultContext;
import org.gridsuite.voltageinit.server.computation.service.AbstractWorkerService;
import org.gridsuite.voltageinit.server.computation.service.ExecutionService;
import org.gridsuite.voltageinit.server.computation.service.ReportService;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.service.parameters.VoltageInitParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.gridsuite.voltageinit.server.service.VoltageInitNotificationService.HEADER_REACTIVE_SLACKS_OVER_THRESHOLD;
import static org.gridsuite.voltageinit.server.service.VoltageInitNotificationService.HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitWorkerService extends AbstractWorkerService<OpenReacResult, VoltageInitRunContext, Void, VoltageInitResultService> {

    public static final String COMPUTATION_TYPE = "VoltageInit";
    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitWorkerService.class);

    private static final String ERROR = "error";
    private static final String ERROR_DURING_VOLTAGE_PROFILE_INITIALISATION = "Error during voltage profile initialization";

    private final NetworkModificationService networkModificationService;

    private final VoltageInitParametersService voltageInitParametersService;

    public VoltageInitWorkerService(NetworkStoreService networkStoreService,
                                    VoltageInitNotificationService voltageInitNotificationService,
                                    ExecutionService executionService,
                                    NetworkModificationService networkModificationService,
                                    VoltageInitParametersService voltageInitParametersService,
                                    VoltageInitResultService resultService,
                                    ReportService reportService,
                                    VoltageInitObserver voltageInitObserver) {
        super(networkStoreService, voltageInitNotificationService, reportService, resultService, executionService, voltageInitObserver, null);
        this.networkModificationService = Objects.requireNonNull(networkModificationService);
        this.voltageInitParametersService = Objects.requireNonNull(voltageInitParametersService);
    }

    @Override
    protected PreloadingStrategy getNetworkPreloadingStrategy() {
        return PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW;
    }

    @Override
    protected VoltageInitResultContext fromMessage(Message<String> message) {
        return VoltageInitResultContext.fromMessage(message);
    }

    private boolean checkReactiveSlacksOverThreshold(OpenReacResult openReacResult, double reactiveSlacksThreshold, Reporter reporter) {
        boolean isOverThreshold = openReacResult.getReactiveSlacks().stream().anyMatch(r -> Math.abs(r.slack) > reactiveSlacksThreshold);
        if (isOverThreshold) {
            reporter.report(Report.builder()
                    .withKey("reactiveSlacksOverThreshold")
                    .withDefaultMessage("Reactive slack exceeds ${threshold} MVar for at least one bus")
                    .withValue("threshold", reactiveSlacksThreshold)
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .build());
        }
        return isOverThreshold;
    }

    protected CompletableFuture<OpenReacResult> getCompletableFuture(Network network, VoltageInitRunContext context, String provider, UUID resultUuid) {
        OpenReacParameters parameters = voltageInitParametersService.buildOpenReacParameters(context, network);
        OpenReacConfig config = OpenReacConfig.load();
        return OpenReacRunner.runAsync(network, network.getVariantManager().getWorkingVariantId(), parameters, config, executionService.getComputationManager());
    }

    @Override
    protected void handleNonCancellationException(AbstractResultContext<VoltageInitRunContext> resultContext, Exception exception) {
        Map<String, String> errorIndicators = new HashMap<>();
        errorIndicators.put(ERROR, ERROR_DURING_VOLTAGE_PROFILE_INITIALISATION);
        resultService.insertErrorResult(resultContext.getResultUuid(), errorIndicators);
        resultService.insertStatus(List.of(resultContext.getResultUuid()), VoltageInitStatus.NOT_OK);
    }

    private UUID createModificationGroup(OpenReacResult openReacResult, Network network, boolean updateBusVoltage) {
        return openReacResult.getStatus() == OpenReacStatus.OK ?
                networkModificationService.createVoltageInitModificationGroup(network, openReacResult, updateBusVoltage) :
                null;
    }

    @Bean
    @Override
    public Consumer<Message<String>> consumeRun() {
        return super.consumeRun();
    }

    @Bean
    @Override
    public Consumer<Message<String>> consumeCancel() {
        return super.consumeCancel();
    }

    @Override
    protected void saveResult(Network network, AbstractResultContext<VoltageInitRunContext> resultContext, OpenReacResult result) {
        VoltageInitRunContext context = resultContext.getRunContext();
        UUID parametersUuid = context.getParametersUuid();
        VoltageInitParametersInfos param = parametersUuid != null ? voltageInitParametersService.getParameters(parametersUuid) : null;
        boolean updateBusVoltage = param == null || param.isUpdateBusVoltage();
        UUID modificationsGroupUuid = createModificationGroup(result, network, updateBusVoltage);
        Map<String, Bus> networkBuses = network.getBusView().getBusStream().collect(Collectors.toMap(Bus::getId, Function.identity()));
        // check if at least one reactive slack over the threshold value
        double reactiveSlacksThreshold = voltageInitParametersService.getReactiveSlacksThreshold(context.getParametersUuid());
        boolean resultCheckReactiveSlacks = checkReactiveSlacksOverThreshold(result, reactiveSlacksThreshold, context.getReporter());
        resultService.insert(resultContext.getResultUuid(), result, networkBuses, modificationsGroupUuid, result.getStatus().name(), resultCheckReactiveSlacks, reactiveSlacksThreshold);
        LOGGER.info("Status : {}", result.getStatus());
        LOGGER.info("Reactive slacks : {}", result.getReactiveSlacks());
        LOGGER.info("Indicators : {}", result.getIndicators());
    }

    @Override
    protected void sendResultMessage(AbstractResultContext<VoltageInitRunContext> resultContext, OpenReacResult result) {
        VoltageInitRunContext context = resultContext.getRunContext();
        double reactiveSlacksThreshold = voltageInitParametersService.getReactiveSlacksThreshold(context.getParametersUuid());
        boolean resultCheckReactiveSlacks = checkReactiveSlacksOverThreshold(result, reactiveSlacksThreshold, context.getReporter());
        Map<String, Object> additionalHeaders = new HashMap<>();
        additionalHeaders.put(HEADER_REACTIVE_SLACKS_OVER_THRESHOLD, resultCheckReactiveSlacks);
        additionalHeaders.put(HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE, reactiveSlacksThreshold);
        notificationService.sendResultMessage(resultContext.getResultUuid(), context.getReceiver(), context.getUserId(), additionalHeaders);
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }
}
