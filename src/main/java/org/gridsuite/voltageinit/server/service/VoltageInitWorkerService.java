/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.openreac.OpenReacConfig;
import com.powsybl.openreac.OpenReacRunner;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import com.powsybl.openreac.parameters.output.OpenReacStatus;
import org.gridsuite.computation.s3.ComputationS3Service;
import org.gridsuite.computation.service.*;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.service.parameters.VoltageInitParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.gridsuite.voltageinit.server.util.ReportUtil.checkReportWithKey;


/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitWorkerService extends AbstractWorkerService<OpenReacResult, VoltageInitRunContext, Void, VoltageInitResultService> {

    public static final String COMPUTATION_TYPE = "VoltageInit";
    public static final String HEADER_REACTIVE_SLACKS_OVER_THRESHOLD = "REACTIVE_SLACKS_OVER_THRESHOLD";
    public static final String HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE = "reactiveSlacksThreshold";
    public static final String HEADER_VOLTAGE_LEVEL_LIMITS_OUT_OF_NOMINAL_VOLTAGE_RANGE = "VOLTAGE_LEVEL_LIMITS_OUT_OF_NOMINAL_VOLTAGE_RANGE";
    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitWorkerService.class);

    private static final String ERROR = "error";
    private static final String ERROR_DURING_VOLTAGE_PROFILE_INITIALISATION = "Error during voltage profile initialization";

    private final NetworkModificationService networkModificationService;

    private final VoltageInitParametersService voltageInitParametersService;

    public VoltageInitWorkerService(NetworkStoreService networkStoreService,
                                    NotificationService notificationService,
                                    ExecutionService executionService,
                                    NetworkModificationService networkModificationService,
                                    VoltageInitParametersService voltageInitParametersService,
                                    VoltageInitResultService resultService,
                                    @Autowired(required = false)
                                    ComputationS3Service computationS3Service,
                                    ReportService reportService,
                                    VoltageInitObserver voltageInitObserver,
                                    ObjectMapper objectMapper) {
        super(networkStoreService, notificationService, reportService, resultService, computationS3Service, executionService, voltageInitObserver, objectMapper);
        this.networkModificationService = Objects.requireNonNull(networkModificationService);
        this.voltageInitParametersService = Objects.requireNonNull(voltageInitParametersService);
    }

    @Override
    protected PreloadingStrategy getNetworkPreloadingStrategy() {
        return PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW;
    }

    @Override
    protected VoltageInitResultContext fromMessage(Message<String> message) {
        return VoltageInitResultContext.fromMessage(message, objectMapper);
    }

    private boolean checkReactiveSlacksOverThreshold(OpenReacResult openReacResult, double reactiveSlacksThreshold) {
        return openReacResult.getReactiveSlacks().stream().anyMatch(r -> Math.abs(r.slack) > reactiveSlacksThreshold);
    }

    @Override
    protected CompletableFuture<OpenReacResult> getCompletableFuture(VoltageInitRunContext context, String provider, UUID resultUuid) {
        OpenReacParameters parameters = voltageInitParametersService.buildOpenReacParameters(context, context.getNetwork());
        if (context.getDebugDir() != null) {
            parameters.setDebugDir(context.getDebugDir().toString());
        }
        OpenReacConfig config = OpenReacConfig.load();
        return OpenReacRunner.runAsync(context.getNetwork(), context.getNetwork().getVariantManager().getWorkingVariantId(), parameters, config, executionService.getComputationManager(), context.getReportNode(), null);
    }

    @Override
    protected void handleNonCancellationException(AbstractResultContext<VoltageInitRunContext> resultContext, Exception exception, AtomicReference<ReportNode> rootReporter) {
        Map<String, String> errorIndicators = new HashMap<>();
        errorIndicators.put(ERROR, ERROR_DURING_VOLTAGE_PROFILE_INITIALISATION);
        resultService.insertErrorResult(resultContext.getResultUuid(), errorIndicators);
        resultService.insertStatus(List.of(resultContext.getResultUuid()), VoltageInitStatus.NOT_OK);
        super.postRun(resultContext.getRunContext(), rootReporter, null);
    }

    private UUID createModificationGroup(OpenReacResult openReacResult, Network network, boolean updateBusVoltage, String rootNetworkName, String nodeName) {
        return openReacResult.getStatus() == OpenReacStatus.OK ?
                networkModificationService.createVoltageInitModificationGroup(network, openReacResult, updateBusVoltage, rootNetworkName, nodeName) :
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
        UUID modificationsGroupUuid = createModificationGroup(result, network, updateBusVoltage, context.getRootNetworkName(), context.getNodeName());
        Map<String, Bus> networkBuses = network.getBusView().getBusStream().collect(Collectors.toMap(Bus::getId, Function.identity()));
        // check if at least one reactive slack over the threshold value
        double reactiveSlacksThreshold = voltageInitParametersService.getReactiveSlacksThreshold(context.getParametersUuid());
        boolean resultCheckReactiveSlacks = checkReactiveSlacksOverThreshold(result, reactiveSlacksThreshold);
        resultService.insert(resultContext.getResultUuid(), result, networkBuses, modificationsGroupUuid, result.getStatus().name(), resultCheckReactiveSlacks, reactiveSlacksThreshold);
        LOGGER.info("Status : {}", result.getStatus());
        LOGGER.info("Reactive slacks : {}", result.getReactiveSlacks());
        LOGGER.info("Indicators : {}", result.getIndicators());
    }

    @Override
    protected void postRun(VoltageInitRunContext runContext, AtomicReference<ReportNode> rootReportNode, OpenReacResult result) {
        double reactiveSlacksThreshold = voltageInitParametersService.getReactiveSlacksThreshold(runContext.getParametersUuid());
        boolean resultCheckReactiveSlacks = checkReactiveSlacksOverThreshold(result, reactiveSlacksThreshold);
        if (resultCheckReactiveSlacks) {
            runContext.getReportNode().newReportNode()
                    .withMessageTemplate("voltage.init.server.reactiveSlacksOverThreshold")
                    .withUntypedValue("threshold", reactiveSlacksThreshold)
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .add();
        }
        super.postRun(runContext, rootReportNode, result);
    }

    @Override
    protected void sendResultMessage(AbstractResultContext<VoltageInitRunContext> resultContext, OpenReacResult result) {
        VoltageInitRunContext context = resultContext.getRunContext();
        double reactiveSlacksThreshold = voltageInitParametersService.getReactiveSlacksThreshold(context.getParametersUuid());
        boolean resultCheckReactiveSlacks = checkReactiveSlacksOverThreshold(result, reactiveSlacksThreshold);
        boolean voltageLevelsWithLimitsOutOfNominalVRange = checkReportWithKey("optimizer.openreac.nbVoltageLevelsWithLimitsOutOfNominalVRange", resultContext.getRunContext().getReportNode());
        Map<String, Object> additionalHeaders = new HashMap<>();
        additionalHeaders.put(HEADER_REACTIVE_SLACKS_OVER_THRESHOLD, resultCheckReactiveSlacks);
        additionalHeaders.put(HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE, reactiveSlacksThreshold);
        additionalHeaders.put(HEADER_VOLTAGE_LEVEL_LIMITS_OUT_OF_NOMINAL_VOLTAGE_RANGE, voltageLevelsWithLimitsOutOfNominalVRange);
        notificationService.sendResultMessage(resultContext.getResultUuid(), context.getReceiver(), context.getUserId(), additionalHeaders);
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }
}
