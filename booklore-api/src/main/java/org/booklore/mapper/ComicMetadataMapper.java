package org.booklore.mapper;

import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.entity.ComicMetadataEntity;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.repository.jooq.JooqComicMetadataRepository;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class ComicMetadataMapper {

    @Autowired
    protected JooqComicMetadataRepository relationsRepository;

    public abstract ComicMetadata toComicMetadata(ComicMetadataEntity entity);

    @AfterMapping
    protected void mapCharacters(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setCharacters(relationsRepository.findCharacterNamesByBookId(entity.getBookId()));
    }

    @AfterMapping
    protected void mapTeams(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setTeams(relationsRepository.findTeamNamesByBookId(entity.getBookId()));
    }

    @AfterMapping
    protected void mapLocations(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setLocations(relationsRepository.findLocationNamesByBookId(entity.getBookId()));
    }

    @AfterMapping
    protected void mapPencillers(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setPencillers(relationsRepository.findCreatorNamesByBookIdAndRole(entity.getBookId(), ComicCreatorRole.PENCILLER));
    }

    @AfterMapping
    protected void mapInkers(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setInkers(relationsRepository.findCreatorNamesByBookIdAndRole(entity.getBookId(), ComicCreatorRole.INKER));
    }

    @AfterMapping
    protected void mapColorists(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setColorists(relationsRepository.findCreatorNamesByBookIdAndRole(entity.getBookId(), ComicCreatorRole.COLORIST));
    }

    @AfterMapping
    protected void mapLetterers(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setLetterers(relationsRepository.findCreatorNamesByBookIdAndRole(entity.getBookId(), ComicCreatorRole.LETTERER));
    }

    @AfterMapping
    protected void mapCoverArtists(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setCoverArtists(relationsRepository.findCreatorNamesByBookIdAndRole(entity.getBookId(), ComicCreatorRole.COVER_ARTIST));
    }

    @AfterMapping
    protected void mapEditors(ComicMetadataEntity entity, @MappingTarget ComicMetadata dto) {
        dto.setEditors(relationsRepository.findCreatorNamesByBookIdAndRole(entity.getBookId(), ComicCreatorRole.EDITOR));
    }
}
