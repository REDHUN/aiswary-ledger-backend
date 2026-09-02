package com.redhun.aiswarya_ledger_api.domain.entity;

import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "member_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "special_loan_type_id")
    private SpecialLoanType specialLoanType;

    @Builder.Default
    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "order_number", nullable = false)
    private Integer orderNumber = 0;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }
        if (orderNumber == null || orderNumber == 0) {
            orderNumber = getDefaultOrderNumber(accountType);
        }
    }

    public static int getDefaultOrderNumber(AccountType type) {
        if (type == null) return 99;
        return switch (type) {
            case DEPOSIT -> 1;
            case LOAN -> 2;
            case SPECIAL_LOAN -> 3;
            case FINE -> 4;
            case TOTAL_PAID_FINE -> 5;
            case MONTHLY_CONTRIBUTION -> 6;
            case FINANCIAL_AID -> 7;
            case INTEREST -> 8;
            case GROUP_PROFIT -> 9;
            case GROUP_EXPENSE -> 10;
            case SURPLUS_FUND -> 11;
        };
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
