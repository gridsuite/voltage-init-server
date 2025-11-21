/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import com.powsybl.commons.PowsyblException;
import com.powsybl.network.store.client.NetworkStoreService;
import lombok.NonNull;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.AbstractFilterService;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.error.VoltageInitBusinessErrorCode;
import org.gridsuite.voltageinit.server.error.VoltageInitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Service
public class FilterService extends AbstractFilterService {

    private static final String FILTER_SERVER_API_VERSION = "v1";

    private static final String DELIMITER = "/";

    public static final String FILTERS_NOT_FOUND = "Filters not found";

    public FilterService(NetworkStoreService networkStoreService,
                         @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        super(networkStoreService, filterServerBaseUri);
    }

    public List<FilterEquipments> exportFilters(List<UUID> filtersUuids, UUID networkUuid, String variantId) {
        var ids = "&ids=" + filtersUuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        var variant = variantId != null ? "&variantId=" + variantId : "";
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/export?networkUuid=" + networkUuid + variant + ids)
                .buildAndExpand()
                .toUriString();
        return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<FilterEquipments>>() { })
                .getBody();
    }

    public Set<UUID> getFiltersExistence(Collection<UUID> filtersUuids) {
        List<UUID> filterIds = filtersUuids.stream()
            .distinct()
            .toList();
        List<AbstractFilter> filters;
        try {
            filters = getFilters(filterIds);
        } catch (PowsyblException e) {
            return Set.of();
        }
        return filters.stream()
            .map(AbstractFilter::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void ensureFiltersExist(Map<UUID, String> filterNamesByUuid) {
        List<AbstractFilter> filters = getFilters(new ArrayList<>(filterNamesByUuid.keySet()));
        Set<UUID> validFilters = filters.stream()
            .map(AbstractFilter::getId)
            .collect(Collectors.toSet());
        List<UUID> missingFilters = filterNamesByUuid.keySet().stream()
            .filter(filterId -> !validFilters.contains(filterId))
            .toList();
        if (!missingFilters.isEmpty()) {
            throw new VoltageInitException(VoltageInitBusinessErrorCode.MISSING_FILTER, buildMissingFiltersMessage(missingFilters, filterNamesByUuid));
        }
    }

    private String buildMissingFiltersMessage(Collection<UUID> filtersUuids, Map<UUID, String> filterNamesByUuid) {
        List<String> missingFilterNames = filtersUuids.stream()
            .map(filterUuid -> Optional.ofNullable(filterNamesByUuid.get(filterUuid)).orElse(filterUuid.toString()))
            .distinct()
            .toList();
        return "Some filters do not exist: " + " [" + String.join(", ", missingFilterNames) + "]";
    }

    @SuppressWarnings("unchecked")
    public List<String> getResourceFilters(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
        Optional<ResourceFilterDTO> res = super.getResourceFilter(networkUuid, variantId, globalFilter, List.of(EquipmentType.VOLTAGE_LEVEL), null);
        if (res.isEmpty() || !(res.get().value() instanceof List<?> list)) {
            return List.of();
        } else {
            return (List<String>) list;
        }
    }
}

