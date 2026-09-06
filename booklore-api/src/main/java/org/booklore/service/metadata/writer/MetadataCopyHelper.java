package org.booklore.service.metadata.writer;

import org.booklore.model.entity.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MetadataCopyHelper {

    private final BookMetadataEntity metadata;

    public MetadataCopyHelper(BookMetadataEntity metadata) {
        this.metadata = metadata;
    }

    private boolean isLocked(Boolean lockedFlag) {
        return Boolean.TRUE.equals(lockedFlag);
    }

    public void copyTitle(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getTitleLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getTitle() != null) consumer.accept(metadata.getTitle());
        }
    }

    public void copySubtitle(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getSubtitleLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getSubtitle() != null) consumer.accept(metadata.getSubtitle());
        }
    }

    public void copyPublisher(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getPublisherLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getPublisher() != null) consumer.accept(metadata.getPublisher());
        }
    }

    public void copyPublishedDate(boolean clear, Consumer<LocalDate> consumer) {
        if (!isLocked(metadata.getPublishedDateLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getPublishedDate() != null) consumer.accept(metadata.getPublishedDate());
        }
    }

    public void copyDescription(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getDescriptionLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getDescription() != null) consumer.accept(metadata.getDescription());
        }
    }

    public void copySeriesName(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getSeriesNameLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getSeriesName() != null) consumer.accept(metadata.getSeriesName());
        }
    }

    public void copySeriesNumber(boolean clear, Consumer<Float> consumer) {
        if (!isLocked(metadata.getSeriesNumberLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getSeriesNumber() != null) consumer.accept(metadata.getSeriesNumber());
        }
    }

    public void copySeriesTotal(boolean clear, Consumer<Integer> consumer) {
        if (!isLocked(metadata.getSeriesTotalLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getSeriesTotal() != null) consumer.accept(metadata.getSeriesTotal());
        }
    }

    public void copyIsbn13(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getIsbn13Locked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getIsbn13() != null) consumer.accept(metadata.getIsbn13());
        }
    }

    public void copyIsbn10(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getIsbn10Locked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getIsbn10() != null) consumer.accept(metadata.getIsbn10());
        }
    }

    public void copyAsin(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getAsinLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getAsin() != null) consumer.accept(metadata.getAsin());
        }
    }

    public void copyPageCount(boolean clear, Consumer<Integer> consumer) {
        if (!isLocked(metadata.getPageCountLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getPageCount() != null) consumer.accept(metadata.getPageCount());
        }
    }

    public void copyLanguage(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getLanguageLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getLanguage() != null) consumer.accept(metadata.getLanguage());
        }
    }

    public void copyGoodreadsId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getGoodreadsIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getGoodreadsId() != null) consumer.accept(metadata.getGoodreadsId());
        }
    }

    public void copyComicvineId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getComicvineIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getComicvineId() != null) consumer.accept(metadata.getComicvineId());
        }
    }

    public void copyHardcoverId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getHardcoverIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getHardcoverId() != null) consumer.accept(metadata.getHardcoverId());
        }
    }

    public void copyHardcoverBookId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getHardcoverBookIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getHardcoverBookId() != null) consumer.accept(metadata.getHardcoverBookId());
        }
    }

    public void copyGoogleId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getGoogleIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getGoogleId() != null) consumer.accept(metadata.getGoogleId());
        }
    }



    public void copyLubimyczytacId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getLubimyczytacIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getLubimyczytacId() != null) consumer.accept(metadata.getLubimyczytacId());
        }
    }

    public void copyRanobedbId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getRanobedbIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getRanobedbId() != null) consumer.accept(metadata.getRanobedbId());
        }
    }

    public void copyAuthors(boolean clear, Consumer<Set<String>> consumer) {
        if (!isLocked(metadata.getAuthorsLocked())) {
            if (clear) {
                consumer.accept(Set.of());
            } else if (metadata.getAuthors() != null) {
                Set<String> names = metadata.getAuthors().stream()
                        .map(AuthorEntity::getName)
                        .filter(n -> n != null && !n.isBlank())
                        .collect(Collectors.toSet());
                consumer.accept(names);
            }
        }
    }

    /** Same reasoning as copyMoods: categories is no longer Hibernate-managed, caller resolves via jOOQ. */
    public void copyCategories(boolean clear, Set<String> currentCategoryNames, Consumer<Set<String>> consumer) {
        if (!isLocked(metadata.getCategoriesLocked())) {
            if (clear) {
                consumer.accept(Set.of());
            } else if (currentCategoryNames != null) {
                consumer.accept(currentCategoryNames);
            }
        }
    }

    /**
     * moods is no longer a Hibernate-managed collection on BookMetadataEntity: the caller resolves the
     * current names via JooqBookMetadataRelationsRepository (this class is a plain POJO, not a Spring
     * bean, so it cannot inject the repository itself) and passes them in.
     */
    public void copyMoods(boolean clear, Set<String> currentMoodNames, Consumer<Set<String>> consumer) {
        if (!isLocked(metadata.getMoodsLocked())) {
            if (clear) {
                consumer.accept(Set.of());
            } else if (currentMoodNames != null) {
                consumer.accept(currentMoodNames);
            }
        }
    }

    /** Same reasoning as copyMoods/copyCategories: caller resolves current names via jOOQ. */
    public void copyTags(boolean clear, Set<String> currentTagNames, Consumer<Set<String>> consumer) {
        if (!isLocked(metadata.getTagsLocked())) {
            if (clear) {
                consumer.accept(Set.of());
            } else if (currentTagNames != null) {
                consumer.accept(currentTagNames);
            }
        }
    }

    public void copyRating(boolean clear, Consumer<Double> consumer) {
        if (clear) {
            consumer.accept(null);
        } else if (metadata.getRating() != null) {
            consumer.accept(metadata.getRating());
        }
    }

    public void copyAmazonRating(boolean clear, Consumer<Double> consumer) {
        if (!isLocked(metadata.getAmazonRatingLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getAmazonRating() != null) consumer.accept(metadata.getAmazonRating());
        }
    }

    public void copyGoodreadsRating(boolean clear, Consumer<Double> consumer) {
        if (!isLocked(metadata.getGoodreadsRatingLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getGoodreadsRating() != null) consumer.accept(metadata.getGoodreadsRating());
        }
    }

    public void copyHardcoverRating(boolean clear, Consumer<Double> consumer) {
        if (!isLocked(metadata.getHardcoverRatingLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getHardcoverRating() != null) consumer.accept(metadata.getHardcoverRating());
        }
    }



    public void copyLubimyczytacRating(boolean clear, Consumer<Double> consumer) {
        if (!isLocked(metadata.getLubimyczytacRatingLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getLubimyczytacRating() != null) consumer.accept(metadata.getLubimyczytacRating());
        }
    }

    public void copyRanobedbRating(boolean clear, Consumer<Double> consumer) {
        if (!isLocked(metadata.getRanobedbRatingLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getRanobedbRating() != null) consumer.accept(metadata.getRanobedbRating());
        }
    }


}
