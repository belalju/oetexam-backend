package com.oet.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String timestamp,
        int status,
        T data,
        String error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Instant.now().toString(), 200, data, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(Instant.now().toString(), 201, data, null);
    }

    public static ApiResponse<Void> error(int status, String message) {
        return new ApiResponse<>(Instant.now().toString(), status, null, message);
    }
}
