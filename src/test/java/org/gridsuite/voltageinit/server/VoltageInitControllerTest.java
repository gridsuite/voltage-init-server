/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.openreac.OpenReacRunner;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import com.powsybl.openreac.parameters.output.OpenReacStatus;
import com.powsybl.openreac.parameters.output.ReactiveSlackOutput;
import lombok.SneakyThrows;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.service.ReportService;
import org.gridsuite.voltageinit.server.service.UuidGeneratorService;
import org.gridsuite.voltageinit.server.service.VoltageInitRunContext;
import org.gridsuite.voltageinit.server.service.VoltageInitWorkerService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.voltageinit.server.service.NotificationService.HEADER_USER_ID;
import static org.gridsuite.voltageinit.server.service.NotificationService.CANCEL_MESSAGE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
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
    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");
    private static final UUID NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("11111111-7977-4592-ba19-88027e4254e4");
    private static final UUID OTHER_NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("22222222-7977-4592-ba19-88027e4254e4");
    private static final Map<String, String> INDICATORS = Map.of("defaultPmax", "1000.000000", "defaultQmax", "300.000000", "minimalQPrange", "1.000000");
    private static final OpenReacResult RESULT = new OpenReacResult(OpenReacStatus.OK, List.of(new ReactiveSlackOutput.ReactiveSlack("bus1", "vl1", 10.5)), INDICATORS);

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";

    private static final int TIMEOUT = 1000;

    @Autowired
    private OutputDestination output;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private VoltageInitWorkerService workerService;

    @MockBean
    private ReportService reportService;

    @MockBean
    private UuidGeneratorService uuidGeneratorService;

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    private final ObjectMapper mapper = restTemplateConfig.objectMapper();

    private Network network;
    private Network network1;
    private Network networkForMergingView;
    private Network otherNetworkForMergingView;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);

        networkForMergingView = new NetworkFactoryImpl().createNetwork("mergingView", "test");
        given(networkStoreService.getNetwork(NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(networkForMergingView);

        otherNetworkForMergingView = new NetworkFactoryImpl().createNetwork("other", "test 2");
        given(networkStoreService.getNetwork(OTHER_NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(otherNetworkForMergingView);

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);

        when(workerService.runVoltageInitAsync(any(VoltageInitRunContext.class), any(Network.class), any(UUID.class))).thenReturn(CompletableFuture.completedFuture(RESULT));

        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);

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
//        try (MockedStatic<OpenReacRunner> openReacRunnerMockedStatic = Mockito.mockStatic(OpenReacRunner.class)) {
//            openReacRunnerMockedStatic.when(() -> OpenReacRunner.run(eq(network), anyString(), any(OpenReacParameters.class)))
//                    .thenReturn(RESULT);

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
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
            OpenReacResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), OpenReacResult.class);
            //assertResultsEquals(RESULT, resultDto);

            // should throw not found if result does not exist
            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", OTHER_RESULT_UUID))
                    .andExpect(status().isNotFound());

            // test one result deletion
            mockMvc.perform(delete("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isNotFound());
//        }
    }

    @Test
    public void stopTest() throws Exception {
        try (MockedStatic<OpenReacRunner> openReacRunnerMockedStatic = Mockito.mockStatic(OpenReacRunner.class)) {
            openReacRunnerMockedStatic.when(() -> OpenReacRunner.run(eq(network), anyString(), any(OpenReacParameters.class)))
                    .thenReturn(RESULT);

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
        try (MockedStatic<OpenReacRunner> openReacRunnerMockedStatic = Mockito.mockStatic(OpenReacRunner.class)) {
            openReacRunnerMockedStatic.when(() -> OpenReacRunner.run(eq(network), anyString(), any(OpenReacParameters.class)))
                    .thenReturn(RESULT);

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
    public void testStatus() {
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

//    @SneakyThrows
//    @Test
//    public void runWithReportTest() {
//        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
//            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
//                    .thenReturn(CompletableFuture.completedFuture(RESULT));
//
//            mockMvc.perform(post(
//                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reporterId=myReporter&receiver=me&reportUuid=" + REPORT_UUID + "&variantId=" + VARIANT_2_ID, NETWORK_UUID)
//                            .header(HEADER_USER_ID, "user"))
//                    .andExpect(status().isOk())
//                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                    .andReturn();
//        }
//    }
}
