package org.booklore.config.security.service;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.repository.jooq.JooqOpdsUserV2Repository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class OpdsUserDetailsService implements UserDetailsService {

    private final JooqOpdsUserV2Repository opdsUserV2Repository;

    @Override
    @Transactional(readOnly = true)
    public OpdsUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        OpdsUserV2 mappedCredential = opdsUserV2Repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new OpdsUserDetails(mappedCredential);
    }
}
