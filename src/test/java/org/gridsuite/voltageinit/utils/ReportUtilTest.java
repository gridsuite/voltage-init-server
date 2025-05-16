/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.voltageinit.utils;

import com.powsybl.commons.report.ReportNode;
import org.gridsuite.voltageinit.server.util.ReportUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
class ReportUtilTest {
    @Test
    void test() {
        ReportNode rootNode = ReportNode.newRootReportNode()
                .withResourceBundles("i18n.reports")
                .withMessageTemplate("VoltageInit").build();
        ReportNode child1Node = rootNode.newReportNode().withMessageTemplate("key1").add();
        ReportNode child2Node = rootNode.newReportNode().withMessageTemplate("key2").add();
        ReportNode child11Node = child1Node.newReportNode().withMessageTemplate("key11").add();
        ReportNode child21Node = child2Node.newReportNode().withMessageTemplate("key21").add();
        ReportNode child22Node = child2Node.newReportNode().withMessageTemplate("key22").add();
        ReportNode child221Node = child22Node.newReportNode().withMessageTemplate("key221").add();

        assertTrue(ReportUtil.checkReportWithKey("VoltageInit", rootNode));
        assertFalse(ReportUtil.checkReportWithKey("key", rootNode));
        assertTrue(ReportUtil.checkReportWithKey("key221", rootNode));
        assertTrue(ReportUtil.checkReportWithKey("key11", child11Node));
        assertTrue(ReportUtil.checkReportWithKey("key21", child21Node));
        assertTrue(ReportUtil.checkReportWithKey("key221", child22Node));
        assertFalse(ReportUtil.checkReportWithKey("key222", child221Node));
    }
}
