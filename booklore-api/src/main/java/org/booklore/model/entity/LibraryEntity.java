package org.booklore.model.entity;

import org.booklore.model.dto.Sort;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.IconType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.model.enums.MetadataSource;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryEntity {
    private Long id;

    private String name;

    private Sort sort;

    private boolean watch;

    private String icon;

    private IconType iconType;

    private String fileNamingPattern;

    @Builder.Default
    private List<BookFileType> formatPriority = new ArrayList<>();

    private List<BookFileType> allowedFormats;

    @Builder.Default
    private LibraryOrganizationMode organizationMode = LibraryOrganizationMode.AUTO_DETECT;

    @Builder.Default
    private MetadataSource metadataSource = MetadataSource.EMBEDDED;

}
