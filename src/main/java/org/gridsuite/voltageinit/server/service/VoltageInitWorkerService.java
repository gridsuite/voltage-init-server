/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.google.common.collect.Sets;
import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.openreac.OpenReacRunner;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.voltageinit.server.repository.VoltageInitResultRepository;
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
import java.util.stream.Collectors;

import static org.gridsuite.voltageinit.server.service.NotificationService.FAIL_MESSAGE;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitWorkerService.class);

    private NetworkStoreService networkStoreService;

    private VoltageInitResultRepository resultRepository;

    private Map<UUID, CompletableFuture<OpenReacResult>> futures = new ConcurrentHashMap<>();

    private Map<UUID, VoltageInitCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private Set<UUID> runRequests = Sets.newConcurrentHashSet();

    private final Lock lockRunAndCancelVoltageInit = new ReentrantLock();

    private final Executor threadPool = ForkJoinPool.commonPool();

    @Autowired
    NotificationService notificationService;

    public VoltageInitWorkerService(NetworkStoreService networkStoreService, VoltageInitResultRepository resultRepository) {
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
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

    private Network getNetwork(UUID networkUuid, List<UUID> otherNetworkUuids, String variantId) {
        Network network = getNetwork(networkUuid, variantId);
        if (otherNetworkUuids.isEmpty()) {
            return network;
        } else {
            List<Network> otherNetworks = otherNetworkUuids.stream().map(uuid -> getNetwork(uuid, variantId)).collect(Collectors.toList());
            List<Network> networks = new ArrayList<>();
            networks.add(network);
            networks.addAll(otherNetworks);
            MergingView mergingView = MergingView.create("merge", "iidm");
            mergingView.merge(networks.toArray(new Network[0]));
            return mergingView;
        }
    }

    private OpenReacResult run(VoltageInitRunContext context, UUID resultUuid) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(context);

        LOGGER.info("Run voltage init...");
        Network network = getNetwork(context.getNetworkUuid(), context.getOtherNetworkUuids(), context.getVariantId());

        CompletableFuture<OpenReacResult> future = runVoltageInitAsync(context, network, resultUuid);

        return future == null ? null : future.get();
    }

    public CompletableFuture<OpenReacResult> runVoltageInitAsync(VoltageInitRunContext context, Network network, UUID resultUuid) {
        lockRunAndCancelVoltageInit.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }

            CompletableFuture<OpenReacResult> future = CompletableFutureTask.runAsync(() -> OpenReacRunner.run(network, network.getVariantManager().getWorkingVariantId(), context.getParameters()), this.threadPool);

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
    }

    @Bean
    public Consumer<Message<String>> consumeRun() {
        return message -> {
            VoltageInitResultContext resultContext = VoltageInitResultContext.fromMessage(message);
            try {
                runRequests.add(resultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();

                startTime.set(System.nanoTime());
                OpenReacResult result = run(resultContext.getRunContext(), resultContext.getResultUuid());
                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                resultRepository.insertStatus(List.of(resultContext.getResultUuid()), result.getStatus().name());
                LOGGER.info("Status : {}", result.getStatus());
                LOGGER.info("Reactive slacks : {}", result.getReactiveSlacks());
                LOGGER.info("Indicators : {}", result.getIndicators());
                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver());
                    LOGGER.info("Voltage initialization complete (resultUuid='{}')", resultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanVoltageInitResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error(FAIL_MESSAGE, e);
                if (!(e instanceof CancellationException)) {
                    notificationService.publishFail(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), e.getMessage(), resultContext.getRunContext().getUserId());
                    resultRepository.delete(resultContext.getResultUuid());
                }
            } finally {
                futures.remove(resultContext.getResultUuid());
                cancelComputationRequests.remove(resultContext.getResultUuid());
                runRequests.remove(resultContext.getResultUuid());
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeCancel() {
        return message -> cancelVoltageInitAsync(VoltageInitCancelContext.fromMessage(message));
    }
}
