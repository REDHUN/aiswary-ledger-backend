package com.redhun.aiswarya_ledger_api.dto.response;

import com.redhun.aiswarya_ledger_api.domain.enums.InterestStatus;
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
public class InterestCalculationDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String interestPeriod;
    private Long meetingId;
    private BigDecimal loanBalanceUsed;
    private BigDecimal interestRate;
    private BigDecimal interestAmount;
    private InterestStatus status;
    private ZonedDateTime calculatedAt;
}
