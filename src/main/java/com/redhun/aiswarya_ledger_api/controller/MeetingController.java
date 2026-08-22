package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.dto.request.RescheduleMeetingRequest;
import com.redhun.aiswarya_ledger_api.dto.request.ScheduleMeetingRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.MeetingDto;
import com.redhun.aiswarya_ledger_api.dto.response.MeetingMemberDto;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MeetingDto>> scheduleMeeting(
            @Valid @RequestBody ScheduleMeetingRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        User operator = userRepository.getReferenceById(userPrincipal.getId());
        MeetingDto meeting = meetingService.scheduleMeeting(request, operator);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(meeting, "Meeting scheduled successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<MeetingDto>>> getAllMeetings() {
        List<MeetingDto> meetings = meetingService.getAllMeetings();
        return ResponseEntity.ok(ApiResponse.ok(meetings));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<MeetingDto>> getMeetingById(@PathVariable Long id) {
        MeetingDto meeting = meetingService.getMeetingById(id);
        return ResponseEntity.ok(ApiResponse.ok(meeting));
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MeetingDto>> openMeeting(@PathVariable Long id) {
        MeetingDto meeting = meetingService.openMeeting(id);
        return ResponseEntity.ok(ApiResponse.ok(meeting, "Meeting opened successfully"));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MeetingDto>> completeMeeting(@PathVariable Long id) {
        MeetingDto meeting = meetingService.completeMeeting(id);
        return ResponseEntity.ok(ApiResponse.ok(meeting, "Meeting completed successfully. Next Sunday meeting automatically scheduled."));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MeetingDto>> rescheduleMeeting(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleMeetingRequest request
    ) {
        MeetingDto meeting = meetingService.rescheduleMeeting(id, request);
        return ResponseEntity.ok(ApiResponse.ok(meeting, "Meeting rescheduled successfully"));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<MeetingMemberDto>>> getMeetingMembers(@PathVariable Long id) {
        List<MeetingMemberDto> members = meetingService.getMeetingMembers(id);
        return ResponseEntity.ok(ApiResponse.ok(members));
    }
}
