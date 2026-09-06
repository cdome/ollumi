package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.model.enums.ProvisioningMethod;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "users")
public class BookLoreUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_default_password", nullable = false)
    private boolean isDefaultPassword;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    @Column(name = "provisioning_method")
    @Enumerated(EnumType.STRING)
    private ProvisioningMethod provisioningMethod;

    @Column(name = "oidc_subject")
    private String oidcSubject;

    @Column(name = "oidc_issuer", length = 512)
    private String oidcIssuer;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserPermissionsEntity permissions;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}