package org.booklore.repository;

import org.booklore.model.entity.BookEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long> {
    Optional<BookEntity> findBookByIdAndLibraryId(long id, long libraryId);

    @EntityGraph(attributePaths = { "metadata", "metadata.comicMetadata", "shelves", "libraryPath", "library", "bookFiles" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdWithBookFiles(@Param("id") Long id);

    @EntityGraph(attributePaths = { "metadata", "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags", "metadata.comicMetadata", "library" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdWithMetadata(@Param("id") Long id);

    @EntityGraph(attributePaths = { "metadata", "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags", "metadata.comicMetadata", "bookFiles" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdFull(@Param("id") Long id);

    @EntityGraph(attributePaths = {"bookFiles", "metadata", "library", "libraryPath"})
    @Query("SELECT b FROM BookEntity b JOIN b.bookFiles bf WHERE bf.currentHash = :currentHash AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByCurrentHash(@Param("currentHash") String currentHash);

    @Query("SELECT b FROM BookEntity b JOIN FETCH b.bookFiles bf WHERE bf.currentHash = :currentHash AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false OR b.deletedAt > :cutoff)")
    Optional<BookEntity> findByCurrentHashIncludingRecentlyDeleted(@Param("currentHash") String currentHash, @Param("cutoff") Instant cutoff);

    Optional<BookEntity> findByBookCoverHash(String bookCoverHash);

    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND (bf.fileSubPath = :fileSubPathPrefix OR bf.fileSubPath LIKE CONCAT(:fileSubPathPrefix, '/%')) AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryPathIdAndFileSubPathStartingWith(@Param("libraryPathId") Long libraryPathId, @Param("fileSubPathPrefix") String fileSubPathPrefix);

    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND bf.fileSubPath = :fileSubPath AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryPathIdAndFileSubPath(@Param("libraryPathId") Long libraryPathId, @Param("fileSubPath") String fileSubPath);

    @Query("SELECT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND bf.fileSubPath = :fileSubPath AND bf.fileName = :fileName AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByLibraryPath_IdAndFileSubPathAndFileName(@Param("libraryPathId") Long libraryPathId,
                                                                       @Param("fileSubPath") String fileSubPath,
                                                                       @Param("fileName") String fileName);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadata();

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIds(@Param("bookIds") Set<Long> bookIds);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findWithMetadataByIdsWithPagination(@Param("bookIds") Set<Long> bookIds, Pageable pageable);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryId(@Param("libraryId") Long libraryId);

    @EntityGraph(attributePaths = {"metadata", "bookFiles", "library"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryIdWithFiles(@Param("libraryId") Long libraryId);

    @Query("""
            SELECT DISTINCT b FROM BookEntity b
            LEFT JOIN FETCH b.metadata m
            LEFT JOIN FETCH m.authors
            LEFT JOIN FETCH b.bookFiles
            LEFT JOIN FETCH b.libraryPath
            WHERE b.library.id = :libraryId
            AND (b.deleted IS NULL OR b.deleted = false)
            """)
    List<BookEntity> findAllForDuplicateDetection(@Param("libraryId") Long libraryId);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByShelfId(@Param("shelfId") Long shelfId);

    @EntityGraph(attributePaths = { "metadata", "metadata.comicMetadata", "shelves", "libraryPath", "library", "bookFiles" })
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE bf.isBookFormat = true AND bf.fileSizeKb IS NULL AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByFileSizeKbIsNull();

    @Query("""
                SELECT DISTINCT b FROM BookEntity b
                LEFT JOIN FETCH b.metadata m
                LEFT JOIN FETCH m.authors
                LEFT JOIN FETCH m.categories
                LEFT JOIN FETCH m.comicMetadata
                LEFT JOIN FETCH b.shelves
                WHERE (b.deleted IS NULL OR b.deleted = false)
            """)
    List<BookEntity> findAllFullBooks();

    @Query(value = """
                SELECT DISTINCT b.* FROM book b
                LEFT JOIN book_metadata m ON b.id = m.book_id
                WHERE (b.deleted IS NULL OR b.deleted = false)
                ORDER BY b.id
                LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<BookEntity> findBooksForMigrationBatch(@Param("offset") int offset, @Param("limit") int limit);

    @Query("""
                SELECT DISTINCT b FROM BookEntity b
                LEFT JOIN FETCH b.metadata m
                LEFT JOIN FETCH m.authors
                WHERE b.id IN :bookIds
            """)
    List<BookEntity> findBooksWithMetadataAndAuthors(@Param("bookIds") List<Long> bookIds);

    @Query("""
        SELECT DISTINCT b FROM BookEntity b
        JOIN FETCH b.bookFiles bf
        WHERE b.libraryPath.id = :libraryPathId
        AND (bf.fileSubPath = :folderPath
             OR bf.fileSubPath LIKE CONCAT(:folderPath, '/%')
             OR (bf.folderBased = true AND CONCAT(bf.fileSubPath, '/', bf.fileName) = :folderPath))
        AND bf.isBookFormat = true
        AND (b.deleted IS NULL OR b.deleted = false)
        """)
    List<BookEntity> findBooksWithFilesUnderPath(@Param("libraryPathId") Long libraryPathId,
                                                  @Param("folderPath") String folderPath);

    @Query(value = """
        SELECT b.*
        FROM book b
        JOIN book_file bf ON bf.book_id = b.id
        WHERE b.library_id = :libraryId
          AND b.library_path_id = :libraryPathId
          AND bf.file_sub_path = :fileSubPath
          AND bf.file_name = :fileName
          AND bf.is_book = true
        LIMIT 1
    """, nativeQuery = true)
    Optional<BookEntity> findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
            @Param("libraryId") Long libraryId,
            @Param("libraryPathId") Long libraryPathId,
            @Param("fileSubPath") String fileSubPath,
            @Param("fileName") String fileName);

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
