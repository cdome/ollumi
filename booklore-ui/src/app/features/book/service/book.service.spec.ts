import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {AuthService} from '../../../shared/service/auth.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {Router} from '@angular/router';
import {BehaviorSubject, config, filter, firstValueFrom, of, take, throwError} from 'rxjs';
import {BookService} from './book.service';
import {BookStateService} from './book-state.service';
import {BookSocketService} from './book-socket.service';
import {BookPatchService} from './book-patch.service';
import {
  mockHttpClientProvider,
  mockMessageServiceProvider,
  mockRouterProvider,
  mockTranslocoServiceProvider
} from '../../../../testing/providers';
import {createMockBook, createMockBookMetadata} from '../../../../testing/factories';
import {ReadStatus} from '../model/book.model';
import {BookType} from '../model/book.model';

describe('BookService', () => {
  let service: BookService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let authTokenSubject: BehaviorSubject<string | null>;
  let bookStateService: BookStateService;
  let messageService: {add: ReturnType<typeof vi.fn>; clear: ReturnType<typeof vi.fn>};
  let router: Router;
  let bookPatchServiceMock: {
    updateBookShelves: ReturnType<typeof vi.fn>;
    updateLastReadTime: ReturnType<typeof vi.fn>;
    savePdfProgress: ReturnType<typeof vi.fn>;
    saveCbxProgress: ReturnType<typeof vi.fn>;
    updateDateFinished: ReturnType<typeof vi.fn>;
    resetProgress: ReturnType<typeof vi.fn>;
    updateBookReadStatus: ReturnType<typeof vi.fn>;
    resetPersonalRating: ReturnType<typeof vi.fn>;
    updatePersonalRating: ReturnType<typeof vi.fn>;
  };
  let bookSocketServiceMock: {
    handleNewlyCreatedBook: ReturnType<typeof vi.fn>;
    handleRemovedBookIds: ReturnType<typeof vi.fn>;
    handleBookUpdate: ReturnType<typeof vi.fn>;
    handleMultipleBookUpdates: ReturnType<typeof vi.fn>;
    handleMultipleBookCoverPatches: ReturnType<typeof vi.fn>;
  };
  let originalUnhandledError: typeof config.onUnhandledError;

  beforeAll(() => {
    originalUnhandledError = config.onUnhandledError;
    config.onUnhandledError = () => {};
  });

  afterAll(async () => {
    await new Promise(resolve => setTimeout(resolve, 50));
    config.onUnhandledError = originalUnhandledError;
  });

  beforeEach(() => {
    authTokenSubject = new BehaviorSubject<string | null>('token');

    bookPatchServiceMock = {
      updateBookShelves: vi.fn().mockReturnValue(of([])),
      updateLastReadTime: vi.fn(),
      savePdfProgress: vi.fn().mockReturnValue(of(undefined)),
      saveCbxProgress: vi.fn().mockReturnValue(of(undefined)),
      updateDateFinished: vi.fn().mockReturnValue(of(undefined)),
      resetProgress: vi.fn().mockReturnValue(of([])),
      updateBookReadStatus: vi.fn().mockReturnValue(of([])),
      resetPersonalRating: vi.fn().mockReturnValue(of([])),
      updatePersonalRating: vi.fn().mockReturnValue(of([]))
    };

    bookSocketServiceMock = {
      handleNewlyCreatedBook: vi.fn(),
      handleRemovedBookIds: vi.fn(),
      handleBookUpdate: vi.fn(),
      handleMultipleBookUpdates: vi.fn(),
      handleMultipleBookCoverPatches: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookService,
        BookStateService,
        mockHttpClientProvider,
        mockMessageServiceProvider,
        mockRouterProvider,
        mockTranslocoServiceProvider,
        {provide: AuthService, useValue: {token$: authTokenSubject.asObservable(), tokenSubject: authTokenSubject}},
        {provide: BookPatchService, useValue: bookPatchServiceMock},
        {provide: BookSocketService, useValue: bookSocketServiceMock}
      ]
    });

    service = TestBed.inject(BookService);
    httpClient = TestBed.inject(HttpClient) as any;
    bookStateService = TestBed.inject(BookStateService);
    messageService = TestBed.inject(MessageService) as any;
    router = TestBed.inject(Router);

    vi.clearAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of(undefined));
    httpClient.delete.mockReturnValue(of({}));
    httpClient.patch.mockReturnValue(of({}));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('auth token changes', () => {
    it('should reset the book state when the token becomes null', () => {
      authTokenSubject.next(null);

      const state = bookStateService.getCurrentBookState();
      expect(state.books).toBeNull();
      expect(state.loaded).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should mark state as unloaded when logging in with no books', () => {
      bookStateService.updateBookState({books: null, loaded: true, error: null});

      authTokenSubject.next('new-token');

      expect(bookStateService.getCurrentBookState().loaded).toBe(false);
    });
  });

  describe('bookState$ loading', () => {
    it('should fetch books on subscription when not loaded', async () => {
      const bookA = createMockBook({id: 1, metadata: createMockBookMetadata({title: 'Alpha'})});
      const bookB = createMockBook({id: 2, metadata: createMockBookMetadata({title: 'Beta'})});
      httpClient.get.mockReturnValue(of([bookB, bookA]));

      const state = await firstValueFrom(
        service.bookState$.pipe(filter(s => s.loaded && s.books !== null), take(1))
      );

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/books');
      expect(state.books).toEqual([bookB, bookA]);
    });

    it('should keep current books and set an error on fetch failure', async () => {
      httpClient.get.mockReturnValue(throwError(() => ({message: 'Network error'})));

      const state = await firstValueFrom(
        service.bookState$.pipe(filter(s => s.loaded && s.error !== null), take(1))
      );

      expect(state.books).toBeNull();
      expect(state.error).toBe('Network error');
    });
  });

  describe('state helpers', () => {
    it('should return the current book state', () => {
      const state = bookStateService.getCurrentBookState();
      expect(service.getCurrentBookState()).toBe(state);
    });

    it('should find a book by id from state', () => {
      const book = createMockBook({id: 42});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      expect(service.getBookByIdFromState(42)).toBe(book);
      expect(service.getBookByIdFromState('42' as any)).toBe(book);
      expect(service.getBookByIdFromState(99)).toBeUndefined();
    });

    it('should find multiple books by ids from state', () => {
      const book1 = createMockBook({id: 1});
      const book2 = createMockBook({id: 2});
      const book3 = createMockBook({id: 3});
      bookStateService.updateBookState({books: [book1, book2, book3], loaded: true, error: null});

      expect(service.getBooksByIdsFromState([1, 3])).toEqual([book1, book3]);
      expect(service.getBooksByIdsFromState([])).toEqual([]);
    });

    it('should remove books by library id from state', () => {
      const book1 = createMockBook({id: 1, libraryId: 1});
      const book2 = createMockBook({id: 2, libraryId: 2});
      bookStateService.updateBookState({books: [book1, book2], loaded: true, error: null});

      service.removeBooksByLibraryId(1);

      expect(bookStateService.getCurrentBookState().books).toEqual([book2]);
    });

    it('should remove a shelf from all books in state', () => {
      const shelfA = {id: 1, name: 'A', icon: '', iconType: 'PRIME_NG' as const, bookCount: 0};
      const shelfB = {id: 2, name: 'B', icon: '', iconType: 'PRIME_NG' as const, bookCount: 0};
      const book = createMockBook({id: 1, shelves: [shelfA, shelfB]});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      service.removeBooksFromShelf(1);

      expect(bookStateService.getCurrentBookState().books![0].shelves).toEqual([shelfB]);
    });
  });

  describe('refreshBooks', () => {
    it('should fetch books and update the state', async () => {
      const book = createMockBook({id: 1});
      httpClient.get.mockReturnValue(of([book]));

      service.refreshBooks();
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/books');
      expect(bookStateService.getCurrentBookState().books).toEqual([book]);
    });

    it('should clear books and set an error on refresh failure', async () => {
      httpClient.get.mockReturnValue(throwError(() => ({message: 'Refresh failed'})));

      service.refreshBooks();
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(bookStateService.getCurrentBookState().books).toBeNull();
      expect(bookStateService.getCurrentBookState().error).toBe('Refresh failed');
    });
  });

  describe('book retrieval', () => {
    it('should fetch a single book from the API', async () => {
      const book = createMockBook({id: 7});
      httpClient.get.mockReturnValue(of(book));

      const result = await firstValueFrom(service.getBookByIdFromAPI(7, true));

      expect(httpClient.get).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/7',
        {params: {withDescription: 'true'}}
      );
      expect(result).toEqual(book);
    });

    it('should return books in the same series', async () => {
      const book1 = createMockBook({
        id: 1,
        metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 1})
      });
      const book2 = createMockBook({
        id: 2,
        metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 2})
      });
      const other = createMockBook({
        id: 3,
        metadata: createMockBookMetadata({seriesName: 'Other'})
      });
      bookStateService.updateBookState({books: [book1, book2, other], loaded: true, error: null});

      const result = await firstValueFrom(service.getBooksInSeries(1));

      expect(result).toEqual([book1, book2]);
    });

    it('should return an empty array for a book with no series', async () => {
      const book = createMockBook({id: 1, metadata: createMockBookMetadata({seriesName: undefined})});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      const result = await firstValueFrom(service.getBooksInSeries(1));

      expect(result).toEqual([]);
    });

    it('should fetch book recommendations', async () => {
      const recommendations = [{book: createMockBook({id: 2}), similarityScore: 0.9}];
      httpClient.get.mockReturnValue(of(recommendations));

      const result = await firstValueFrom(service.getBookRecommendations(1, 5));

      expect(httpClient.get).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/recommendations',
        {params: {limit: '5'}}
      );
      expect(result).toEqual(recommendations);
    });
  });

  describe('book operations', () => {
    it('should delete books and update state on success', async () => {
      const book1 = createMockBook({id: 1});
      const book2 = createMockBook({id: 2});
      bookStateService.updateBookState({books: [book1, book2], loaded: true, error: null});
      httpClient.delete.mockReturnValue(of({deleted: [1], failedFileDeletions: []}));

      await firstValueFrom(service.deleteBooks(new Set([1])));

      expect(httpClient.delete).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books',
        expect.objectContaining({params: expect.any(Object)})
      );
      expect(bookStateService.getCurrentBookState().books).toEqual([book2]);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should warn when some file deletions fail', async () => {
      bookStateService.updateBookState({books: [createMockBook({id: 1})], loaded: true, error: null});
      httpClient.delete.mockReturnValue(of({deleted: [1], failedFileDeletions: ['file.txt']}));

      await firstValueFrom(service.deleteBooks(new Set([1])));

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'warn'}));
    });

    it('should show an error toast when delete fails', async () => {
      httpClient.delete.mockReturnValue(throwError(() => ({error: {message: 'Delete failed'}, message: ''})));

      service.deleteBooks(new Set([1])).subscribe({error: () => {}});
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });

    it('should delegate updateBookShelves to the patch service', async () => {
      const updatedBooks = [createMockBook({id: 1})];
      bookPatchServiceMock.updateBookShelves.mockReturnValue(of(updatedBooks));

      const result = await firstValueFrom(
        service.updateBookShelves(new Set([1]), new Set([2]), new Set([3]))
      );

      expect(bookPatchServiceMock.updateBookShelves).toHaveBeenCalledWith(
        new Set([1]),
        new Set([2]),
        new Set([3])
      );
      expect(result).toEqual(updatedBooks);
    });

    it('should set an error state when updateBookShelves fails', async () => {
      bookPatchServiceMock.updateBookShelves.mockReturnValue(throwError(() => ({message: 'Patch failed'})));

      service.updateBookShelves(new Set([1]), new Set([]), new Set([])).subscribe({error: () => {}});
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(bookStateService.getCurrentBookState().error).toBe('Patch failed');
    });

    it('should create a physical book and add it to state', async () => {
      const newBook = createMockBook({id: 3});
      httpClient.post.mockReturnValue(of(newBook));

      await firstValueFrom(service.createPhysicalBook({libraryId: 1, title: 'New Book'}));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/physical',
        {libraryId: 1, title: 'New Book'}
      );
      expect(bookStateService.getCurrentBookState().books).toContain(newBook);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should show an error toast when creating a physical book fails', async () => {
      httpClient.post.mockReturnValue(throwError(() => ({message: 'Creation failed'})));

      service.createPhysicalBook({libraryId: 1}).subscribe({error: () => {}});
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });

    it('should toggle the physical flag and update state', async () => {
      const book = createMockBook({id: 1, isPhysical: false});
      const updatedBook = {...book, isPhysical: true};
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.patch.mockReturnValue(of(updatedBook));

      await firstValueFrom(service.togglePhysicalFlag(1, true));

      expect(httpClient.patch).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/physical',
        null,
        {params: {physical: true}}
      );
      expect(bookStateService.getCurrentBookState().books![0].isPhysical).toBe(true);
    });
  });

  describe('reading and viewer', () => {
    it('should navigate to the PDF reader', () => {
      const book = createMockBook({
        id: 1,
        primaryFile: {id: 1, bookId: 1, bookType: 'PDF' as BookType}
      });
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      service.readBook(1);

      expect(router.navigate).toHaveBeenCalledWith(['/pdf-reader/book/1'], undefined);
      expect(bookPatchServiceMock.updateLastReadTime).toHaveBeenCalledWith(1);
    });

    it('should navigate to the EPUB reader with streaming', () => {
      const book = createMockBook({
        id: 2,
        primaryFile: {id: 1, bookId: 2, bookType: 'EPUB' as BookType}
      });
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      service.readBook(2, 'epub-streaming');

      expect(router.navigate).toHaveBeenCalledWith(
        ['/ebook-reader/book/2'],
        {queryParams: {streaming: true}}
      );
    });

    it('should pass an alternative book type as a query param', () => {
      const book = createMockBook({
        id: 3,
        primaryFile: {id: 1, bookId: 3, bookType: 'EPUB' as BookType}
      });
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      service.readBook(3, undefined, 'PDF' as BookType);

      expect(router.navigate).toHaveBeenCalledWith(
        ['/pdf-reader/book/3'],
        {queryParams: {bookType: 'PDF'}}
      );
    });

    it('should log an error and not navigate when the book is not found', () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      service.readBook(999);

      expect(consoleSpy).toHaveBeenCalledWith('Book not found');
      expect(router.navigate).not.toHaveBeenCalled();
      consoleSpy.mockRestore();
    });

    it('should fetch a book setting', async () => {
      const setting = {pdfSettings: {zoom: 'auto' as any, spread: 'off'}};
      httpClient.get.mockReturnValue(of(setting));

      const result = await firstValueFrom(service.getBookSetting(1, 10));

      expect(httpClient.get).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/viewer-setting?bookFileId=10'
      );
      expect(result).toEqual(setting);
    });

    it('should update a viewer setting', async () => {
      const setting = {epubSettings: {theme: 'dark'} as any};
      httpClient.put.mockReturnValue(of(undefined));

      await firstValueFrom(service.updateViewerSetting(setting, 1));

      expect(httpClient.put).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/viewer-setting',
        setting
      );
    });
  });

  describe('progress and status', () => {
    it('should delegate updateLastReadTime to the patch service', () => {
      service.updateLastReadTime(7);
      expect(bookPatchServiceMock.updateLastReadTime).toHaveBeenCalledWith(7);
    });

    it('should delegate savePdfProgress to the patch service', async () => {
      await firstValueFrom(service.savePdfProgress(1, 10, 0.5, 3));
      expect(bookPatchServiceMock.savePdfProgress).toHaveBeenCalledWith(1, 10, 0.5, 3);
    });

    it('should delegate saveCbxProgress to the patch service', async () => {
      await firstValueFrom(service.saveCbxProgress(1, 5, 0.25));
      expect(bookPatchServiceMock.saveCbxProgress).toHaveBeenCalledWith(1, 5, 0.25, undefined);
    });

    it('should delegate updateDateFinished to the patch service', async () => {
      await firstValueFrom(service.updateDateFinished(1, '2026-01-01'));
      expect(bookPatchServiceMock.updateDateFinished).toHaveBeenCalledWith(1, '2026-01-01');
    });

    it('should delegate resetProgress to the patch service', async () => {
      await firstValueFrom(service.resetProgress(1, 'BOOKLORE'));
      expect(bookPatchServiceMock.resetProgress).toHaveBeenCalledWith(1, 'BOOKLORE');
    });

    it('should delegate updateBookReadStatus to the patch service', async () => {
      await firstValueFrom(service.updateBookReadStatus([1, 2], ReadStatus.READ));
      expect(bookPatchServiceMock.updateBookReadStatus).toHaveBeenCalledWith([1, 2], ReadStatus.READ);
    });

    it('should delegate resetPersonalRating to the patch service', async () => {
      await firstValueFrom(service.resetPersonalRating([1]));
      expect(bookPatchServiceMock.resetPersonalRating).toHaveBeenCalledWith([1]);
    });

    it('should delegate updatePersonalRating to the patch service', async () => {
      await firstValueFrom(service.updatePersonalRating([1, 2], 5));
      expect(bookPatchServiceMock.updatePersonalRating).toHaveBeenCalledWith([1, 2], 5);
    });
  });

  describe('websocket handlers', () => {
    it('should delegate handleNewlyCreatedBook', () => {
      const book = createMockBook({id: 1});
      service.handleNewlyCreatedBook(book);
      expect(bookSocketServiceMock.handleNewlyCreatedBook).toHaveBeenCalledWith(book);
    });

    it('should delegate handleRemovedBookIds', () => {
      service.handleRemovedBookIds([1, 2]);
      expect(bookSocketServiceMock.handleRemovedBookIds).toHaveBeenCalledWith([1, 2]);
    });

    it('should delegate handleBookUpdate', () => {
      const book = createMockBook({id: 1});
      service.handleBookUpdate(book);
      expect(bookSocketServiceMock.handleBookUpdate).toHaveBeenCalledWith(book);
    });

    it('should delegate handleMultipleBookUpdates', () => {
      const books = [createMockBook({id: 1})];
      service.handleMultipleBookUpdates(books);
      expect(bookSocketServiceMock.handleMultipleBookUpdates).toHaveBeenCalledWith(books);
    });

    it('should delegate handleMultipleBookCoverPatches', () => {
      const patches = [{id: 1, coverUpdatedOn: 'now'}];
      service.handleMultipleBookCoverPatches(patches);
      expect(bookSocketServiceMock.handleMultipleBookCoverPatches).toHaveBeenCalledWith(patches);
    });
  });
});
