/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.voltageinit.utils;

import com.google.common.io.ByteStreams;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
public final class TestUtils {

    private TestUtils() {
    }

    public static String resourceToString(String resource) throws IOException {
        String content = new String(ByteStreams.toByteArray(TestUtils.class.getResourceAsStream(resource)), StandardCharsets.UTF_8);
        return StringUtils.replaceWhitespaceCharacters(content, "");
    }
}
