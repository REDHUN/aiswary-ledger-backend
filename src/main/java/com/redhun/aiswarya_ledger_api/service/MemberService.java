package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import com.redhun.aiswarya_ledger_api.domain.entity.MemberAccount;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.UserRole;
import com.redhun.aiswarya_ledger_api.dto.request.CreateMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.request.UpdateMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.response.MemberAccountDto;
import com.redhun.aiswarya_ledger_api.dto.response.MemberDto;
import com.redhun.aiswarya_ledger_api.exception.DuplicateResourceException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.MemberAccountRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberAccountRepository memberAccountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberDto createMember(CreateMemberRequest request) {
        if (memberRepository.existsByMemberNumber(request.getMemberNumber())) {
            throw new DuplicateResourceException("Member", "memberNumber", request.getMemberNumber());
        }

        if (request.getPhone() != null && memberRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Member", "phone", request.getPhone());
        }

        User user = null;
        if (request.getUsername() != null && request.getPassword() != null) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new DuplicateResourceException("User", "username", request.getUsername());
            }
            user = User.builder()
                    .username(request.getUsername())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .role(UserRole.MEMBER)
                    .isActive(true)
                    .build();
            user = userRepository.save(user);
        }

        Member member = Member.builder()
                .memberNumber(request.getMemberNumber())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .joiningDate(request.getJoiningDate())
                .isActive(true)
                .user(user)
                .build();

        member = memberRepository.save(member);

        // Initialize 6 account types for the new member
        for (AccountType accountType : AccountType.values()) {
            MemberAccount account = MemberAccount.builder()
                    .member(member)
                    .accountType(accountType)
                    .currentBalance(BigDecimal.ZERO)
                    .version(0L)
                    .build();
            memberAccountRepository.save(account);
        }

        return mapToMemberDto(member);
    }

    @Transactional(readOnly = true)
    public MemberDto getMemberById(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
        return mapToMemberDto(member);
    }

    @Transactional(readOnly = true)
    public Page<MemberDto> getAllMembers(Pageable pageable) {
        return memberRepository.findAll(pageable).map(this::mapToMemberDto);
    }

    @Transactional
    public MemberDto updateMember(Long id, UpdateMemberRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));

        if (request.getFullName() != null) member.setFullName(request.getFullName());
        if (request.getPhone() != null) member.setPhone(request.getPhone());
        if (request.getAddress() != null) member.setAddress(request.getAddress());
        if (request.getIsActive() != null) member.setIsActive(request.getIsActive());

        member = memberRepository.save(member);
        return mapToMemberDto(member);
    }

    @Transactional(readOnly = true)
    public List<MemberAccountDto> getMemberAccounts(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("Member", "id", memberId);
        }
        return memberAccountRepository.findByMemberId(memberId).stream()
                .map(this::mapToAccountDto)
                .collect(Collectors.toList());
    }

    public MemberDto mapToMemberDto(Member member) {
        List<MemberAccountDto> accounts = memberAccountRepository.findByMemberId(member.getId()).stream()
                .map(this::mapToAccountDto)
                .collect(Collectors.toList());

        return MemberDto.builder()
                .id(member.getId())
                .userId(member.getUser() != null ? member.getUser().getId() : null)
                .memberNumber(member.getMemberNumber())
                .fullName(member.getFullName())
                .phone(member.getPhone())
                .address(member.getAddress())
                .isActive(member.getIsActive())
                .joiningDate(member.getJoiningDate())
                .accounts(accounts)
                .build();
    }

    public MemberAccountDto mapToAccountDto(MemberAccount account) {
        return MemberAccountDto.builder()
                .id(account.getId())
                .memberId(account.getMember().getId())
                .accountType(account.getAccountType())
                .currentBalance(account.getCurrentBalance())
                .version(account.getVersion())
                .build();
    }
}
