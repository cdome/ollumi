import {describe, expect, it} from 'vitest';

import {
  doesBookMatchFilter,
  doesBookMatchReadStatus,
  filterBooksByFilters,
  isFileSizeInRange,
  isMatchScoreInRange,
  isPageCountInRange,
  isRatingInRange,
  isRatingInRange10,
} from './sidebar-filter';
import {ReadStatus} from '../../../model/book.model';
import {makeBook} from '../../../../../testing/book.fixture';

describe('range predicates', () => {
  describe('isRatingInRange (5-star buckets)', () => {
    it('matches a rating inside the [min, max) bucket', () => {
      // bucket id 3 is [3, 4)
      expect(isRatingInRange(3.5, 3)).toBe(true);
      expect(isRatingInRange(4, 3)).toBe(false);
    });

    it('returns false for null/undefined ratings', () => {
      expect(isRatingInRange(null, 3)).toBe(false);
      expect(isRatingInRange(undefined, 3)).toBe(false);
    });

    it('accepts a string range id', () => {
      expect(isRatingInRange(3.5, '3')).toBe(true);
    });

    it('returns false for an unknown range id', () => {
      expect(isRatingInRange(3.5, 99)).toBe(false);
    });
  });

  describe('isRatingInRange10 (exact rounded rating)', () => {
    it('rounds the rating and compares to the id', () => {
      expect(isRatingInRange10(7.4, 7)).toBe(true);
      expect(isRatingInRange10(7.6, 7)).toBe(false);
    });
  });

  describe('isFileSizeInRange', () => {
    it('matches sizes within a bucket (id 1 = 1–10 MB)', () => {
      expect(isFileSizeInRange(2048, 1)).toBe(true);
      expect(isFileSizeInRange(500, 1)).toBe(false);
    });
  });

  describe('isPageCountInRange', () => {
    it('matches page counts within a bucket (id 2 = 100–200)', () => {
      expect(isPageCountInRange(150, 2)).toBe(true);
      expect(isPageCountInRange(200, 2)).toBe(false);
    });
  });

  describe('isMatchScoreInRange', () => {
    it('normalizes a percentage (>1) to a fraction before comparing', () => {
      // id 0 = [0.95, 1.01)
      expect(isMatchScoreInRange(98, 0)).toBe(true);
      expect(isMatchScoreInRange(0.98, 0)).toBe(true);
    });
  });
});

describe('doesBookMatchReadStatus', () => {
  it('treats a missing status as UNSET', () => {
    expect(doesBookMatchReadStatus(makeBook({readStatus: undefined}), [ReadStatus.UNSET])).toBe(true);
    expect(doesBookMatchReadStatus(makeBook({readStatus: undefined}), [ReadStatus.READ])).toBe(false);
  });
});

describe('doesBookMatchFilter', () => {
  const book = makeBook({
    libraryId: 7,
    readStatus: ReadStatus.READING,
    metadata: {authors: ['Orwell', 'Huxley'], categories: ['SciFi'], language: 'en'},
  });

  it('empty filter values match in "or" mode but not "and"', () => {
    expect(doesBookMatchFilter(book, 'author', [], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'author', [], 'and')).toBe(false);
  });

  it('author OR matches when any value is present', () => {
    expect(doesBookMatchFilter(book, 'author', ['Orwell', 'Tolkien'], 'or')).toBe(true);
  });

  it('author AND requires every value to be present', () => {
    expect(doesBookMatchFilter(book, 'author', ['Orwell', 'Huxley'], 'and')).toBe(true);
    expect(doesBookMatchFilter(book, 'author', ['Orwell', 'Tolkien'], 'and')).toBe(false);
  });

  it('"not" mode is evaluated as OR at the predicate level', () => {
    // filterBooksByFilters negates the result; the predicate itself uses OR semantics
    expect(doesBookMatchFilter(book, 'author', ['Orwell'], 'not')).toBe(true);
  });

  it('library uses loose equality so string ids match numeric library ids', () => {
    expect(doesBookMatchFilter(book, 'library', ['7'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'library', [7], 'or')).toBe(true);
  });

  it('language matches by inclusion', () => {
    expect(doesBookMatchFilter(book, 'language', ['en'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'language', ['de'], 'or')).toBe(false);
  });

  it('returns false for an unknown filter type', () => {
    expect(doesBookMatchFilter(book, 'unknownType', ['x'], 'or')).toBe(false);
  });
});

describe('filterBooksByFilters', () => {
  const orwell = makeBook({id: 1, metadata: {authors: ['Orwell'], categories: ['SciFi']}});
  const tolkien = makeBook({id: 2, metadata: {authors: ['Tolkien'], categories: ['Fantasy']}});
  const books = [orwell, tolkien];

  it('returns all books when no filters are active', () => {
    expect(filterBooksByFilters(books, null, 'or')).toBe(books);
    expect(filterBooksByFilters(books, {}, 'or')).toEqual(books);
  });

  it('applies an OR filter across the active set', () => {
    const result = filterBooksByFilters(books, {author: ['Orwell']}, 'or');
    expect(result.map(b => b.id)).toEqual([1]);
  });

  it('AND across filter types requires every type to match', () => {
    const result = filterBooksByFilters(books, {author: ['Orwell'], category: ['SciFi']}, 'and');
    expect(result.map(b => b.id)).toEqual([1]);
    const none = filterBooksByFilters(books, {author: ['Orwell'], category: ['Fantasy']}, 'and');
    expect(none).toEqual([]);
  });

  it('"not" mode excludes books matching any active filter', () => {
    const result = filterBooksByFilters(books, {author: ['Orwell']}, 'not');
    expect(result.map(b => b.id)).toEqual([2]);
  });

  it('excludes a filter type via excludeFilterType', () => {
    // Without the exclusion this would filter to just Orwell; excluded => untouched
    const result = filterBooksByFilters(books, {author: ['Orwell']}, 'or', 'author');
    expect(result).toEqual(books);
  });
});
