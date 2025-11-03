/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service.parameters;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.computation.dto.GlobalFilter;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.FilterLoader;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.AbstractExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.CombinatorExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.EnumExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.FilterUuidExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.NumberExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.PropertiesExpertRule;
import org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.FilterServiceUtils;
import org.gridsuite.filter.utils.expertfilter.CombinatorType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.gridsuite.voltageinit.server.dto.parameters.FilterEquipments;
import org.gridsuite.voltageinit.server.error.VoltageInitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Service
public class FilterService implements FilterLoader {

    private static final String FILTER_SERVER_API_VERSION = "v1";

    private static final String DELIMITER = "/";

    private static String filterServerBaseUri;

    private final RestTemplate restTemplate;

    private final NetworkStoreService networkStoreService;

    public static final String FILTERS_NOT_FOUND = "Filters not found";

    public FilterService(NetworkStoreService networkStoreService,
                         @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri,
                         RestTemplate restTemplate) {
        this.networkStoreService = networkStoreService;
        setFilterServerBaseUri(filterServerBaseUri);
        this.restTemplate = restTemplate;
    }

    public static void setFilterServerBaseUri(String filterServerBaseUri) {
        FilterService.filterServerBaseUri = filterServerBaseUri;
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

    private Network getNetwork(UUID networkUuid, String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            network.getVariantManager().setWorkingVariant(variantId);
            return network;
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public List<AbstractFilter> getFilters(List<UUID> filtersUuids) {
        if (CollectionUtils.isEmpty(filtersUuids)) {
            return List.of();
        }
        var ids = "?ids=" + filtersUuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/metadata" + ids)
                .buildAndExpand()
                .toUriString();
        try {
            return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<AbstractFilter>>() { }).getBody();
        } catch (HttpStatusCodeException e) {
            throw new PowsyblException(FILTERS_NOT_FOUND + " [" + filtersUuids + "]");
        }
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
            throw new VoltageInitException(buildMissingFiltersMessage(missingFilters, filterNamesByUuid));
        }
    }

    private String buildMissingFiltersMessage(Collection<UUID> filtersUuids, Map<UUID, String> filterNamesByUuid) {
        List<String> missingFilterNames = filtersUuids.stream()
            .map(filterUuid -> Optional.ofNullable(filterNamesByUuid.get(filterUuid)).orElse(filterUuid.toString()))
            .distinct()
            .toList();
        return "Some filters do not exist: " + " [" + String.join(", ", missingFilterNames) + "]";
    }

    private List<AbstractExpertRule> createNumberExpertRules(List<String> values, FieldType fieldType) {
        List<AbstractExpertRule> rules = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                rules.add(NumberExpertRule.builder()
                    .value(Double.valueOf(value))
                    .field(fieldType)
                    .operator(OperatorType.EQUALS)
                    .build());
            }
        }
        return rules;
    }

    private List<AbstractExpertRule> createEnumExpertRules(List<Country> values, FieldType fieldType) {
        List<AbstractExpertRule> rules = new ArrayList<>();
        if (values != null) {
            for (Country value : values) {
                rules.add(EnumExpertRule.builder()
                        .value(value.toString())
                        .field(fieldType)
                        .operator(OperatorType.EQUALS)
                        .build());
            }
        }
        return rules;
    }

    private List<AbstractExpertRule> createNominalVoltageRules(List<String> nominalVoltageList, List<FieldType> nominalFieldTypes) {
        List<AbstractExpertRule> nominalVoltageRules = new ArrayList<>();
        for (FieldType fieldType : nominalFieldTypes) {
            nominalVoltageRules.addAll(createNumberExpertRules(nominalVoltageList, fieldType));
        }
        return nominalVoltageRules;
    }

    private List<AbstractExpertRule> createCountryCodeRules(List<Country> countryCodeList, List<FieldType> countryCodeFieldTypes) {
        List<AbstractExpertRule> countryCodeRules = new ArrayList<>();
        for (FieldType fieldType : countryCodeFieldTypes) {
            countryCodeRules.addAll(createEnumExpertRules(countryCodeList, fieldType));
        }
        return countryCodeRules;
    }

    private AbstractExpertRule createPropertiesRule(String property, List<String> propertiesValues, FieldType fieldType) {
        return PropertiesExpertRule.builder()
            .combinator(CombinatorType.OR)
            .operator(OperatorType.IN)
            .field(fieldType)
            .propertyName(property)
            .propertyValues(propertiesValues)
            .build();
    }

    private List<AbstractExpertRule> createPropertiesRules(String property, List<String> propertiesValues, List<FieldType> propertiesFieldTypes) {
        List<AbstractExpertRule> propertiesRules = new ArrayList<>();
        for (FieldType fieldType : propertiesFieldTypes) {
            propertiesRules.add(createPropertiesRule(property, propertiesValues, fieldType));
        }
        return propertiesRules;
    }

    private AbstractExpertRule createCombination(CombinatorType combinatorType, List<AbstractExpertRule> rules) {
        return CombinatorExpertRule.builder().combinator(combinatorType).rules(rules).build();
    }

    private Optional<AbstractExpertRule> createOrCombination(List<AbstractExpertRule> rules) {
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rules.size() > 1 ? createCombination(CombinatorType.OR, rules) : rules.getFirst());
    }

    private ExpertFilter buildExpertFilter(GlobalFilter globalFilter) {
        List<AbstractExpertRule> andRules = new ArrayList<>();

        // among themselves the various global filter rules are OR combinated
        List<AbstractExpertRule> nominalVRules = createNominalVoltageRules(globalFilter.getNominalV(), List.of(FieldType.NOMINAL_VOLTAGE));
        createOrCombination(nominalVRules).ifPresent(andRules::add);

        List<AbstractExpertRule> countryCodeRules = createCountryCodeRules(globalFilter.getCountryCode(), List.of(FieldType.COUNTRY));
        createOrCombination(countryCodeRules).ifPresent(andRules::add);

        if (globalFilter.getSubstationProperty() != null) {
            List<AbstractExpertRule> propertiesRules = new ArrayList<>();
            globalFilter.getSubstationProperty().forEach((propertyName, propertiesValues) ->
                    propertiesRules.addAll(createPropertiesRules(
                            propertyName,
                            propertiesValues,
                            List.of(FieldType.SUBSTATION_PROPERTIES)
                    )));
            createOrCombination(propertiesRules).ifPresent(andRules::add);
        }

        // between them the various global filter rules are AND combinated
        AbstractExpertRule andCombination = createCombination(CombinatorType.AND, andRules);

        return new ExpertFilter(UUID.randomUUID(), new Date(), EquipmentType.VOLTAGE_LEVEL, andCombination);
    }

    private static List<String> filterNetwork(AbstractFilter filter, Network network, FilterLoader filterLoader) {
        return FilterServiceUtils.getIdentifiableAttributes(filter, network, filterLoader)
                .stream()
                .map(IdentifiableAttributes::getId)
                .toList();
    }

    private AbstractExpertRule createVoltageLevelIdRule(UUID filterUuid) {
        return FilterUuidExpertRule.builder()
            .operator(OperatorType.IS_PART_OF)
            .field(FieldType.ID)
            .values(Set.of(filterUuid.toString()))
            .build();
    }

    public List<String> getResourceFilters(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
        Network network = getNetwork(networkUuid, variantId);

        List<List<String>> idsFilteredThroughEachFilter = new ArrayList<>();
        ExpertFilter expertFilter = buildExpertFilter(globalFilter);
        idsFilteredThroughEachFilter.add(new ArrayList<>(filterNetwork(expertFilter, network, this)));

        final List<AbstractFilter> genericFilters = getFilters(globalFilter.getGenericFilter());
        for (AbstractFilter filter : genericFilters) {
            AbstractExpertRule voltageLevelIdRule = createVoltageLevelIdRule(filter.getId());
            ExpertFilter expertFilterWithIdsCriteria = new ExpertFilter(UUID.randomUUID(), new Date(), EquipmentType.VOLTAGE_LEVEL, voltageLevelIdRule);
            idsFilteredThroughEachFilter.add(new ArrayList<>(filterNetwork(expertFilterWithIdsCriteria, network, this)));
        }

        // combine the results
        // attention : generic filters all use AND operand between them while other filters use OR between them
        EnumMap<EquipmentType, List<String>> subjectIdsByEquipmentType = new EnumMap<>(EquipmentType.class);
        for (List<String> idsFiltered : idsFilteredThroughEachFilter) {
            // if there was already a filtered list for this equipment type : AND filtering :
            subjectIdsByEquipmentType.computeIfPresent(EquipmentType.VOLTAGE_LEVEL, (key, value) -> value.stream()
                .filter(idsFiltered::contains).toList());
            // otherwise, initialisation :
            subjectIdsByEquipmentType.computeIfAbsent(EquipmentType.VOLTAGE_LEVEL, key -> new ArrayList<>(idsFiltered));
        }

        // combine all the results into one list
        List<String> idsFromEvalFilter = new ArrayList<>();
        subjectIdsByEquipmentType.values().forEach(idsList ->
            Optional.ofNullable(idsList).ifPresent(idsFromEvalFilter::addAll)
        );

        return idsFromEvalFilter;
    }
}

