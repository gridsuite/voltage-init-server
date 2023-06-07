/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;

import java.io.IOException;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

public class VoltageLimitOverrideDeserializer extends StdDeserializer<VoltageLimitOverride> {

    public VoltageLimitOverrideDeserializer() {
        super(VoltageLimitOverride.class);
    }

    @Override
    public VoltageLimitOverride deserialize(JsonParser parser, DeserializationContext deserializationContext) throws IOException {
        double deltaLowVoltageLimit = 0;
        double deltaHighVoltageLimit = 0;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            switch (parser.getCurrentName()) {
                case "deltaLowVoltageLimit":
                    parser.nextToken();
                    deltaLowVoltageLimit = parser.readValueAs(Double.class);
                    break;
                case "deltaHighVoltageLimit":
                    parser.nextToken();
                    deltaHighVoltageLimit = parser.readValueAs(Double.class);
                    break;
                default:
                    throw new IllegalStateException("Unexpected field: " + parser.getCurrentName());
            }
        }
        return new VoltageLimitOverride(deltaLowVoltageLimit, deltaHighVoltageLimit);
    }
}
