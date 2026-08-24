package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CreateMemberRequest {

    @NotBlank(message = "Member number is required")
    private String memberNumber;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String username;
    private String password;

    private String phone;
    private String address;

    private LocalDate joiningDate;
}
