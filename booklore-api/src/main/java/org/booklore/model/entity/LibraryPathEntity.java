package org.booklore.model.entity;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryPathEntity {

    private Long id;

    private LibraryEntity library;

    private String path;
}
