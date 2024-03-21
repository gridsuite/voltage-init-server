/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.service;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import org.gridsuite.voltageinit.server.service.ReportService;
import org.gridsuite.voltageinit.utils.ContextConfigurationWithTestChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.gridsuite.voltageinit.utils.TestUtils.resourceToString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Anis TOURI <anis.touri at rte-france.com>
 */
@WireMockTest
@SpringBootTest
@ContextConfigurationWithTestChannel
class ReportServiceTest {
    private static final UUID REPORT_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final String REPORT_API_VERSION = "v1";

    @Autowired
    private ReportService reportService;
    @MockBean
    private RestTemplate restTemplate;
    private URI baseUrl;

    private void configureStubReports(final WireMock server) throws Exception {
        server.stubFor(get(urlEqualTo("/v1/reports/" + REPORT_UUID))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(resourceToString("/report.json"))));
    }

    @BeforeEach
    public void setUp(final WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        configureStubReports(wmRuntimeInfo.getWireMock());
        reportService.setReportServiceBaseUri(wmRuntimeInfo.getHttpBaseUrl());
        this.baseUrl = URI.create(wmRuntimeInfo.getHttpBaseUrl());
    }

    @Test
    void testSendReport() {
        Reporter reporter = new ReporterModel("test", "test");
        URI expectedUri = UriComponentsBuilder.fromUri(baseUrl)
                .pathSegment(REPORT_API_VERSION)
                .path("/reports/{reportUuid}")
                .build(REPORT_UUID);
        reportService.sendReport(REPORT_UUID, reporter);
        verify(restTemplate, times(1)).put(expectedUri.toString(), reporter);
    }

    @Test
    void testDeleteReport() {
        reportService.deleteReport(REPORT_UUID, "VoltageInit");
        URI expectedUri = UriComponentsBuilder.fromUri(baseUrl)
                .pathSegment(REPORT_API_VERSION)
                .path("/reports/{reportUuid}")
                .queryParam("reportTypeFilter", "VoltageInit")
                .queryParam("errorOnReportNotFound", false)
                .build(REPORT_UUID);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        verify(restTemplate, times(1)).exchange(expectedUri.toString(), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
}
