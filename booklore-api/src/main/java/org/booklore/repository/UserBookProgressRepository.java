package org.booklore.repository;

import org.booklore.model.entity.UserBookProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserBookProgressRepository extends JpaRepository<UserBookProgressEntity, Long> {

    Optional<UserBookProgressEntity> findByUserIdAndBookId(Long userId, Long bookId);

    List<UserBookProgressEntity> findByUserIdAndBookIdIn(Long userId, Set<Long> bookIds);

    // Native query (not JPQL) so it references the kobo_library_snapshot_book TABLE directly and does
    // not depend on the KoboSnapshotBook JPA entity, which has been migrated to jOOQ. SELECT ubp.*
    // still maps to managed UserBookProgressEntity rows.
    @Query(value = """
        SELECT ubp.* FROM user_book_progress ubp
        WHERE ubp.user_id = :userId
          AND ubp.book_id IN (
              SELECT ksb.book_id FROM kobo_library_snapshot_book ksb
              WHERE ksb.snapshot_id = :snapshotId
          )
          AND (
              (ubp.read_status_modified_time IS NOT NULL AND (
                  ubp.kobo_status_sent_time IS NULL
                  OR ubp.read_status_modified_time > ubp.kobo_status_sent_time
              ))
              OR
              (ubp.kobo_progress_received_time IS NOT NULL AND (
                  ubp.kobo_progress_sent_time IS NULL
                  OR ubp.kobo_progress_received_time > ubp.kobo_progress_sent_time
              ))
              OR
              (ubp.epub_progress_percent IS NOT NULL
                  AND ubp.epub_progress IS NOT NULL
                  AND (ubp.kobo_progress_sent_time IS NULL OR ubp.last_read_time > ubp.kobo_progress_sent_time))
          )
    """, nativeQuery = true)
    List<UserBookProgressEntity> findAllBooksNeedingKoboSync(
            @Param("userId") Long userId,
            @Param("snapshotId") String snapshotId
    );
}
