package org.booklore.it.util;

import org.booklore.model.dto.request.UserLoginRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.repository.jooq.JooqUserRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.function.Consumer;

public class AuthTestHelper {

    private final RestTemplate rest;
    private final PasswordEncoder passwordEncoder;
    private final JooqUserRepository userRepository;
    private final JdbcTemplate jdbc;

    public AuthTestHelper(RestTemplate rest, PasswordEncoder passwordEncoder, JooqUserRepository userRepository, JdbcTemplate jdbc) {
        this.rest = rest;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
    }

    public Tokens login(String baseUrl, String username, String password) {
        BookLoreUserEntity existing = userRepository.findByUsername(username);
        if (existing != null) {
            jdbc.update("DELETE FROM refresh_token WHERE user_id = ?", existing.getId());
        }
        UserLoginRequest request = new UserLoginRequest(username, password);
        ResponseEntity<Map> response = rest.postForEntity(baseUrl + "/api/v1/auth/login", request, Map.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Login failed: " + response.getStatusCode() + " body=" + response.getBody());
        }
        Map<String, Object> body = response.getBody();
        return new Tokens((String) body.get("accessToken"), (String) body.get("refreshToken"));
    }

    public ResponseEntity<Map> tryLogin(String baseUrl, String username, String password) {
        UserLoginRequest request = new UserLoginRequest(username, password);
        return rest.postForEntity(baseUrl + "/api/v1/auth/login", request, Map.class);
    }

    public BookLoreUserEntity createUser(String username, String password, Consumer<UserPermissionsEntity> permissionsConfigurer) {
        BookLoreUserEntity existingUser = userRepository.findByUsername(username);
        if (existingUser != null) {
            return existingUser;
        }
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(username);
        user.setName(username);
        user.setEmail(username + "@example.com");
        user.setProvisioningMethod(ProvisioningMethod.LOCAL);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDefaultPassword(false);

        UserPermissionsEntity perms = new UserPermissionsEntity();
        perms.setPermissionAdmin(false);
        perms.setUser(user);
        user.setPermissions(perms);
        permissionsConfigurer.accept(perms);

        return userRepository.save(user);
    }

    public BookLoreUserEntity createUser(String username, String password) {
        return createUser(username, password, p -> {
        });
    }

    public BookLoreUserEntity createAdmin(String username, String password) {
        return createUser(username, password, perms -> perms.setPermissionAdmin(true));
    }

    public HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    public HttpEntity<Void> bearerEntity(String accessToken) {
        return new HttpEntity<>(bearerHeaders(accessToken));
    }

    public <T> HttpEntity<T> bearerEntity(T body, String accessToken) {
        HttpHeaders headers = bearerHeaders(accessToken);
        return new HttpEntity<>(body, headers);
    }

    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    public JooqUserRepository userRepository() {
        return userRepository;
    }

    public record Tokens(String accessToken, String refreshToken) {}
}
