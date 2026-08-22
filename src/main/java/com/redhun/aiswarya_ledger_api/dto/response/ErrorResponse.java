package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private boolean success = false;
    private ErrorDetails error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetails {
        private String code;
        private String message;
    }

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(new ErrorDetails(code, message))
                .build();
    }
}
