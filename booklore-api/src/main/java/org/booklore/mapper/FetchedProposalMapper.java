package org.booklore.mapper;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.FetchedProposal;
import org.booklore.repository.jooq.dto.MetadataFetchProposalRow;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class FetchedProposalMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "metadataJson", ignore = true)
    public abstract FetchedProposal toDto(MetadataFetchProposalRow row);

    @AfterMapping
    protected void mapMetadataJson(MetadataFetchProposalRow row, @MappingTarget FetchedProposal target) {
        if (row.getMetadataJson() != null) {
            try {
                BookMetadata metadata = objectMapper.readValue(row.getMetadataJson(), BookMetadata.class);
                target.setMetadataJson(metadata);
            } catch (Exception e) {
                log.error("Failed to parse metadata JSON for proposal id {}: {}", row.getProposalId(), e.getMessage(), e);
            }
        }
    }
}
