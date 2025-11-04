/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.voltageinit.server.error.VoltageInitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * @author Mohamed Benrejeb <mohamed.ben-rejeb at rte-france.com>
 */
class FilterServiceTest {

    private FilterService filterService;

    @BeforeEach
    void setUp() {
        filterService = spy(new FilterService(Mockito.mock(NetworkStoreService.class), "http://filter-server/", null));
    }

    @Test
    void ensureFiltersExistThrowsWhenSomeFiltersAreMissing() {
        UUID existingFilterId = UUID.fromString("a572411c-772b-41c2-8674-a0ffbe87e3c0");
        UUID missingFilterId = UUID.fromString("9d2be9eb-e8ce-4bb0-94d6-b8e9b1be9d6a");
        AbstractFilter existingFilter = mock(AbstractFilter.class);
        Mockito.when(existingFilter.getId()).thenReturn(existingFilterId);

        doReturn(List.of(existingFilter)).when(filterService).getFilters(anyList());

        Map<UUID, String> filters = Map.of(
            existingFilterId, "Existing filter",
            missingFilterId, "Missing filter"
        );

        assertThatThrownBy(() -> filterService.ensureFiltersExist(filters))
            .isInstanceOf(VoltageInitException.class)
            .hasMessageContaining("Some filters do not exist:  [Missing filter]")
            .hasMessageNotContaining("Existing filter");
    }

    @Test
    void ensureFiltersExistCompletesWhenAllFiltersArePresent() {
        UUID filterId = UUID.fromString("89c30dd3-06f2-4be2-9fd2-6b57efcc6c37");
        AbstractFilter filter = mock(AbstractFilter.class);
        Mockito.when(filter.getId()).thenReturn(filterId);

        doReturn(List.of(filter)).when(filterService).getFilters(anyList());

        Map<UUID, String> filters = Map.of(filterId, "Filter");

        assertThatCode(() -> filterService.ensureFiltersExist(filters)).doesNotThrowAnyException();
    }
}

