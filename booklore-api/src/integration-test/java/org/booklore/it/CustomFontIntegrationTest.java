package org.booklore.it;

import org.booklore.config.AppProperties;
import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.CustomFontEntity;
import org.booklore.model.enums.FontFormat;
import org.booklore.repository.CustomFontRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomFontIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private CustomFontRepository customFontRepository;

    @Autowired
    private AppProperties appProperties;

    private CustomFontEntity seedFont(Long userId) throws Exception {
        BookLoreUserEntity user = userRepository.findById(userId).orElseThrow();
        String fileName = "user_" + userId + "_font_" + UUID.randomUUID().toString().substring(0, 8) + ".ttf";

        Path fontDir = Path.of(appProperties.getPathConfig(), "custom-fonts", String.valueOf(userId));
        Files.createDirectories(fontDir);
        Path fontFile = fontDir.resolve(fileName);
        Files.writeString(fontFile, "dummy font content");

        CustomFontEntity font = CustomFontEntity.builder()
                .user(user)
                .fontName("IT Font")
                .fileName(fileName)
                .originalFileName("it-font.ttf")
                .format(FontFormat.TTF)
                .fileSize(Files.size(fontFile))
                .uploadedAt(LocalDateTime.now())
                .build();

        return customFontRepository.save(font);
    }

    @Test
    void adminCanListSeededFont() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity admin = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
        CustomFontEntity font = seedFont(admin.getId());

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/custom-fonts",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).anySatisfy(f ->
                assertThat(f.get("id")).isEqualTo(font.getId().intValue())
        );

        rest.exchange(
                baseUrl() + "/api/v1/custom-fonts/" + font.getId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );
    }

    @Test
    void adminCanDownloadSeededFontFile() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity admin = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
        CustomFontEntity font = seedFont(admin.getId());

        ResponseEntity<byte[]> response = rest.exchange(
                baseUrl() + "/api/v1/custom-fonts/" + font.getId() + "/file?token=" + tokens.accessToken(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("font/ttf");

        rest.exchange(
                baseUrl() + "/api/v1/custom-fonts/" + font.getId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );
    }

    @Test
    void adminCanDeleteSeededFont() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity admin = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
        CustomFontEntity font = seedFont(admin.getId());

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/custom-fonts/" + font.getId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/custom-fonts",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getBody()).noneSatisfy(f ->
                assertThat(f.get("id")).isEqualTo(font.getId().intValue())
        );
    }
}
