package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.MagicShelf;
import org.booklore.repository.jooq.JooqMagicShelfRepository;
import org.booklore.repository.jooq.dto.MagicShelfRow;
import lombok.AllArgsConstructor;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class MagicShelfService {

    private final JooqMagicShelfRepository magicShelfRepository;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public List<MagicShelf> getUserShelves() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return getShelvesForUser(userId);
    }

    public List<MagicShelf> getUserShelvesForOpds(Long userId) {
        return getShelvesForUser(userId);
    }

    private List<MagicShelf> getShelvesForUser(Long userId) {
        List<MagicShelf> shelves = magicShelfRepository.findAllByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        List<Long> userShelfIds = shelves.stream().map(MagicShelf::getId).toList();

        List<MagicShelf> publicShelves = magicShelfRepository.findAllPublic().stream()
                .map(this::toDto)
                .filter(shelf -> !userShelfIds.contains(shelf.getId()))
                .toList();

        shelves.addAll(publicShelves);
        return shelves;
    }

    @Transactional
    public MagicShelf createOrUpdateShelf(MagicShelf dto) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        if (dto.getId() != null) {
            MagicShelfRow existing = magicShelfRepository.findById(dto.getId()).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
            if (existing.getUserId() != userId.longValue()) {
                throw new SecurityException("You are not authorized to update this shelf");
            }
            if (existing.isPublic() && !authenticationService.getAuthenticatedUser().getPermissions().isAdmin()) {
                throw new SecurityException("You are not authorized to update a public shelf");
            }
            MagicShelfRow updated = magicShelfRepository.update(
                    existing.getId(), existing.getUserId(), dto.getName(), dto.getIcon(),
                    dto.getIconType(), dto.getFilterJson(), dto.getIsPublic());
            MagicShelf result = toDto(updated);
            auditService.log(AuditAction.MAGIC_SHELF_UPDATED, "MagicShelf", dto.getId(), "Updated magic shelf: " + dto.getName());
            return result;
        }
        if (magicShelfRepository.existsByUserIdAndName(userId, dto.getName())) {
            throw new IllegalArgumentException("A shelf with the same name already exists for this user.");
        }
        MagicShelfRow created = magicShelfRepository.insert(
                userId, dto.getName(), dto.getIcon(), dto.getIconType(), dto.getFilterJson(), dto.getIsPublic());
        MagicShelf result = toDto(created);
        auditService.log(AuditAction.MAGIC_SHELF_CREATED, "MagicShelf", result.getId(), "Created magic shelf: " + dto.getName());
        return result;
    }

    @Transactional
    public void deleteShelf(Long id) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        MagicShelfRow shelf = magicShelfRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
        if (shelf.getUserId() != userId.longValue()) {
            throw new SecurityException("You are not authorized to delete this shelf");
        }
        String shelfName = shelf.getName();
        magicShelfRepository.deleteById(id);
        auditService.log(AuditAction.MAGIC_SHELF_DELETED, "MagicShelf", id, "Deleted magic shelf: " + shelfName);
    }

    private MagicShelf toDto(MagicShelfRow entity) {
        MagicShelf dto = new MagicShelf();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setIcon(entity.getIcon());
        dto.setIconType(entity.getIconType());
        dto.setFilterJson(entity.getFilterJson());
        dto.setIsPublic(entity.isPublic());
        return dto;
    }

    public MagicShelf getShelf(Long id) {
        MagicShelfRow shelf = magicShelfRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
        return toDto(shelf);
    }
}
