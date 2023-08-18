/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.utils.assertions;

import org.assertj.core.util.CheckReturnValue;
import org.gridsuite.voltageinit.server.dto.settings.VoltageInitSettingInfos;

/**
 *  @author Tristan Chuine <tristan.chuine at rte-france.com>
 * {@link org.assertj.core.api.Assertions Assertions} completed with our custom assertions classes.
 */
public class Assertions extends org.assertj.core.api.Assertions {
    @CheckReturnValue
    public static DTOAssert<VoltageInitSettingInfos> assertThat(VoltageInitSettingInfos actual) {
        return new DTOAssert<>(actual);
    }
}
