package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReverseTransactionRequest {

    @NotBlank(message = "Reason for reversal is required")
    private String reason;
}
