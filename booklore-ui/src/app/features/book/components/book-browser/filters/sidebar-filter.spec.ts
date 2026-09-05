import {beforeEach, describe, expect, it} from 'vitest';
import {
  doesBookMatchFilter,
  doesBookMatchReadStatus,
  filterBooksByFilters,
  isFileSizeInRange,
  isMatchScoreInRange,
  isPageCountInRange,
  isRatingInRange,
  isRatingInRange10,
  SideBarFilter
} from './sidebar-filter';
import {Book, ReadStatus} from '../../../model/book.model';
import {BookFilterMode} from '../../../../settings/user-management/user.service';
import {of} from 'rxjs';
import {first} from 'rxjs/operators';

function createBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 1,
    libraryId: 1,
    libraryName: 'Library',
    fileName: 'book.epub',
    filePath: '/books/book.epub',
    metadata: {bookId: 1},
    ...overrides
  } as Book;
}

describe('sidebar-filter', () => {
  describe('isRatingInRange', () => {
    it('should return false for null/undefined rating', () => {
      expect(isRatingInRange(null, 0)).toBe(false);
      expect(isRatingInRange(undefined, 0)).toBe(false);
    });

    it('should return false for unknown range id', () => {
      expect(isRatingInRange(3, 999)).toBe(false);
    });

    it('should check range [min, max) for 5-star ratings', () => {
      expect(isRatingInRange(0.5, 0)).toBe(true);
      expect(isRatingInRange(1, 0)).toBe(false);
      expect(isRatingInRange(4.7, 5)).toBe(true);
      expect(isRatingInRange(4.5, 5)).toBe(true);
      expect(isRatingInRange(4.4, 5)).toBe(false);
    });

    it('should accept string range id', () => {
      expect(isRatingInRange(2.5, '2')).toBe(true);
    });
  });

  describe('isRatingInRange10', () => {
    it('should return false for null/undefined rating', () => {
      expect(isRatingInRange10(null, 5)).toBe(false);
      expect(isRatingInRange10(undefined, 5)).toBe(false);
    });

    it('should match rounded rating to exact id', () => {
      expect(isRatingInRange10(5, 5)).toBe(true);
      expect(isRatingInRange10(5.4, 5)).toBe(true);
      expect(isRatingInRange10(5.6, 6)).toBe(true);
      expect(isRatingInRange10(10, 10)).toBe(true);
    });

    it('should accept string range id', () => {
      expect(isRatingInRange10(7, '7')).toBe(true);
    });
  });

  describe('isFileSizeInRange', () => {
    it('should return false for undefined size', () => {
      expect(isFileSizeInRange(undefined, 0)).toBe(false);
    });

    it('should match file size ranges', () => {
      expect(isFileSizeInRange(500, 0)).toBe(true);
      expect(isFileSizeInRange(1024, 1)).toBe(true);
      expect(isFileSizeInRange(10240, 2)).toBe(true);
      expect(isFileSizeInRange(5242880, 7)).toBe(true);
    });
  });

  describe('isPageCountInRange', () => {
    it('should return false for undefined page count', () => {
      expect(isPageCountInRange(undefined, 0)).toBe(false);
    });

    it('should match page count ranges', () => {
      expect(isPageCountInRange(25, 0)).toBe(true);
      expect(isPageCountInRange(50, 1)).toBe(true);
      expect(isPageCountInRange(1000, 6)).toBe(true);
    });
  });

  describe('isMatchScoreInRange', () => {
    it('should return false for null/undefined score', () => {
      expect(isMatchScoreInRange(null, 0)).toBe(false);
      expect(isMatchScoreInRange(undefined, 0)).toBe(false);
    });

    it('should normalize scores above 1 to 0-1 range', () => {
      expect(isMatchScoreInRange(97, 0)).toBe(true);
      expect(isMatchScoreInRange(85, 2)).toBe(true);
    });

    it('should handle 0-1 scores directly', () => {
      expect(isMatchScoreInRange(0.97, 0)).toBe(true);
      expect(isMatchScoreInRange(0.85, 2)).toBe(true);
    });
  });

  describe('doesBookMatchReadStatus', () => {
    it('should match when selected includes book status', () => {
      const book = createBook({readStatus: ReadStatus.READ});
      expect(doesBookMatchReadStatus(book, [ReadStatus.READ, ReadStatus.READING])).toBe(true);
    });

    it('should default missing status to UNSET', () => {
      const book = createBook({});
      expect(doesBookMatchReadStatus(book, [ReadStatus.UNSET])).toBe(true);
    });

    it('should return false when status not selected', () => {
      const book = createBook({readStatus: ReadStatus.UNREAD});
      expect(doesBookMatchReadStatus(book, [ReadStatus.READ])).toBe(false);
    });
  });

  describe('doesBookMatchFilter', () => {
    it('should return mode === or for empty filter values', () => {
      const book = createBook();
      expect(doesBookMatchFilter(book, 'author', [], 'and')).toBe(false);
      expect(doesBookMatchFilter(book, 'author', [], 'or')).toBe(true);
      expect(doesBookMatchFilter(book, 'author', null as any, 'and')).toBe(false);
    });

    describe('author filter', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Alice', 'Bob']}});

      it('should match any author in or mode', () => {
        expect(doesBookMatchFilter(book, 'author', ['Alice'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'author', ['Charlie'], 'or')).toBe(false);
      });

      it('should match all authors in and mode', () => {
        expect(doesBookMatchFilter(book, 'author', ['Alice', 'Bob'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'author', ['Alice', 'Charlie'], 'and')).toBe(false);
      });

      it('should convert not mode to or', () => {
        expect(doesBookMatchFilter(book, 'author', ['Alice'], 'not')).toBe(true);
      });
    });

    describe('category filter', () => {
      const book = createBook({metadata: {bookId: 1, categories: ['Fiction', 'Sci-Fi']}});

      it('should match any category in or mode', () => {
        expect(doesBookMatchFilter(book, 'category', ['Fiction'], 'or')).toBe(true);
      });

      it('should require all categories in and mode', () => {
        expect(doesBookMatchFilter(book, 'category', ['Fiction', 'Sci-Fi'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'category', ['Fiction', 'Romance'], 'and')).toBe(false);
      });
    });

    describe('series filter', () => {
      const book = createBook({metadata: {bookId: 1, seriesName: '  Dune  '}});

      it('should trim series name and match', () => {
        expect(doesBookMatchFilter(book, 'series', ['Dune'], 'or')).toBe(true);
      });

      it('should require all series in and mode', () => {
        expect(doesBookMatchFilter(book, 'series', ['Dune', 'Other'], 'and')).toBe(false);
      });
    });

    describe('bookType filter', () => {
      it('should match primary file book type', () => {
        const book = createBook({primaryFile: {id: 1, bookId: 1, bookType: 'PDF'}});
        expect(doesBookMatchFilter(book, 'bookType', ['PDF'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'bookType', ['EPUB'], 'and')).toBe(false);
      });

      it('should match physical books', () => {
        const book = createBook({isPhysical: true, primaryFile: {id: 1, bookId: 1, bookType: 'EPUB'}});
        expect(doesBookMatchFilter(book, 'bookType', ['PHYSICAL'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'bookType', ['EPUB'], 'and')).toBe(false);
      });
    });

    describe('readStatus filter', () => {
      it('should match selected read status', () => {
        const book = createBook({readStatus: ReadStatus.READING});
        expect(doesBookMatchFilter(book, 'readStatus', [ReadStatus.READING], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'readStatus', [ReadStatus.READ], 'and')).toBe(false);
      });
    });

    describe('personalRating filter', () => {
      const book = createBook({personalRating: 7});

      it('should match rounded personal rating', () => {
        expect(doesBookMatchFilter(book, 'personalRating', [7], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'personalRating', [5], 'and')).toBe(false);
      });
    });

    describe('publisher filter', () => {
      const book = createBook({metadata: {bookId: 1, publisher: 'Penguin'}});

      it('should match publisher', () => {
        expect(doesBookMatchFilter(book, 'publisher', ['Penguin'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'publisher', ['Random House'], 'or')).toBe(false);
      });
    });

    describe('matchScore filter', () => {
      const book = createBook({metadataMatchScore: 0.97});

      it('should match match score range', () => {
        expect(doesBookMatchFilter(book, 'matchScore', [0], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'matchScore', [1], 'and')).toBe(false);
      });
    });

    describe('library filter', () => {
      const book = createBook({libraryId: 5});

      it('should match library id with loose equality', () => {
        expect(doesBookMatchFilter(book, 'library', [5], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'library', ['5'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'library', [6], 'or')).toBe(false);
      });
    });

    describe('shelf filter', () => {
      const book = createBook({shelves: [{id: 1, name: 'Favorites'}, {id: 2, name: 'TBR'}]});

      it('should match any shelf with loose equality', () => {
        expect(doesBookMatchFilter(book, 'shelf', [1], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'shelf', ['2'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'shelf', [3], 'or')).toBe(false);
      });

      it('should require all shelves in and mode', () => {
        expect(doesBookMatchFilter(book, 'shelf', [1, 2], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'shelf', [1, 3], 'and')).toBe(false);
      });
    });

    describe('shelfStatus filter', () => {
      it('should match shelved/unshelved status', () => {
        const shelved = createBook({shelves: [{id: 1, name: 'Favorites'}]});
        const unshelved = createBook({shelves: []});
        expect(doesBookMatchFilter(shelved, 'shelfStatus', ['shelved'], 'and')).toBe(true);
        expect(doesBookMatchFilter(unshelved, 'shelfStatus', ['unshelved'], 'and')).toBe(true);
        expect(doesBookMatchFilter(shelved, 'shelfStatus', ['unshelved'], 'and')).toBe(false);
      });
    });

    describe('tag filter', () => {
      const book = createBook({metadata: {bookId: 1, tags: ['classic', 'bestseller']}});

      it('should match tags', () => {
        expect(doesBookMatchFilter(book, 'tag', ['classic'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'tag', ['classic', 'bestseller'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'tag', ['scifi'], 'or')).toBe(false);
      });
    });

    describe('publishedDate filter', () => {
      it('should match year', () => {
        const book = createBook({metadata: {bookId: 1, publishedDate: '2021-06-15'}});
        expect(doesBookMatchFilter(book, 'publishedDate', [2021], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'publishedDate', ['2021'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'publishedDate', [2022], 'and')).toBe(false);
      });

      it('should return false when no published date', () => {
        const book = createBook();
        expect(doesBookMatchFilter(book, 'publishedDate', [2021], 'and')).toBe(false);
      });
    });

    describe('fileSize filter', () => {
      const book = createBook({fileSizeKb: 5000});

      it('should match file size range', () => {
        expect(doesBookMatchFilter(book, 'fileSize', [1], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'fileSize', [0], 'and')).toBe(false);
      });
    });

    describe('amazon/goodreads/hardcover rating filters', () => {
      const book = createBook({metadata: {bookId: 1, amazonRating: 4.7, goodreadsRating: 3.2, hardcoverRating: 2.1}});

      it('should match rating ranges', () => {
        expect(doesBookMatchFilter(book, 'amazonRating', [5], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'goodreadsRating', [3], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'hardcoverRating', [2], 'and')).toBe(true);
      });
    });

    describe('language filter', () => {
      const book = createBook({metadata: {bookId: 1, language: 'en'}});

      it('should match language', () => {
        expect(doesBookMatchFilter(book, 'language', ['en'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'language', ['fr'], 'and')).toBe(false);
      });
    });

    describe('pageCount filter', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 150}});

      it('should match page count range', () => {
        expect(doesBookMatchFilter(book, 'pageCount', [2], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'pageCount', [0], 'and')).toBe(false);
      });
    });

    describe('mood filter', () => {
      const book = createBook({metadata: {bookId: 1, moods: ['dark', 'epic']}});

      it('should match moods', () => {
        expect(doesBookMatchFilter(book, 'mood', ['dark'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'mood', ['dark', 'epic'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'mood', ['romantic'], 'or')).toBe(false);
      });
    });

    describe('ageRating filter', () => {
      const book = createBook({metadata: {bookId: 1, ageRating: 13}});

      it('should match exact age rating', () => {
        expect(doesBookMatchFilter(book, 'ageRating', [13], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'ageRating', ['13'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'ageRating', [18], 'and')).toBe(false);
      });
    });

    describe('contentRating filter', () => {
      const book = createBook({metadata: {bookId: 1, contentRating: 'MATURE'}});

      it('should match content rating', () => {
        expect(doesBookMatchFilter(book, 'contentRating', ['MATURE'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'contentRating', ['TEEN'], 'and')).toBe(false);
      });
    });

    describe('narrator filter', () => {
      const book = createBook({metadata: {bookId: 1, narrator: 'Jane Doe'}});

      it('should match narrator', () => {
        expect(doesBookMatchFilter(book, 'narrator', ['Jane Doe'], 'and')).toBe(true);
        expect(doesBookMatchFilter(book, 'narrator', ['John Smith'], 'and')).toBe(false);
      });
    });

    describe('comic filters', () => {
      const book = createBook({
        metadata: {
          bookId: 1,
          comicMetadata: {
            characters: ['Batman', 'Superman'],
            teams: ['Justice League'],
            locations: ['Gotham'],
            pencillers: ['Jim Lee']
          }
        }
      });

      it('should match comic characters', () => {
        expect(doesBookMatchFilter(book, 'comicCharacter', ['Batman'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'comicCharacter', ['Batman', 'Wonder Woman'], 'and')).toBe(false);
      });

      it('should match comic teams', () => {
        expect(doesBookMatchFilter(book, 'comicTeam', ['Justice League'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'comicTeam', ['Avengers'], 'or')).toBe(false);
      });

      it('should match comic locations', () => {
        expect(doesBookMatchFilter(book, 'comicLocation', ['Gotham'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'comicLocation', ['Metropolis'], 'or')).toBe(false);
      });

      it('should match comic creators with role suffix', () => {
        expect(doesBookMatchFilter(book, 'comicCreator', ['Jim Lee:penciller'], 'or')).toBe(true);
        expect(doesBookMatchFilter(book, 'comicCreator', ['Jim Lee:inker'], 'or')).toBe(false);
      });

      it('should return false for comic filters when no comic metadata', () => {
        const noComic = createBook();
        expect(doesBookMatchFilter(noComic, 'comicCharacter', ['Batman'], 'or')).toBe(false);
        expect(doesBookMatchFilter(noComic, 'comicCreator', ['Jim Lee:penciller'], 'or')).toBe(false);
      });
    });

    describe('default case', () => {
      it('should return false for unknown filter type', () => {
        const book = createBook();
        expect(doesBookMatchFilter(book, 'unknownType', ['value'], 'and')).toBe(false);
      });
    });
  });

  describe('filterBooksByFilters', () => {
    const books = [
      createBook({id: 1, metadata: {bookId: 1, authors: ['Alice'], title: 'A'}}),
      createBook({id: 2, metadata: {bookId: 2, authors: ['Bob'], title: 'B'}}),
      createBook({id: 3, metadata: {bookId: 3, authors: ['Alice'], title: 'C'}})
    ];

    it('should return original books when no active filters', () => {
      expect(filterBooksByFilters(books, null, 'and')).toBe(books);
    });

    it('should return original books when all filters are excluded', () => {
      const filters = {author: ['Alice']};
      expect(filterBooksByFilters(books, filters, 'and', 'author')).toBe(books);
    });

    it('should apply and mode', () => {
      const filters = {author: ['Alice'], title: 'not a real field but uses default'.split(' ')};
      // title is unknown -> false, so and mode fails for all
      const result = filterBooksByFilters(books, filters, 'and');
      expect(result).toEqual([]);
    });

    it('should apply or mode', () => {
      const filters = {author: ['Alice'], category: ['Fiction']};
      const result = filterBooksByFilters(books, filters, 'or');
      expect(result.map(b => b.id)).toEqual([1, 3]);
    });

    it('should apply not mode', () => {
      const filters = {author: ['Alice']};
      const result = filterBooksByFilters(books, filters, 'not');
      expect(result.map(b => b.id)).toEqual([2]);
    });
  });

  describe('SideBarFilter', () => {
    let filter: SideBarFilter;
    let filters$: { next: (value: unknown) => void };
    let mode$: { next: (value: BookFilterMode) => void };

    beforeEach(() => {
      filters$ = {next: () => {}};
      mode$ = {next: () => {}};
      filter = new SideBarFilter(of(filters$), of(mode$));
    });

    it('should return bookState when books is null', async () => {
      const state = {books: null, loaded: true, error: null};
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result).toBe(state);
    });

    it('should return bookState when filters is falsy', async () => {
      const state = {books: [createBook()], loaded: true, error: null};
      filter = new SideBarFilter(of(null), of('and'));
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result).toBe(state);
    });

    it('should filter books and return new state', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, authors: ['Alice']}}),
        createBook({id: 2, metadata: {bookId: 2, authors: ['Bob']}})
      ];
      const state = {books, loaded: true, error: null};
      filter = new SideBarFilter(of({author: ['Alice']}), of('and'));
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([1]);
      expect(result).not.toBe(state);
    });
  });
});
