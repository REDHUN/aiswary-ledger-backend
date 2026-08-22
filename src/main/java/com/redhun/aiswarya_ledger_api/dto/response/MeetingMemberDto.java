package com.redhun.aiswarya_ledger_api.dto.response;

import com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingMemberDto {
    private Long id;
    private Long meetingId;
    private Long memberId;
    private String memberNumber;
    private String fullName;
    private MemberProcessingStatus processingStatus;
    private ZonedDateTime processedAt;
}
