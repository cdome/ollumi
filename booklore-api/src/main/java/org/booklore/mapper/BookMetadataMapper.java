package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = {AuthorMapper.class, CategoryMapper.class, TagMapper.class, ComicMetadataMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class BookMetadataMapper {

    @Autowired
    protected JooqBookMetadataRelationsRepository relationsRepository;

    @AfterMapping
    protected void mapWithDescriptionCondition(BookMetadataEntity bookMetadataEntity, @MappingTarget BookMetadata bookMetadata, @Context boolean includeDescription) {
        if (includeDescription) {
            bookMetadata.setDescription(bookMetadataEntity.getDescription());
        } else {
            bookMetadata.setDescription(null);
        }
    }

    /**
     * moods is no longer a Hibernate-managed collection (see BookMetadataEntity.moodNames), so
     * MapStruct has no source property to auto-map it from; fill it via jOOQ instead. Safe within the
     * caller's transaction even for a book whose moods were just written by a prior jOOQ call in the
     * same transaction (jOOQ shares the JDBC connection, so same-transaction writes are visible here).
     */
    @AfterMapping
    protected void mapMoods(BookMetadataEntity bookMetadataEntity, @MappingTarget BookMetadata bookMetadata) {
        bookMetadata.setMoods(relationsRepository.findMoodNamesByBookId(bookMetadataEntity.getBookId()));
    }

    @Named("withRelations")
    @Mapping(target = "description", ignore = true)
    public abstract BookMetadata toBookMetadata(BookMetadataEntity bookMetadataEntity, @Context boolean includeDescription);

    public BookMetadata toBookMetadata(BookMetadataEntity bookMetadataEntity) {
        return toBookMetadata(bookMetadataEntity, true);
    }

}
