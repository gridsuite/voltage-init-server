/*
  Copyright (c) 2023, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import org.gridsuite.voltageinit.server.dto.AttributeModification;
import org.gridsuite.voltageinit.server.dto.GeneratorModificationInfos;
import org.gridsuite.voltageinit.server.dto.TableEquipmentModificationInfos;
import org.gridsuite.voltageinit.server.dto.OperationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NetworkModificationService {
    private static final String NETWORK_MODIFICATION_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private static final String GROUP_PATH = "groups" + DELIMITER + "{groupUuid}";
    public static final String QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND = "errorOnGroupNotFound";

    private String networkModificationServerBaseUri;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper;

    @Autowired
    NetworkModificationService(@Value("${gridsuite.services.network-modification-server.base-uri:http://network-modification-server/}") String networkModificationServerBaseUri,
                               ObjectMapper objectMapper) {
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
        this.objectMapper = objectMapper;
    }

    public void setNetworkModificationServerBaseUri(String networkModificationServerBaseUri) {
        this.networkModificationServerBaseUri = networkModificationServerBaseUri + DELIMITER;
    }

    private String getNetworkModificationServerURI() {
        return this.networkModificationServerBaseUri + DELIMITER + NETWORK_MODIFICATION_API_VERSION + DELIMITER;
    }

    public void deleteModificationsGroup(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
            .buildAndExpand(groupUUid)
            .toUriString();

        try {
            restTemplate.delete(getNetworkModificationServerURI() + path);
        } catch (HttpStatusCodeException e) {
            throw new PowsyblException("Error deleting modifications group", e);
        }
    }

    public UUID createTableEquipmentModificationGroup(OpenReacResult result) {
        UUID modificationsGroupUuid = null;

        try {
            TableEquipmentModificationInfos tableEquipmentModificationInfos = new TableEquipmentModificationInfos();

            result.getGeneratorModifications().forEach(gm -> {
                if (gm.getModifs().getTargetV() != null || gm.getModifs().getTargetQ() != null) {
                    GeneratorModificationInfos.GeneratorModificationInfosBuilder builder = GeneratorModificationInfos.builder()
                        .equipmentId(gm.getGeneratorId());
                    if (gm.getModifs().getTargetV() != null) {
                        builder.voltageSetpoint(new AttributeModification<>(gm.getModifs().getTargetV(), OperationType.SET));
                    }
                    if (gm.getModifs().getTargetQ() != null) {
                        builder.reactivePowerSetpoint(new AttributeModification<>(gm.getModifs().getTargetQ(), OperationType.SET));
                    }
                    tableEquipmentModificationInfos.addModification(builder.build());
                }
            });

            var uriComponentsBuilder = UriComponentsBuilder
                    .fromUriString(getNetworkModificationServerURI() + "groups" + DELIMITER + "table-equipment-modification");
            var path = uriComponentsBuilder
                    .buildAndExpand()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> httpEntity = new HttpEntity<>(objectMapper.writeValueAsString(tableEquipmentModificationInfos), headers);

            modificationsGroupUuid = restTemplate.exchange(path, HttpMethod.POST, httpEntity, UUID.class)
                .getBody();
        } catch (JsonProcessingException e) {
            throw new PowsyblException("Error generating json modifications", e);
        } catch (HttpStatusCodeException e) {
            throw new PowsyblException("Error creating modifications group", e);
        }

        return modificationsGroupUuid;
    }
}
