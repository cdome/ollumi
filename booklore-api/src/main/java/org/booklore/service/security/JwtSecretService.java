package org.booklore.service.security;

import org.booklore.repository.jooq.JooqJwtSecretRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class JwtSecretService {

    private final JooqJwtSecretRepository jwtSecretRepository;
    private volatile String cachedSecret;
    private final ReentrantLock lock = new ReentrantLock();

    public JwtSecretService(JooqJwtSecretRepository jwtSecretRepository) {
        this.jwtSecretRepository = jwtSecretRepository;
    }

    @Transactional
    public String getSecret() {
        if (cachedSecret == null) {
            lock.lock();
            try {
                if (cachedSecret == null) {
                    String latest = jwtSecretRepository.findLatestSecret();
                    cachedSecret = latest != null ? latest : generateAndStoreNewSecret();
                }
            } finally {
                lock.unlock();
            }
        }
        return cachedSecret;
    }

    private String generateAndStoreNewSecret() {
        String newSecret = generateRandomSecret();
        jwtSecretRepository.insertSecret(newSecret);
        return newSecret;
    }

    private String generateRandomSecret() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }
}
