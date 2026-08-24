package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateMemberGroupRequest {

    @NotBlank(message = "Group name is required")
    private String name;

    private String description;

    private Boolean isActive = true;

    private List<Long> memberIds;
}
