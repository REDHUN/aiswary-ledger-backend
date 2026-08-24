package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialLoanTypeDto {
    private Long id;
    private String name;
    private String description;
    private Boolean isActive;
    private ZonedDateTime createdAt;
}
