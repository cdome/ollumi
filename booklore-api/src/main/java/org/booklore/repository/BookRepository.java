package org.booklore.repository;

import org.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long> {

    @EntityGraph(attributePaths = { "metadata", "metadata.comicMetadata", "libraryPath", "library", "bookFiles" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdWithBookFiles(@Param("id") Long id);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIds(@Param("bookIds") Set<Long> bookIds);
}
