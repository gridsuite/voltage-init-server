/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.iidm.modification.GeneratorModification;
import com.powsybl.iidm.modification.tapchanger.RatioTapPositionModification;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.openreac.parameters.OpenReacAmplIOFiles;
import com.powsybl.openreac.parameters.input.*;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import com.powsybl.openreac.parameters.output.OpenReacStatus;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.voltageinit.server.dto.VoltageInitResult;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.service.NetworkModificationService;
import org.gridsuite.voltageinit.server.service.UuidGeneratorService;
import org.gridsuite.voltageinit.server.util.annotations.PostCompletionAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.voltageinit.server.service.NotificationService.HEADER_USER_ID;
import static org.gridsuite.voltageinit.server.service.NotificationService.CANCEL_MESSAGE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {VoltageInitApplication.class, TestChannelBinderConfiguration.class})})
public class VoltageInitControllerTest {

    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID OTHER_NETWORK_UUID = UUID.fromString("06824085-db85-4883-9458-8c5c9f1585d6");
    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");
    private static final UUID NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("11111111-7977-4592-ba19-88027e4254e4");
    private static final UUID OTHER_NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("22222222-7977-4592-ba19-88027e4254e4");
    private static final Map<String, String> INDICATORS = Map.of("defaultPmax", "1000.000000", "defaultQmax", "300.000000", "minimalQPrange", "1.000000");
    private static final UUID MODIFICATIONS_GROUP_UUID = UUID.fromString("33333333-aaaa-bbbb-cccc-dddddddddddd");

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";

    private static final String NOT_OK_RESULT = "NOT_OK";

    private static final int TIMEOUT = 1000;

    @Autowired
    private OutputDestination output;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NetworkModificationService networkModificationService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private UuidGeneratorService uuidGeneratorService;

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    private final ObjectMapper mapper = restTemplateConfig.objectMapper();

    private Network network;
    private Network network1;
    private Network networkForMergingView;
    private Network otherNetworkForMergingView;
    OpenReacParameters openReacParameters;
    OpenReacResult openReacResult;
    CompletableFutureTask<OpenReacResult> completableFutureResultsTask;

    private MockWebServer server;

    private OpenReacResult buildOpenReacResult() {
        OpenReacAmplIOFiles openReacAmplIOFiles = new OpenReacAmplIOFiles(openReacParameters, network, false);

        GeneratorModification.Modifs m1 = new GeneratorModification.Modifs();
        m1.setTargetV(228.);
        openReacAmplIOFiles.getNetworkModifications().getGeneratorModifications().add(new GeneratorModification("GEN", m1));

        GeneratorModification.Modifs m2 = new GeneratorModification.Modifs();
        m2.setTargetQ(50.);
        openReacAmplIOFiles.getNetworkModifications().getGeneratorModifications().add(new GeneratorModification("GEN2", m2));

        openReacAmplIOFiles.getNetworkModifications().getTapPositionModifications().add(new RatioTapPositionModification("NHV2_NLOAD", 2));

        openReacResult = new OpenReacResult(OpenReacStatus.OK, openReacAmplIOFiles, INDICATORS);
        return openReacResult;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        server = new MockWebServer();
        server.start();

        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);
        given(networkStoreService.getNetwork(OTHER_NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willThrow(new PowsyblException("Not found"));

        networkForMergingView = new NetworkFactoryImpl().createNetwork("mergingView", "test");
        given(networkStoreService.getNetwork(NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(networkForMergingView);

        otherNetworkForMergingView = new NetworkFactoryImpl().createNetwork("other", "test 2");
        given(networkStoreService.getNetwork(OTHER_NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(otherNetworkForMergingView);

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);

        // OpenReac run mocking
        openReacParameters = new OpenReacParameters();
        openReacResult = buildOpenReacResult();

        completableFutureResultsTask = CompletableFutureTask.runAsync(() -> openReacResult, ForkJoinPool.commonPool());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());

                if (path.matches("/v1/groups/.*") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/modification") && request.getMethod().equals("POST")) {
                    return new MockResponse().setResponseCode(200).setBody("\"" + MODIFICATIONS_GROUP_UUID + "\"")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                }
                return new MockResponse().setResponseCode(418);
            }
        };
        server.setDispatcher(dispatcher);

        // purge messages
        while (output.receive(1000, "voltageinit.result") != null) {
        }
        // purge messages
        while (output.receive(1000, "voltageinit.run") != null) {
        }
        while (output.receive(1000, "voltageinit.cancel") != null) {
        }
        while (output.receive(1000, "voltageinit.stopped") != null) {
        }
        while (output.receive(1000, "voltageinit.failed") != null) {
        }
    }

    @SneakyThrows
    @After
    public void tearDown() {
        mockMvc.perform(delete("/" + VERSION + "/results"))
                .andExpect(status().isOk());
    }

    @Test
    public void runTest() throws Exception {
        try (MockedStatic<CompletableFutureTask> openReacRunnerMockedStatic = Mockito.mockStatic(CompletableFutureTask.class)) {
            openReacRunnerMockedStatic.when(() -> CompletableFutureTask.runAsync(any(Callable.class), any(Executor.class)))
                    .thenReturn(completableFutureResultsTask);

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID).content(mapper.writeValueAsString(openReacParameters))
                            .header(HEADER_USER_ID, "userId").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, "voltageinit.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            VoltageInitResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), VoltageInitResult.class);
            assertEquals(RESULT_UUID, resultDto.getResultUuid());
            assertEquals(INDICATORS, resultDto.getIndicators());

            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}/modifications-group-uuid", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            UUID modificationsGroupUuid = mapper.readValue(result.getResponse().getContentAsString(), UUID.class);
            assertEquals(MODIFICATIONS_GROUP_UUID, modificationsGroupUuid);

            // should throw not found if result does not exist
            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", OTHER_RESULT_UUID))
                   .andExpect(status().isNotFound());
            // test one result deletion
            mockMvc.perform(delete("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    public void runWrongNetworkTest() throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, OTHER_NETWORK_UUID)
                        .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

        result = mockMvc.perform(get(
                        "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
                .andExpect(status().isOk())
                .andReturn();
        // assert result is NOT_OK
        assertEquals(NOT_OK_RESULT, result.getResponse().getContentAsString());

        // should return some results since we store failed result into database
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isOk());
    }

    @Test
    public void stopTest() throws Exception {
        try (MockedStatic<CompletableFutureTask> openReacRunnerMockedStatic = Mockito.mockStatic(CompletableFutureTask.class)) {
            openReacRunnerMockedStatic.when(() -> CompletableFutureTask.runAsync(any(Callable.class), any(Executor.class)))
                    .thenReturn(completableFutureResultsTask);

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            // stop voltage init analysis
            assertNotNull(output.receive(TIMEOUT, "voltageinit.run"));
            mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/stop" + "?receiver=me", RESULT_UUID))
                    .andExpect(status().isOk());
            assertNotNull(output.receive(TIMEOUT, "voltageinit.cancel"));

            Message<byte[]> message = output.receive(TIMEOUT, "voltageinit.stopped");
            assertNotNull(message);
            assertEquals(RESULT_UUID.toString(), message.getHeaders().get("resultUuid"));
            assertEquals("me", message.getHeaders().get("receiver"));
            assertEquals(CANCEL_MESSAGE, message.getHeaders().get("message"));
        }
    }

    @SneakyThrows
    @Test
    public void mergingViewTest() {
        try (MockedStatic<CompletableFutureTask> openReacRunnerMockedStatic = Mockito.mockStatic(CompletableFutureTask.class)) {
            openReacRunnerMockedStatic.when(() -> CompletableFutureTask.runAsync(any(Callable.class), any(Executor.class)))
                    .thenReturn(completableFutureResultsTask);

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&networkUuid=" + NETWORK_FOR_MERGING_VIEW_UUID, OTHER_NETWORK_FOR_MERGING_VIEW_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));
        }
    }

    @SneakyThrows
    @Test
    public void getStatusTest() {
        MvcResult result = mockMvc.perform(get(
                        "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("", result.getResponse().getContentAsString());

        mockMvc.perform(put("/" + VERSION + "/results/invalidate-status?resultUuid=" + RESULT_UUID))
                .andExpect(status().isOk());

        result = mockMvc.perform(get(
                        "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals(VoltageInitStatus.NOT_DONE.name(), result.getResponse().getContentAsString());
    }

    @SneakyThrows
    @Test
    public void postCompletionAdapterTest() {
        CompletableFutureTask<OpenReacResult> task = CompletableFutureTask.runAsync(() -> openReacResult, ForkJoinPool.commonPool());
        PostCompletionAdapter adapter = new PostCompletionAdapter();
        adapter.execute(task);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.registerSynchronization(adapter);
        adapter.execute(task);
        adapter.afterCompletion(0);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.clearSynchronization();
    }
}
