package com.redhun.aiswarya_ledger_api.dto.request;

import lombok.Data;

@Data
public class UpdateMemberRequest {
    private String fullName;
    private String phone;
    private String address;
    private Boolean isActive;
}
