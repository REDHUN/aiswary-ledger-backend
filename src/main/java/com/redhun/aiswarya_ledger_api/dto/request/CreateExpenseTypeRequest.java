package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateExpenseTypeRequest {
    @NotBlank(message = "Expense type name is required")
    private String name;
    private String description;
}
