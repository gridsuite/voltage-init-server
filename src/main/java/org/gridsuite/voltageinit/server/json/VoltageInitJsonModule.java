/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

public class VoltageInitJsonModule extends SimpleModule {

    public VoltageInitJsonModule() {
        addDeserializer(OpenReacParameters.class, new OpenReactParametersDeserializer());
        addDeserializer(VoltageLimitOverride.class, new VoltageLimitOverrideDeserializer());
    }
}
