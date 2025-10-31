/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageInitParametersInfos;
import org.gridsuite.voltageinit.server.dto.parameters.VoltageLimitInfos;
import org.gridsuite.voltageinit.server.entities.parameters.FilterEquipmentsEmbeddable;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageInitParametersEntity;
import org.gridsuite.voltageinit.server.entities.parameters.VoltageLimitEntity;
import org.gridsuite.voltageinit.server.repository.parameters.VoltageInitParametersRepository;
import org.gridsuite.voltageinit.server.util.VoltageLimitParameterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mohamed BENREJEB <mohamed.ben-rejeb at rte-france.com>
 */
@ExtendWith(MockitoExtension.class)
class VoltageInitParametersServiceTest {

    @Mock
    private VoltageInitParametersRepository repository;

    @Mock
    private FilterService filterService;

    private VoltageInitParametersService service;

    @BeforeEach
    void setUp() {
        service = new VoltageInitParametersService(repository, filterService);
    }

    @Test
    void getParametersMarksFilterExistenceFlags() {
        UUID parametersId = UUID.fromString("2b2c32c3-602c-4e2b-8bcd-5e21ef83a1d5");
        UUID existingFilterId = UUID.fromString("89f78f2d-9b7e-4015-9348-f52f62a5a351");
        UUID missingFilterId = UUID.fromString("6dff5217-1640-4c51-bd31-7bb4e7ceaf8d");

        VoltageLimitEntity defaultLimit = VoltageLimitEntity.builder()
            .voltageLimitParameterType(VoltageLimitParameterType.DEFAULT)
            .filters(List.of(
                new FilterEquipmentsEmbeddable(existingFilterId, "Existing filter", false),
                new FilterEquipmentsEmbeddable(missingFilterId, "Missing filter", false)
            ))
            .build();

        VoltageInitParametersEntity entity = VoltageInitParametersEntity.builder()
            .id(parametersId)
            .variableQGenerators(List.of(new FilterEquipmentsEmbeddable(existingFilterId, "Existing filter", false)))
            .variableTwoWindingsTransformers(List.of(new FilterEquipmentsEmbeddable(missingFilterId, "Missing filter", false)))
            .voltageLimits(List.of(defaultLimit))
            .build();

        when(repository.findById(parametersId)).thenReturn(Optional.of(entity));

        LinkedHashMap<UUID, Boolean> existence = new LinkedHashMap<>();
        existence.put(existingFilterId, Boolean.TRUE);
        existence.put(missingFilterId, Boolean.FALSE);
        when(filterService.getFiltersExistence(any())).thenReturn(existence);

        VoltageInitParametersInfos parameters = service.getParameters(parametersId);

        assertThat(parameters).isNotNull();
        assertThat(parameters.getVariableQGenerators()).singleElement()
            .extracting(FilterEquipments::isValid)
            .isEqualTo(true);
        assertThat(parameters.getVariableTwoWindingsTransformers()).singleElement()
            .extracting(FilterEquipments::isValid)
            .isEqualTo(false);

        List<VoltageLimitInfos> defaultLimits = parameters.getVoltageLimitsDefault();
        assertThat(defaultLimits).hasSize(1);
        assertThat(defaultLimits.get(0).getFilters())
            .extracting(FilterEquipments::isValid)
            .containsExactly(true, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(filterService).getFiltersExistence(captor.capture());
        assertThat(captor.getValue()).containsExactly(existingFilterId, missingFilterId);
    }
}
