/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.server.util;

import com.powsybl.commons.report.ReportNode;

import java.util.Iterator;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public final class ReportUtil {
    private ReportUtil() {
    }

    public static boolean checkReportWithKey(String key, ReportNode reportNode) {
        if (reportNode.getMessageKey() != null && reportNode.getMessageKey().equals(key)) {
            return true;
        }
        boolean found = false;
        Iterator<ReportNode> reportersIterator = reportNode.getChildren().iterator();
        while (!found && reportersIterator.hasNext()) {
            found = checkReportWithKey(key, reportersIterator.next());
        }
        return found;
    }
}
