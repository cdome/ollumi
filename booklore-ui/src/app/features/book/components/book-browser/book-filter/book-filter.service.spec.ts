import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject, firstValueFrom, of} from 'rxjs';
import {BookFilterService} from './book-filter.service';
import {BookService} from '../../../service/book.service';
import {LibraryService} from '../../../service/library.service';
import {BookRuleEvaluatorService} from '../../../../magic-shelf/service/book-rule-evaluator.service';
import {EntityType} from '../book-browser.component';
import {Book, BookType, ReadStatus} from '../../../model/book.model';
import {Library} from '../../../model/library.model';
import {Shelf} from '../../../model/shelf.model';
import {MagicShelf} from '../../../../magic-shelf/service/magic-shelf.service';
import {BookState} from '../../../model/state/book-state.model';
import {LibraryState} from '../../../model/state/library-state.model';
import {BookFilterMode} from '../../../../settings/user-management/user.service';

describe('BookFilterService', () => {
  let service: BookFilterService;
  let bookStateSubject: BehaviorSubject<BookState>;
  let libraryStateSubject: BehaviorSubject<LibraryState>;
  let evaluateGroupSpy: ReturnType<typeof vi.fn>;

  function createBook(overrides: Partial<Book> = {}): Book {
    return {
      id: 1,
      libraryId: 1,
      libraryName: 'Fiction Library',
      metadata: {bookId: 1},
      ...overrides
    } as Book;
  }

  const libraryA: Library = {id: 1, name: 'Fiction Library', watch: false, paths: []};
  const libraryB: Library = {id: 2, name: 'History Library', watch: false, paths: []};

  beforeEach(() => {
    bookStateSubject = new BehaviorSubject<BookState>({
      books: [],
      loaded: true,
      error: null
    });
    libraryStateSubject = new BehaviorSubject<LibraryState>({
      libraries: [libraryA, libraryB],
      loaded: true,
      error: null
    });
    evaluateGroupSpy = vi.fn().mockReturnValue(false);

    TestBed.configureTestingModule({
      providers: [
        BookFilterService,
        {provide: BookService, useValue: {bookState$: bookStateSubject.asObservable()}},
        {provide: LibraryService, useValue: {libraryState$: libraryStateSubject.asObservable()}},
        {provide: BookRuleEvaluatorService, useValue: {evaluateGroup: evaluateGroupSpy}}
      ]
    });

    service = TestBed.inject(BookFilterService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('processFilterValue', () => {
    it('should convert numeric ID filter string values to numbers', () => {
      expect(service.processFilterValue('library', '42')).toBe(42);
    });

    it('should leave non-string values unchanged for numeric ID filters', () => {
      expect(service.processFilterValue('library', 7)).toBe(7);
    });

    it('should leave non-numeric filter values unchanged', () => {
      expect(service.processFilterValue('author', 'Author Name')).toBe('Author Name');
    });
  });

  describe('isNumericFilter', () => {
    it('should return true for numeric ID filter types', () => {
      expect(service.isNumericFilter('library')).toBe(true);
      expect(service.isNumericFilter('shelf')).toBe(true);
      expect(service.isNumericFilter('personalRating')).toBe(true);
    });

    it('should return false for non-numeric filter types', () => {
      expect(service.isNumericFilter('author')).toBe(false);
      expect(service.isNumericFilter('category')).toBe(false);
      expect(service.isNumericFilter('readStatus')).toBe(false);
    });
  });

  describe('filterBooksByEntity', () => {
    const books: Book[] = [
      createBook({id: 1, libraryId: 1}),
      createBook({id: 2, libraryId: 2}),
      createBook({id: 3, libraryId: 2, shelves: [{id: 5, name: 'Shelf A'} as Shelf]})
    ];

    it('should return all books when entity is null', () => {
      expect(service.filterBooksByEntity(books, null, EntityType.LIBRARY)).toEqual(books);
    });

    it('should filter books by library', () => {
      const result = service.filterBooksByEntity(books, libraryA, EntityType.LIBRARY);
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe(1);
    });

    it('should filter books by shelf membership', () => {
      const shelf: Shelf = {id: 5, name: 'Shelf A'};
      const result = service.filterBooksByEntity(books, shelf, EntityType.SHELF);
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe(3);
    });

    it('should filter books by magic shelf rules', () => {
      evaluateGroupSpy.mockReturnValue(true);
      const magicShelf: MagicShelf = {
        id: 1,
        name: 'Magic Shelf',
        filterJson: JSON.stringify({join: 'and', rules: []})
      };

      const result = service.filterBooksByEntity(books, magicShelf, EntityType.MAGIC_SHELF);

      expect(result).toEqual(books);
      expect(evaluateGroupSpy).toHaveBeenCalled();
    });

    it('should return all books for unknown entity type', () => {
      expect(service.filterBooksByEntity(books, null, 'UNKNOWN' as EntityType)).toEqual(books);
    });
  });

  describe('createFilterStreams', () => {
    it('should build author filters from book metadata', async () => {
      bookStateSubject.next({
        books: [
          createBook({id: 1, metadata: {bookId: 1, authors: ['Author A']}}),
          createBook({id: 2, metadata: {bookId: 2, authors: ['Author B']}}),
          createBook({id: 3, metadata: {bookId: 3, authors: ['Author A', 'Author B']}})
        ],
        loaded: true,
        error: null
      });

      const streams = service.createFilterStreams(
        of(null),
        of(EntityType.ALL_BOOKS),
        of(null),
        of('and' as BookFilterMode)
      );

      const authorFilters = await firstValueFrom(streams.author);
      expect(authorFilters).toHaveLength(2);
      const counts = new Map(authorFilters.map(f => [f.value.name, f.bookCount]));
      expect(counts.get('Author A')).toBe(2);
      expect(counts.get('Author B')).toBe(2);
    });

    it('should build library filters with names from library state', async () => {
      bookStateSubject.next({
        books: [
          createBook({id: 1, libraryId: 1}),
          createBook({id: 2, libraryId: 2}),
          createBook({id: 3, libraryId: 2})
        ],
        loaded: true,
        error: null
      });

      const streams = service.createFilterStreams(
        of(null),
        of(EntityType.ALL_BOOKS),
        of(null),
        of('and' as BookFilterMode)
      );

      const libraryFilters = await firstValueFrom(streams.library);
      expect(libraryFilters).toHaveLength(2);

      const names = libraryFilters.map(f => f.value.name);
      expect(names).toContain('Fiction Library');
      expect(names).toContain('History Library');

      const historyFilter = libraryFilters.find(f => f.value.name === 'History Library');
      expect(historyFilter?.bookCount).toBe(2);
    });

    it('should use a fallback name for unknown libraries', async () => {
      libraryStateSubject.next({
        libraries: [libraryA],
        loaded: true,
        error: null
      });
      bookStateSubject.next({
        books: [createBook({id: 1, libraryId: 99})],
        loaded: true,
        error: null
      });

      const streams = service.createFilterStreams(
        of(null),
        of(EntityType.ALL_BOOKS),
        of(null),
        of('and' as BookFilterMode)
      );

      const libraryFilters = await firstValueFrom(streams.library);
      expect(libraryFilters[0].value.name).toBe('Library 99');
    });

    it('should cascade active filters so unrelated filter options narrow accordingly', async () => {
      bookStateSubject.next({
        books: [
          createBook({id: 1, libraryId: 1, metadata: {bookId: 1, authors: ['Author A'], tags: ['Tag 1']}}),
          createBook({id: 2, libraryId: 2, metadata: {bookId: 2, authors: ['Author B'], tags: ['Tag 2']}})
        ],
        loaded: true,
        error: null
      });

      const activeFilters$ = of({tag: ['Tag 1']} as Record<string, unknown[]>);
      const streams = service.createFilterStreams(
        of(null),
        of(EntityType.ALL_BOOKS),
        activeFilters$,
        of('and' as BookFilterMode)
      );

      const authorFilters = await firstValueFrom(streams.author);
      expect(authorFilters).toHaveLength(1);
      expect(authorFilters[0].value.name).toBe('Author A');
    });

    it('should narrow library filters when an author filter is active', async () => {
      bookStateSubject.next({
        books: [
          createBook({id: 1, libraryId: 1, metadata: {bookId: 1, authors: ['Author A']}}),
          createBook({id: 2, libraryId: 2, metadata: {bookId: 2, authors: ['Author B']}})
        ],
        loaded: true,
        error: null
      });

      const activeFilters$ = of({author: ['Author A']} as Record<string, unknown[]>);
      const streams = service.createFilterStreams(
        of(null),
        of(EntityType.ALL_BOOKS),
        activeFilters$,
        of('and' as BookFilterMode)
      );

      const libraryFilters = await firstValueFrom(streams.library);
      expect(libraryFilters).toHaveLength(1);
      expect(libraryFilters[0].value.id).toBe(1);
    });

    it('should include only read status options present in the books', async () => {
      bookStateSubject.next({
        books: [
          createBook({id: 1, readStatus: ReadStatus.READ}),
          createBook({id: 2, readStatus: ReadStatus.READING}),
          createBook({id: 3})
        ],
        loaded: true,
        error: null
      });

      const streams = service.createFilterStreams(
        of(null),
        of(EntityType.ALL_BOOKS),
        of(null),
        of('and' as BookFilterMode)
      );

      const readStatusFilters = await firstValueFrom(streams.readStatus);
      const statuses = readStatusFilters.map(f => f.value.id);
      expect(statuses).toContain(ReadStatus.READ);
      expect(statuses).toContain(ReadStatus.READING);
      expect(statuses).toContain(ReadStatus.UNSET);
    });

    it('should return empty filters for a magic shelf with invalid filter JSON', async () => {
      const magicShelf = {id: 1, name: 'Broken Shelf', filterJson: 'not-valid-json'};
      bookStateSubject.next({
        books: [createBook({id: 1})],
        loaded: true,
        error: null
      });

      const result = service.filterBooksByEntity(
        bookStateSubject.value.books!,
        magicShelf,
        EntityType.MAGIC_SHELF
      );

      expect(result).toEqual([]);
    });

    it('should filter by entity before building filters', async () => {
      bookStateSubject.next({
        books: [
          createBook({id: 1, libraryId: 1, metadata: {bookId: 1, authors: ['Author A']}}),
          createBook({id: 2, libraryId: 2, metadata: {bookId: 2, authors: ['Author B']}})
        ],
        loaded: true,
        error: null
      });

      const streams = service.createFilterStreams(
        of(libraryA),
        of(EntityType.LIBRARY),
        of(null),
        of('and' as BookFilterMode)
      );

      const authorFilters = await firstValueFrom(streams.author);
      expect(authorFilters).toHaveLength(1);
      expect(authorFilters[0].value.name).toBe('Author A');
    });
  });
});
