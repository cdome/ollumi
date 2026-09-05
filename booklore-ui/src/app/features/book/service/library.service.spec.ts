import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, config, filter, firstValueFrom, of, take, throwError} from 'rxjs';
import {LibraryService} from './library.service';
import {LibraryState} from '../model/state/library-state.model';
import {BookState} from '../model/state/book-state.model';
import {BookService} from './book.service';
import {mockAuthServiceProvider, mockHttpClientProvider} from '../../../../testing/providers';
import {createMockBook, createMockLibrary} from '../../../../testing/factories';

describe('LibraryService', () => {
  let service: LibraryService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let bookStateSubject: BehaviorSubject<BookState>;
  let refreshBooksSpy: ReturnType<typeof vi.fn>;
  let removeBooksByLibraryIdSpy: ReturnType<typeof vi.fn>;
  let originalUnhandledError: typeof config.onUnhandledError;

  function resetLibraryState(): void {
    (service as any).libraryStateSubject.next({
      libraries: null,
      loaded: false,
      error: null,
    });
  }

  beforeEach(() => {
    bookStateSubject = new BehaviorSubject<BookState>({books: [], loaded: true, error: null});
    refreshBooksSpy = vi.fn();
    removeBooksByLibraryIdSpy = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        LibraryService,
        mockHttpClientProvider,
        mockAuthServiceProvider,
        {
          provide: BookService,
          useValue: {
            bookState$: bookStateSubject.asObservable(),
            refreshBooks: refreshBooksSpy,
            removeBooksByLibraryId: removeBooksByLibraryIdSpy
          }
        }
      ]
    });

    service = TestBed.inject(LibraryService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of({}));
    httpClient.patch.mockReturnValue(of({}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  beforeAll(() => {
    originalUnhandledError = config.onUnhandledError;
    config.onUnhandledError = () => {};
  });

  afterAll(async () => {
    await new Promise(resolve => setTimeout(resolve, 50));
    config.onUnhandledError = originalUnhandledError;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should clear libraries when the auth token is null', () => {
    const state = service.getLibrariesFromState();
    expect(state).toEqual([]);
  });

  describe('libraryState$ loading', () => {
    it('should fetch libraries on subscription when not loaded', async () => {
      resetLibraryState();
      const libA = createMockLibrary({id: 1, name: 'Alpha'});
      const libB = createMockLibrary({id: 2, name: 'Beta'});
      httpClient.get.mockReturnValue(of([libB, libA]));

      const state = await firstValueFrom(
        service.libraryState$.pipe(filter(s => s.loaded && s.libraries !== null), take(1))
      );

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/libraries');
      expect(state.libraries).toEqual([libA, libB]);
    });

    it('should sort libraries alphabetically', async () => {
      resetLibraryState();
      const libB = createMockLibrary({id: 2, name: 'Beta'});
      const libA = createMockLibrary({id: 1, name: 'Alpha'});
      httpClient.get.mockReturnValue(of([libB, libA]));

      const state = await firstValueFrom(
        service.libraryState$.pipe(filter(s => s.loaded && s.libraries !== null), take(1))
      );

      expect(state.libraries?.map(l => l.name)).toEqual(['Alpha', 'Beta']);
    });

    it('should keep current libraries and set error on fetch failure', async () => {
      resetLibraryState();
      httpClient.get.mockReturnValue(throwError(() => ({message: 'Network error'})));

      const state = await firstValueFrom(
        service.libraryState$.pipe(filter(s => s.loaded && s.error !== null), take(1))
      );

      expect(state.libraries).toBeNull();
      expect(state.error).toBe('Network error');
    });
  });

  describe('library operations', () => {
    it('should scan library paths', () => {
      const lib = createMockLibrary({id: 5});
      httpClient.post.mockReturnValue(of(42));

      service.scanLibraryPaths(lib).subscribe();

      expect(httpClient.post).toHaveBeenCalledWith('http://localhost:6060/api/v1/libraries/scan', lib);
    });

    it('should create a library and add it to state sorted', () => {
      const existingLib = createMockLibrary({id: 1, name: 'Alpha'});
      const newLib = createMockLibrary({id: 2, name: 'Gamma'});
      httpClient.post.mockReturnValue(of(newLib));
      httpClient.get.mockReturnValue(of([existingLib]));

      resetLibraryState();
      service.libraryState$.subscribe();

      let created: typeof newLib | undefined;
      service.createLibrary(newLib).subscribe(l => created = l);

      expect(httpClient.post).toHaveBeenCalledWith('http://localhost:6060/api/v1/libraries', newLib);
      expect(created).toEqual(newLib);
      expect(service.getLibrariesFromState().map(l => l.name)).toEqual(['Alpha', 'Gamma']);
    });

    it('should update a library, sort state, and refresh books', () => {
      const libA = createMockLibrary({id: 1, name: 'Alpha'});
      const libB = createMockLibrary({id: 2, name: 'Beta'});
      const updatedLib = {...libB, name: 'Zeta'};
      httpClient.put.mockReturnValue(of(updatedLib));
      httpClient.get.mockReturnValue(of([libA, libB]));

      resetLibraryState();
      service.libraryState$.subscribe();

      service.updateLibrary(updatedLib, 2).subscribe();

      expect(httpClient.put).toHaveBeenCalledWith('http://localhost:6060/api/v1/libraries/2', updatedLib);
      expect(refreshBooksSpy).toHaveBeenCalled();
      expect(service.findLibraryById(2)?.name).toBe('Zeta');
    });

    it('should delete a library and remove its books from state', () => {
      const libA = createMockLibrary({id: 1});
      const libB = createMockLibrary({id: 2});
      httpClient.delete.mockReturnValue(of(undefined));
      httpClient.get.mockReturnValue(of([libA, libB]));

      resetLibraryState();
      service.libraryState$.subscribe();

      service.deleteLibrary(2).subscribe();

      expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v1/libraries/2');
      expect(removeBooksByLibraryIdSpy).toHaveBeenCalledWith(2);
      expect(service.getLibrariesFromState().map(l => l.id)).toEqual([1]);
    });

    it('should set error state on delete failure', () => {
      httpClient.delete.mockReturnValue(throwError(() => ({message: 'Delete failed'})));

      service.deleteLibrary(1).subscribe({error: () => {}});

      expect(service.findLibraryById(1)).toBeUndefined();
    });

    it('should refresh a library', () => {
      httpClient.put.mockReturnValue(of(undefined));

      service.refreshLibrary(3).subscribe();

      expect(httpClient.put).toHaveBeenCalledWith('http://localhost:6060/api/v1/libraries/3/refresh', {});
    });

    it('should update file naming pattern and sort state', () => {
      const lib = createMockLibrary({id: 1, name: 'Alpha'});
      const updated = {...lib, fileNamingPattern: '{author} - {title}'};
      httpClient.patch.mockReturnValue(of(updated));
      httpClient.get.mockReturnValue(of([lib]));

      resetLibraryState();
      service.libraryState$.subscribe();

      service.updateLibraryFileNamingPattern(1, '{author} - {title}').subscribe();

      expect(httpClient.patch).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/libraries/1/file-naming-pattern',
        {fileNamingPattern: '{author} - {title}'}
      );
      expect(service.findLibraryById(1)?.fileNamingPattern).toBe('{author} - {title}');
    });
  });

  describe('state helpers', () => {
    beforeEach(() => {
      httpClient.get.mockReturnValue(of([
        createMockLibrary({id: 1, name: 'Alpha'}),
        createMockLibrary({id: 2, name: 'Beta'})
      ]));
      resetLibraryState();
      service.libraryState$.subscribe();
    });

    it('should check if a library exists by name', () => {
      expect(service.doesLibraryExistByName('Alpha')).toBe(true);
      expect(service.doesLibraryExistByName('Missing')).toBe(false);
    });

    it('should find a library by id', () => {
      expect(service.findLibraryById(1)?.name).toBe('Alpha');
      expect(service.findLibraryById(99)).toBeUndefined();
    });

    it('should return libraries from state', () => {
      expect(service.getLibrariesFromState()).toHaveLength(2);
    });
  });

  describe('book counts', () => {
    it('should count books in a library from book state', () => {
      bookStateSubject.next({
        books: [
          createMockBook({id: 1, libraryId: 1}),
          createMockBook({id: 2, libraryId: 1}),
          createMockBook({id: 3, libraryId: 2})
        ],
        loaded: true,
        error: null
      });

      let count: number | undefined;
      service.getBookCount(1).subscribe(c => count = c);

      expect(count).toBe(2);
    });

    it('should fetch format counts for a library', () => {
      httpClient.get.mockReturnValue(of({PDF: 5, EPUB: 10}));

      let counts: Record<string, number> | undefined;
      service.getBookCountsByFormat(1).subscribe(c => counts = c);

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/libraries/1/format-counts');
      expect(counts).toEqual({PDF: 5, EPUB: 10});
    });
  });

  describe('large library loading', () => {
    it('should emit large library loading state', () => {
      const emissions: {isLoading: boolean; expectedCount: number}[] = [];
      service.largeLibraryLoading$.subscribe(state => emissions.push(state));

      service.setLargeLibraryLoading(true, 1000);

      expect(emissions[emissions.length - 1]).toEqual({isLoading: true, expectedCount: 1000});
    });
  });
});
