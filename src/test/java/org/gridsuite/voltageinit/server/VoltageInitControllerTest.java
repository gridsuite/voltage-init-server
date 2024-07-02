/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.ampl.converter.AmplExportConfig;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.modification.GeneratorModification;
import com.powsybl.iidm.modification.ShuntCompensatorModification;
import com.powsybl.iidm.modification.StaticVarCompensatorModification;
import com.powsybl.iidm.modification.VscConverterStationModification;
import com.powsybl.iidm.modification.tapchanger.RatioTapPositionModification;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.openreac.OpenReacConfig;
import com.powsybl.openreac.OpenReacRunner;
import com.powsybl.openreac.parameters.OpenReacAmplIOFiles;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import com.powsybl.openreac.parameters.output.OpenReacStatus;
import com.powsybl.openreac.parameters.output.ReactiveSlackOutput;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import com.powsybl.ws.commons.computation.service.UuidGeneratorService;
import com.powsybl.ws.commons.computation.utils.annotations.PostCompletionAdapter;
import org.gridsuite.voltageinit.server.dto.VoltageInitResult;
import org.gridsuite.voltageinit.server.dto.VoltageInitStatus;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageLimitInfos;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.service.NetworkModificationService;
import org.gridsuite.voltageinit.server.service.parameters.FilterService;
import org.jgrapht.alg.util.Pair;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static com.powsybl.ws.commons.computation.service.NotificationService.*;
import static org.gridsuite.voltageinit.server.service.VoltageInitWorkerService.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private static final UUID REPORT_UUID = UUID.fromString("0c4de370-3e6a-4d72-b292-d355a97e0d53");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");
    private static final Map<String, String> INDICATORS = Map.of("defaultPmax", "1000.000000", "defaultQmax", "300.000000", "minimalQPrange", "1.000000");
    private static final UUID MODIFICATIONS_GROUP_UUID = UUID.fromString("33333333-aaaa-bbbb-cccc-dddddddddddd");
    private static final String FILTER_EQUIPMENT_JSON = "[{\"filterId\":\"cf399ef3-7f14-4884-8c82-1c90300da329\",\"identifiableAttributes\":[{\"id\":\"VL1\",\"type\":\"VOLTAGE_LEVEL\"}],\"notFoundEquipments\":[]}]";
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

    @Autowired
    private VoltageInitParametersRepository parametersRepository;

    @Autowired
    private FilterService filterService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private UuidGeneratorService uuidGeneratorService;

    @Autowired
    private ObjectMapper mapper;

    private Network network;
    OpenReacParameters openReacParameters;
    OpenReacResult openReacResult;
    CompletableFutureTask<OpenReacResult> completableFutureResultsTask;

    private MockWebServer server;

    private OpenReacResult buildOpenReacResult() {
        OpenReacAmplIOFiles openReacAmplIOFiles = new OpenReacAmplIOFiles(openReacParameters, null, network, false, ReportNode.NO_OP);

        GeneratorModification.Modifs m1 = new GeneratorModification.Modifs();
        m1.setTargetV(228.);
        openReacAmplIOFiles.getNetworkModifications().getGeneratorModifications().add(new GeneratorModification("GEN", m1));

        GeneratorModification.Modifs m2 = new GeneratorModification.Modifs();
        m2.setTargetQ(50.);
        openReacAmplIOFiles.getNetworkModifications().getGeneratorModifications().add(new GeneratorModification("GEN2", m2));

        openReacAmplIOFiles.getNetworkModifications().getTapPositionModifications().add(new RatioTapPositionModification("NHV2_NLOAD", 2));
        openReacAmplIOFiles.getNetworkModifications().getTapPositionModifications().add(new RatioTapPositionModification("unknown2WT", 2));

        openReacAmplIOFiles.getNetworkModifications().getSvcModifications().add(new StaticVarCompensatorModification("SVC_1", 227., 50.));
        openReacAmplIOFiles.getNetworkModifications().getVscModifications().add(new VscConverterStationModification("VSC_1", 385., 70.));
        openReacAmplIOFiles.getNetworkModifications().getShuntModifications().add(new ShuntCompensatorModification("SHUNT_1", true, 1));
        openReacAmplIOFiles.getNetworkModifications().getShuntModifications().add(new ShuntCompensatorModification("unknownShunt", true, 1));

        Map<String, Pair<Double, Double>> voltageProfile = openReacAmplIOFiles.getVoltageProfileOutput().getVoltageProfile();
        voltageProfile.put("NHV2_NLOAD_busId1", Pair.of(100., 100.));
        voltageProfile.put("SHUNT_1_busId1", Pair.of(100., 100.));
        voltageProfile.put("VLHV1_0", Pair.of(100., 100.));
        voltageProfile.put("VLGEN_0", Pair.of(100., 100.));

        openReacAmplIOFiles.getReactiveSlackOutput().getSlacks().add(new ReactiveSlackOutput.ReactiveSlack("NGEN", "VLGEN", 200.));

        openReacResult = new OpenReacResult(OpenReacStatus.OK, openReacAmplIOFiles, INDICATORS);
        return openReacResult;
    }

    private OpenReacResult buildNokOpenReacResult() {
        OpenReacAmplIOFiles openReacAmplIOFiles = new OpenReacAmplIOFiles(openReacParameters, null, network, false, ReportNode.NO_OP);
        openReacResult = new OpenReacResult(OpenReacStatus.NOT_OK, openReacAmplIOFiles, INDICATORS);
        return openReacResult;
    }

    private VoltageInitParametersEntity buildVoltageInitParametersEntity() {
        return VoltageInitParametersInfos.builder()
            .voltageLimitsModification(List.of(VoltageLimitInfos.builder()
                .priority(0)
                .lowVoltageLimit(2.0)
                .highVoltageLimit(20.0)
                .filters(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("filterName")
                    .build()))
                .build())).voltageLimitsDefault(List.of(VoltageLimitInfos.builder()
                .priority(0)
                .lowVoltageLimit(2.0)
                .highVoltageLimit(20.0)
                .filters(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("filterName")
                    .build()))
                .build())).constantQGenerators(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("qgenFilter1")
                    .build(), FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("qgenFilter2")
                    .build()))
            .variableTwoWindingsTransformers(List.of(FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter1")
                    .build(), FilterEquipments.builder()
                    .filterId(UUID.randomUUID())
                    .filterName("vtwFilter2")
                    .build()))
            .reactiveSlacksThreshold(100.)
            .build().toEntity();
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        server = new MockWebServer();
        server.start();

        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
        filterService.setFilterServerBaseUri(baseUrl);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getVoltageLevel("VLGEN").newShuntCompensator()
            .setId("SHUNT_1")
            .setBus("NGEN")
            .setConnectableBus("NGEN")
            .setTargetV(30.)
            .setTargetDeadband(10)
            .setVoltageRegulatorOn(false)
            .newLinearModel()
            .setMaximumSectionCount(1)
            .setBPerSection(1)
            .setGPerSection(1)
            .add()
            .setSectionCount(1)
            .add();

        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);
        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);
        given(networkStoreService.getNetwork(OTHER_NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willThrow(new PowsyblException("Not found"));

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
                } else if (path.matches("/v1/filters/export\\?networkUuid=" + NETWORK_UUID + "&variantId=" + VARIANT_2_ID + "&ids=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(FILTER_EQUIPMENT_JSON)
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
        try (MockedStatic<OpenReacRunner> openReacRunnerMockedStatic = Mockito.mockStatic(OpenReacRunner.class)) {
            openReacRunnerMockedStatic.when(() -> OpenReacRunner.runAsync(eq(network), eq(VARIANT_2_ID), any(OpenReacParameters.class), any(OpenReacConfig.class), any(ComputationManager.class), any(ReportNode.class), isNull(AmplExportConfig.class)))
                .thenReturn(completableFutureResultsTask);

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

            VoltageInitResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), VoltageInitResult.class);
            assertEquals(RESULT_UUID, resultDto.getResultUuid());
            assertEquals(INDICATORS, resultDto.getIndicators());
            assertEquals(MODIFICATIONS_GROUP_UUID, resultDto.getModificationsGroupUuid());

            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}/modifications-group-uuid", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            UUID modificationsGroupUuid = mapper.readValue(result.getResponse().getContentAsString(), UUID.class);
            assertEquals(MODIFICATIONS_GROUP_UUID, modificationsGroupUuid);

            // reset the modifications group uuid
            mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/modifications-group-uuid", RESULT_UUID)).andExpect(status().isOk());
            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}/modifications-group-uuid", RESULT_UUID)).andExpect(status().isNotFound());

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
    public void runWithReactiveSlacksOverThresholdTest() throws Exception {
        try (MockedStatic<OpenReacRunner> openReacRunnerMockedStatic = Mockito.mockStatic(OpenReacRunner.class)) {
            openReacRunnerMockedStatic.when(() -> OpenReacRunner.runAsync(eq(network), eq(VARIANT_2_ID), any(OpenReacParameters.class), any(OpenReacConfig.class), any(ComputationManager.class), any(ReportNode.class), isNull(AmplExportConfig.class)))
                .thenReturn(completableFutureResultsTask);

            // run with parameters and at least one reactive slack over the threshold value
            parametersRepository.save(buildVoltageInitParametersEntity());
            UUID parametersUuid = parametersRepository.findAll().get(0).getId();
            MvcResult result = mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + parametersUuid, NETWORK_UUID)
                    .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}?parametersUuid=" + parametersUuid, RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

            VoltageInitResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), VoltageInitResult.class);
            assertEquals(RESULT_UUID, resultDto.getResultUuid());
            assertEquals(INDICATORS, resultDto.getIndicators());
            assertEquals(MODIFICATIONS_GROUP_UUID, resultDto.getModificationsGroupUuid());
            assertEquals(100., resultDto.getReactiveSlacksThreshold(), 0.001);
            assertTrue(resultDto.isReactiveSlacksOverThreshold());

            Message<byte[]> resultMessage = output.receive(TIMEOUT, "voltageinit.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));
            assertEquals(Boolean.TRUE, resultMessage.getHeaders().get(HEADER_REACTIVE_SLACKS_OVER_THRESHOLD));
            Double threshold = resultMessage.getHeaders().get(HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE, Double.class);
            assertNotNull(threshold);
            assertEquals(100., threshold, 0.001);
        }
    }

    @Test
    public void testReturnsResultAndDoesNotGenerateModificationIfResultNotOk() throws Exception {
        try (MockedStatic<OpenReacRunner> openReacRunnerMockedStatic = Mockito.mockStatic(OpenReacRunner.class)) {
            openReacRunnerMockedStatic.when(() -> OpenReacRunner.runAsync(eq(network), eq(VARIANT_2_ID), any(OpenReacParameters.class), any(OpenReacConfig.class), any(ComputationManager.class), any(ReportNode.class), isNull(AmplExportConfig.class)))
                .thenReturn(CompletableFutureTask.runAsync(this::buildNokOpenReacResult, ForkJoinPool.commonPool()));

            MvcResult result = mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                    .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, "voltageinit.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get(HEADER_RESULT_UUID));
            assertEquals("me", resultMessage.getHeaders().get(HEADER_RECEIVER));

            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

            VoltageInitResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), VoltageInitResult.class);
            assertEquals(RESULT_UUID, resultDto.getResultUuid());
            assertEquals(INDICATORS, resultDto.getIndicators());
            assertNull(resultDto.getModificationsGroupUuid());
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
    public void runWithReportTest() throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId={variantId}&reportType=VoltageInit&reportUuid=" + REPORT_UUID + "&reporterId=" + UUID.randomUUID(), NETWORK_UUID, VARIANT_2_ID)
                        .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));
    }

    @Test
    public void stopTest() throws Exception {
        try (MockedStatic<OpenReacRunner> openReacRunnerMockedStatic = Mockito.mockStatic(OpenReacRunner.class)) {
            openReacRunnerMockedStatic.when(() -> OpenReacRunner.runAsync(eq(network), eq(VARIANT_2_ID), any(OpenReacParameters.class), any(OpenReacConfig.class), any(ComputationManager.class), any(ReportNode.class), isNull(AmplExportConfig.class)))
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
            assertEquals(getCancelMessage(COMPUTATION_TYPE), message.getHeaders().get("message"));
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
