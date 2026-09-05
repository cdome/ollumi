import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject} from 'rxjs';
import {BookSocketService} from './book-socket.service';
import {BookStateService} from './book-state.service';
import {BookState} from '../model/state/book-state.model';
import {createMockBook, createMockBookMetadata} from '../../../../testing/factories';

describe('BookSocketService', () => {
  let service: BookSocketService;
  let bookStateSubject: BehaviorSubject<BookState>;
  let updateBookStateSpy: ReturnType<typeof vi.fn>;

  function getUpdatedState(): BookState | undefined {
    const calls = updateBookStateSpy.mock.calls;
    return calls[calls.length - 1]?.[0] as BookState | undefined;
  }

  beforeEach(() => {
    bookStateSubject = new BehaviorSubject<BookState>({books: [], loaded: true, error: null});
    updateBookStateSpy = vi.fn(state => bookStateSubject.next(state));

    TestBed.configureTestingModule({
      providers: [
        BookSocketService,
        {
          provide: BookStateService,
          useValue: {
            bookState$: bookStateSubject.asObservable(),
            getCurrentBookState: () => bookStateSubject.value,
            updateBookState: updateBookStateSpy,
            resetBookState: vi.fn()
          }
        }
      ]
    });

    service = TestBed.inject(BookSocketService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('handleNewlyCreatedBook', () => {
    it('should add a new book to an empty state', () => {
      const newBook = createMockBook({id: 10});
      service.handleNewlyCreatedBook(newBook);

      const updatedState = getUpdatedState();
      expect(updatedState?.books).toHaveLength(1);
      expect(updatedState?.books?.[0].id).toBe(10);
    });

    it('should replace an existing book with the same id', () => {
      const existingBook = createMockBook({id: 5, fileName: 'old.epub'});
      bookStateSubject.next({books: [existingBook], loaded: true, error: null});

      const updatedBook = createMockBook({id: 5, fileName: 'new.epub'});
      service.handleNewlyCreatedBook(updatedBook);

      const updatedState = getUpdatedState();
      expect(updatedState?.books).toHaveLength(1);
      expect(updatedState?.books?.[0].fileName).toBe('new.epub');
    });
  });

  describe('handleRemovedBookIds', () => {
    it('should remove books with the given ids', () => {
      bookStateSubject.next({
        books: [createMockBook({id: 1}), createMockBook({id: 2}), createMockBook({id: 3})],
        loaded: true,
        error: null
      });

      service.handleRemovedBookIds([1, 3]);

      const updatedState = getUpdatedState();
      expect(updatedState?.books).toHaveLength(1);
      expect(updatedState?.books?.[0].id).toBe(2);
    });

    it('should handle empty state', () => {
      bookStateSubject.next({books: null, loaded: true, error: null});

      service.handleRemovedBookIds([1]);

      const updatedState = getUpdatedState();
      expect(updatedState?.books).toEqual([]);
    });
  });

  describe('handleBookUpdate', () => {
    it('should update a matching book and leave others unchanged', () => {
      bookStateSubject.next({
        books: [createMockBook({id: 1, fileName: 'a.epub'}), createMockBook({id: 2, fileName: 'b.epub'})],
        loaded: true,
        error: null
      });

      const updatedBook = createMockBook({id: 1, fileName: 'updated.epub'});
      service.handleBookUpdate(updatedBook);

      const updatedState = getUpdatedState();
      expect(updatedState?.books?.find(b => b.id === 1)?.fileName).toBe('updated.epub');
      expect(updatedState?.books?.find(b => b.id === 2)?.fileName).toBe('b.epub');
    });
  });

  describe('handleMultipleBookUpdates', () => {
    it('should merge multiple updated books into the current state', () => {
      bookStateSubject.next({
        books: [
          createMockBook({id: 1, fileName: 'a.epub'}),
          createMockBook({id: 2, fileName: 'b.epub'}),
          createMockBook({id: 3, fileName: 'c.epub'})
        ],
        loaded: true,
        error: null
      });

      service.handleMultipleBookUpdates([
        createMockBook({id: 1, fileName: 'a-new.epub'}),
        createMockBook({id: 2, fileName: 'b-new.epub'})
      ]);

      const updatedState = getUpdatedState();
      expect(updatedState?.books?.find(b => b.id === 1)?.fileName).toBe('a-new.epub');
      expect(updatedState?.books?.find(b => b.id === 2)?.fileName).toBe('b-new.epub');
      expect(updatedState?.books?.find(b => b.id === 3)?.fileName).toBe('c.epub');
    });
  });

  describe('handleBookMetadataUpdate', () => {
    it('should update metadata for the matching book only', () => {
      bookStateSubject.next({
        books: [createMockBook({id: 1}), createMockBook({id: 2})],
        loaded: true,
        error: null
      });

      const newMetadata = createMockBookMetadata({bookId: 1, title: 'Updated Title'});
      service.handleBookMetadataUpdate(1, newMetadata);

      const updatedState = getUpdatedState();
      expect(updatedState?.books?.find(b => b.id === 1)?.metadata?.title).toBe('Updated Title');
      expect(updatedState?.books?.find(b => b.id === 2)?.metadata?.title).toBe('Test Book');
    });
  });

  describe('handleMultipleBookCoverPatches', () => {
    it('should patch coverUpdatedOn dates for matching books', () => {
      bookStateSubject.next({
        books: [
          createMockBook({id: 1, metadata: createMockBookMetadata({coverUpdatedOn: 'old'})}),
          createMockBook({id: 2, metadata: createMockBookMetadata({coverUpdatedOn: 'old'})})
        ],
        loaded: true,
        error: null
      });

      service.handleMultipleBookCoverPatches([
        {id: 1, coverUpdatedOn: '2026-01-01'},
        {id: 99, coverUpdatedOn: 'ignored'}
      ]);

      const updatedState = getUpdatedState();
      expect(updatedState?.books?.find(b => b.id === 1)?.metadata?.coverUpdatedOn).toBe('2026-01-01');
      expect(updatedState?.books?.find(b => b.id === 2)?.metadata?.coverUpdatedOn).toBe('old');
    });

    it('should do nothing when patches are empty', () => {
      service.handleMultipleBookCoverPatches([]);
      expect(updateBookStateSpy).not.toHaveBeenCalled();
    });
  });
});
