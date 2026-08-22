package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDto {
    private Long id;
    private Long userId;
    private String memberNumber;
    private String fullName;
    private String phone;
    private String address;
    private Boolean isActive;
    private LocalDate joiningDate;
    private List<MemberAccountDto> accounts;
}
