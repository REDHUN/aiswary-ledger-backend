package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CalculateInterestRequest {

    @NotBlank(message = "Interest period is required (e.g., '2026-09')")
    private String interestPeriod;

    @NotNull(message = "Meeting ID is required")
    private Long meetingId;
}
