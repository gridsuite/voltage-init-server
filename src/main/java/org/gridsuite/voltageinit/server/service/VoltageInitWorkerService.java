/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.google.common.collect.Sets;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.openreac.OpenReacConfig;
import com.powsybl.openreac.OpenReacRunner;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import com.powsybl.openreac.parameters.output.OpenReacStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.voltageinit.server.repository.VoltageInitResultRepository;
import org.gridsuite.voltageinit.server.service.parameters.VoltageInitParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.gridsuite.voltageinit.server.service.NotificationService.CANCEL_MESSAGE;
import static org.gridsuite.voltageinit.server.service.NotificationService.FAIL_MESSAGE;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitWorkerService.class);

    private static final String ERROR = "error";
    private static final String ERROR_DURING_VOLTAGE_PROFILE_INITIALISATION = "Error during voltage profile initialization";

    private static final String VOLTAGE_INIT_TYPE_REPORT = "VoltageInit";

    private final NetworkStoreService networkStoreService;

    private final NetworkModificationService networkModificationService;

    private final VoltageInitParametersService voltageInitParametersService;

    private final ReportService reportService;

    private final VoltageInitResultRepository resultRepository;

    private final VoltageInitExecutionService voltageInitExecutionService;

    private final VoltageInitObserver voltageInitObserver;

    private final Map<UUID, CompletableFuture<OpenReacResult>> futures = new ConcurrentHashMap<>();

    private final Map<UUID, VoltageInitCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private final Set<UUID> runRequests = Sets.newConcurrentHashSet();

    private final Lock lockRunAndCancelVoltageInit = new ReentrantLock();

    @Autowired
    NotificationService notificationService;

    public VoltageInitWorkerService(NetworkStoreService networkStoreService,
                                    NetworkModificationService networkModificationService,
                                    VoltageInitParametersService voltageInitParametersService,
                                    VoltageInitResultRepository resultRepository,
                                    ReportService reportService,
                                    VoltageInitExecutionService voltageInitExecutionService,
                                    VoltageInitObserver voltageInitObserver) {
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.networkModificationService = Objects.requireNonNull(networkModificationService);
        this.voltageInitParametersService = Objects.requireNonNull(voltageInitParametersService);
        this.reportService = reportService;
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.voltageInitExecutionService = Objects.requireNonNull(voltageInitExecutionService);
        this.voltageInitObserver = voltageInitObserver;
    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        Network network;
        try {
            network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW);
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return network;
    }

    public static void addRestrictedVoltageLevelReport(Map<String, Double> voltageLevelsIdsRestricted, Reporter reporter) {
        if (!voltageLevelsIdsRestricted.isEmpty()) {
            String joinedVoltageLevelsIds = voltageLevelsIdsRestricted.entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + " : " + entry.getValue())
                    .collect(Collectors.joining(", "));

            reporter.report(Report.builder()
                    .withKey("restrictedVoltageLevels")
                    .withDefaultMessage(String.format("The modifications to the low limits for certain voltage levels have been restricted to avoid negative voltage limits: %s", joinedVoltageLevelsIds))
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .build());
        }
    }

    private Pair<Network, OpenReacResult> run(VoltageInitRunContext context, UUID resultUuid) throws Exception {
        Objects.requireNonNull(context);

        LOGGER.info("Run voltage init...");
        Network network = voltageInitObserver.observe("network.load", () ->
                getNetwork(context.getNetworkUuid(), context.getVariantId()));

        AtomicReference<Reporter> rootReporter = new AtomicReference<>(Reporter.NO_OP);
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportUuid() != null) {
            String rootReporterId = context.getReporterId() == null ? VOLTAGE_INIT_TYPE_REPORT : context.getReporterId() + "@" + context.getReportType();
            rootReporter.set(new ReporterModel(rootReporterId, rootReporterId));
            reporter = rootReporter.get().createSubReporter(context.getReportType(), VOLTAGE_INIT_TYPE_REPORT, VOLTAGE_INIT_TYPE_REPORT, context.getReportUuid().toString());
            // Delete any previous VoltageInit computation logs
            voltageInitObserver.observe("report.delete", () ->
                    reportService.deleteReport(context.getReportUuid(), context.getReportType()));
        }
        CompletableFuture<OpenReacResult> future = runVoltageInitAsync(context, network, resultUuid);
        if (context.getReportUuid() != null) {
            addRestrictedVoltageLevelReport(context.getVoltageLevelsIdsRestricted(), reporter);
            voltageInitObserver.observe("report.send", () ->
                    reportService.sendReport(context.getReportUuid(), rootReporter.get()));
        }

        return future == null ? Pair.of(network, null) : Pair.of(network, voltageInitObserver.observeRun("run", future::get));
    }

    public CompletableFuture<OpenReacResult> runVoltageInitAsync(VoltageInitRunContext context, Network network, UUID resultUuid) {
        lockRunAndCancelVoltageInit.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }

            OpenReacParameters parameters = voltageInitParametersService.buildOpenReacParameters(context, network);
            OpenReacConfig config = OpenReacConfig.load();
            CompletableFuture<OpenReacResult> future = OpenReacRunner.runAsync(network, network.getVariantManager().getWorkingVariantId(), parameters, config, voltageInitExecutionService.getComputationManager());
            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancelVoltageInit.unlock();
        }
    }

    private void cancelVoltageInitAsync(VoltageInitCancelContext cancelContext) {
        lockRunAndCancelVoltageInit.lock();
        try {
            cancelComputationRequests.put(cancelContext.getResultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<OpenReacResult> future = futures.get(cancelContext.getResultUuid());
            if (future != null) {
                future.cancel(true);  // cancel computation in progress
            }
            cleanVoltageInitResultsAndPublishCancel(cancelContext.getResultUuid(), cancelContext.getReceiver());
        } finally {
            lockRunAndCancelVoltageInit.unlock();
        }
    }

    private void cleanVoltageInitResultsAndPublishCancel(UUID resultUuid, String receiver) {
        resultRepository.delete(resultUuid);
        notificationService.publishStop(resultUuid, receiver);
        LOGGER.info(CANCEL_MESSAGE + " (resultUuid='{}')", resultUuid);
    }

    @Bean
    public Consumer<Message<String>> consumeRun() {
        return message -> {
            VoltageInitResultContext resultContext = VoltageInitResultContext.fromMessage(message);
            try {
                runRequests.add(resultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();

                startTime.set(System.nanoTime());
                Pair<Network, OpenReacResult> res = run(resultContext.getRunContext(), resultContext.getResultUuid());
                Network network = res.getLeft();
                OpenReacResult openReacResult = res.getRight();
                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                if (openReacResult != null) {  // result available
                    UUID modificationsGroupUuid = createModificationGroup(openReacResult, network);
                    Map<String, Bus> networkBuses = network.getBusView().getBusStream().collect(Collectors.toMap(Bus::getId, Function.identity()));
                    voltageInitObserver.observe("results.save", () ->
                        resultRepository.insert(resultContext.getResultUuid(), openReacResult, networkBuses, modificationsGroupUuid, openReacResult.getStatus().name()));
                    LOGGER.info("Status : {}", openReacResult.getStatus());
                    LOGGER.info("Reactive slacks : {}", openReacResult.getReactiveSlacks());
                    LOGGER.info("Indicators : {}", openReacResult.getIndicators());

                    notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), resultContext.getRunContext().getUserId());
                    LOGGER.info("Voltage initialization complete (resultUuid='{}')", resultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanVoltageInitResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!(e instanceof CancellationException)) {
                    LOGGER.error(FAIL_MESSAGE, e);
                    Map<String, String> errorIndicators = new HashMap<>();
                    errorIndicators.put(ERROR, ERROR_DURING_VOLTAGE_PROFILE_INITIALISATION);

                    resultRepository.insertErrorResult(resultContext.getResultUuid(), errorIndicators);
                    resultRepository.insertStatus(List.of(resultContext.getResultUuid()), OpenReacStatus.NOT_OK.name());
                    notificationService.publishFail(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), e.getMessage(), resultContext.getRunContext().getUserId());
                }
            } finally {
                futures.remove(resultContext.getResultUuid());
                cancelComputationRequests.remove(resultContext.getResultUuid());
                runRequests.remove(resultContext.getResultUuid());
            }
        };
    }

    private UUID createModificationGroup(OpenReacResult openReacResult, Network network) {
        return openReacResult.getStatus() == OpenReacStatus.OK ?
            networkModificationService.createVoltageInitModificationGroup(network, openReacResult) :
            null;
    }

    @Bean
    public Consumer<Message<String>> consumeCancel() {
        return message -> cancelVoltageInitAsync(VoltageInitCancelContext.fromMessage(message));
    }
}
