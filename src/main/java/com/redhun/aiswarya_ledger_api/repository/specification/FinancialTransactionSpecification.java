package com.redhun.aiswarya_ledger_api.repository.specification;

import com.redhun.aiswarya_ledger_api.domain.entity.FinancialTransaction;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class FinancialTransactionSpecification {

    public static Specification<FinancialTransaction> getFilterSpecification(
            String query,
            AccountType accountType,
            TransactionType transactionType,
            Boolean isReversed,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search query across member name, member number, description, and createdBy
            if (query != null && !query.trim().isEmpty()) {
                String searchPattern = "%" + query.trim().toLowerCase() + "%";
                var memberJoin = root.join("member", jakarta.persistence.criteria.JoinType.LEFT);
                var createdByJoin = root.join("createdBy", jakarta.persistence.criteria.JoinType.LEFT);

                Predicate nameMatch = cb.like(cb.lower(memberJoin.get("fullName")), searchPattern);
                Predicate numberMatch = cb.like(cb.lower(memberJoin.get("memberNumber")), searchPattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), searchPattern);
                Predicate createdByMatch = cb.like(cb.lower(createdByJoin.get("username")), searchPattern);

                predicates.add(cb.or(nameMatch, numberMatch, descMatch, createdByMatch));
            }

            // Account Type Filter
            if (accountType != null) {
                predicates.add(cb.equal(root.get("accountType"), accountType));
            }

            // Transaction Type Filter
            if (transactionType != null) {
                predicates.add(cb.equal(root.get("transactionType"), transactionType));
            }

            // Reversal Filter
            if (isReversed != null) {
                predicates.add(cb.equal(root.get("isReversed"), isReversed));
            }

            // Date Range Filter
            if (startDate != null) {
                ZonedDateTime startDateTime = startDate.atStartOfDay(ZoneId.systemDefault());
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDateTime));
            }

            if (endDate != null) {
                ZonedDateTime endDateTime = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault());
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDateTime));
            }

            // Order by createdAt descending, then id descending
            criteriaQuery.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
