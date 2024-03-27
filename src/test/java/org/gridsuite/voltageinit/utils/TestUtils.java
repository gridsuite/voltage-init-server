/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.voltageinit.utils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
public final class TestUtils {

    private TestUtils() {
        throw new RuntimeException("Utility class can't be instantiated");
    }

    public static String resourceToString(final String resource) throws IOException, URISyntaxException {
        return Files.readString(Paths.get(ClassLoader.getSystemClassLoader().getResource(resource).toURI()), StandardCharsets.UTF_8).trim();
    }
}
