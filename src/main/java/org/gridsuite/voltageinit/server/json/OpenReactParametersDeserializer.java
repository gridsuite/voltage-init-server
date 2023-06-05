/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;

import java.io.IOException;
import java.util.*;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

public class OpenReactParametersDeserializer extends StdDeserializer<OpenReacParameters> {

    public OpenReactParametersDeserializer() {
        super(OpenReacParameters.class);
    }

    @Override
    public OpenReacParameters deserialize(JsonParser parser, DeserializationContext deserializationContext) throws IOException {
        return deserialize(parser, deserializationContext, new OpenReacParameters());
    }

    @Override
    public OpenReacParameters deserialize(JsonParser parser, DeserializationContext deserializationContext, OpenReacParameters parameters) throws IOException {
        TypeReference<HashMap<String, VoltageLimitOverride>> specificVoltageLimitType
                = new TypeReference<HashMap<String, VoltageLimitOverride>>() { };

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            switch (parser.getCurrentName()) {
                case "specificVoltageLimits":
                    parser.nextToken();
                    parameters.addSpecificVoltageLimits(parser.readValueAs(specificVoltageLimitType));
                    break;
                case "variableShuntCompensators":
                case "constantQGenerators":
                case "variableTwoWindingsTransformers":
                case "genericParamsList":
                case "objective":
                case "objectiveDistance":
                case "allAlgorithmParams":
                    parser.nextToken();
                    parser.readValueAs(Object.class);
                    break;
                default:
                    throw new IllegalStateException("Unexpected field: " + parser.getCurrentName());
            }
        }

        return parameters;
    }

}
