package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupProfitDto {
    private Long id;
    private String title;
    private BigDecimal amount;
    private LocalDate profitDate;
    private String description;
    private Long meetingId;
    private ZonedDateTime createdAt;
}
