import {beforeEach, afterEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject, firstValueFrom, take, toArray} from 'rxjs';
import {SeriesDataService} from './series-data.service';
import {BookService} from '../../book/service/book.service';
import {BookState} from '../../book/model/state/book-state.model';
import {ReadStatus} from '../../book/model/book.model';
import {createMockBook, createMockBookMetadata} from '../../../../testing/factories';

describe('SeriesDataService', () => {
  let service: SeriesDataService;
  let bookStateSubject: BehaviorSubject<BookState>;

  beforeEach(() => {
    bookStateSubject = new BehaviorSubject<BookState>({books: null, loaded: false, error: null});

    TestBed.configureTestingModule({
      providers: [
        SeriesDataService,
        {provide: BookService, useValue: {bookState$: bookStateSubject.asObservable()}}
      ]
    });

    service = TestBed.inject(SeriesDataService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should emit an empty array when no books have a series name', async () => {
    bookStateSubject.next({
      books: [createMockBook({id: 1, metadata: createMockBookMetadata({seriesName: undefined})})],
      loaded: true,
      error: null
    });

    const result = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(result).toEqual([]);
  });

  it('should build a series summary from matching books', async () => {
    const book1 = createMockBook({
      id: 1,
      readStatus: ReadStatus.READ,
      lastReadTime: '2026-01-01T10:00:00.000Z',
      addedOn: '2026-01-01T08:00:00.000Z',
      metadata: createMockBookMetadata({
        seriesName: 'Dune',
        seriesNumber: 1,
        authors: ['Frank Herbert'],
        categories: ['Sci-Fi']
      })
    });
    const book2 = createMockBook({
      id: 2,
      readStatus: ReadStatus.UNREAD,
      lastReadTime: '2026-01-02T10:00:00.000Z',
      addedOn: '2026-01-02T08:00:00.000Z',
      metadata: createMockBookMetadata({
        seriesName: ' dune ',
        seriesNumber: 2,
        authors: ['Frank Herbert'],
        categories: ['Sci-Fi']
      })
    });

    bookStateSubject.next({books: [book2, book1], loaded: true, error: null});

    const result = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(result).toHaveLength(1);
    const summary = result[0];
    expect(summary.seriesName).toBe('Dune');
    expect(summary.books).toEqual([book1, book2]);
    expect(summary.authors).toEqual(['Frank Herbert']);
    expect(summary.categories).toEqual(['Sci-Fi']);
    expect(summary.bookCount).toBe(2);
    expect(summary.readCount).toBe(1);
    expect(summary.progress).toBe(0.5);
    expect(summary.seriesStatus).toBe(ReadStatus.PARTIALLY_READ);
    expect(summary.nextUnread).toBe(book2);
    expect(summary.lastReadTime).toBe('2026-01-02T10:00:00.000Z');
    expect(summary.addedOn).toBe('2026-01-02T08:00:00.000Z');
    expect(summary.coverBooks).toEqual([book1, book2]);
  });

  it('should sort series books by seriesNumber', async () => {
    const book1 = createMockBook({
      id: 1,
      metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 1})
    });
    const book2 = createMockBook({
      id: 2,
      metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 2})
    });
    const book3 = createMockBook({
      id: 3,
      metadata: createMockBookMetadata({seriesName: 'Dune'}) // no series number
    });

    bookStateSubject.next({books: [book2, book3, book1], loaded: true, error: null});

    const result = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(result[0].books.map(b => b.id)).toEqual([1, 2, 3]);
  });

  it('should compute READ status when every book is read', async () => {
    const books = [
      createMockBook({id: 1, readStatus: ReadStatus.READ, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 1})}),
      createMockBook({id: 2, readStatus: ReadStatus.READ, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 2})})
    ];

    bookStateSubject.next({books, loaded: true, error: null});

    const result = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(result[0].seriesStatus).toBe(ReadStatus.READ);
    expect(result[0].nextUnread).toBeNull();
  });

  it('should compute WONT_READ status when any book is marked WONT_READ', async () => {
    const books = [
      createMockBook({id: 1, readStatus: ReadStatus.READ, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 1})}),
      createMockBook({id: 2, readStatus: ReadStatus.WONT_READ, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 2})})
    ];

    bookStateSubject.next({books, loaded: true, error: null});

    const result = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(result[0].seriesStatus).toBe(ReadStatus.WONT_READ);
  });

  it('should compute ABANDONED status when any book is abandoned', async () => {
    const books = [
      createMockBook({id: 1, readStatus: ReadStatus.READ, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 1})}),
      createMockBook({id: 2, readStatus: ReadStatus.ABANDONED, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 2})})
    ];

    bookStateSubject.next({books, loaded: true, error: null});

    const result = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(result[0].seriesStatus).toBe(ReadStatus.ABANDONED);
  });

  it('should compute READING status when at least one book is being read', async () => {
    const books = [
      createMockBook({id: 1, readStatus: ReadStatus.READ, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 1})}),
      createMockBook({id: 2, readStatus: ReadStatus.READING, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 2})})
    ];

    bookStateSubject.next({books, loaded: true, error: null});

    const result = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(result[0].seriesStatus).toBe(ReadStatus.READING);
  });

  it('should compute UNREAD status when every book is unread', async () => {
    const books = [
      createMockBook({id: 1, readStatus: ReadStatus.UNREAD, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 1})}),
      createMockBook({id: 2, readStatus: ReadStatus.UNREAD, metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 2})})
    ];

    bookStateSubject.next({books, loaded: true, error: null});

    const result = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(result[0].seriesStatus).toBe(ReadStatus.UNREAD);
  });

  it('should shareReplay the latest computed summaries', async () => {
    const book = createMockBook({
      id: 1,
      metadata: createMockBookMetadata({seriesName: 'Dune', seriesNumber: 1})
    });

    bookStateSubject.next({books: [book], loaded: true, error: null});

    const first = await firstValueFrom(service.allSeries$.pipe(take(1)));
    const second = await firstValueFrom(service.allSeries$.pipe(take(1)));

    expect(first).toBe(second);
  });
});
