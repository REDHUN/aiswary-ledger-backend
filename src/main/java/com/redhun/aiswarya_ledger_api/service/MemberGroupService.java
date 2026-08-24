package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import com.redhun.aiswarya_ledger_api.domain.entity.MemberGroup;
import com.redhun.aiswarya_ledger_api.dto.request.CreateMemberGroupRequest;
import com.redhun.aiswarya_ledger_api.dto.response.MemberGroupDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.MemberGroupRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberGroupService {

    private final MemberGroupRepository memberGroupRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;

    @Transactional(readOnly = true)
    public List<MemberGroupDto> getAllGroups() {
        return memberGroupRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MemberGroupDto getGroupById(Long id) {
        MemberGroup group = memberGroupRepository.findByIdWithMembers(id);
        if (group == null || !group.getIsActive()) {
            throw new ResourceNotFoundException("MemberGroup", "id", id);
        }
        return mapToDto(group);
    }

    @Transactional
    public MemberGroupDto createGroup(CreateMemberGroupRequest request) {
        if (memberGroupRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new BusinessException("GROUP_NAME_EXISTS", "Group with name '" + request.getName() + "' already exists");
        }

        Set<Member> members = new HashSet<>();
        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            members = new HashSet<>(memberRepository.findAllById(request.getMemberIds()));
        }

        MemberGroup group = MemberGroup.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .members(members)
                .build();

        group = memberGroupRepository.save(group);
        return mapToDto(group);
    }

    @Transactional
    public MemberGroupDto updateGroup(Long id, CreateMemberGroupRequest request) {
        MemberGroup group = memberGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MemberGroup", "id", id));

        if (memberGroupRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new BusinessException("GROUP_NAME_EXISTS", "Group with name '" + request.getName() + "' already exists");
        }

        group.setName(request.getName().trim());
        if (request.getDescription() != null) group.setDescription(request.getDescription());
        if (request.getIsActive() != null) group.setIsActive(request.getIsActive());

        if (request.getMemberIds() != null) {
            Set<Member> members = new HashSet<>(memberRepository.findAllById(request.getMemberIds()));
            group.setMembers(members);
        }

        group = memberGroupRepository.save(group);
        return mapToDto(group);
    }

    @Transactional
    public void deleteGroup(Long id) {
        MemberGroup group = memberGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MemberGroup", "id", id));
        group.setIsActive(false);
        memberGroupRepository.save(group);
    }

    public MemberGroupDto mapToDto(MemberGroup group) {
        List<com.redhun.aiswarya_ledger_api.dto.response.MemberDto> memberDtos = group.getMembers().stream()
                .map(memberService::mapToMemberDto)
                .collect(Collectors.toList());

        return MemberGroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .isActive(group.getIsActive())
                .memberCount(group.getMembers() != null ? group.getMembers().size() : 0)
                .members(memberDtos)
                .createdAt(group.getCreatedAt())
                .build();
    }
}
