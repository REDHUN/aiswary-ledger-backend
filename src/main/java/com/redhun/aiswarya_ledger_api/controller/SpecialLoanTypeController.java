package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.request.CreateSpecialLoanTypeRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.SpecialLoanTypeDto;
import com.redhun.aiswarya_ledger_api.service.SpecialLoanTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/settings/special-loan-types", "/api/v1/special-loan-types"})
@RequiredArgsConstructor
public class SpecialLoanTypeController {

    private final SpecialLoanTypeService specialLoanTypeService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<SpecialLoanTypeDto>>> getSpecialLoanTypes(@RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<SpecialLoanTypeDto> types = activeOnly ? specialLoanTypeService.getActiveSpecialLoanTypes() : specialLoanTypeService.getAllSpecialLoanTypes();
        return ResponseEntity.ok(ApiResponse.ok(types));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SpecialLoanTypeDto>> createSpecialLoanType(@Valid @RequestBody CreateSpecialLoanTypeRequest request) {
        SpecialLoanTypeDto created = specialLoanTypeService.createSpecialLoanType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created, "Special loan type created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SpecialLoanTypeDto>> updateSpecialLoanType(@PathVariable Long id, @Valid @RequestBody CreateSpecialLoanTypeRequest request) {
        SpecialLoanTypeDto updated = specialLoanTypeService.updateSpecialLoanType(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Special loan type updated successfully"));
    }
}
