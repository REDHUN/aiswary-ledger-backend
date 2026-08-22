package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ScheduleMeetingRequest {

    @NotNull(message = "Meeting date is required")
    private LocalDate meetingDate;

    private String notes;
}
