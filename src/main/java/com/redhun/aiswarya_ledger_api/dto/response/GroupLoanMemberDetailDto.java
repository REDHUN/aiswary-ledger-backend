package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupLoanMemberDetailDto {
    private Long memberId;
    private String memberNumber;
    private String fullName;
    private BigDecimal issuedAmount;
    private BigDecimal repaidAmount;
    private BigDecimal currentBalance;
    private Boolean isFullyRepaid;
}
