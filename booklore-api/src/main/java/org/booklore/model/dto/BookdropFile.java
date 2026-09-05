package org.booklore.model.dto;

import org.booklore.model.enums.BookdropFileStatus;
import lombok.Data;

@Data
public class BookdropFile {
    private Long id;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private BookMetadata originalMetadata;
    private BookMetadata fetchedMetadata;
    private String createdAt;
    private String updatedAt;
    private BookdropFileStatus status;
}
