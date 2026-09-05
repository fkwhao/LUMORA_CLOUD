package com.lumora.cloud.billing.utils;

import com.lumora.cloud.billing.error.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BillingAmounts {

    public static final int SCALE = 6;

    private BillingAmounts() {
    }

    public static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw invalid(field);
        }
        try {
            return value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUOTA_PRECISION",
                    field + " 最多支持 6 位小数");
        }
    }

    public static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUOTA", field + " 不能小于 0");
        }
        return value.signum() == 0 ? zero() : positive(value, field);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }

    private static ApiException invalid(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUOTA", field + " 必须大于 0");
    }
}
