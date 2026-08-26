package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.FinancialTransaction;
import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import com.redhun.aiswarya_ledger_api.domain.entity.SystemSetting;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.repository.FinancialTransactionRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import com.redhun.aiswarya_ledger_api.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final MemberRepository memberRepository;
    public static final String SURPLUS_AMOUNT_KEY = "surplus_amount";

    @Transactional(readOnly = true)
    public BigDecimal getSurplusAmount() {
        BigDecimal savedAmount = systemSettingRepository.findById(SURPLUS_AMOUNT_KEY)
                .map(s -> {
                    try {
                        return new BigDecimal(s.getSettingValue());
                    } catch (Exception e) {
                        return BigDecimal.ZERO;
                    }
                })
                .orElse(BigDecimal.ZERO);

        if (savedAmount.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal computed = calculateSurplusFromCompletedMeetings();
            if (computed.compareTo(BigDecimal.ZERO) > 0) {
                return computed;
            }
        }

        return savedAmount;
    }

    public BigDecimal calculateSurplusFromCompletedMeetings() {
        BigDecimal collections = financialTransactionRepository.sumAllCollections();
        if (collections == null) collections = BigDecimal.ZERO;

        BigDecimal aid = financialTransactionRepository.sumAllFinancialAidIssued();
        if (aid == null) aid = BigDecimal.ZERO;

        BigDecimal loansIssued = financialTransactionRepository.sumAllLoansIssued();
        if (loansIssued == null) loansIssued = BigDecimal.ZERO;

        BigDecimal net = collections.subtract(aid).subtract(loansIssued);
        return net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net;
    }

    @Transactional
    public BigDecimal addSurplusAmount(BigDecimal amountToAdd, String description, User operator) {
        if (amountToAdd == null || amountToAdd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Surplus amount to add must be greater than zero");
        }

        BigDecimal currentSurplus = getSurplusAmount();
        BigDecimal newSurplus = currentSurplus.add(amountToAdd);

        updateSurplusAmount(newSurplus);

        FinancialTransaction tx = FinancialTransaction.builder()
                .member(null)
                .accountType(AccountType.SURPLUS_FUND)
                .transactionType(TransactionType.SURPLUS_ADDITION)
                .amount(amountToAdd)
                .balanceBefore(currentSurplus)
                .balanceAfter(newSurplus)
                .referenceType("SURPLUS_FUND_ADDITION")
                .description(description != null && !description.trim().isEmpty()
                        ? description
                        : "Surplus Fund Addition (മിച്ച തുക ചേർത്തു)")
                .createdBy(operator)
                .createdAt(ZonedDateTime.now())
                .isReversed(false)
                .build();

        financialTransactionRepository.save(tx);

        return newSurplus;
    }

    @Transactional
    public BigDecimal updateSurplusAmount(BigDecimal amount) {
        SystemSetting setting = systemSettingRepository.findById(SURPLUS_AMOUNT_KEY)
                .orElse(SystemSetting.builder()
                        .settingKey(SURPLUS_AMOUNT_KEY)
                        .description("Group Surplus Reserve Fund (മിച്ച തുക)")
                        .build());

        setting.setSettingValue(amount != null ? amount.setScale(2).toString() : "0.00");
        setting = systemSettingRepository.save(setting);
        return new BigDecimal(setting.getSettingValue());
    }
}
