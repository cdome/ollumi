package org.booklore.model.entity;

import lombok.*;
import org.booklore.model.enums.ProvisioningMethod;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookLoreUserEntity {

    private Long id;

    private String username;

    private String passwordHash;

    private boolean isDefaultPassword;

    private String name;

    private String email;

    private ProvisioningMethod provisioningMethod;

    private String oidcSubject;

    private String oidcIssuer;

    private String avatarUrl;

    private LocalDateTime createdAt;

    private UserPermissionsEntity permissions;
}
