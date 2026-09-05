import {beforeEach, describe, expect, it} from 'vitest';
import {HeaderFilter} from './HeaderFilter';
import {Book} from '../../../model/book.model';
import {BehaviorSubject} from 'rxjs';
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

describe('HeaderFilter', () => {
  let searchTerm$: BehaviorSubject<string>;

  beforeEach(() => {
    searchTerm$ = new BehaviorSubject<string>('');
  });

  describe('short or empty search terms', () => {
    it('should return original state when term is empty', async () => {
      const state = {
        books: [createBook({metadata: {bookId: 1, title: 'Hello'}})],
        loaded: true,
        error: null
      };
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result).toBe(state);
    });

    it('should return original state when term is one character', async () => {
      const state = {
        books: [createBook({metadata: {bookId: 1, title: 'Hello'}})],
        loaded: true,
        error: null
      };
      searchTerm$.next('H');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result).toBe(state);
    });

    it('should return original state when term is only whitespace', async () => {
      const state = {
        books: [createBook({metadata: {bookId: 1, title: 'Hello'}})],
        loaded: true,
        error: null
      };
      searchTerm$.next('   ');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result).toBe(state);
    });
  });

  describe('title search', () => {
    it('should match title substring case-insensitively', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, title: 'The Great Gatsby'}}),
        createBook({id: 2, metadata: {bookId: 2, title: 'Moby Dick'}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('gatsby');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([1]);
    });
  });

  describe('series search', () => {
    it('should match series name', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, seriesName: 'Dune'}}),
        createBook({id: 2, metadata: {bookId: 2, seriesName: 'Foundation'}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('dune');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([1]);
    });
  });

  describe('author search', () => {
    it('should match any author', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, authors: ['F. Scott Fitzgerald']}}),
        createBook({id: 2, metadata: {bookId: 2, authors: ['Herman Melville']}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('melville');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([2]);
    });
  });

  describe('category search', () => {
    it('should match any category', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, categories: ['Classic', 'Drama']}}),
        createBook({id: 2, metadata: {bookId: 2, categories: ['Sci-Fi']}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('sci-fi');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([2]);
    });
  });

  describe('ISBN search', () => {
    it('should match isbn10', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, isbn10: '1234567890'}}),
        createBook({id: 2, metadata: {bookId: 2, isbn10: '0987654321'}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('1234567890');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([1]);
    });

    it('should match isbn13', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, isbn13: '9781234567897'}}),
        createBook({id: 2, metadata: {bookId: 2, isbn13: '9789876543210'}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('9789876543210');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([2]);
    });
  });

  describe('fileName search', () => {
    it('should match primary file name', async () => {
      const books = [
        createBook({id: 1, primaryFile: {id: 1, bookId: 1, fileName: 'great-gatsby.epub'}}),
        createBook({id: 2, primaryFile: {id: 2, bookId: 2, fileName: 'moby-dick.epub'}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('moby');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([2]);
    });
  });

  describe('unicode normalization', () => {
    it('should match accented characters with plain equivalents', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, title: 'Café'}}),
        createBook({id: 2, metadata: {bookId: 2, title: 'Resume'}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('cafe');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([1]);
    });

    it('should normalize special characters like ø, œ, ß', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, title: 'Møssbauer effect'}}),
        createBook({id: 2, metadata: {bookId: 2, title: 'Strasse'}}),
        createBook({id: 3, metadata: {bookId: 3, title: 'Cœur'}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('mossbauer');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([1]);
    });
  });

  describe('punctuation stripping', () => {
    it('should ignore stripped punctuation in search', async () => {
      const books = [
        createBook({id: 1, metadata: {bookId: 1, title: 'Hello! World'}}),
        createBook({id: 2, metadata: {bookId: 2, title: 'Plain'}})
      ];
      const state = {books, loaded: true, error: null};
      searchTerm$.next('hello world');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.map(b => b.id)).toEqual([1]);
    });
  });

  describe('empty/null books', () => {
    it('should return state with null books unchanged', async () => {
      const state = {books: null, loaded: true, error: null};
      searchTerm$.next('test');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books).toBeNull();
    });

    it('should return empty array when no books match', async () => {
      const state = {
        books: [createBook({metadata: {bookId: 1, title: 'A'}})],
        loaded: true,
        error: null
      };
      searchTerm$.next('zzz');
      const filter = new HeaderFilter(searchTerm$);
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books).toEqual([]);
    });
  });

  describe('distinctUntilChanged', () => {
    it('should emit only when term changes', async () => {
      const state = {books: [createBook()], loaded: true, error: null};
      searchTerm$.next('test');
      const filter = new HeaderFilter(searchTerm$);
      const emissions: string[] = [];
      filter.filter(state).subscribe(() => emissions.push(searchTerm$.value));
      searchTerm$.next('test');
      searchTerm$.next('new');
      searchTerm$.next('new');
      // First emission from initial subscribe, then one for 'new'
      expect(emissions).toEqual(['test', 'new']);
    });
  });
});
