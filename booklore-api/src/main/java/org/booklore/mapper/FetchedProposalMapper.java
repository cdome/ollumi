package org.booklore.mapper;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.FetchedProposal;
import org.booklore.repository.jooq.dto.MetadataFetchProposalRow;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class FetchedProposalMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    public FetchedProposal toDto(MetadataFetchProposalRow row) {
        return new FetchedProposal(
                row.getProposalId(),
                row.getTaskId(),
                row.getBookId(),
                row.getFetchedAt(),
                row.getReviewedAt(),
                null, // reviewerUserId was not mapped by the original Lombok/MapStruct mapper (source type is Long, target type is String)
                row.getStatus(),
                parseMetadataJson(row.getMetadataJson())
        );
    }

    protected BookMetadata parseMetadataJson(String metadataJson) {
        if (metadataJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(metadataJson, BookMetadata.class);
        } catch (Exception e) {
            log.error("Failed to parse metadata JSON: {}", e.getMessage(), e);
            return null;
        }
    }
}
