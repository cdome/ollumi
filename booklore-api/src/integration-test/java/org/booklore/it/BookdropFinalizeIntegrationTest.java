package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.dto.request.BookdropFinalizeRequest;
import org.booklore.model.dto.response.BookdropFinalizeResult;
import org.booklore.service.bookdrop.BookDropService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class BookdropFinalizeIntegrationTest extends RestApiIntegrationTest {

    @MockitoBean
    private BookDropService bookDropService;

    @AfterEach
    void resetMock() {
        reset(bookDropService);
    }

    @Test
    void userWithBookdropPermissionCanFinalizeImport() {
        auth.createUser("dropper", "password", p -> p.setPermissionAccessBookdrop(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), "dropper", "password");

        BookdropFinalizeResult expected = BookdropFinalizeResult.builder()
                .totalFiles(2)
                .successfullyImported(2)
                .failed(0)
                .processedAt(Instant.now())
                .build();

        when(bookDropService.finalizeImport(any(BookdropFinalizeRequest.class))).thenReturn(expected);

        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(true);
        request.setExcludedIds(List.of());

        HttpEntity<BookdropFinalizeRequest> entity = auth.bearerEntity(request, tokens.accessToken());
        ResponseEntity<BookdropFinalizeResult> response = rest.exchange(
                baseUrl() + "/api/v1/bookdrop/imports/finalize",
                HttpMethod.POST,
                entity,
                BookdropFinalizeResult.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalFiles()).isEqualTo(expected.getTotalFiles());
        assertThat(response.getBody().getSuccessfullyImported()).isEqualTo(expected.getSuccessfullyImported());
        assertThat(response.getBody().getFailed()).isEqualTo(expected.getFailed());
    }
}
