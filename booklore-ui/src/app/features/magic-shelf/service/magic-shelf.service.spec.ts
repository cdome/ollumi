import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, config, firstValueFrom, of, Subscription, throwError} from 'rxjs';
import {catchError, filter, take} from 'rxjs/operators';
import {MagicShelf, MagicShelfService} from './magic-shelf.service';
import {BookRuleEvaluatorService} from './book-rule-evaluator.service';
import {BookService} from '../../book/service/book.service';
import {AuthService} from '../../../shared/service/auth.service';
import {BookState} from '../../book/model/state/book-state.model';
import {mockHttpClientProvider} from '../../../../testing/providers';
import {createMockBook} from '../../../../testing/factories';
import type {GroupRule} from '../component/magic-shelf-component';

function createMockMagicShelf(overrides: Partial<MagicShelf> = {}): MagicShelf {
  const group: GroupRule = {
    name: 'test',
    type: 'group',
    join: 'and',
    rules: [{field: 'title', operator: 'contains', value: 'test'}]
  };

  return {
    id: 1,
    name: 'Test Shelf',
    icon: 'pi pi-book',
    iconType: 'PRIME_NG',
    filterJson: JSON.stringify(group),
    isPublic: false,
    ...overrides
  };
}

describe('MagicShelfService', () => {
  let service: MagicShelfService;
  let httpClient: {get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn>};
  let tokenSubject: BehaviorSubject<string | null>;
  let bookStateSubject: BehaviorSubject<BookState>;
  let evaluatorMock: {evaluateGroup: ReturnType<typeof vi.fn>};
  let subscriptions: Subscription[];
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
    tokenSubject = new BehaviorSubject<string | null>('token');
    bookStateSubject = new BehaviorSubject<BookState>({books: [], loaded: true, error: null});
    evaluatorMock = {evaluateGroup: vi.fn(() => false)};
    subscriptions = [];

    TestBed.configureTestingModule({
      providers: [
        MagicShelfService,
        mockHttpClientProvider,
        {provide: AuthService, useValue: {token$: tokenSubject.asObservable()}},
        {provide: BookService, useValue: {bookState$: bookStateSubject.asObservable()}},
        {provide: BookRuleEvaluatorService, useValue: evaluatorMock}
      ]
    });

    service = TestBed.inject(MagicShelfService);
    httpClient = TestBed.inject(HttpClient) as unknown as typeof httpClient;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    subscriptions.forEach(sub => sub.unsubscribe());
    tokenSubject.complete();
    bookStateSubject.complete();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  async function loadShelves(shelves: MagicShelf[]): Promise<void> {
    httpClient.get.mockReturnValue(of(shelves));
    const state = await firstValueFrom(
      service.shelvesState$.pipe(filter(s => s.loaded && s.shelves !== null), take(1))
    );
    expect(state.shelves).toEqual(shelves);
  }

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('shelvesState$ loading', () => {
    it('should fetch shelves on subscription when not loaded', async () => {
      const shelf = createMockMagicShelf();
      httpClient.get.mockReturnValue(of([shelf]));

      const state = await firstValueFrom(
        service.shelvesState$.pipe(filter(s => s.loaded && s.shelves !== null), take(1))
      );

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/magic-shelves');
      expect(state.shelves).toEqual([shelf]);
    });

    it('should store an error message when fetching shelves fails', async () => {
      httpClient.get.mockReturnValue(throwError(() => ({message: 'shelf fetch failed'})));

      const state = await firstValueFrom(
        service.shelvesState$.pipe(filter(s => s.loaded && s.error !== null), take(1))
      );

      expect(state.error).toBe('shelf fetch failed');
    });
  });

  describe('auth token changes', () => {
    it('should clear shelves when the auth token becomes null', async () => {
      await loadShelves([createMockMagicShelf()]);

      const states: {shelves: MagicShelf[] | null; loaded: boolean}[] = [];
      subscriptions.push(
        service.shelvesState$.subscribe(state => states.push({shelves: state.shelves, loaded: state.loaded}))
      );

      tokenSubject.next(null);

      const lastState = states[states.length - 1];
      expect(lastState.shelves).toBeNull();
      expect(lastState.loaded).toBe(true);
    });
  });

  describe('saveShelf', () => {
    it('should create a new shelf and add it to state', async () => {
      await loadShelves([]);

      const group: GroupRule = {name: 'g', type: 'group', join: 'and', rules: []};
      const newShelf = createMockMagicShelf({id: 99, name: 'Brand New'});
      httpClient.post.mockReturnValue(of(newShelf));

      const result = await firstValueFrom(
        service.saveShelf({name: 'Brand New', icon: 'pi pi-book', group, isPublic: true})
      );

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/magic-shelves',
        {
          id: undefined,
          name: 'Brand New',
          icon: 'pi pi-book',
          iconType: undefined,
          filterJson: JSON.stringify(group),
          isPublic: true
        }
      );
      expect(result).toEqual(newShelf);

      const shelf = await firstValueFrom(service.getShelf(99));
      expect(shelf).toEqual(newShelf);
    });

    it('should update an existing shelf in state', async () => {
      const existing = createMockMagicShelf({id: 1, name: 'Original'});
      await loadShelves([existing]);

      const group: GroupRule = {name: 'g', type: 'group', join: 'and', rules: []};
      const updated = createMockMagicShelf({id: 1, name: 'Updated', icon: 'pi pi-star', iconType: 'PRIME_NG'});
      httpClient.post.mockReturnValue(of(updated));

      await firstValueFrom(service.saveShelf({id: 1, name: 'Updated', icon: 'pi pi-star', iconType: 'PRIME_NG', group}));

      const shelf = await firstValueFrom(service.getShelf(1));
      expect(shelf?.name).toBe('Updated');
    });

    it('should update error state when saving fails', async () => {
      const group: GroupRule = {name: 'g', type: 'group', join: 'and', rules: []};
      httpClient.post.mockReturnValue(throwError(() => ({message: 'save failed'})));

      const result = await firstValueFrom(
        service.saveShelf({name: 'Bad', icon: null, group}).pipe(catchError(err => of(err)))
      );

      expect(result.message).toBe('save failed');
      const state = await firstValueFrom(service.shelvesState$.pipe(take(1)));
      expect(state.error).toBe('save failed');
    });
  });

  describe('deleteShelf', () => {
    it('should delete a shelf and remove it from state', async () => {
      const shelfA = createMockMagicShelf({id: 1});
      const shelfB = createMockMagicShelf({id: 2, name: 'Other'});
      await loadShelves([shelfA, shelfB]);

      await firstValueFrom(service.deleteShelf(1));

      expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/magic-shelves/1');
      const remaining = await firstValueFrom(service.getShelf(1));
      expect(remaining).toBeUndefined();
      const stillThere = await firstValueFrom(service.getShelf(2));
      expect(stillThere).toEqual(shelfB);
    });

    it('should update error state when deletion fails', async () => {
      await loadShelves([createMockMagicShelf()]);
      httpClient.delete.mockReturnValue(throwError(() => ({message: 'delete failed'})));

      const result = await firstValueFrom(
        service.deleteShelf(1).pipe(catchError(err => of(err)))
      );

      expect(result.message).toBe('delete failed');
      const state = await firstValueFrom(service.shelvesState$.pipe(take(1)));
      expect(state.error).toBe('delete failed');
    });
  });

  describe('getBookCount', () => {
    it('should count books matching a shelf filter', async () => {
      const group: GroupRule = {name: 'g', type: 'group', join: 'and', rules: []};
      const shelf = createMockMagicShelf({id: 10, filterJson: JSON.stringify(group)});
      await loadShelves([shelf]);

      const bookA = createMockBook({id: 1});
      const bookB = createMockBook({id: 2});
      evaluatorMock.evaluateGroup.mockImplementation((book: ReturnType<typeof createMockBook>) => book.id === 1);

      bookStateSubject.next({books: [bookA, bookB], loaded: true, error: null});

      const count = await firstValueFrom(service.getBookCount(10));

      expect(count).toBe(1);
      expect(evaluatorMock.evaluateGroup).toHaveBeenCalledWith(bookA, group, [bookA, bookB]);
      expect(evaluatorMock.evaluateGroup).toHaveBeenCalledWith(bookB, group, [bookA, bookB]);
    });

    it('should return 0 when the shelf does not exist', async () => {
      await loadShelves([createMockMagicShelf({id: 1})]);

      const count = await firstValueFrom(service.getBookCount(999));

      expect(count).toBe(0);
    });

    it('should return 0 when the shelf filter JSON is invalid', async () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      const shelf = createMockMagicShelf({id: 20, filterJson: '{invalid'});
      await loadShelves([shelf]);

      bookStateSubject.next({books: [createMockBook()], loaded: true, error: null});

      const count = await firstValueFrom(service.getBookCount(20));

      expect(count).toBe(0);
      consoleSpy.mockRestore();
    });

    it('should count 0 when no books are loaded', async () => {
      const group: GroupRule = {name: 'g', type: 'group', join: 'and', rules: []};
      const shelf = createMockMagicShelf({id: 30, filterJson: JSON.stringify(group)});
      await loadShelves([shelf]);

      bookStateSubject.next({books: null, loaded: false, error: null});

      const count = await firstValueFrom(service.getBookCount(30));

      expect(count).toBe(0);
    });
  });
});
