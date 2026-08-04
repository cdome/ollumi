package org.booklore.repository;

import org.booklore.model.entity.ComicMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComicMetadataRepository extends JpaRepository<ComicMetadataEntity, Long> {
}
