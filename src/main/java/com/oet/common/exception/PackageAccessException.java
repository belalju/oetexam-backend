package com.oet.common.exception;

/**
 * The user's pricing package is missing, expired, or does not include the requested test.
 */
public class PackageAccessException extends RuntimeException {

    public PackageAccessException(String message) {
        super(message);
    }
}
