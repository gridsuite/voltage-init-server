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
import org.springframework.stereotype.Service;

/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */
@Service
public class VoltageInitObserver {
    private static final String OBSERVATION_PREFIX = "app.computation.";

    private static final String TYPE_TAG_NAME = "type";

    private static final String PROVIDER_TAG_NAME = "provider";
    private static final String STATUS_TAG_NAME = "status";

    private static final String COMPUTATION_TYPE = "voltageinit";

    private static final String COMPUTATION_COUNTER_NAME = OBSERVATION_PREFIX + "count";
    private static final String OK = "OK";

    private static final String NOK = "NOK";
    private final ObservationRegistry observationRegistry;

    private final MeterRegistry meterRegistry;

    public VoltageInitObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
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
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(PROVIDER_TAG_NAME, COMPUTATION_TYPE)
                .lowCardinalityKeyValue(TYPE_TAG_NAME, COMPUTATION_TYPE);
    }

    private void incrementCount(OpenReacResult result) {
        Counter.builder(COMPUTATION_COUNTER_NAME)
                .tag(PROVIDER_TAG_NAME, COMPUTATION_TYPE)
                .tag(TYPE_TAG_NAME, COMPUTATION_TYPE)
                .tag(STATUS_TAG_NAME, getStatusFromResult(result))
                .register(meterRegistry)
                .increment();
    }

    private static String getStatusFromResult(OpenReacResult result) {
        return result.getStatus() == OpenReacStatus.OK ? OK : NOK;
    }

}
