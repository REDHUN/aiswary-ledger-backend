package com.redhun.aiswarya_ledger_api.dto.response;

import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDto {
    private Long id;
    private Integer meetingNumber;
    private LocalDate meetingDate;
    private MeetingStatus status;
    private String interestPeriod;
    private Boolean isFirstMeetingOfMonth;
    private String notes;
    private ZonedDateTime openedAt;
    private ZonedDateTime completedAt;
    private java.math.BigDecimal surplusAmount;
    private Long totalMembers;
    private Long processedMembers;
    private Long pendingMembers;
}
