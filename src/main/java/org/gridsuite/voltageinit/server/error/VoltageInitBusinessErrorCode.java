package org.gridsuite.voltageinit.server.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public enum VoltageInitBusinessErrorCode implements BusinessErrorCode {
    MISSING_FILTER("voltageInit.missingFilter");

    private final String code;

    VoltageInitBusinessErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}
