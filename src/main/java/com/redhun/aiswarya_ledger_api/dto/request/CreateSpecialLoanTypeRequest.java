package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSpecialLoanTypeRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String description;
    private Boolean isActive = true;
}
