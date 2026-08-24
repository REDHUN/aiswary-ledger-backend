package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.request.CreateExpenseTypeRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.ExpenseTypeDto;
import com.redhun.aiswarya_ledger_api.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/expense-types")
@RequiredArgsConstructor
public class ExpenseTypeController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseTypeDto>>> getAllExpenseTypes() {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getAllExpenseTypes()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseTypeDto>> createExpenseType(@Valid @RequestBody CreateExpenseTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.createExpenseType(request), "Expense type created successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteExpenseType(@PathVariable Long id) {
        expenseService.deleteExpenseType(id);
        return ResponseEntity.ok(ApiResponse.ok("Success", "Expense type deleted successfully"));
    }
}
