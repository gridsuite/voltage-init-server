package org.gridsuite.voltageinit.server.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;

public class VoltageInitJsonModule extends SimpleModule {

    public VoltageInitJsonModule() {
        addDeserializer(OpenReacParameters.class, new OpenReactParametersDeserializer());
        addDeserializer(VoltageLimitOverride.class, new VoltageLimitOverrideDeserializer());
    }
}
