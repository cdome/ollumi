import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap} from '@angular/router';
import {BehaviorSubject, firstValueFrom, of, throwError} from 'rxjs';
import {BookBrowserEntityService, EntityInfo} from './book-browser-entity.service';
import {BookService} from '../../service/book.service';
import {LibraryService} from '../../service/library.service';
import {ShelfService} from '../../service/shelf.service';
import {SortService} from '../../service/sort.service';
import {MagicShelf, MagicShelfService, MagicShelfState} from '../../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../../magic-shelf/service/book-rule-evaluator.service';
import {EntityType} from './book-browser.component';
import {BookState} from '../../model/state/book-state.model';
import {LibraryState} from '../../model/state/library-state.model';
import {ShelfState} from '../../model/state/shelf-state.model';
import {SortDirection, SortOption} from '../../model/sort.model';
import {createMockBook} from '../../../../../testing/factories';

describe('BookBrowserEntityService', () => {
  let service: BookBrowserEntityService;
  let bookStateSubject: BehaviorSubject<BookState>;
  let libraryStateSubject: BehaviorSubject<LibraryState>;
  let shelfStateSubject: BehaviorSubject<ShelfState>;
  let magicShelfStateSubject: BehaviorSubject<MagicShelfState>;
  let applySortSpy: ReturnType<typeof vi.fn>;
  let evaluateGroupSpy: ReturnType<typeof vi.fn>;
  let getShelfSpy: ReturnType<typeof vi.fn>;

  const sortOption: SortOption = {label: 'Title', field: 'title', direction: SortDirection.ASCENDING};

  beforeEach(() => {
    bookStateSubject = new BehaviorSubject<BookState>({books: [], loaded: true, error: null});
    libraryStateSubject = new BehaviorSubject<LibraryState>({libraries: [], loaded: true, error: null});
    shelfStateSubject = new BehaviorSubject<ShelfState>({shelves: [], loaded: true, error: null});
    magicShelfStateSubject = new BehaviorSubject<MagicShelfState>({shelves: [], loaded: true, error: null});
    applySortSpy = vi.fn((books: any[]) => books);
    evaluateGroupSpy = vi.fn().mockReturnValue(false);
    getShelfSpy = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        BookBrowserEntityService,
        {provide: BookService, useValue: {bookState$: bookStateSubject.asObservable()}},
        {provide: LibraryService, useValue: {libraryState$: libraryStateSubject.asObservable()}},
        {provide: ShelfService, useValue: {shelfState$: shelfStateSubject.asObservable()}},
        {
          provide: MagicShelfService,
          useValue: {
            shelvesState$: magicShelfStateSubject.asObservable(),
            getShelf: getShelfSpy
          }
        },
        {provide: SortService, useValue: {applySort: applySortSpy}},
        {provide: BookRuleEvaluatorService, useValue: {evaluateGroup: evaluateGroupSpy}}
      ]
    });

    service = TestBed.inject(BookBrowserEntityService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getEntityInfoFromRoute', () => {
    it('should identify a library route', async () => {
      const route = {paramMap: of(convertToParamMap({libraryId: '5'}))} as unknown as ActivatedRoute;

      const info = await firstValueFrom(service.getEntityInfoFromRoute(route));

      expect(info).toEqual({entityId: 5, entityType: EntityType.LIBRARY});
    });

    it('should identify a shelf route', async () => {
      const route = {paramMap: of(convertToParamMap({shelfId: '8'}))} as unknown as ActivatedRoute;

      const info = await firstValueFrom(service.getEntityInfoFromRoute(route));

      expect(info).toEqual({entityId: 8, entityType: EntityType.SHELF});
    });

    it('should identify a magic shelf route', async () => {
      const route = {paramMap: of(convertToParamMap({magicShelfId: '12'}))} as unknown as ActivatedRoute;

      const info = await firstValueFrom(service.getEntityInfoFromRoute(route));

      expect(info).toEqual({entityId: 12, entityType: EntityType.MAGIC_SHELF});
    });

    it('should default to all books when no entity params are present', async () => {
      const route = {paramMap: of(convertToParamMap({}))} as unknown as ActivatedRoute;

      const info = await firstValueFrom(service.getEntityInfoFromRoute(route));

      expect(info.entityType).toBe(EntityType.ALL_BOOKS);
      expect(isNaN(info.entityId)).toBe(true);
    });
  });

  describe('fetchEntity', () => {
    it('should return a library from library state', async () => {
      libraryStateSubject.next({
        libraries: [{id: 3, name: 'Fiction', watch: false, paths: [{path: '/books'}]}],
        loaded: true,
        error: null
      });

      const entity = await firstValueFrom(service.fetchEntity(3, EntityType.LIBRARY));

      expect(entity).toEqual(expect.objectContaining({id: 3, name: 'Fiction'}));
      expect(service.isLibrary(entity!)).toBe(true);
    });

    it('should return null for a missing library', async () => {
      libraryStateSubject.next({libraries: [], loaded: true, error: null});

      const entity = await firstValueFrom(service.fetchEntity(99, EntityType.LIBRARY));

      expect(entity).toBeNull();
    });

    it('should return a shelf from shelf state', async () => {
      shelfStateSubject.next({
        shelves: [{id: 7, name: 'Want to Read'}],
        loaded: true,
        error: null
      });

      const entity = await firstValueFrom(service.fetchEntity(7, EntityType.SHELF));

      expect(entity).toEqual(expect.objectContaining({id: 7, name: 'Want to Read'}));
      expect(service.isLibrary(entity!)).toBe(false);
    });

    it('should return a magic shelf from cache when available', async () => {
      const magicShelf: MagicShelf = {id: 9, name: 'Highly Rated', filterJson: '{}'};
      magicShelfStateSubject.next({shelves: [magicShelf], loaded: true, error: null});

      const entity = await firstValueFrom(service.fetchEntity(9, EntityType.MAGIC_SHELF));

      expect(entity).toEqual(magicShelf);
      expect(service.isMagicShelf(entity)).toBe(true);
      expect(getShelfSpy).not.toHaveBeenCalled();
    });

    it('should fetch a magic shelf from the API when not cached', async () => {
      magicShelfStateSubject.next({shelves: [], loaded: true, error: null});
      const magicShelf: MagicShelf = {id: 10, name: 'Recent', filterJson: '{}'};
      getShelfSpy.mockReturnValue(of(magicShelf));

      const entity = await firstValueFrom(service.fetchEntity(10, EntityType.MAGIC_SHELF));

      expect(entity).toEqual(magicShelf);
      expect(getShelfSpy).toHaveBeenCalledWith(10);
    });

    it('should return null when the magic shelf API call fails', async () => {
      magicShelfStateSubject.next({shelves: [], loaded: true, error: null});
      getShelfSpy.mockReturnValue(throwError(() => new Error('network error')));

      const entity = await firstValueFrom(service.fetchEntity(99, EntityType.MAGIC_SHELF));

      expect(entity).toBeNull();
    });

    it('should return null for all books', async () => {
      const entity = await firstValueFrom(service.fetchEntity(NaN, EntityType.ALL_BOOKS));

      expect(entity).toBeNull();
    });
  });

  describe('fetchBooksByEntity', () => {
    it('should filter books by library id and sort the result', async () => {
      const books = [createMockBook({id: 1, libraryId: 1}), createMockBook({id: 2, libraryId: 2})];
      bookStateSubject.next({books, loaded: true, error: null});

      const state = await firstValueFrom(service.fetchBooksByEntity(1, EntityType.LIBRARY, sortOption));

      expect(state.books).toHaveLength(1);
      expect(state.books![0].id).toBe(1);
      expect(applySortSpy).toHaveBeenCalledWith([state.books![0]], sortOption);
    });

    it('should filter books by shelf membership', async () => {
      const books = [
        createMockBook({id: 1, shelves: [{id: 5, name: 'S'}]}),
        createMockBook({id: 2, shelves: []}),
        createMockBook({id: 3, shelves: [{id: 6, name: 'Other'}]})
      ];
      bookStateSubject.next({books, loaded: true, error: null});

      const state = await firstValueFrom(service.fetchBooksByEntity(5, EntityType.SHELF, sortOption));

      expect(state.books).toHaveLength(1);
      expect(state.books![0].id).toBe(1);
    });

    it('should evaluate magic shelf rules against all books', async () => {
      const books = [createMockBook({id: 1}), createMockBook({id: 2})];
      bookStateSubject.next({books, loaded: true, error: null});
      const magicShelf: MagicShelf = {
        id: 4,
        name: 'Magic',
        filterJson: JSON.stringify({join: 'and', rules: []})
      };
      getShelfSpy.mockReturnValue(of(magicShelf));
      evaluateGroupSpy.mockReturnValue(true);

      const state = await firstValueFrom(service.fetchBooksByEntity(4, EntityType.MAGIC_SHELF, sortOption));

      expect(state.books).toHaveLength(2);
      expect(evaluateGroupSpy).toHaveBeenCalled();
      expect(applySortSpy).toHaveBeenCalledWith(books, sortOption);
    });

    it('should return the original book state for a magic shelf when it is not loaded', async () => {
      const books = [createMockBook({id: 1})];
      bookStateSubject.next({books, loaded: false, error: null});
      const magicShelf: MagicShelf = {
        id: 4,
        name: 'Magic',
        filterJson: JSON.stringify({join: 'and', rules: []})
      };
      getShelfSpy.mockReturnValue(of(magicShelf));

      const state = await firstValueFrom(service.fetchBooksByEntity(4, EntityType.MAGIC_SHELF, sortOption));

      expect(state.loaded).toBe(false);
      expect(applySortSpy).not.toHaveBeenCalled();
    });

    it('should sort all books for the all-books view', async () => {
      const books = [createMockBook({id: 1}), createMockBook({id: 2})];
      bookStateSubject.next({books, loaded: true, error: null});

      const state = await firstValueFrom(service.fetchBooksByEntity(NaN, EntityType.ALL_BOOKS, sortOption));

      expect(state.books).toHaveLength(2);
      expect(applySortSpy).toHaveBeenCalledWith(books, sortOption);
    });
  });

  describe('fetchAllBooks', () => {
    it('should sort loaded books', async () => {
      const books = [createMockBook({id: 1})];
      bookStateSubject.next({books, loaded: true, error: null});

      await firstValueFrom(service.fetchAllBooks(sortOption));

      expect(applySortSpy).toHaveBeenCalledWith(books, sortOption);
    });

    it('should not sort when state is not loaded', async () => {
      bookStateSubject.next({books: [], loaded: false, error: null});

      const state = await firstValueFrom(service.fetchAllBooks(sortOption));

      expect(state.loaded).toBe(false);
      expect(applySortSpy).not.toHaveBeenCalled();
    });

    it('should not sort when state has an error', async () => {
      bookStateSubject.next({books: null, loaded: true, error: 'failed'});

      const state = await firstValueFrom(service.fetchAllBooks(sortOption));

      expect(state.error).toBe('failed');
      expect(applySortSpy).not.toHaveBeenCalled();
    });
  });

  describe('fetchUnshelvedBooks', () => {
    it('should only include books with no shelves', async () => {
      const books = [
        createMockBook({id: 1, shelves: []}),
        createMockBook({id: 2, shelves: [{id: 1, name: 'S'}]}),
        createMockBook({id: 3, shelves: undefined as any})
      ];
      bookStateSubject.next({books, loaded: true, error: null});

      const state = await firstValueFrom(service.fetchUnshelvedBooks(sortOption));

      expect(state.books).toHaveLength(2);
      expect(state.books!.map(b => b.id)).toContain(1);
      expect(state.books!.map(b => b.id)).toContain(3);
    });
  });

  describe('type guards', () => {
    it('should identify a library by the presence of paths', () => {
      expect(service.isLibrary({id: 1, name: 'Lib', watch: false, paths: []})).toBe(true);
      expect(service.isLibrary({id: 1, name: 'Shelf'})).toBe(false);
    });

    it('should identify a magic shelf by the presence of filterJson', () => {
      expect(service.isMagicShelf({id: 1, name: 'Magic', filterJson: '{}'})).toBe(true);
      expect(service.isMagicShelf(null)).toBe(false);
      expect(service.isMagicShelf({id: 1, name: 'Shelf'} as any)).toBe(false);
    });
  });
});
