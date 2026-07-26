package org.booklore.service.customfont;

import org.booklore.config.AppProperties;
import org.booklore.model.dto.CustomFontDto;
import org.booklore.model.enums.FontFormat;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqCustomFontRepository;
import org.booklore.repository.jooq.dto.CustomFont;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomFontServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    JooqCustomFontRepository customFontRepository;
    @Mock
    UserRepository userRepository;

    AppProperties appProperties;
    CustomFontService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appProperties = new AppProperties();
        appProperties.setPathConfig(tempDir.toString());
        service = new CustomFontService(customFontRepository, userRepository, appProperties);
    }

    private CustomFont font(Long id, String fontName, String fileName, FontFormat format) {
        return new CustomFont(
                id,
                1L,
                fontName,
                fileName,
                fileName,
                format,
                123L,
                LocalDateTime.now());
    }

    @Test
    @DisplayName("uploadFont_withValidFile_shouldSaveSuccessfully")
    void uploadFont_withValidFile_shouldSaveSuccessfully() {
        Long userId = 1L;
        String fontName = "My Custom Font";
        // Create valid TTF magic bytes: 0x00 0x01 0x00 0x00
        byte[] fontContent = new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("font.ttf", "font.ttf", "font/ttf", fontContent);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(customFontRepository.countByUserId(userId)).thenReturn(0);
        when(customFontRepository.insert(eq(userId), eq(fontName), anyString(), eq("font.ttf"), eq(FontFormat.TTF), eq((long) fontContent.length), any()))
                .thenAnswer(inv -> font(1L, inv.getArgument(1), inv.getArgument(2), inv.getArgument(4)));

        CustomFontDto result = service.uploadFont(file, fontName, userId);

        assertThat(result).isNotNull();
        assertThat(result.getFontName()).isEqualTo(fontName);
        assertThat(result.getFormat()).isEqualTo(FontFormat.TTF);
        verify(customFontRepository).insert(eq(userId), eq(fontName), anyString(), eq("font.ttf"), eq(FontFormat.TTF), anyLong(), any());

        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        assertThat(Files.exists(userFontDir)).isTrue();
        assertThatCode(() -> assertThat(Files.list(userFontDir).count()).isEqualTo(1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("uploadFont_whenQuotaExceeded_shouldThrowException")
    void uploadFont_whenQuotaExceeded_shouldThrowException() {
        Long userId = 1L;
        MultipartFile file = new MockMultipartFile("font.ttf", "font.ttf", "font/ttf", "content".getBytes());

        when(customFontRepository.countByUserId(userId)).thenReturn(10); // Max quota

        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Font limit exceeded");

        verify(customFontRepository, never()).insert(anyLong(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("uploadFont_withOversizedFile_shouldThrowException")
    void uploadFont_withOversizedFile_shouldThrowException() {
        Long userId = 1L;
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB (exceeds 5MB limit)
        MultipartFile file = new MockMultipartFile("font.ttf", "font.ttf", "font/ttf", largeContent);

        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size exceeds maximum limit");

        verify(customFontRepository, never()).insert(anyLong(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("uploadFont_withInvalidExtension_shouldThrowException")
    void uploadFont_withInvalidExtension_shouldThrowException() {
        Long userId = 1L;
        MultipartFile file = new MockMultipartFile("font.exe", "font.exe", "application/octet-stream", "content".getBytes());

        when(customFontRepository.countByUserId(userId)).thenReturn(0);

        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported font format");

        verify(customFontRepository, never()).insert(anyLong(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("uploadFont_withInvalidMagicBytes_shouldThrowException")
    void uploadFont_withInvalidMagicBytes_shouldThrowException() throws IOException {
        Long userId = 1L;
        // File has .ttf extension but contains malicious content (not TTF magic bytes)
        byte[] maliciousContent = "This is not a font file".getBytes();
        MultipartFile file = new MockMultipartFile("malicious.ttf", "malicious.ttf", "font/ttf", maliciousContent);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(customFontRepository.countByUserId(userId)).thenReturn(0);

        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid font file format");

        verify(customFontRepository, never()).insert(anyLong(), any(), any(), any(), any(), anyLong(), any());

        // Verify file was cleaned up
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        if (Files.exists(userFontDir)) {
            assertThat(Files.list(userFontDir).count()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("uploadFont_whenDatabaseSaveFails_shouldCleanupFile")
    void uploadFont_whenDatabaseSaveFails_shouldCleanupFile() throws IOException {
        Long userId = 1L;
        // Create valid TTF magic bytes
        byte[] fontContent = new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("font.ttf", "font.ttf", "font/ttf", fontContent);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(customFontRepository.countByUserId(userId)).thenReturn(0);
        when(customFontRepository.insert(anyLong(), any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");

        // Verify file was cleaned up
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        if (Files.exists(userFontDir)) {
            assertThat(Files.list(userFontDir).count()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("deleteFont_withValidId_shouldDeleteFileAndRecord")
    void deleteFont_withValidId_shouldDeleteFileAndRecord() throws IOException {
        Long userId = 1L;
        Long fontId = 1L;
        String fileName = "user_1_font_123.ttf";

        // Create actual font file
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        Files.createDirectories(userFontDir);
        Path fontFile = userFontDir.resolve(fileName);
        Files.writeString(fontFile, "font content");

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(font(fontId, "Test Font", fileName, FontFormat.TTF));

        service.deleteFont(fontId, userId);

        verify(customFontRepository).deleteById(fontId);
        assertThat(Files.exists(fontFile)).isFalse();
    }

    @Test
    @DisplayName("deleteFont_whenFileDeletionFails_shouldStillDeleteRecord")
    void deleteFont_whenFileDeletionFails_shouldStillDeleteRecord() {
        Long userId = 1L;
        Long fontId = 1L;
        String fileName = "non_existent_font.ttf";

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(font(fontId, "Test Font", fileName, FontFormat.TTF));

        // Files.deleteIfExists doesn't throw when file doesn't exist, so this should succeed
        assertThatCode(() -> service.deleteFont(fontId, userId))
                .doesNotThrowAnyException();

        verify(customFontRepository, times(1)).deleteById(fontId);
    }

    @Test
    @DisplayName("deleteFont_withInvalidId_shouldThrowException")
    void deleteFont_withInvalidId_shouldThrowException() {
        Long userId = 1L;
        Long fontId = 999L;

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteFont(fontId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Font not found or access denied");

        verify(customFontRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("getFontFile_withValidId_shouldReturnResource")
    void getFontFile_withValidId_shouldReturnResource() throws IOException {
        Long userId = 1L;
        Long fontId = 1L;
        String fileName = "user_1_font_123.ttf";

        // Create actual font file
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        Files.createDirectories(userFontDir);
        Path fontFile = userFontDir.resolve(fileName);
        Files.writeString(fontFile, "font content");

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(font(fontId, "Test Font", fileName, FontFormat.TTF));

        Resource result = service.getFontFile(fontId, userId);

        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
        assertThat(result.getFile().toPath()).isEqualTo(fontFile);
    }

    @Test
    @DisplayName("getFontFile_whenUserNotOwner_shouldThrowException")
    void getFontFile_whenUserNotOwner_shouldThrowException() {
        Long otherUserId = 2L;
        Long fontId = 1L;

        when(customFontRepository.findByIdAndUserId(fontId, otherUserId)).thenReturn(null);

        assertThatThrownBy(() -> service.getFontFile(fontId, otherUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Font not found or access denied");
    }

    @Test
    @DisplayName("getFontFile_withNonExistentFile_shouldThrowException")
    void getFontFile_withNonExistentFile_shouldThrowException() {
        Long userId = 1L;
        Long fontId = 1L;
        String fileName = "non_existent.ttf";

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(font(fontId, "Test Font", fileName, FontFormat.TTF));

        assertThatThrownBy(() -> service.getFontFile(fontId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Font file not found on disk");
    }

    @Test
    @DisplayName("validatePath_withPathTraversal_shouldThrowException")
    void validatePath_withPathTraversal_shouldThrowException() {
        Long userId = 1L;
        // Use valid font extension to pass extension validation
        String maliciousFileName = "../../../etc/passwd.ttf";
        // Create valid TTF magic bytes to pass magic byte validation
        byte[] fontContent = new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile(
                maliciousFileName,
                maliciousFileName,
                "font/ttf",
                fontContent
        );

        when(userRepository.existsById(userId)).thenReturn(true);
        when(customFontRepository.countByUserId(userId)).thenReturn(0);
        when(customFontRepository.insert(anyLong(), any(), any(), any(), any(), anyLong(), any()))
                .thenAnswer(inv -> font(1L, inv.getArgument(1), inv.getArgument(2), inv.getArgument(4)));

        // The service generates safe filenames, so even with malicious input, the path is safe
        assertThatCode(() -> service.uploadFont(file, "Test", userId))
                .doesNotThrowAnyException();

        // Verify no files were created outside the expected directory
        Path expectedDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        Path parentDir = tempDir.resolve("custom-fonts");

        assertThatCode(() -> {
            long userDirFiles = Files.exists(expectedDir) ? Files.list(expectedDir).count() : 0;
            long parentDirEntries = Files.list(parentDir).count();
            assertThat(userDirFiles).isGreaterThanOrEqualTo(0);
            assertThat(parentDirEntries).isEqualTo(1); // Only user directory, no escaped files
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getUserFonts_shouldReturnAllUserFonts")
    void getUserFonts_shouldReturnAllUserFonts() {
        Long userId = 1L;
        List<CustomFont> fonts = List.of(
                font(1L, "Font 1", "f1.ttf", FontFormat.TTF),
                font(2L, "Font 2", "f2.otf", FontFormat.OTF)
        );

        when(customFontRepository.findByUserId(userId)).thenReturn(fonts);

        List<CustomFontDto> result = service.getUserFonts(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getFontName()).isEqualTo("Font 1");
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getFontFormat_shouldReturnCorrectFormat")
    void getFontFormat_shouldReturnCorrectFormat() {
        Long userId = 1L;
        Long fontId = 1L;

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(font(fontId, "Test Font", "f.woff2", FontFormat.WOFF2));

        FontFormat result = service.getFontFormat(fontId, userId);

        assertThat(result).isEqualTo(FontFormat.WOFF2);
    }
}
