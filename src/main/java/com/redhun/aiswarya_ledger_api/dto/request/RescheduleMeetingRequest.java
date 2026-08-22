package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RescheduleMeetingRequest {

    @NotNull(message = "New meeting date is required")
    private LocalDate newMeetingDate;

    private String reason;
}
