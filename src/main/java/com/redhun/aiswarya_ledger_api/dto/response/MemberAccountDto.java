package com.redhun.aiswarya_ledger_api.dto.response;

import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberAccountDto {
    private Long id;
    private Long memberId;
    private AccountType accountType;
    private Long specialLoanTypeId;
    private String specialLoanTypeName;
    private BigDecimal currentBalance;
    private Long version;
}
