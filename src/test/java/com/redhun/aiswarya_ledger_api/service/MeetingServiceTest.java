package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus;
import com.redhun.aiswarya_ledger_api.dto.response.MeetingDto;
import com.redhun.aiswarya_ledger_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private MeetingMemberRepository meetingMemberRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SystemSettingService systemSettingService;
    @Mock
    private FinancialTransactionRepository financialTransactionRepository;
    @Mock
    private GroupExpenseRepository groupExpenseRepository;
    @Mock
    private GroupProfitRepository groupProfitRepository;
    @Mock
    private GroupProfitService groupProfitService;
    @Mock
    private InterestCalculationRepository interestCalculationRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MeetingService meetingService;

    private Meeting meeting;

    @BeforeEach
    public void setUp() {
        meeting = Meeting.builder()
                .id(1L)
                .meetingNumber(1)
                .meetingDate(LocalDate.of(2026, 10, 4))
                .interestPeriod("2026-10")
                .status(MeetingStatus.OPEN)
                .build();
    }

    @Test
    public void testCompleteMeeting_TriggersPushNotification() {
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.countByMeetingIdAndProcessingStatus(1L, MemberProcessingStatus.PENDING)).thenReturn(0L);
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(systemSettingService.getSurplusAmount()).thenReturn(BigDecimal.ZERO);
        when(meetingRepository.existsByMeetingDate(any(LocalDate.class))).thenReturn(true);
        when(meetingMemberRepository.countByMeetingId(1L)).thenReturn(5L);
        when(meetingMemberRepository.countByMeetingIdAndProcessingStatus(1L, MemberProcessingStatus.COMPLETED)).thenReturn(5L);
        when(meetingRepository.findMeetingsInPeriodSorted("2026-10")).thenReturn(List.of(meeting));

        MeetingDto result = meetingService.completeMeeting(1L);

        assertNotNull(result);
        assertEquals(MeetingStatus.COMPLETED, result.getStatus());
        verify(notificationService, times(1)).sendMeetingCompletedNotification(any(Meeting.class));
    }
}
