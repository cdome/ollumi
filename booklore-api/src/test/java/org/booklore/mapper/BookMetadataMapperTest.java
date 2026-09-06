package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookMetadataEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class BookMetadataMapperTest {

    @Mock
    private org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;

    @Mock
    private ComicMetadataMapper comicMetadataMapper;

    private BookMetadataMapperImpl bookMetadataMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        bookMetadataMapper = new BookMetadataMapperImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(bookMetadataMapper, "relationsRepository", relationsRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(bookMetadataMapper, "comicMetadataMapper", comicMetadataMapper);
    }

    @Test
    void testMapping() {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setBookId(1L);
        entity.setTitle("Test Title");
        entity.setHardcoverId("hc-id");
        entity.setHardcoverRating(4.5);
        entity.setGoodreadsId("gr-id");

        BookMetadata dto = bookMetadataMapper.toBookMetadata(entity);

        assertEquals("Test Title", dto.getTitle());
        assertEquals("hc-id", dto.getHardcoverId());
        assertEquals(4.5, dto.getHardcoverRating());
        assertEquals("gr-id", dto.getGoodreadsId());
    }
}

