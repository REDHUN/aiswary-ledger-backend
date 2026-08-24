package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberGroupDto {
    private Long id;
    private String name;
    private String description;
    private Boolean isActive;
    private Integer memberCount;
    private List<MemberDto> members;
    private ZonedDateTime createdAt;
}
