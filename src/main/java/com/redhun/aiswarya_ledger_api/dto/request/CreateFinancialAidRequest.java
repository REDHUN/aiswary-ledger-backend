package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class CreateFinancialAidRequest {

    @NotNull(message = "Financial aid amount is required")
    @DecimalMin(value = "0.01", message = "Financial aid amount must be greater than zero")
    private BigDecimal amount;

    private Long meetingId;
    private String description;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;
}
