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

    public static final String PARAMETERS_UUID_HEADER = "parametersUuid";

    public static final String REPORT_TYPE_HEADER = "reportType";

    public static final String VOLTAGE_LEVELS_IDS_RESTRICTED = "voltageLevelsIdsRestricted";

    private final UUID resultUuid;

    private final VoltageInitRunContext runContext;

    public VoltageInitResultContext(UUID resultUuid, VoltageInitRunContext runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
    }

    private static String getNonNullHeader(MessageHeaders headers, String name) {
        final String header = headers.get(name, String.class);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        } else {
            return header;
        }
    }

    public static VoltageInitResultContext fromMessage(Message<String> message) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(getNonNullHeader(headers, "resultUuid"));
        UUID networkUuid = UUID.fromString(getNonNullHeader(headers, "networkUuid"));
        String variantId = headers.get(VARIANT_ID_HEADER, String.class);
        String receiver = headers.get(HEADER_RECEIVER, String.class);
        String userId = headers.get(HEADER_USER_ID, String.class);
        @SuppressWarnings("unchecked")
        Map<String, Double> voltageLevelsIdsRestricted = headers.get(VOLTAGE_LEVELS_IDS_RESTRICTED, Map.class);
        UUID parametersUuid = headers.containsKey(PARAMETERS_UUID_HEADER) ? UUID.fromString(headers.get(PARAMETERS_UUID_HEADER, String.class)) : null;
        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString(headers.get(REPORT_UUID_HEADER, String.class)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? headers.get(REPORTER_ID_HEADER, String.class) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? headers.get(REPORT_TYPE_HEADER, String.class) : null;
        return new VoltageInitResultContext(resultUuid, new VoltageInitRunContext(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid, voltageLevelsIdsRestricted));
    }

    public Message<String> toMessage() {
        return MessageBuilder.withPayload("")
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", runContext.getNetworkUuid().toString())
                .setHeader(PARAMETERS_UUID_HEADER, runContext.getParametersUuid())
                .setHeader(VARIANT_ID_HEADER, runContext.getVariantId())
                .setHeader(HEADER_RECEIVER, runContext.getReceiver())
                .setHeader(HEADER_USER_ID, runContext.getUserId())
                .setHeader(REPORT_UUID_HEADER, runContext.getReportUuid() != null ? runContext.getReportUuid().toString() : null)
                .setHeader(REPORTER_ID_HEADER, runContext.getReporterId())
                .setHeader(REPORT_TYPE_HEADER, runContext.getReportType())
                .setHeader(VOLTAGE_LEVELS_IDS_RESTRICTED, runContext.getVoltageLevelsIdsRestricted())
                .build();
    }
}
