/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import lombok.Getter;
import com.powsybl.ws.commons.computation.service.AbstractResultContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.io.UncheckedIOException;
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

    public static VoltageInitResultContext fromMessage(Message<String> message, ObjectMapper objectMapper) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(getNonNullHeader(headers, RESULT_UUID_HEADER));
        UUID networkUuid = UUID.fromString(getNonNullHeader(headers, NETWORK_UUID_HEADER));
        String variantId = (String) headers.get(VARIANT_ID_HEADER);
        String receiver = (String) headers.get(HEADER_RECEIVER);
        String userId = (String) headers.get(HEADER_USER_ID);
        Map<String, Double> voltageLevelsIdsRestricted;
        try {
            voltageLevelsIdsRestricted = headers.get(VOLTAGE_LEVELS_IDS_RESTRICTED) != null ?
                    objectMapper.readValue((String) headers.get(VOLTAGE_LEVELS_IDS_RESTRICTED), new TypeReference<>() { }) :
                    null;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID parametersUuid = Optional.ofNullable((String) headers.get(PARAMETERS_UUID_HEADER))
                .map(UUID::fromString)
                .orElse(null);
        UUID reportUuid = Optional.ofNullable((String) headers.get(REPORT_UUID_HEADER))
                .map(UUID::fromString)
                .orElse(null);
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        VoltageInitRunContext runContext = new VoltageInitRunContext(
                networkUuid, variantId, receiver, reportUuid, reporterId, reportType, userId, parametersUuid, voltageLevelsIdsRestricted
        );
        return new VoltageInitResultContext(resultUuid, runContext);
    }

    @Override
    protected Map<String, String> getSpecificMsgHeaders(ObjectMapper objectMapper) {
        Map<String, String> specificMsgHeaders = new HashMap<>();
        if (getRunContext().getParametersUuid() != null) {
            specificMsgHeaders.put(PARAMETERS_UUID_HEADER, getRunContext().getParametersUuid().toString());
        }
        if (getRunContext().getVoltageLevelsIdsRestricted() != null) {
            try {
                specificMsgHeaders.put(VOLTAGE_LEVELS_IDS_RESTRICTED,
                        objectMapper.writeValueAsString(getRunContext().getVoltageLevelsIdsRestricted()));
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }
        return specificMsgHeaders;
    }
}
