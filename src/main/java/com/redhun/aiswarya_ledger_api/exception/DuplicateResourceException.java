package com.redhun.aiswarya_ledger_api.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(
            "DUPLICATE_RESOURCE",
            String.format("%s already exists with %s : '%s'", resourceName, fieldName, fieldValue),
            HttpStatus.CONFLICT
        );
    }
}
