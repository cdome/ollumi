package org.booklore.service.restriction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ContentRestriction;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.UserContentRestrictionEntity;
import org.booklore.model.enums.ContentRestrictionMode;
import org.booklore.model.enums.ContentRestrictionType;
import org.booklore.repository.UserContentRestrictionRepository;
import org.booklore.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRestrictionService {

    private final UserContentRestrictionRepository restrictionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ContentRestriction> getUserRestrictions(Long userId) {
        return restrictionRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContentRestriction getRestriction(Long restrictionId) {
        return restrictionRepository.findById(restrictionId)
                .map(this::toDto)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Content restriction not found"));
    }

    @Transactional
    public ContentRestriction addRestriction(Long userId, ContentRestriction restriction) {
        BookLoreUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        if (restrictionRepository.existsByUserIdAndRestrictionTypeAndValue(
                userId, restriction.getRestrictionType(), restriction.getValue())) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Restriction already exists");
        }

        UserContentRestrictionEntity entity = UserContentRestrictionEntity.builder()
                .user(user)
                .restrictionType(restriction.getRestrictionType())
                .mode(restriction.getMode())
                .value(restriction.getValue())
                .build();

        return toDto(restrictionRepository.save(entity));
    }

    @Transactional
    public List<ContentRestriction> updateRestrictions(Long userId, List<ContentRestriction> restrictions) {
        BookLoreUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        restrictionRepository.deleteByUserId(userId);

        List<UserContentRestrictionEntity> entities = restrictions.stream()
                .map(r -> UserContentRestrictionEntity.builder()
                        .user(user)
                        .restrictionType(r.getRestrictionType())
                        .mode(r.getMode())
                        .value(r.getValue())
                        .build())
                .collect(Collectors.toList());

        return restrictionRepository.saveAll(entities).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRestriction(Long restrictionId) {
        if (!restrictionRepository.existsById(restrictionId)) {
            throw ApiError.GENERIC_NOT_FOUND.createException("Content restriction not found");
        }
        restrictionRepository.deleteById(restrictionId);
    }

    @Transactional
    public void deleteAllUserRestrictions(Long userId) {
        restrictionRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<BookEntity> applyRestrictions(List<BookEntity> books, Long userId) {
        return filterByContent(books, userId, this::contentOf);
    }

    /** DTO-based counterpart of {@link #applyRestrictions}, sharing the same filter logic. */
    @Transactional(readOnly = true)
    public List<Book> applyRestrictionsToDtos(List<Book> books, Long userId) {
        return filterByContent(books, userId, this::contentOf);
    }

    private <T> List<T> filterByContent(List<T> books, Long userId, Function<T, BookContent> toContent) {
        List<UserContentRestrictionEntity> restrictions = restrictionRepository.findByUserId(userId);

        if (restrictions.isEmpty()) {
            return books;
        }

        Set<String> excludedCategories = getValuesForTypeAndMode(restrictions, ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE);
        Set<String> excludedTags = getValuesForTypeAndMode(restrictions, ContentRestrictionType.TAG, ContentRestrictionMode.EXCLUDE);
        Set<String> excludedMoods = getValuesForTypeAndMode(restrictions, ContentRestrictionType.MOOD, ContentRestrictionMode.EXCLUDE);
        Set<String> excludedContentRatings = getValuesForTypeAndMode(restrictions, ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.EXCLUDE);

        Set<String> allowedCategories = getValuesForTypeAndMode(restrictions, ContentRestrictionType.CATEGORY, ContentRestrictionMode.ALLOW_ONLY);
        Set<String> allowedTags = getValuesForTypeAndMode(restrictions, ContentRestrictionType.TAG, ContentRestrictionMode.ALLOW_ONLY);
        Set<String> allowedMoods = getValuesForTypeAndMode(restrictions, ContentRestrictionType.MOOD, ContentRestrictionMode.ALLOW_ONLY);
        Set<String> allowedContentRatings = getValuesForTypeAndMode(restrictions, ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.ALLOW_ONLY);

        Integer maxAgeRating = getMaxAgeRating(restrictions);

        return books.stream()
                .filter(book -> {
                    BookContent content = toContent.apply(book);
                    return !hasExcludedContent(content, excludedCategories, excludedTags, excludedMoods, excludedContentRatings)
                            && matchesAllowList(content, allowedCategories, allowedTags, allowedMoods, allowedContentRatings)
                            && isWithinAgeRating(content, maxAgeRating);
                })
                .collect(Collectors.toList());
    }

    /** Normalized view of the content fields the restrictions filter on. */
    private record BookContent(Set<String> categories, Set<String> tags, Set<String> moods,
                               String contentRating, Integer ageRating) {
    }

    private BookContent contentOf(BookEntity book) {
        BookMetadataEntity m = book.getMetadata();
        if (m == null) {
            return new BookContent(Set.of(), Set.of(), Set.of(), null, null);
        }
        return new BookContent(
                names(m.getCategories() == null ? null : m.getCategories().stream().map(c -> c.getName())),
                names(m.getTags() == null ? null : m.getTags().stream().map(t -> t.getName())),
                names(m.getMoods() == null ? null : m.getMoods().stream().map(mo -> mo.getName())),
                m.getContentRating(), m.getAgeRating());
    }

    private BookContent contentOf(Book book) {
        BookMetadata m = book.getMetadata();
        if (m == null) {
            return new BookContent(Set.of(), Set.of(), Set.of(), null, null);
        }
        return new BookContent(
                m.getCategories() == null ? Set.of() : m.getCategories(),
                m.getTags() == null ? Set.of() : m.getTags(),
                m.getMoods() == null ? Set.of() : m.getMoods(),
                m.getContentRating(), m.getAgeRating());
    }

    private Set<String> names(java.util.stream.Stream<String> stream) {
        return stream == null ? Set.of() : stream.collect(Collectors.toSet());
    }

    private Set<String> getValuesForTypeAndMode(List<UserContentRestrictionEntity> restrictions,
                                                 ContentRestrictionType type,
                                                 ContentRestrictionMode mode) {
        return restrictions.stream()
                .filter(r -> r.getRestrictionType() == type && r.getMode() == mode)
                .map(r -> r.getValue().toLowerCase())
                .collect(Collectors.toSet());
    }

    private Integer getMaxAgeRating(List<UserContentRestrictionEntity> restrictions) {
        return restrictions.stream()
                .filter(r -> r.getRestrictionType() == ContentRestrictionType.AGE_RATING)
                .filter(r -> r.getMode() == ContentRestrictionMode.EXCLUDE)
                .map(r -> {
                    try {
                        return Integer.parseInt(r.getValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private boolean hasExcludedContent(BookContent content,
                                       Set<String> excludedCategories,
                                       Set<String> excludedTags,
                                       Set<String> excludedMoods,
                                       Set<String> excludedContentRatings) {
        if (!excludedCategories.isEmpty()
                && content.categories().stream().anyMatch(c -> excludedCategories.contains(c.toLowerCase()))) {
            return true;
        }
        if (!excludedTags.isEmpty()
                && content.tags().stream().anyMatch(t -> excludedTags.contains(t.toLowerCase()))) {
            return true;
        }
        if (!excludedMoods.isEmpty()
                && content.moods().stream().anyMatch(m -> excludedMoods.contains(m.toLowerCase()))) {
            return true;
        }
        return !excludedContentRatings.isEmpty()
                && content.contentRating() != null
                && excludedContentRatings.contains(content.contentRating().toLowerCase());
    }

    private boolean matchesAllowList(BookContent content,
                                     Set<String> allowedCategories,
                                     Set<String> allowedTags,
                                     Set<String> allowedMoods,
                                     Set<String> allowedContentRatings) {
        if (allowedCategories.isEmpty() && allowedTags.isEmpty() && allowedMoods.isEmpty() && allowedContentRatings.isEmpty()) {
            return true;
        }

        if (!allowedCategories.isEmpty()
                && content.categories().stream().noneMatch(c -> allowedCategories.contains(c.toLowerCase()))) {
            return false;
        }
        if (!allowedTags.isEmpty()
                && content.tags().stream().noneMatch(t -> allowedTags.contains(t.toLowerCase()))) {
            return false;
        }
        if (!allowedMoods.isEmpty()
                && content.moods().stream().noneMatch(m -> allowedMoods.contains(m.toLowerCase()))) {
            return false;
        }
        return allowedContentRatings.isEmpty()
                || (content.contentRating() != null && allowedContentRatings.contains(content.contentRating().toLowerCase()));
    }

    private boolean isWithinAgeRating(BookContent content, Integer maxAgeRating) {
        if (maxAgeRating == null || content.ageRating() == null) {
            return true;
        }
        return content.ageRating() < maxAgeRating;
    }

    private ContentRestriction toDto(UserContentRestrictionEntity entity) {
        return ContentRestriction.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .restrictionType(entity.getRestrictionType())
                .mode(entity.getMode())
                .value(entity.getValue())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
