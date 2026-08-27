package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.dto.request.BulkImportItemDto;
import com.redhun.aiswarya_ledger_api.dto.request.BulkImportRequest;
import com.redhun.aiswarya_ledger_api.dto.request.CreateMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.response.BulkImportResponseDto;
import com.redhun.aiswarya_ledger_api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final LedgerService ledgerService;
    private final MeetingRepository meetingRepository;
    private final GroupExpenseRepository groupExpenseRepository;
    private final GroupProfitRepository groupProfitRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final UserRepository userRepository;
    private final InterestCalculationRepository interestCalculationRepository;
    private final SystemSettingService systemSettingService;
    private final MeetingMemberRepository meetingMemberRepository;

    @Transactional
    public BulkImportResponseDto importBulkTransactions(BulkImportRequest request, User operator) {
        List<BulkImportItemDto> items = new ArrayList<>(request.getItems());
        // Sort items by meeting number & transactionDate ascending
        items.sort(Comparator.comparing(BulkImportItemDto::getMeetingNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BulkImportItemDto::getTransactionDate, Comparator.nullsLast(Comparator.naturalOrder())));

        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();

        Map<Integer, Meeting> createdMeetingMap = new HashMap<>();

        for (int i = 0; i < items.size(); i++) {
                        BulkImportItemDto item = items.get(i);
            try {
                // 1. Resolve or Auto-Create Historical Meeting if meetingNumber is provided
                Meeting meeting = null;
                if (item.getMeetingNumber() != null && item.getMeetingNumber() > 0) {
                    final Integer mNo = item.getMeetingNumber();
                    meeting = createdMeetingMap.computeIfAbsent(mNo, key ->
                            meetingRepository.findByMeetingNumber(key)
                                    .orElseGet(() -> {
                                        LocalDate mDate = item.getTransactionDate() != null ? item.getTransactionDate() : LocalDate.now();
                                        String mPeriod = item.getInterestPeriod() != null && !item.getInterestPeriod().isBlank()
                                                ? item.getInterestPeriod()
                                                : String.format("%04d-%02d", mDate.getYear(), mDate.getMonthValue());

                                        User safeOperator = (operator != null && operator.getId() != null)
                                                ? userRepository.findById(operator.getId()).orElse(null)
                                                : userRepository.findAll().stream().findFirst().orElse(null);
                                        Meeting newM = Meeting.builder()
                                                .meetingNumber(key)
                                                .meetingDate(mDate)
                                                .interestPeriod(mPeriod)
                                                .status(MeetingStatus.COMPLETED)
                                                .isFirstMeetingOfMonth(true)
                                                .createdBy(safeOperator)
                                                .createdAt(ZonedDateTime.now())
                                                .updatedAt(ZonedDateTime.now())
                                                .build();
                                        return meetingRepository.save(newM);
                                    })
                    );
                }

                Long meetingId = meeting != null ? meeting.getId() : null;
                String categoryType = item.getCategoryType() != null ? item.getCategoryType().toUpperCase() : "";

                // 1. Handle Group Surplus Fund imported via CSV
                if ("SURPLUS_FUND".equals(categoryType) || item.getAccountType() == AccountType.SURPLUS_FUND || item.getTransactionType() == TransactionType.SURPLUS_ADDITION) {
                    String desc = (item.getDescription() != null && !item.getDescription().isBlank())
                            ? item.getDescription().trim()
                            : "Initial Group Surplus Reserve (Michathuka)";

                    systemSettingService.addSurplusAmount(
                            item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO,
                            desc,
                            operator
                    );
                    successCount++;
                    continue;
                }

                // 2. Handle Group Expenses imported via CSV
                if ("EXPENSE".equals(categoryType) || "GROUP_EXPENSE".equals(categoryType)) {
                    String expTypeName = (item.getDescription() != null && !item.getDescription().isBlank())
                            ? item.getDescription().trim()
                            : "General Expense";

                    ExpenseType expType = expenseTypeRepository.findByNameIgnoreCase(expTypeName)
                            .orElseGet(() -> expenseTypeRepository.save(ExpenseType.builder().name(expTypeName).isActive(true).build()));

                    GroupExpense exp = GroupExpense.builder()
                            .expenseType(expType)
                            .amount(item.getAmount())
                            .expenseDate(item.getTransactionDate() != null ? item.getTransactionDate() : LocalDate.now())
                            .description(expTypeName)
                            .meeting(meeting)
                            .createdBy(operator)
                            .build();
                    groupExpenseRepository.save(exp);
                    successCount++;
                    continue;
                }

                // 3. Handle Group Profits imported via CSV
                if ("PROFIT".equals(categoryType) || "GROUP_PROFIT".equals(categoryType)) {
                    String title = (item.getDescription() != null && !item.getDescription().isBlank())
                            ? item.getDescription().trim()
                            : "Group Profit";

                    GroupProfit profit = GroupProfit.builder()
                            .title(title)
                            .amount(item.getAmount())
                            .profitDate(item.getTransactionDate() != null ? item.getTransactionDate() : LocalDate.now())
                            .meeting(meeting)
                            .createdBy(operator)
                            .build();
                    groupProfitRepository.save(profit);
                    successCount++;
                    continue;
                }

                // 4. Handle Member Transactions (Deposits, Loan Repayments, Contributions, Fines, Aid, Loans)
                if (item.getMemberNumber() != null && !item.getMemberNumber().isBlank()) {
                    Member member = memberRepository.findByMemberNumber(item.getMemberNumber().trim())
                            .orElseGet(() -> {
                                CreateMemberRequest createReq = new CreateMemberRequest();
                                createReq.setMemberNumber(item.getMemberNumber().trim());
                                createReq.setFullName(item.getFullName() != null && !item.getFullName().isBlank()
                                        ? item.getFullName().trim()
                                        : "Member " + item.getMemberNumber().trim());
                                createReq.setPhone(item.getPhone());
                                createReq.setJoiningDate(item.getJoiningDate() != null ? item.getJoiningDate() : item.getTransactionDate());
                                createReq.setUsername(item.getMemberNumber().trim().toLowerCase());
                                createReq.setPassword("123456");
                                var createdDto = memberService.createMember(createReq);
                                return memberRepository.findById(createdDto.getId()).orElseThrow();
                            });

                    String desc = (item.getDescription() != null && !item.getDescription().isBlank())
                            ? item.getDescription()
                            : (meeting != null ? "Meeting #" + meeting.getMeetingNumber() + " Import" : "Historical ledger import");

                    ledgerService.recordTransaction(
                            member.getId(),
                            item.getAccountType(),
                            item.getTransactionType(),
                            item.getAmount(),
                            meetingId,
                            meetingId != null ? "MEETING_COLLECTION" : "HISTORICAL_IMPORT",
                            null,
                            desc,
                            null,
                            item.getTransactionDate() != null ? item.getTransactionDate() : LocalDate.now(),
                            operator
                    );

                    if (meeting != null && member != null) {
                        if (meetingMemberRepository.findByMeetingIdAndMemberId(meeting.getId(), member.getId()).isEmpty()) {
                            MeetingMember mm = MeetingMember.builder()
                                    .meeting(meeting)
                                    .member(member)
                                    .processingStatus(com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus.COMPLETED)
                                    .processedAt(ZonedDateTime.now())
                                    .processedBy(operator)
                                    .build();
                            meetingMemberRepository.save(mm);
                        }
                    }

                    if (item.getTransactionType() == TransactionType.INTEREST_APPLIED) {
                                                LocalDate txDate = item.getTransactionDate() != null ? item.getTransactionDate() : LocalDate.now();
                        String period = item.getInterestPeriod() != null && !item.getInterestPeriod().isBlank()
                                ? item.getInterestPeriod()
                                : String.format("%04d-%02d", txDate.getYear(), txDate.getMonthValue());

                        if (!interestCalculationRepository.existsByMemberIdAndInterestPeriod(member.getId(), period)) {
                            User safeOp = (operator != null && operator.getId() != null) ? userRepository.findById(operator.getId()).orElse(null) : userRepository.findAll().stream().findFirst().orElse(null);
                            if (safeOp == null || safeOp.getId() == null) {
                                safeOp = userRepository.findByUsername("admin")
                                        .orElseGet(() -> userRepository.save(User.builder()
                                                .username("admin_sys_" + System.currentTimeMillis())
                                                .passwordHash("$2a$10$e8w.b.e6.4523")
                                                .role(com.redhun.aiswarya_ledger_api.domain.enums.UserRole.ADMIN)
                                                .isActive(true)
                                                .build()));
                            }
                            Meeting safeMeeting = meeting != null ? meeting : meetingRepository.findAll().stream().findFirst().orElseGet(() ->
                                    meetingRepository.save(Meeting.builder()
                                            .meetingNumber(9999)
                                            .meetingDate(txDate)
                                            .interestPeriod(period)
                                            .status(MeetingStatus.COMPLETED)
                                            .isFirstMeetingOfMonth(true)
                                            .build())
                            );

                            try {
                                InterestCalculation calc = InterestCalculation.builder()
                                        .member(member)
                                        .interestPeriod(period)
                                        .meeting(safeMeeting)
                                        .loanBalanceUsed(BigDecimal.ZERO)
                                        .interestRate(new BigDecimal("0.0100"))
                                        .interestAmount(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO)
                                        .status(com.redhun.aiswarya_ledger_api.domain.enums.InterestStatus.CALCULATED)
                                        .calculatedAt(txDate.atStartOfDay(ZoneId.systemDefault()))
                                        .calculatedBy(safeOp)
                                        .build();
                                interestCalculationRepository.save(calc);
                                                            } catch (Exception ex) {
                                System.err.println("FAILED TO SAVE INTEREST CALCULATION: " + ex.getMessage());
                                ex.printStackTrace();
                            }
                        }
                    }

                    successCount++;
                }
            } catch (Exception e) {
                errorCount++;
                String err = String.format("Row #%d (Member %s, Meeting %s, Date %s): %s",
                        (i + 1), item.getMemberNumber(), item.getMeetingNumber(), item.getTransactionDate(), e.getMessage());
                log.error("Error importing bulk item: {}", err, e);
                errors.add(err);
            }
        }

        // Ensure meeting_members snapshot is populated for all imported meetings
        Set<Long> processedMeetingIds = new HashSet<>();
        for (BulkImportItemDto item : items) {
            if (item.getMeetingNumber() != null) {
                meetingRepository.findByMeetingNumber(item.getMeetingNumber()).ifPresent(m -> {
                    if (processedMeetingIds.add(m.getId())) {
                        List<Member> allMembers = memberRepository.findAll();
                        for (Member activeM : allMembers) {
                            if (meetingMemberRepository.findByMeetingIdAndMemberId(m.getId(), activeM.getId()).isEmpty()) {
                                MeetingMember mm = MeetingMember.builder()
                                        .meeting(m)
                                        .member(activeM)
                                        .processingStatus(com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus.COMPLETED)
                                        .processedAt(ZonedDateTime.now())
                                        .processedBy(operator)
                                        .build();
                                meetingMemberRepository.save(mm);
                            }
                        }
                    }
                });
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            Map<String, Integer> headerMap = new HashMap<>();
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",", -1);

                // Detect Header Line
                if (isFirstLine) {
                    isFirstLine = false;
                    boolean isHeaderRow = false;
                    for (int c = 0; c < parts.length; c++) {
                        String col = parts[c].trim().toLowerCase().replace("_", "").replace(" ", "");
                        if (col.contains("member") || col.contains("account") || col.contains("amount") || col.contains("meeting") || col.contains("type") || col.contains("name") || col.contains("date") || col.contains("period") || col.contains("desc")) {
                            isHeaderRow = true;
                        }
                        headerMap.put(col, c);
                    }
                    if (isHeaderRow) {
                        continue; // Skip header row
                    }
                }

                String memberNumber = getColValue(parts, headerMap, 0, "membernumber", "member", "memberno", "id");
                String fullName = getColValue(parts, headerMap, 1, "fullname", "name", "full_name");
                String phone = getColValue(parts, headerMap, 2, "phone", "mobile", "contact");
                String accountTypeStr = getColValue(parts, headerMap, 3, "accounttype", "account", "category", "account_type");
                String transactionTypeStr = getColValue(parts, headerMap, 4, "transactiontype", "type", "transaction_type");
                String amountStr = getColValue(parts, headerMap, 5, "amount", "sum", "val", "value");
                String dateStr = getColValue(parts, headerMap, 6, "transactiondate", "date", "transaction_date");
                String meetingStr = getColValue(parts, headerMap, 7, "meetingnumber", "meeting", "meetingno", "meeting_number");
                String periodStr = getColValue(parts, headerMap, 8, "interestperiod", "period", "interest_period");
                String desc = getColValue(parts, headerMap, 9, "description", "notes", "desc");

                if (accountTypeStr == null || accountTypeStr.isBlank()) {
                    accountTypeStr = "LOAN";
                }
                accountTypeStr = accountTypeStr.trim().toUpperCase();

                if (transactionTypeStr == null || transactionTypeStr.isBlank()) {
                    transactionTypeStr = "LOAN_ISSUED";
                }
                transactionTypeStr = transactionTypeStr.trim().toUpperCase();

                String categoryType = "MEMBER_TX";
                AccountType accountType = null;
                TransactionType transactionType = null;

                if (accountTypeStr.contains("SURPLUS") || transactionTypeStr.contains("SURPLUS")) {
                    categoryType = "SURPLUS_FUND";
                    accountType = AccountType.SURPLUS_FUND;
                    transactionType = TransactionType.SURPLUS_ADDITION;
                } else if (accountTypeStr.contains("EXPENSE") || accountTypeStr.contains("CHELAVU")) {
                    categoryType = "EXPENSE";
                } else if (accountTypeStr.contains("PROFIT") || accountTypeStr.contains("MICHATHUKA")) {
                    categoryType = "PROFIT";
                } else {
                    if (accountTypeStr.equals("INTEREST") || accountTypeStr.equals("LOAN_INTEREST")) {
                        accountTypeStr = "LOAN";
                    }
                    try {
                        accountType = AccountType.valueOf(accountTypeStr);
                    } catch (Exception e) {
                        accountType = AccountType.LOAN;
                    }

                    if (transactionTypeStr.equals("INTEREST") || transactionTypeStr.equals("INTEREST_CALCULATED") || transactionTypeStr.equals("INTEREST_ADDED")) {
                        transactionTypeStr = "INTEREST_APPLIED";
                    }
                    try {
                        transactionType = TransactionType.valueOf(transactionTypeStr);
                    } catch (Exception e) {
                        transactionType = TransactionType.ADDITION;
                    }
                }

                BigDecimal amount = BigDecimal.ZERO;
                if (amountStr != null && !amountStr.isBlank()) {
                    try {
                        amount = new BigDecimal(amountStr.trim());
                    } catch (Exception ignored) {}
                }

                Integer meetingNumber = null;
                if (meetingStr != null && !meetingStr.isBlank()) {
                    try {
                        meetingNumber = Integer.parseInt(meetingStr.trim().replaceAll("[^0-9]", ""));
                    } catch (Exception ignored) {}
                }

                BulkImportItemDto item = BulkImportItemDto.builder()
                        .memberNumber(memberNumber != null ? memberNumber.trim() : "")
                        .fullName(fullName != null ? fullName.trim() : null)
                        .phone(phone != null ? phone.trim() : null)
                        .accountType(accountType)
                        .transactionType(transactionType)
                        .amount(amount)
                        .transactionDate(parseFlexibleDate(dateStr))
                        .meetingNumber(meetingNumber)
                        .interestPeriod(periodStr != null ? periodStr.trim() : null)
                        .categoryType(categoryType)
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

    private String getColValue(String[] parts, Map<String, Integer> headerMap, int fallbackIdx, String... keys) {
        if (headerMap != null && !headerMap.isEmpty()) {
            for (String k : keys) {
                if (k != null && headerMap.containsKey(k)) {
                    int idx = headerMap.get(k);
                    if (idx < parts.length && parts[idx] != null && !parts[idx].isBlank()) {
                        return parts[idx];
                    }
                }
            }
            return null;
        }
        if (fallbackIdx >= 0 && fallbackIdx < parts.length && parts[fallbackIdx] != null && !parts[fallbackIdx].isBlank()) {
            return parts[fallbackIdx];
        }
        return null;
    }

    private LocalDate parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        dateStr = dateStr.trim();

        try {
            return LocalDate.parse(dateStr);
        } catch (Exception ignored) {}

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
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {}
        }

        log.warn("Could not parse date '{}', defaulting to today's date.", dateStr);
        return LocalDate.now();
    }
}
