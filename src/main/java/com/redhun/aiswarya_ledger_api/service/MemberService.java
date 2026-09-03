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
import java.time.LocalDate;
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
        String reqUsername = request.getUsername();
        if ((reqUsername == null || reqUsername.trim().isEmpty()) && request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            reqUsername = request.getPhone().trim();
        }
        if (reqUsername != null && !reqUsername.trim().isEmpty() && request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            if (userRepository.existsByUsername(reqUsername.trim())) {
                throw new DuplicateResourceException("User", "username", reqUsername.trim());
            }
            user = User.builder()
                    .username(reqUsername.trim())
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
                .joiningDate(request.getJoiningDate() != null ? request.getJoiningDate() : LocalDate.now())
                .isActive(true)
                .user(user)
                .build();

        member = memberRepository.save(member);

        // Initialize 6 account types for the new member (excluding SPECIAL_LOAN without type)
        for (AccountType accountType : AccountType.values()) {
            if (accountType == AccountType.SPECIAL_LOAN) continue;
            MemberAccount account = MemberAccount.builder()
                    .member(member)
                    .accountType(accountType)
                    .currentBalance(BigDecimal.ZERO)
                    .orderNumber(MemberAccount.getDefaultOrderNumber(accountType))
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
        return getAllMembers(null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<MemberDto> getAllMembers(String query, Pageable pageable) {
        if (query != null && !query.trim().isEmpty()) {
            return memberRepository.searchMembers(query.trim(), pageable)
                    .map(this::mapToMemberDto);
        }

        return memberRepository.findByIsActiveTrueOrderByIdAsc(pageable)
                .map(this::mapToMemberDto);
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
        return memberAccountRepository.findByMemberIdOrderByOrderNumberAscIdAsc(memberId).stream()
                .filter(a -> a.getAccountType() != AccountType.SPECIAL_LOAN || a.getSpecialLoanType() != null)
                .map(this::mapToAccountDto)
                .collect(Collectors.toList());
    }

    public MemberDto mapToMemberDto(Member member) {
        List<MemberAccountDto> accounts = memberAccountRepository.findByMemberIdOrderByOrderNumberAscIdAsc(member.getId()).stream()
                .filter(a -> a.getAccountType() != AccountType.SPECIAL_LOAN || a.getSpecialLoanType() != null)
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
                .specialLoanTypeId(account.getSpecialLoanType() != null ? account.getSpecialLoanType().getId() : null)
                .specialLoanTypeName(account.getSpecialLoanType() != null ? account.getSpecialLoanType().getName() : null)
                .currentBalance(account.getCurrentBalance())
                .orderNumber(account.getOrderNumber() != null ? account.getOrderNumber() : MemberAccount.getDefaultOrderNumber(account.getAccountType()))
                .version(account.getVersion())
                .build();
    }
}
