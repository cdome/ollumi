package org.booklore.model.entity;

import lombok.*;
import org.booklore.model.dto.Sort;
import org.booklore.model.enums.IconType;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class ShelfEntity {

    private Long id;

    private BookLoreUserEntity user;

    private String name;

    private Sort sort;

    private String icon;

    private IconType iconType;

    @Builder.Default
    private boolean isPublic = false;

    private int bookCount;
}
