package com.redhun.aiswarya_ledger_api.domain.entity;

import com.redhun.aiswarya_ledger_api.domain.enums.InterestStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(
    name = "interest_calculations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_interest_period", columnNames = {"member_id", "interest_period"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterestCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "interest_period", nullable = false, length = 7)
    private String interestPeriod; // 'YYYY-MM'

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "loan_balance_used", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanBalanceUsed;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal interestRate = new BigDecimal("0.0100");

    @Column(name = "interest_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InterestStatus status = InterestStatus.CALCULATED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calculated_by", nullable = false)
    private User calculatedBy;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private ZonedDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        calculatedAt = ZonedDateTime.now();
    }
}
