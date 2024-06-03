/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.commons.PowsyblException;
import lombok.Getter;
import com.powsybl.ws.commons.computation.service.AbstractResultContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.*;

import static com.powsybl.ws.commons.computation.service.NotificationService.HEADER_RECEIVER;
import static com.powsybl.ws.commons.computation.service.NotificationService.HEADER_USER_ID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class VoltageInitResultContext extends AbstractResultContext<VoltageInitRunContext> {

    public static final String PARAMETERS_UUID_HEADER = "parametersUuid";

    public static final String VOLTAGE_LEVELS_IDS_RESTRICTED = "voltageLevelsIdsRestricted";

    public VoltageInitResultContext(UUID resultUuid, VoltageInitRunContext runContext) {
        super(resultUuid, runContext);
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
        String variantId = (String) headers.get(VARIANT_ID_HEADER);
        String receiver = (String) headers.get(HEADER_RECEIVER);
        String userId = (String) headers.get(HEADER_USER_ID);
        Map<String, Double> voltageLevelsIdsRestricted = (Map<String, Double>) headers.get(VOLTAGE_LEVELS_IDS_RESTRICTED);

        UUID parametersUuid = headers.containsKey(PARAMETERS_UUID_HEADER) ? UUID.fromString((String) headers.get(PARAMETERS_UUID_HEADER)) : null;
        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString((String) headers.get(REPORT_UUID_HEADER)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        VoltageInitRunContext runContext = new VoltageInitRunContext(networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid, voltageLevelsIdsRestricted);
        return new VoltageInitResultContext(resultUuid, runContext);
    }

    public Message<String> toMessage() {
        return MessageBuilder.withPayload("")
                .setHeader("resultUuid", getResultUuid().toString())
                .setHeader("networkUuid", getRunContext().getNetworkUuid().toString())
                .setHeader(PARAMETERS_UUID_HEADER, getRunContext().getParametersUuid() != null ? getRunContext().getParametersUuid().toString() : null)
                .setHeader(VARIANT_ID_HEADER, getRunContext().getVariantId())
                .setHeader(HEADER_RECEIVER, getRunContext().getReceiver())
                .setHeader(HEADER_USER_ID, getRunContext().getUserId())
                .setHeader(REPORT_UUID_HEADER, getRunContext().getReportInfos().reportUuid() != null ? getRunContext().getReportInfos().reportUuid().toString() : null)
                .setHeader(REPORTER_ID_HEADER, getRunContext().getReportInfos().reporterId())
                .setHeader(REPORT_TYPE_HEADER, getRunContext().getReportInfos().computationType())
                .setHeader(VOLTAGE_LEVELS_IDS_RESTRICTED, getRunContext().getVoltageLevelsIdsRestricted())
                .build();
    }
}
