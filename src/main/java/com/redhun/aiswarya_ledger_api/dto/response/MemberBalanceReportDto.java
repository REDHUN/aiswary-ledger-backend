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
public class MemberBalanceReportDto {
    private Long memberId;
    private String memberNumber;
    private String fullName;
    private String phone;
    private Boolean isActive;

    private BigDecimal loanBalance;
    private BigDecimal depositBalance;
    private BigDecimal fineBalance;
    private BigDecimal contributionBalance;
    private BigDecimal financialAidBalance;
    private BigDecimal interestBalance;

    public BigDecimal getTotalNetBalance() {
        // Net balance: (Deposits + Contributions) - (Loans + Fines + Aid + Interest)
        BigDecimal credits = (depositBalance != null ? depositBalance : BigDecimal.ZERO)
                .add(contributionBalance != null ? contributionBalance : BigDecimal.ZERO);

        BigDecimal debits = (loanBalance != null ? loanBalance : BigDecimal.ZERO)
                .add(fineBalance != null ? fineBalance : BigDecimal.ZERO)
                .add(financialAidBalance != null ? financialAidBalance : BigDecimal.ZERO)
                .add(interestBalance != null ? interestBalance : BigDecimal.ZERO);

        return credits.subtract(debits);
    }
}
