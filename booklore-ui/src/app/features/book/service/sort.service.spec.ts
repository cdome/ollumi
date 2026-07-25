import {describe, expect, it} from 'vitest';

import {SortService} from './sort.service';
import {SortDirection, SortOption} from '../model/sort.model';
import {ReadStatus} from '../model/book.model';
import {makeBook} from '../../../testing/book.fixture';

function sort(field: string, direction = SortDirection.ASCENDING): SortOption {
  return {label: field, field, direction};
}

describe('SortService', () => {
  const service = new SortService();

  describe('applySort', () => {
    it('returns the input unchanged when no sort is selected', () => {
      const books = [makeBook({id: 2}), makeBook({id: 1})];
      expect(service.applySort(books, null)).toBe(books);
    });

    it('does not mutate the input array', () => {
      const books = [
        makeBook({id: 1, metadata: {title: 'B'}}),
        makeBook({id: 2, metadata: {title: 'A'}}),
      ];
      const result = service.applySort(books, sort('title'));
      expect(result).not.toBe(books);
      expect(books.map(b => b.id)).toEqual([1, 2]);
      expect(result.map(b => b.id)).toEqual([2, 1]);
    });
  });

  describe('natural (numeric-aware) string comparison', () => {
    it('orders embedded numbers numerically, not lexically', () => {
      const books = [
        makeBook({id: 1, metadata: {title: 'Chapter 10'}}),
        makeBook({id: 2, metadata: {title: 'Chapter 2'}}),
        makeBook({id: 3, metadata: {title: 'Chapter 1'}}),
      ];
      const result = service.applySort(books, sort('title'));
      expect(result.map(b => b.metadata!.title)).toEqual(['Chapter 1', 'Chapter 2', 'Chapter 10']);
    });

    it('reverses order for descending direction', () => {
      const books = [
        makeBook({id: 1, metadata: {title: 'Chapter 1'}}),
        makeBook({id: 2, metadata: {title: 'Chapter 10'}}),
        makeBook({id: 3, metadata: {title: 'Chapter 2'}}),
      ];
      const result = service.applySort(books, sort('title', SortDirection.DESCENDING));
      expect(result.map(b => b.metadata!.title)).toEqual(['Chapter 10', 'Chapter 2', 'Chapter 1']);
    });
  });

  describe('null handling', () => {
    it('sorts books missing the sort field last when ascending', () => {
      const withTitle = makeBook({id: 1, metadata: {title: 'A'}});
      const noTitle = makeBook({id: 2, metadata: {title: undefined}});

      const asc = service.applySort([noTitle, withTitle], sort('title'));
      expect(asc.map(b => b.id)).toEqual([1, 2]);
    });

    it('moves missing-field books first when descending (the raw null-last order is negated)', () => {
      const withTitle = makeBook({id: 1, metadata: {title: 'A'}});
      const noTitle = makeBook({id: 2, metadata: {title: undefined}});

      const desc = service.applySort([withTitle, noTitle], sort('title', SortDirection.DESCENDING));
      expect(desc.map(b => b.id)).toEqual([2, 1]);
    });
  });

  describe('numeric fields', () => {
    it('compares page counts numerically', () => {
      const books = [
        makeBook({id: 1, metadata: {pageCount: 300}}),
        makeBook({id: 2, metadata: {pageCount: 90}}),
        makeBook({id: 3, metadata: {pageCount: 120}}),
      ];
      const result = service.applySort(books, sort('pageCount'));
      expect(result.map(b => b.id)).toEqual([2, 3, 1]);
    });
  });

  describe('readStatus ranking', () => {
    it('orders by the defined read-status rank, not the enum string', () => {
      const books = [
        makeBook({id: 1, readStatus: ReadStatus.READ}),
        makeBook({id: 2, readStatus: ReadStatus.UNSET}),
        makeBook({id: 3, readStatus: ReadStatus.READING}),
      ];
      const result = service.applySort(books, sort('readStatus'));
      // rank: UNSET(0) < READING(2) < READ(6)
      expect(result.map(b => b.id)).toEqual([2, 3, 1]);
    });
  });

  describe('applyMultiSort', () => {
    it('returns the input unchanged for empty criteria', () => {
      const books = [makeBook({id: 1})];
      expect(service.applyMultiSort(books, [])).toBe(books);
    });

    it('breaks ties with the next criterion', () => {
      const books = [
        makeBook({id: 1, metadata: {seriesName: 'Dune', seriesNumber: 2}}),
        makeBook({id: 2, metadata: {seriesName: 'Dune', seriesNumber: 1}}),
        makeBook({id: 3, metadata: {seriesName: 'Amber', seriesNumber: 5}}),
      ];
      const result = service.applyMultiSort(books, [sort('seriesName'), sort('seriesNumber')]);
      expect(result.map(b => b.id)).toEqual([3, 2, 1]);
    });
  });

  describe('unknown field', () => {
    it('treats an unknown sort field as equal (stable no-op)', () => {
      const books = [makeBook({id: 1}), makeBook({id: 2})];
      const result = service.applySort(books, sort('doesNotExist'));
      expect(result.map(b => b.id)).toEqual([1, 2]);
    });
  });

  describe('author surname sorting', () => {
    it('sorts by "surname, firstname" for authorSurnameVorname', () => {
      const books = [
        makeBook({id: 1, metadata: {authors: ['George Orwell']}}),
        makeBook({id: 2, metadata: {authors: ['Isaac Asimov']}}),
      ];
      const result = service.applySort(books, sort('authorSurnameVorname'));
      // "asimov, isaac" < "orwell, george"
      expect(result.map(b => b.id)).toEqual([2, 1]);
    });
  });
});
