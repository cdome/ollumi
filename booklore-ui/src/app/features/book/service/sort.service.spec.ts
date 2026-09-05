import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SortService} from './sort.service';
import {Book, ReadStatus} from '../model/book.model';
import {SortDirection, SortOption} from '../model/sort.model';

function createBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 1,
    libraryId: 1,
    libraryName: 'Library',
    fileName: 'book.epub',
    filePath: '/books/book.epub',
    ...overrides
  } as Book;
}

describe('SortService', () => {
  let service: SortService;

  beforeEach(() => {
    service = new SortService();
  });

  describe('applySort', () => {
    it('should return the original array when no sort option is provided', () => {
      const books = [createBook({id: 1}), createBook({id: 2})];
      expect(service.applySort(books, null as any)).toBe(books);
    });

    it('should apply a single sort criterion', () => {
      const books = [
        createBook({metadata: {bookId: 1, title: 'B'}}),
        createBook({metadata: {bookId: 2, title: 'A'}})
      ];
      const result = service.applySort(books, {field: 'title', direction: SortDirection.ASCENDING, label: 'Title'});
      expect(result.map(b => b.metadata?.title)).toEqual(['A', 'B']);
    });

    it('should preserve original array', () => {
      const books = [createBook({id: 2}), createBook({id: 1})];
      service.applySort(books, {field: 'fileName', direction: SortDirection.ASCENDING, label: 'File'});
      expect(books[0].id).toBe(2);
    });
  });

  describe('applyMultiSort', () => {
    it('should return original array when sort criteria is empty', () => {
      const books = [createBook({id: 1})];
      expect(service.applyMultiSort(books, [])).toBe(books);
    });

    it('should return original array when sort criteria is null', () => {
      const books = [createBook({id: 1})];
      expect(service.applyMultiSort(books, null as any)).toBe(books);
    });

    it('should apply multiple sort criteria as tie-breakers', () => {
      const books = [
        createBook({metadata: {bookId: 1, authors: ['A'], title: 'B'}}),
        createBook({metadata: {bookId: 2, authors: ['A'], title: 'A'}}),
        createBook({metadata: {bookId: 3, authors: ['B'], title: 'Z'}})
      ];
      const criteria: SortOption[] = [
        {field: 'author', direction: SortDirection.ASCENDING, label: 'Author'},
        {field: 'title', direction: SortDirection.ASCENDING, label: 'Title'}
      ];
      const result = service.applyMultiSort(books, criteria);
      expect(result.map(b => b.metadata?.title)).toEqual(['A', 'B', 'Z']);
    });
  });

  describe('title sort', () => {
    it('should sort titles case-insensitively ascending', () => {
      const books = [
        createBook({metadata: {bookId: 1, title: 'zebra'}}),
        createBook({metadata: {bookId: 2, title: 'Apple'}}),
        createBook({metadata: {bookId: 3, title: 'banana'}})
      ];
      const result = service.applySort(books, {field: 'title', direction: SortDirection.ASCENDING, label: 'Title'});
      expect(result.map(b => b.metadata?.title)).toEqual(['Apple', 'banana', 'zebra']);
    });

    it('should sort titles descending', () => {
      const books = [
        createBook({metadata: {bookId: 1, title: 'A'}}),
        createBook({metadata: {bookId: 2, title: 'B'}})
      ];
      const result = service.applySort(books, {field: 'title', direction: SortDirection.DESCENDING, label: 'Title'});
      expect(result.map(b => b.metadata?.title)).toEqual(['B', 'A']);
    });

    it('should place books with missing titles at the end when ascending', () => {
      const books = [
        createBook({metadata: {bookId: 1, title: 'A'}}),
        createBook({metadata: {bookId: 2}}),
        createBook({metadata: {bookId: 3, title: 'B'}})
      ];
      const result = service.applySort(books, {field: 'title', direction: SortDirection.ASCENDING, label: 'Title'});
      expect(result.map(b => b.metadata?.title)).toEqual(['A', 'B', undefined]);
    });
  });

  describe('natural compare', () => {
    it('should sort filenames with numbers naturally', () => {
      const books = [
        createBook({fileName: 'book 10.epub'}),
        createBook({fileName: 'book 2.epub'}),
        createBook({fileName: 'book 1.epub'})
      ];
      const result = service.applySort(books, {field: 'fileName', direction: SortDirection.ASCENDING, label: 'File'});
      expect(result.map(b => b.fileName)).toEqual(['book 1.epub', 'book 2.epub', 'book 10.epub']);
    });

    it('should handle both null values as equal', () => {
      const books = [
        createBook({fileName: undefined}),
        createBook({fileName: undefined})
      ];
      const result = service.applySort(books, {field: 'fileName', direction: SortDirection.ASCENDING, label: 'File'});
      expect(result[0].fileName).toBeUndefined();
      expect(result[1].fileName).toBeUndefined();
    });

    it('should handle null fileName values', () => {
      const books = [
        createBook({fileName: 'a.epub'}),
        createBook({fileName: null as any}),
        createBook({fileName: 'b.epub'})
      ];
      const result = service.applySort(books, {field: 'fileName', direction: SortDirection.ASCENDING, label: 'File'});
      expect(result.map(b => b.fileName)).toEqual(['a.epub', 'b.epub', null]);
    });
  });

  describe('numeric sorts', () => {
    it('should sort by pageCount ascending', () => {
      const books = [
        createBook({metadata: {bookId: 1, pageCount: 300}}),
        createBook({metadata: {bookId: 2, pageCount: 100}}),
        createBook({metadata: {bookId: 3, pageCount: 200}})
      ];
      const result = service.applySort(books, {field: 'pageCount', direction: SortDirection.ASCENDING, label: 'Pages'});
      expect(result.map(b => b.metadata?.pageCount)).toEqual([100, 200, 300]);
    });

    it('should sort by personalRating descending', () => {
      const books = [
        createBook({personalRating: 3}),
        createBook({personalRating: 5}),
        createBook({personalRating: 1})
      ];
      const result = service.applySort(books, {field: 'personalRating', direction: SortDirection.DESCENDING, label: 'Rating'});
      expect(result.map(b => b.personalRating)).toEqual([5, 3, 1]);
    });

    it('should place null numeric values at the end when ascending', () => {
      const books = [
        createBook({metadata: {bookId: 1, pageCount: 100}}),
        createBook({metadata: {bookId: 2}}),
        createBook({metadata: {bookId: 3, pageCount: 200}})
      ];
      const result = service.applySort(books, {field: 'pageCount', direction: SortDirection.ASCENDING, label: 'Pages'});
      expect(result.map(b => b.metadata?.pageCount)).toEqual([100, 200, undefined]);
    });
  });

  describe('date sorts', () => {
    it('should sort by publishedDate', () => {
      const books = [
        createBook({metadata: {bookId: 1, publishedDate: '2023-01-01'}}),
        createBook({metadata: {bookId: 2, publishedDate: '2021-01-01'}}),
        createBook({metadata: {bookId: 3, publishedDate: '2022-01-01'}})
      ];
      const result = service.applySort(books, {field: 'publishedDate', direction: SortDirection.ASCENDING, label: 'Date'});
      expect(result.map(b => b.metadata?.publishedDate)).toEqual(['2021-01-01', '2022-01-01', '2023-01-01']);
    });

    it('should sort by addedOn', () => {
      const books = [
        createBook({addedOn: '2023-01-01T00:00:00Z'}),
        createBook({addedOn: '2021-01-01T00:00:00Z'})
      ];
      const result = service.applySort(books, {field: 'addedOn', direction: SortDirection.ASCENDING, label: 'Added'});
      expect(result.map(b => b.addedOn)).toEqual(['2021-01-01T00:00:00Z', '2023-01-01T00:00:00Z']);
    });
  });

  describe('author sorts', () => {
    it('should sort by author', () => {
      const books = [
        createBook({metadata: {bookId: 1, authors: ['Zebra']}}),
        createBook({metadata: {bookId: 2, authors: ['Alpha']}})
      ];
      const result = service.applySort(books, {field: 'author', direction: SortDirection.ASCENDING, label: 'Author'});
      expect(result.map(b => b.metadata?.authors?.[0])).toEqual(['Alpha', 'Zebra']);
    });

    it('should sort by author surname', () => {
      const books = [
        createBook({metadata: {bookId: 1, authors: ['John Smith']}}),
        createBook({metadata: {bookId: 2, authors: ['Alice Brown']}})
      ];
      const result = service.applySort(books, {field: 'authorSurnameVorname', direction: SortDirection.ASCENDING, label: 'Author Surname'});
      expect(result.map(b => b.metadata?.authors?.[0])).toEqual(['Alice Brown', 'John Smith']);
    });

    it('should keep single-name authors in lowercase', () => {
      const books = [
        createBook({metadata: {bookId: 1, authors: ['Plato']}}),
        createBook({metadata: {bookId: 2, authors: ['Aristotle']}})
      ];
      const result = service.applySort(books, {field: 'authorSurnameVorname', direction: SortDirection.ASCENDING, label: 'Author Surname'});
      expect(result.map(b => b.metadata?.authors?.[0])).toEqual(['Aristotle', 'Plato']);
    });
  });

  describe('readStatus sort', () => {
    it('should sort by readStatus rank ascending', () => {
      const books = [
        createBook({readStatus: ReadStatus.READ}),
        createBook({readStatus: ReadStatus.UNREAD}),
        createBook({readStatus: ReadStatus.READING})
      ];
      const result = service.applySort(books, {field: 'readStatus', direction: SortDirection.ASCENDING, label: 'Status'});
      expect(result.map(b => b.readStatus)).toEqual([ReadStatus.UNREAD, ReadStatus.READING, ReadStatus.READ]);
    });

    it('should handle unknown readStatus as null', () => {
      const books = [
        createBook({readStatus: 'UNKNOWN' as ReadStatus}),
        createBook({readStatus: ReadStatus.UNREAD})
      ];
      const result = service.applySort(books, {field: 'readStatus', direction: SortDirection.ASCENDING, label: 'Status'});
      expect(result[0].readStatus).toBe(ReadStatus.UNREAD);
      expect(result[1].readStatus).toBe('UNKNOWN');
    });
  });

  describe('readingProgress sort', () => {
    it('should pick first available progress percentage', () => {
      const books = [
        createBook({epubProgress: {cfi: '', percentage: 10}}),
        createBook({pdfProgress: {page: 1, percentage: 50}}),
        createBook({cbxProgress: {page: 1, percentage: 30}})
      ];
      const result = service.applySort(books, {field: 'readingProgress', direction: SortDirection.ASCENDING, label: 'Progress'});
      expect(result.map(b => b.epubProgress?.percentage ?? b.pdfProgress?.percentage ?? b.cbxProgress?.percentage)).toEqual([10, 30, 50]);
    });
  });

  describe('series sorts', () => {
    it('should sort by seriesName', () => {
      const books = [
        createBook({metadata: {bookId: 1, seriesName: 'Z'}}),
        createBook({metadata: {bookId: 2, seriesName: 'A'}})
      ];
      const result = service.applySort(books, {field: 'seriesName', direction: SortDirection.ASCENDING, label: 'Series'});
      expect(result.map(b => b.metadata?.seriesName)).toEqual(['A', 'Z']);
    });

    it('should sort by seriesNumber', () => {
      const books = [
        createBook({metadata: {bookId: 1, seriesNumber: 3}}),
        createBook({metadata: {bookId: 2, seriesNumber: 1}}),
        createBook({metadata: {bookId: 3, seriesNumber: 2}})
      ];
      const result = service.applySort(books, {field: 'seriesNumber', direction: SortDirection.ASCENDING, label: 'Series #'});
      expect(result.map(b => b.metadata?.seriesNumber)).toEqual([1, 2, 3]);
    });
  });

  describe('bookType sort', () => {
    it('should sort by bookType', () => {
      const books = [
        createBook({primaryFile: {id: 1, bookId: 1, bookType: 'PDF'}}),
        createBook({primaryFile: {id: 2, bookId: 2, bookType: 'EPUB'}})
      ];
      const result = service.applySort(books, {field: 'bookType', direction: SortDirection.ASCENDING, label: 'Type'});
      expect(result.map(b => b.primaryFile?.bookType)).toEqual(['EPUB', 'PDF']);
    });
  });

  describe('narrator sort', () => {
    it('should sort by narrator case-insensitively', () => {
      const books = [
        createBook({metadata: {bookId: 1, narrator: 'Zebra'}}),
        createBook({metadata: {bookId: 2, narrator: 'Alpha'}})
      ];
      const result = service.applySort(books, {field: 'narrator', direction: SortDirection.ASCENDING, label: 'Narrator'});
      expect(result.map(b => b.metadata?.narrator)).toEqual(['Alpha', 'Zebra']);
    });
  });

  describe('locked sort', () => {
    it('should extract locked status (true vs false) but current comparison treats booleans as equal', () => {
      const books = [
        createBook({metadata: {bookId: 1, allMetadataLocked: true}}),
        createBook({metadata: {bookId: 2, allMetadataLocked: false}})
      ];
      const result = service.applySort(books, {field: 'locked', direction: SortDirection.ASCENDING, label: 'Locked'});
      expect(result.map(b => b.metadata?.allMetadataLocked)).toEqual([true, false]);
    });

    it('should treat missing allMetadataLocked as false via nullish coalescing', () => {
      const books = [
        createBook({metadata: {bookId: 1, allMetadataLocked: true}}),
        createBook({metadata: {bookId: 2}})
      ];
      const result = service.applySort(books, {field: 'locked', direction: SortDirection.ASCENDING, label: 'Locked'});
      expect(result.map(b => b.metadata?.allMetadataLocked)).toEqual([true, undefined]);
    });
  });

  describe('unknown field', () => {
    it('should warn and return 0 for unknown fields', () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const books = [createBook({id: 1}), createBook({id: 2})];
      const result = service.applySort(books, {field: 'unknownField', direction: SortDirection.ASCENDING, label: 'Unknown'});
      expect(result.map(b => b.id)).toEqual([1, 2]);
      expect(warnSpy).toHaveBeenCalledWith('[SortService] No extractor for field: unknownField');
      warnSpy.mockRestore();
    });
  });

  describe('empty arrays', () => {
    it('should return empty array when sorting empty input', () => {
      expect(service.applySort([], {field: 'title', direction: SortDirection.ASCENDING, label: 'Title'})).toEqual([]);
    });
  });
});
