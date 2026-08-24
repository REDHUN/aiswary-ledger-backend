package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkImportRequest {

    @NotEmpty(message = "Import list cannot be empty")
    @Valid
    private List<BulkImportItemDto> items;
}
