package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTestResponse {

    private int totalTargeted;
    private int successCount;
    private int failureCount;
    private String status;
    private String message;
    private List<String> errors;
}
