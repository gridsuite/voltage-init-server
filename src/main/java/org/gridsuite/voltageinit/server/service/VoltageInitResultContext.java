/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.commons.PowsyblException;
import lombok.Getter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.*;

import static org.gridsuite.voltageinit.server.service.NotificationService.HEADER_RECEIVER;
import static org.gridsuite.voltageinit.server.service.NotificationService.HEADER_USER_ID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class VoltageInitResultContext {

    private static final String REPORT_UUID_HEADER = "reportUuid";

    public static final String VARIANT_ID_HEADER = "variantId";

    public static final String REPORTER_ID_HEADER = "reporterId";

    private final UUID resultUuid;

    private final VoltageInitRunContext runContext;

    public VoltageInitResultContext(UUID resultUuid, VoltageInitRunContext runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
    }

    private static String getNonNullHeader(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        }
        return header;
    }

    public static VoltageInitResultContext fromMessage(Message<String> message) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(getNonNullHeader(headers, "resultUuid"));
        UUID networkUuid = UUID.fromString(getNonNullHeader(headers, "networkUuid"));
        UUID parametersUuid = UUID.fromString(getNonNullHeader(headers, "parametersUuid"));
        String variantId = (String) headers.get(VARIANT_ID_HEADER);
        String receiver = (String) headers.get(HEADER_RECEIVER);
        String userId = (String) headers.get(HEADER_USER_ID);

        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString((String) headers.get(REPORT_UUID_HEADER)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        VoltageInitRunContext runContext = new VoltageInitRunContext(networkUuid, variantId, receiver, reportUuid, reporterId, userId, parametersUuid);
        return new VoltageInitResultContext(resultUuid, runContext);
    }

    public Message<String> toMessage() {
        return MessageBuilder.withPayload("")
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", runContext.getNetworkUuid().toString())
                .setHeader("parametersUuid", runContext.getParametersUuid())
                .setHeader(VARIANT_ID_HEADER, runContext.getVariantId())
                .setHeader(HEADER_RECEIVER, runContext.getReceiver())
                .setHeader(HEADER_USER_ID, runContext.getUserId())
                .setHeader(REPORT_UUID_HEADER, runContext.getReportUuid() != null ? runContext.getReportUuid().toString() : null)
                .setHeader(REPORTER_ID_HEADER, runContext.getReporterId())
                .build();
    }
}
