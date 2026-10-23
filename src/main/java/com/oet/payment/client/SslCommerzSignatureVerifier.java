package com.oet.payment.client;

import com.oet.payment.config.SslCommerzProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Verifies the verify_sign of an IPN, following the official SSLCommerz SDK:
 * md5 of the "key=value&..." string built from the fields listed in verify_key
 * plus md5(store_passwd), sorted by key.
 */
@Component
@RequiredArgsConstructor
public class SslCommerzSignatureVerifier {

    private static final String VERIFY_SIGN = "verify_sign";
    private static final String VERIFY_KEY = "verify_key";

    private final SslCommerzProperties properties;

    public boolean isValid(Map<String, String> ipnParams) {
        String verifySign = ipnParams.get(VERIFY_SIGN);
        String verifyKey = ipnParams.get(VERIFY_KEY);
        if (!StringUtils.hasText(verifySign) || !StringUtils.hasText(verifyKey)) {
            return false;
        }

        Map<String, String> signedFields = new TreeMap<>();
        for (String key : verifyKey.split(",")) {
            signedFields.put(key, ipnParams.getOrDefault(key, ""));
        }
        signedFields.put("store_passwd", md5(properties.storePassword()));

        String hashString = signedFields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        return MessageDigest.isEqual(
                md5(hashString).getBytes(StandardCharsets.UTF_8),
                verifySign.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String md5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
