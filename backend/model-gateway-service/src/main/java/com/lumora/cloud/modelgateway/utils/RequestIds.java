package com.lumora.cloud.modelgateway.utils;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RequestIds {

    public String billingRequestId(long userId, String clientRequestId) {
        return "mgw_" + digest(userId + ":" + clientRequestId, 48);
    }

    public String usageId(String billingRequestId, String pricingVersion) {
        return "usage_" + digest(billingRequestId + ":" + pricingVersion, 48);
    }

    public String keyDigest(long userId, String clientRequestId) {
        return digest(userId + ":" + clientRequestId, 48);
    }

    private String digest(String value, int length) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, length);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
