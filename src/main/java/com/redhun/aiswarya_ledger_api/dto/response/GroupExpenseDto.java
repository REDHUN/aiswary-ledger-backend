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
public class GroupExpenseDto {
    private Long id;
    private Long expenseTypeId;
    private String expenseTypeName;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String description;
    private Long meetingId;
    private ZonedDateTime createdAt;
}
