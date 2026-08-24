package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.dto.request.BulkImportItemDto;
import com.redhun.aiswarya_ledger_api.dto.request.BulkImportRequest;
import com.redhun.aiswarya_ledger_api.dto.request.CreateMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.response.BulkImportResponseDto;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final LedgerService ledgerService;
    private final com.redhun.aiswarya_ledger_api.repository.InterestCalculationRepository interestRepository;

    @Transactional
    public BulkImportResponseDto importBulkTransactions(BulkImportRequest request, User operator) {
        List<BulkImportItemDto> items = new ArrayList<>(request.getItems());
        // Sort items by transactionDate ascending so historical sequence is maintained
        items.sort(Comparator.comparing(BulkImportItemDto::getTransactionDate, Comparator.nullsLast(Comparator.naturalOrder())));

        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            BulkImportItemDto item = items.get(i);
            try {
                // Find member by memberNumber or auto-create if missing
                Member member = memberRepository.findByMemberNumber(item.getMemberNumber())
                        .orElseGet(() -> {
                            CreateMemberRequest createReq = new CreateMemberRequest();
                            createReq.setMemberNumber(item.getMemberNumber());
                            createReq.setFullName(item.getFullName() != null && !item.getFullName().isBlank() 
                                    ? item.getFullName() 
                                    : "Member " + item.getMemberNumber());
                            createReq.setPhone(item.getPhone());
                            createReq.setJoiningDate(item.getJoiningDate() != null ? item.getJoiningDate() : item.getTransactionDate());
                            createReq.setUsername(item.getMemberNumber().toLowerCase());
                            createReq.setPassword("123456");
                            var createdDto = memberService.createMember(createReq);
                            return memberRepository.findById(createdDto.getId()).orElseThrow();
                        });

                String desc = (item.getDescription() != null && !item.getDescription().isBlank())
                        ? item.getDescription()
                        : "Historical ledger import (" + item.getTransactionType() + ")";

                ledgerService.recordTransaction(
                        member.getId(),
                        item.getAccountType(),
                        item.getTransactionType(),
                        item.getAmount(),
                        null,
                        "HISTORICAL_IMPORT",
                        null,
                        desc,
                        null,
                        item.getTransactionDate() != null ? item.getTransactionDate() : LocalDate.now(),
                        operator
                );

                successCount++;
            } catch (Exception e) {
                errorCount++;
                String err = String.format("Row #%d (Member %s, Date %s): %s",
                        (i + 1), item.getMemberNumber(), item.getTransactionDate(), e.getMessage());
                log.error("Error importing bulk item: {}", err, e);
                errors.add(err);
            }
        }

        return BulkImportResponseDto.builder()
                .totalProcessed(items.size())
                .successCount(successCount)
                .errorCount(errorCount)
                .errors(errors)
                .build();
    }

    @Transactional
    public BulkImportResponseDto importCsvFile(org.springframework.web.multipart.MultipartFile file, User operator) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is missing or empty");
        }
        List<BulkImportItemDto> items = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (isFirstLine && line.toLowerCase().contains("membernumber")) {
                    isFirstLine = false;
                    continue;
                }
                isFirstLine = false;
                String[] parts = line.split(",", -1);
                if (parts.length < 5) continue;

                String memberNumber = parts[0].trim();
                String fullName = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1].trim() : null;
                String accountTypeStr = parts.length > 2 && !parts[2].trim().isEmpty() ? parts[2].trim().toUpperCase() : "LOAN";
                if (accountTypeStr.equals("INTEREST") || accountTypeStr.equals("LOAN_INTEREST")) {
                    accountTypeStr = "LOAN";
                }

                String transactionTypeStr = parts.length > 3 && !parts[3].trim().isEmpty() ? parts[3].trim().toUpperCase() : "LOAN_ISSUED";
                if (transactionTypeStr.equals("INTEREST") || transactionTypeStr.equals("INTEREST_CALCULATED") || transactionTypeStr.equals("INTEREST_ADDED")) {
                    transactionTypeStr = "INTEREST_APPLIED";
                }
                java.math.BigDecimal amount = parts.length > 4 && !parts[4].trim().isEmpty()
                        ? new java.math.BigDecimal(parts[4].trim())
                        : java.math.BigDecimal.ZERO;
                String dateStr = parts.length > 5 && !parts[5].trim().isEmpty()
                        ? parts[5].trim()
                        : LocalDate.now().toString();
                String desc = parts.length > 6 && !parts[6].trim().isEmpty() ? parts[6].trim() : "CSV Ledger Import";

                BulkImportItemDto item = BulkImportItemDto.builder()
                        .memberNumber(memberNumber)
                        .fullName(fullName)
                        .accountType(com.redhun.aiswarya_ledger_api.domain.enums.AccountType.valueOf(accountTypeStr))
                        .transactionType(com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.valueOf(transactionTypeStr))
                        .amount(amount)
                        .transactionDate(parseFlexibleDate(dateStr))
                        .description(desc)
                        .build();

                items.add(item);
            }
        } catch (Exception e) {
            log.error("Failed to parse CSV file", e);
            throw new RuntimeException("CSV Parsing error: " + e.getMessage());
        }

        BulkImportRequest req = new BulkImportRequest();
        req.setItems(items);
        return importBulkTransactions(req, operator);
    }

    private LocalDate parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        dateStr = dateStr.trim();

        // 1. Try standard ISO-8601 YYYY-MM-DD
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception ignored) {}

        // 2. Try common flexible date patterns
        String[] patterns = new String[] {
                "dd-MM-yyyy",
                "dd/MM/yyyy",
                "MM-dd-yyyy",
                "MM/dd/yyyy",
                "yyyy/MM/dd",
                "d-M-yyyy",
                "d/M/yyyy",
                "dd-MM-yy",
                "dd/MM/yy"
        };

        for (String pattern : patterns) {
            try {
                return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {}
        }

        log.warn("Could not parse date '{}', defaulting to today's date.", dateStr);
        return LocalDate.now();
    }
}
