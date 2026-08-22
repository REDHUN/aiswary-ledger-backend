package com.redhun.aiswarya_ledger_api.exception;

import org.springframework.http.HttpStatus;

public class InterestCalculationRequiredException extends BusinessException {

    public InterestCalculationRequiredException(String interestPeriod) {
        super(
            "INTEREST_CALCULATION_REQUIRED",
            String.format("Monthly interest for period '%s' must be calculated before processing this member.", interestPeriod),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
