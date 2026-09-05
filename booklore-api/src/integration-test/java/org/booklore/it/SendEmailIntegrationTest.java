package org.booklore.it;

import org.booklore.model.dto.request.SendBookByEmailRequest;
import org.booklore.service.email.SendEmailV2Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class SendEmailIntegrationTest extends RestApiIntegrationTest {

    @MockitoBean
    private SendEmailV2Service sendEmailV2Service;

    private static final String EMAILER_USERNAME = "emailer";
    private static final String EMAILER_PASSWORD = "password";

    @AfterEach
    void resetMock() {
        reset(sendEmailV2Service);
    }

    @Test
    void emailBook_permissionedUser_returnsNoContent() {
        auth.createUser(EMAILER_USERNAME, EMAILER_PASSWORD, p -> p.setPermissionEmailBook(true));
        var tokens = auth.login(baseUrl(), EMAILER_USERNAME, EMAILER_PASSWORD);

        SendBookByEmailRequest request = SendBookByEmailRequest.builder()
                .bookId(1L)
                .providerId(2L)
                .recipientId(3L)
                .build();

        ResponseEntity<Void> response = rest.postForEntity(
                baseUrl() + "/api/v1/email/book",
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sendEmailV2Service).emailBook(any(SendBookByEmailRequest.class));
    }

    @Test
    void emailBook_admin_returnsNoContent() {
        var tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        SendBookByEmailRequest request = SendBookByEmailRequest.builder()
                .bookId(1L)
                .providerId(2L)
                .recipientId(3L)
                .build();

        ResponseEntity<Void> response = rest.postForEntity(
                baseUrl() + "/api/v1/email/book",
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sendEmailV2Service).emailBook(any(SendBookByEmailRequest.class));
    }

    @Test
    void emailBookQuick_permissionedUser_returnsNoContent() {
        auth.createUser(EMAILER_USERNAME, EMAILER_PASSWORD, p -> p.setPermissionEmailBook(true));
        var tokens = auth.login(baseUrl(), EMAILER_USERNAME, EMAILER_PASSWORD);

        ResponseEntity<Void> response = rest.postForEntity(
                baseUrl() + "/api/v1/email/book/42",
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sendEmailV2Service).emailBookQuick(42L);
    }

    @Test
    void emailBookQuick_admin_returnsNoContent() {
        var tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Void> response = rest.postForEntity(
                baseUrl() + "/api/v1/email/book/42",
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sendEmailV2Service).emailBookQuick(42L);
    }
}
