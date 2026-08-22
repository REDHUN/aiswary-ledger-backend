package com.redhun.aiswarya_ledger_api.dto.response;

import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialTransactionDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private AccountType accountType;
    private TransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long meetingId;
    private String referenceType;
    private Long referenceId;
    private String description;
    private String createdByUsername;
    private ZonedDateTime createdAt;
}
