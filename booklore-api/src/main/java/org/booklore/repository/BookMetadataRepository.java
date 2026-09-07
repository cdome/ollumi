package org.booklore.repository;

import org.booklore.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookMetadataRepository extends JpaRepository<BookMetadataEntity, Long> {


    List<BookMetadataEntity> findAllBySeriesNameIgnoreCase(String seriesName);

    List<BookMetadataEntity> findAllByPublisherIgnoreCase(String publisher);

    List<BookMetadataEntity> findAllByLanguageIgnoreCase(String language);
}
