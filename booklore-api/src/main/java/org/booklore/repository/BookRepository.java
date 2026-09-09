package org.booklore.repository;

import org.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long> {

    @EntityGraph(attributePaths = { "metadata", "metadata.comicMetadata", "libraryPath", "library", "bookFiles" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdWithBookFiles(@Param("id") Long id);

    @EntityGraph(attributePaths = { "metadata", "metadata.comicMetadata", "library" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdWithMetadata(@Param("id") Long id);

    @Query("SELECT b FROM BookEntity b JOIN FETCH b.bookFiles bf WHERE bf.currentHash = :currentHash AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false OR b.deletedAt > :cutoff)")
    Optional<BookEntity> findByCurrentHashIncludingRecentlyDeleted(@Param("currentHash") String currentHash, @Param("cutoff") Instant cutoff);

    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND bf.fileSubPath = :fileSubPath AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryPathIdAndFileSubPath(@Param("libraryPathId") Long libraryPathId, @Param("fileSubPath") String fileSubPath);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIds(@Param("bookIds") Set<Long> bookIds);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryId(@Param("libraryId") Long libraryId);

    @Query(value = """
                SELECT DISTINCT b.* FROM book b
                LEFT JOIN book_metadata m ON b.id = m.book_id
                WHERE (b.deleted IS NULL OR b.deleted = false)
                ORDER BY b.id
                LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<BookEntity> findBooksForMigrationBatch(@Param("offset") int offset, @Param("limit") int limit);

    @Query("""
            SELECT b FROM BookEntity b
            LEFT JOIN b.bookFiles bf
            WHERE b.library.id = :libraryId
            AND (b.deleted IS NULL OR b.deleted = false)
            GROUP BY b
            HAVING COUNT(bf) = 0
            """)
    List<BookEntity> findFilelessBooksByLibraryId(@Param("libraryId") Long libraryId);
}
