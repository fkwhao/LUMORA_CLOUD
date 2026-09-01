package com.lumora.cloud.catalog.service;

import com.lumora.cloud.catalog.error.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class CatalogAmounts {

    private static final int SCALE = 6;

    private CatalogAmounts() {
    }

    static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATALOG_AMOUNT",
                    field + " 不能为负数");
        }
        try {
            return value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATALOG_AMOUNT",
                    field + " 最多支持 6 位小数");
        }
    }

    static BigDecimal optionalNonNegative(BigDecimal value, String field) {
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : nonNegative(value, field);
    }

    static BigDecimal positive(BigDecimal value, String field) {
        BigDecimal normalized = nonNegative(value, field);
        if (normalized.signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATALOG_AMOUNT",
                    field + " 必须大于 0");
        }
        return normalized;
    }
}
