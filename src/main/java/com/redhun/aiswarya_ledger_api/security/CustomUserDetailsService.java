package com.redhun.aiswarya_ledger_api.security;

import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        Long memberId = memberRepository.findByUserId(user.getId())
                .map(Member::getId)
                .orElse(null);

        return UserPrincipal.create(user, memberId);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        Long memberId = memberRepository.findByUserId(user.getId())
                .map(Member::getId)
                .orElse(null);

        return UserPrincipal.create(user, memberId);
    }
}
