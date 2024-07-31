/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.service;

import com.powsybl.openreac.parameters.output.OpenReacResult;
import com.powsybl.openreac.parameters.output.OpenReacStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import com.powsybl.ws.commons.computation.service.AbstractComputationObserver;
import org.springframework.stereotype.Service;

/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */
@Service
public class VoltageInitObserver extends AbstractComputationObserver<OpenReacResult, Void> {

    private static final String COMPUTATION_TYPE = "voltageinit";
    private static final String OK = "OK";

    private static final String NOK = "NOK";

    public VoltageInitObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        super(observationRegistry, meterRegistry);
    }

    public <E extends Throwable> void observe(String name, Observation.CheckedRunnable<E> callable) throws E {
        createObservation(name).observeChecked(callable);
    }

    public <T, E extends Throwable> T observe(String name, Observation.CheckedCallable<T, E> callable) throws E {
        return createObservation(name).observeChecked(callable);
    }

    public <T extends OpenReacResult, E extends Throwable> T observeRun(String name, Observation.CheckedCallable<T, E> callable) throws E {
        T result = createObservation(name).observeChecked(callable);
        incrementCount(result);
        return result;
    }

    private Observation createObservation(String name) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, getObservationRegistry())
                .lowCardinalityKeyValue(PROVIDER_TAG_NAME, COMPUTATION_TYPE)
                .lowCardinalityKeyValue(TYPE_TAG_NAME, COMPUTATION_TYPE);
    }

    private void incrementCount(OpenReacResult result) {
        Counter.builder(COMPUTATION_COUNTER_NAME)
                .tag(PROVIDER_TAG_NAME, COMPUTATION_TYPE)
                .tag(TYPE_TAG_NAME, COMPUTATION_TYPE)
                .tag(STATUS_TAG_NAME, getResultStatus(result))
                .register(getMeterRegistry())
                .increment();
    }

    @Override
    protected String getResultStatus(OpenReacResult result) {
        return result.getStatus() == OpenReacStatus.OK ? OK : NOK;
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }
}
