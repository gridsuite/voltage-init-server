package org.gridsuite.voltageinit.server.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

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
