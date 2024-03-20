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
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openreac.parameters.output.OpenReacResult;
import org.gridsuite.voltageinit.server.dto.GeneratorModificationInfos;
import org.gridsuite.voltageinit.server.dto.ShuntCompensatorModificationInfos;
import org.gridsuite.voltageinit.server.dto.StaticVarCompensatorModificationInfos;
import org.gridsuite.voltageinit.server.dto.TransformerModificationInfos;
import org.gridsuite.voltageinit.server.dto.VoltageInitModificationInfos;
import org.gridsuite.voltageinit.server.dto.VscConverterStationModificationInfos;
import org.jgrapht.alg.util.Pair;
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.powsybl.iidm.network.IdentifiableType.TWO_WINDINGS_TRANSFORMER;

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

    private Optional<Bus> getRegulatingBus(Terminal terminal) {
        return terminal != null && terminal.getBusView().getBus() != null ? Optional.of(terminal.getBusView().getBus()) : Optional.empty();
    }

    public UUID createVoltageInitModificationGroup(Network network, OpenReacResult result) {
        UUID modificationsGroupUuid;

        try {
            VoltageInitModificationInfos voltageInitModificationInfos = new VoltageInitModificationInfos();

            Map<String, Pair<Double, Double>> voltageProfile = result.getVoltageProfile();

            // generator modifications
            result.getGeneratorModifications().forEach(gm -> {
                if (gm.getModifs().getTargetV() != null || gm.getModifs().getTargetQ() != null) {
                    GeneratorModificationInfos.GeneratorModificationInfosBuilder builder = GeneratorModificationInfos.builder()
                        .generatorId(gm.getGeneratorId())
                        .voltageSetpoint(gm.getModifs().getTargetV())
                        .reactivePowerSetpoint(gm.getModifs().getTargetQ());
                    voltageInitModificationInfos.addGeneratorModification(builder.build());
                }
            });

            // transformer modifications
            AtomicReference<Double> targetV = new AtomicReference<>();
            result.getTapPositionModifications().forEach(tp -> {
                targetV.set(null);
                Identifiable<?> identifiable = network.getIdentifiable(tp.getTransformerId());
                if (identifiable != null && identifiable.getType() == TWO_WINDINGS_TRANSFORMER) {  // Only for 2WT
                    TwoWindingsTransformer twoWindingsTransformer = (TwoWindingsTransformer) identifiable;
                    if (twoWindingsTransformer.getRatioTapChanger() != null) {
                        Optional<Bus> bus = getRegulatingBus(twoWindingsTransformer.getRatioTapChanger().getRegulationTerminal());
                        bus.ifPresent(b -> {
                            Pair<Double, Double> busUpdate = voltageProfile.get(b.getId());
                            if (busUpdate != null) {
                                targetV.set(busUpdate.getFirst() * b.getVoltageLevel().getNominalV());
                            }
                        });
                    }
                }
                TransformerModificationInfos.TransformerModificationInfosBuilder builder = TransformerModificationInfos.builder()
                    .transformerId(tp.getTransformerId())
                    .ratioTapChangerPosition(tp.getTapPosition())
                    .ratioTapChangerTargetV(targetV.get())
                    .legSide(tp.getLegSide());
                voltageInitModificationInfos.addTransformerModification(builder.build());
            });

            // static var compensator modifications
            result.getSvcModifications().forEach(staticVarCompensatorModification -> {
                if (staticVarCompensatorModification.getVoltageSetpoint() != null || staticVarCompensatorModification.getReactivePowerSetpoint() != null) {
                    StaticVarCompensatorModificationInfos.StaticVarCompensatorModificationInfosBuilder builder = StaticVarCompensatorModificationInfos.builder()
                        .staticVarCompensatorId(staticVarCompensatorModification.getStaticVarCompensatorId())
                        .voltageSetpoint(staticVarCompensatorModification.getVoltageSetpoint())
                        .reactivePowerSetpoint(staticVarCompensatorModification.getReactivePowerSetpoint());
                    voltageInitModificationInfos.addStaticVarCompensatorModification(builder.build());
                }
            });

            // vsc converter station modifications
            result.getVscModifications().forEach(vscConverterStationModification -> {
                if (vscConverterStationModification.getVoltageSetpoint() != null || vscConverterStationModification.getReactivePowerSetpoint() != null) {
                    VscConverterStationModificationInfos.VscConverterStationModificationInfosBuilder builder = VscConverterStationModificationInfos.builder()
                        .vscConverterStationId(vscConverterStationModification.getVscConverterStationId())
                        .voltageSetpoint(vscConverterStationModification.getVoltageSetpoint())
                        .reactivePowerSetpoint(vscConverterStationModification.getReactivePowerSetpoint());
                    voltageInitModificationInfos.addVscConverterStationModification(builder.build());
                }
            });

            // shunt compensator modifications
            result.getShuntsModifications().forEach(shuntCompensatorModification -> {
                targetV.set(null);
                ShuntCompensator shuntCompensator = network.getShuntCompensator(shuntCompensatorModification.getShuntCompensatorId());
                if (shuntCompensator != null) {
                    Optional<Bus> bus = getRegulatingBus(shuntCompensator.getRegulatingTerminal());
                    bus.ifPresent(b -> {
                        Pair<Double, Double> busUpdate = voltageProfile.get(b.getId());
                        if (busUpdate != null) {
                            targetV.set(busUpdate.getFirst() * b.getVoltageLevel().getNominalV());
                        }
                    });
                }
                ShuntCompensatorModificationInfos.ShuntCompensatorModificationInfosBuilder builder = ShuntCompensatorModificationInfos.builder()
                    .shuntCompensatorId(shuntCompensatorModification.getShuntCompensatorId())
                    .sectionCount(shuntCompensatorModification.getSectionCount())
                    .connect(shuntCompensatorModification.getConnect())
                    .targetV(targetV.get());
                voltageInitModificationInfos.addShuntCompensatorModification(builder.build());
            });

            var uriComponentsBuilder = UriComponentsBuilder
                    .fromUriString(getNetworkModificationServerURI() + "groups" + DELIMITER + "modification");
            var path = uriComponentsBuilder
                    .buildAndExpand()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> httpEntity = new HttpEntity<>(objectMapper.writeValueAsString(voltageInitModificationInfos), headers);

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
