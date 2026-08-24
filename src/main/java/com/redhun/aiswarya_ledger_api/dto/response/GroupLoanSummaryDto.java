package com.redhun.aiswarya_ledger_api.dto.response;

import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupLoanSummaryDto {
    private Long id;
    private Long groupId;
    private String groupName;
    private AccountType accountType;
    private Long specialLoanTypeId;
    private String specialLoanTypeName;
    private BigDecimal totalAmount;
    private BigDecimal perMemberAmount;
    private Integer memberCount;
    private String notes;
    private LocalDate transactionDate;
    private List<Long> memberIds;
    private BigDecimal totalRepaidAmount;
    private BigDecimal totalRemainingBalance;
    private List<GroupLoanMemberDetailDto> memberDetails;
    private ZonedDateTime createdAt;
}
