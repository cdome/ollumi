package org.booklore.mapper;

import org.booklore.model.dto.Library;
import org.booklore.model.dto.LibraryPath;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.jooq.JooqLibraryPathRepository;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class LibraryMapper {

    @Autowired
    protected JooqLibraryPathRepository libraryPathRepository;

    public abstract Library toLibrary(LibraryEntity libraryEntity);

    @AfterMapping
    protected void mapPaths(LibraryEntity libraryEntity, @MappingTarget Library library) {
        library.setPaths(
                libraryPathRepository.findPathsByLibraryId(libraryEntity.getId()).stream()
                        .map(row -> LibraryPath.builder()
                                .id(row.getId())
                                .libraryId(libraryEntity.getId())
                                .path(row.getPath())
                                .build())
                        .toList()
        );
    }
}
