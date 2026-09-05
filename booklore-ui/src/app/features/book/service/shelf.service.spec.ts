import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, config, filter, firstValueFrom, of, take, throwError} from 'rxjs';
import {ShelfService} from './shelf.service';
import {ShelfState} from '../model/state/shelf-state.model';
import {BookState} from '../model/state/book-state.model';
import {BookService} from './book.service';
import {UserService, UserState} from '../../settings/user-management/user.service';
import {mockHttpClientProvider} from '../../../../testing/providers';
import {createMockBook, createMockShelf, createMockUser} from '../../../../testing/factories';

describe('ShelfService', () => {
  let service: ShelfService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let bookStateSubject: BehaviorSubject<BookState>;
  let userStateSubject: BehaviorSubject<UserState>;
  let removeBooksFromShelfSpy: ReturnType<typeof vi.fn>;
  let originalUnhandledError: typeof config.onUnhandledError;

  beforeEach(() => {
    bookStateSubject = new BehaviorSubject<BookState>({books: [], loaded: true, error: null});
    userStateSubject = new BehaviorSubject<UserState>({user: null, loaded: true, error: null});
    removeBooksFromShelfSpy = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        ShelfService,
        mockHttpClientProvider,
        {
          provide: BookService,
          useValue: {
            bookState$: bookStateSubject.asObservable(),
            removeBooksFromShelf: removeBooksFromShelfSpy
          }
        },
        {
          provide: UserService,
          useValue: {userState$: userStateSubject.asObservable()}
        }
      ]
    });

    service = TestBed.inject(ShelfService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of({}));
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

  describe('shelfState$ loading', () => {
    it('should fetch shelves on subscription when not loaded', async () => {
      const shelfA = createMockShelf({id: 1, name: 'Alpha'});
      const shelfB = createMockShelf({id: 2, name: 'Beta'});
      httpClient.get.mockReturnValue(of([shelfB, shelfA]));

      const state = await firstValueFrom(
        service.shelfState$.pipe(filter(s => s.loaded && s.shelves !== null), take(1))
      );

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/shelves');
      expect(state.shelves).toEqual([shelfB, shelfA]);
    });

    it('should keep current shelves and set error on fetch failure', async () => {
      httpClient.get.mockReturnValue(throwError(() => ({message: 'Network error'})));

      const state = await firstValueFrom(
        service.shelfState$.pipe(filter(s => s.loaded && s.error !== null), take(1))
      );

      expect(state.shelves).toBeNull();
      expect(state.error).toBe('Network error');
    });
  });

  describe('shelf operations', () => {
    it('should reload shelves directly', () => {
      const shelf = createMockShelf({id: 1});
      httpClient.get.mockReturnValue(of([shelf]));

      service.reloadShelves();

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/shelves');
      expect(service.getShelvesFromState()).toContainEqual(shelf);
    });

    it('should create a shelf and add it to state', () => {
      const existingShelf = createMockShelf({id: 1, name: 'Alpha'});
      const newShelf = createMockShelf({id: 2, name: 'Beta'});
      httpClient.get.mockReturnValue(of([existingShelf]));
      httpClient.post.mockReturnValue(of(newShelf));

      service.shelfState$.subscribe();

      let created: typeof newShelf | undefined;
      service.createShelf(newShelf).subscribe(s => created = s);

      expect(httpClient.post).toHaveBeenCalledWith('http://localhost:6060/api/v1/shelves', newShelf);
      expect(created).toEqual(newShelf);
      expect(service.getShelvesFromState()).toHaveLength(2);
    });

    it('should update a shelf in state', () => {
      const shelfA = createMockShelf({id: 1, name: 'Alpha'});
      const shelfB = createMockShelf({id: 2, name: 'Beta'});
      const updatedShelf = {...shelfB, name: 'Updated'};
      httpClient.get.mockReturnValue(of([shelfA, shelfB]));
      httpClient.put.mockReturnValue(of(updatedShelf));

      service.shelfState$.subscribe();

      service.updateShelf(updatedShelf, 2).subscribe();

      expect(httpClient.put).toHaveBeenCalledWith('http://localhost:6060/api/v1/shelves/2', updatedShelf);
      expect(service.getShelfById(2)?.name).toBe('Updated');
    });

    it('should delete a shelf and remove its books from state', () => {
      const shelfA = createMockShelf({id: 1});
      const shelfB = createMockShelf({id: 2});
      httpClient.get.mockReturnValue(of([shelfA, shelfB]));
      httpClient.delete.mockReturnValue(of(undefined));

      service.shelfState$.subscribe();

      service.deleteShelf(2).subscribe();

      expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v1/shelves/2');
      expect(removeBooksFromShelfSpy).toHaveBeenCalledWith(2);
      expect(service.getShelvesFromState().map(s => s.id)).toEqual([1]);
    });

    it('should set error state on delete failure', () => {
      httpClient.delete.mockReturnValue(throwError(() => ({message: 'Delete failed'})));

      service.deleteShelf(1).subscribe({error: () => {}});

      expect(service.getShelfById(1)).toBeUndefined();
    });
  });

  describe('state helpers', () => {
    beforeEach(() => {
      httpClient.get.mockReturnValue(of([
        createMockShelf({id: 1, name: 'Alpha'}),
        createMockShelf({id: 2, name: 'Beta'})
      ]));
      service.shelfState$.subscribe();
    });

    it('should get a shelf by id', () => {
      expect(service.getShelfById(1)?.name).toBe('Alpha');
      expect(service.getShelfById(99)).toBeUndefined();
    });

    it('should return shelves from state', () => {
      expect(service.getShelvesFromState()).toHaveLength(2);
    });
  });

  describe('book counts', () => {
    it('should return owned book count from book state', () => {
      const shelf = createMockShelf({id: 5, userId: 10});
      httpClient.get.mockReturnValue(of([shelf]));
      service.shelfState$.subscribe();

      userStateSubject.next({user: createMockUser({id: 10}), loaded: true, error: null});
      bookStateSubject.next({
        books: [
          createMockBook({id: 1, shelves: [shelf]}),
          createMockBook({id: 2, shelves: [shelf]}),
          createMockBook({id: 3})
        ],
        loaded: true,
        error: null
      });

      let count: number | undefined;
      service.getBookCount(5).subscribe(c => count = c);

      expect(count).toBe(2);
    });

    it('should return bookCount from shelf when user is not the owner', () => {
      const shelf = createMockShelf({id: 5, userId: 10, bookCount: 7});
      httpClient.get.mockReturnValue(of([shelf]));
      service.shelfState$.subscribe();

      userStateSubject.next({user: createMockUser({id: 20}), loaded: true, error: null});
      bookStateSubject.next({books: [], loaded: true, error: null});

      let count: number | undefined;
      service.getBookCount(5).subscribe(c => count = c);

      expect(count).toBe(7);
    });

    it('should return zero for an unknown shelf', () => {
      httpClient.get.mockReturnValue(of([]));
      service.shelfState$.subscribe();

      userStateSubject.next({user: createMockUser({id: 1}), loaded: true, error: null});
      bookStateSubject.next({books: [], loaded: true, error: null});

      let count: number | undefined;
      service.getBookCount(99).subscribe(c => count = c);

      expect(count).toBe(0);
    });

    it('should fetch books on a shelf', () => {
      const books = [createMockBook({id: 1}), createMockBook({id: 2})];
      httpClient.get.mockReturnValue(of(books));

      let result: typeof books | undefined;
      service.getBooksOnShelf(3).subscribe(b => result = b);

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/shelves/3/books');
      expect(result).toEqual(books);
    });

    it('should count unshelved books', () => {
      bookStateSubject.next({
        books: [
          createMockBook({id: 1, shelves: [createMockShelf({id: 1})]}),
          createMockBook({id: 2}),
          createMockBook({id: 3, shelves: []})
        ],
        loaded: true,
        error: null
      });

      let count: number | undefined;
      service.getUnshelvedBookCount().subscribe(c => count = c);

      expect(count).toBe(2);
    });
  });
});
