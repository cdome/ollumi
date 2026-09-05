import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {config, firstValueFrom, of, throwError} from 'rxjs';
import {BookPatchService} from './book-patch.service';
import {BookStateService} from './book-state.service';
import {AuthService} from '../../../shared/service/auth.service';
import {mockHttpClientProvider, mockAuthServiceProvider} from '../../../../testing/providers';
import {createMockBook} from '../../../../testing/factories';
import {ReadStatus} from '../model/book.model';
import {ResetProgressType} from '../../../shared/constants/reset-progress-type';

describe('BookPatchService', () => {
  let service: BookPatchService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let bookStateService: BookStateService;
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
    TestBed.configureTestingModule({
      providers: [
        BookPatchService,
        BookStateService,
        mockHttpClientProvider,
        mockAuthServiceProvider
      ]
    });

    service = TestBed.inject(BookPatchService);
    httpClient = TestBed.inject(HttpClient) as any;
    bookStateService = TestBed.inject(BookStateService);

    vi.clearAllMocks();
    httpClient.post.mockReturnValue(of(undefined));
    httpClient.put.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('updateBookShelves', () => {
    it('should send a shelves update request and refresh the state', async () => {
      const book1 = createMockBook({id: 1, shelves: []});
      const book2 = createMockBook({id: 2, shelves: []});
      const updated = createMockBook({id: 1, shelves: [{id: 5, name: 'Shelf', icon: '', iconType: 'PRIME_NG', bookCount: 0}]});
      bookStateService.updateBookState({books: [book1, book2], loaded: true, error: null});
      httpClient.post.mockReturnValue(of([updated]));

      const result = await firstValueFrom(service.updateBookShelves(new Set([1]), new Set([5]), new Set([])));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/shelves',
        {bookIds: [1], shelvesToAssign: [5], shelvesToUnassign: []}
      );
      expect(result).toEqual([updated]);
      expect(bookStateService.getCurrentBookState().books).toEqual([updated, book2]);
    });

    it('should propagate errors without updating the state', async () => {
      bookStateService.updateBookState({books: [createMockBook({id: 1})], loaded: true, error: null});
      httpClient.post.mockReturnValue(throwError(() => ({message: 'Shelves failed'})));

      service.updateBookShelves(new Set([1]), new Set([]), new Set([])).subscribe({error: () => {}});
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(bookStateService.getCurrentBookState().error).toBeNull();
    });
  });

  describe('savePdfProgress', () => {
    it('should post PDF progress without a file id', async () => {
      await firstValueFrom(service.savePdfProgress(1, 42, 0.5));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/progress',
        {
          bookId: 1,
          pdfProgress: {page: 42, percentage: 0.5}
        }
      );
    });

    it('should include file-level progress when a bookFileId is provided', async () => {
      await firstValueFrom(service.savePdfProgress(2, 10, 0.25, 7));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/progress',
        {
          bookId: 2,
          pdfProgress: {page: 10, percentage: 0.25},
          fileProgress: {bookFileId: 7, positionData: '10', progressPercent: 0.25}
        }
      );
    });
  });

  describe('saveEpubProgress', () => {
    it('should emit EPUB progress through the shared subject', () => {
      service.saveEpubProgress(3, 'epubcfi(/6/2)', 'chapter.xhtml', 0.75);

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/progress',
        {
          bookId: 3,
          epubProgress: {cfi: 'epubcfi(/6/2)', href: 'chapter.xhtml', percentage: 0.75}
        }
      );
    });

    it('should include file-level progress for EPUB when a bookFileId is provided', () => {
      service.saveEpubProgress(4, 'epubcfi(/8/4)', 'toc.xhtml', 0.33, 9);

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/progress',
        {
          bookId: 4,
          epubProgress: {cfi: 'epubcfi(/8/4)', href: 'toc.xhtml', percentage: 0.33},
          fileProgress: {bookFileId: 9, positionData: 'epubcfi(/8/4)', positionHref: 'toc.xhtml', progressPercent: 0.33}
        }
      );
    });

    it('should suppress duplicate consecutive EPUB progress values', () => {
      service.saveEpubProgress(5, 'cfi', 'href', 0.1);
      service.saveEpubProgress(5, 'cfi', 'href', 0.1);

      expect(httpClient.post).toHaveBeenCalledTimes(1);
    });
  });

  describe('saveCbxProgress', () => {
    it('should post CBX progress without a file id', async () => {
      await firstValueFrom(service.saveCbxProgress(6, 12, 0.4));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/progress',
        {
          bookId: 6,
          cbxProgress: {page: 12, percentage: 0.4}
        }
      );
    });

    it('should include file-level progress for CBX when a bookFileId is provided', async () => {
      await firstValueFrom(service.saveCbxProgress(7, 3, 0.1, 11));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/progress',
        {
          bookId: 7,
          cbxProgress: {page: 3, percentage: 0.1},
          fileProgress: {bookFileId: 11, positionData: '3', progressPercent: 0.1}
        }
      );
    });
  });

  describe('saveFileProgress', () => {
    it('should post raw file progress', async () => {
      const fileProgress = {bookFileId: 13, positionData: 'pos', positionHref: 'href', progressPercent: 0.9};
      await firstValueFrom(service.saveFileProgress(8, fileProgress));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/progress',
        {bookId: 8, fileProgress}
      );
    });
  });

  describe('updateDateFinished', () => {
    it('should update the state with a new finish date', async () => {
      const book = createMockBook({id: 1, dateFinished: undefined});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      await firstValueFrom(service.updateDateFinished(1, '2026-01-15'));

      expect(bookStateService.getCurrentBookState().books![0].dateFinished).toBe('2026-01-15');
    });

    it('should clear the finish date in the state when null is provided', async () => {
      const book = createMockBook({id: 1, dateFinished: '2026-01-15'});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      await firstValueFrom(service.updateDateFinished(1, null));

      expect(bookStateService.getCurrentBookState().books![0].dateFinished).toBeUndefined();
    });
  });

  describe('resetProgress', () => {
    it('should reset BOOKLORE progress and update state', async () => {
      const book = createMockBook({
        id: 1,
        readStatus: ReadStatus.READ,
        epubProgress: {cfi: 'cfi', percentage: 1},
        pdfProgress: {page: 10, percentage: 1},
        cbxProgress: {page: 5, percentage: 1}
      });
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      const response = [{bookId: 1, readStatus: ReadStatus.UNREAD, readStatusModifiedTime: 'now'}];
      httpClient.post.mockReturnValue(of(response));

      await firstValueFrom(service.resetProgress(1, 'BOOKLORE'));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/reset-progress',
        [1],
        expect.objectContaining({params: expect.any(Object)})
      );
      const calledUrl = httpClient.post.mock.calls[0][0] as string;
      expect(calledUrl).toBe('http://localhost:6060/api/v1/books/reset-progress');
      const updated = bookStateService.getCurrentBookState().books![0];
      expect(updated.readStatus).toBe(ReadStatus.UNREAD);
      expect(updated.epubProgress).toBeUndefined();
      expect(updated.pdfProgress).toBeUndefined();
      expect(updated.cbxProgress).toBeUndefined();
    });

    it('should reset KOREADER progress and preserve other progress fields', async () => {
      const book = createMockBook({
        id: 1,
        readStatus: ReadStatus.READ,
        epubProgress: {cfi: 'cfi', percentage: 1},
        koreaderProgress: {percentage: 0.8}
      });
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.post.mockReturnValue(of([{bookId: 1, readStatus: ReadStatus.UNREAD, readStatusModifiedTime: 'now'}]));

      await firstValueFrom(service.resetProgress([1], 'KOREADER'));

      const updated = bookStateService.getCurrentBookState().books![0];
      expect(updated.koreaderProgress).toBeUndefined();
      expect(updated.epubProgress).toBeDefined();
    });
  });

  describe('updateBookReadStatus', () => {
    it('should update the read status in state', async () => {
      const book = createMockBook({id: 1, readStatus: ReadStatus.UNREAD});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.post.mockReturnValue(of([{bookId: 1, readStatus: ReadStatus.READ, readStatusModifiedTime: 'now'}]));

      await firstValueFrom(service.updateBookReadStatus([1, 2], ReadStatus.READ));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/status',
        {bookIds: [1, 2], status: ReadStatus.READ}
      );
      expect(bookStateService.getCurrentBookState().books![0].readStatus).toBe(ReadStatus.READ);
    });
  });

  describe('resetPersonalRating', () => {
    it('should reset the personal rating in state', async () => {
      const book = createMockBook({id: 1, personalRating: 5});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.post.mockReturnValue(of([{bookId: 1, personalRating: null}]));

      await firstValueFrom(service.resetPersonalRating(1));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/reset-personal-rating',
        [1]
      );
      expect(bookStateService.getCurrentBookState().books![0].personalRating).toBeNull();
    });
  });

  describe('updatePersonalRating', () => {
    it('should update the personal rating in state', async () => {
      const book = createMockBook({id: 1, personalRating: 2});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.put.mockReturnValue(of([{bookId: 1, personalRating: 4}]));

      await firstValueFrom(service.updatePersonalRating([1, 3], 4));

      expect(httpClient.put).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/personal-rating',
        {ids: [1, 3], rating: 4}
      );
      expect(bookStateService.getCurrentBookState().books![0].personalRating).toBe(4);
    });
  });

  describe('updateLastReadTime', () => {
    it('should update the last read time locally', () => {
      const book = createMockBook({id: 1, lastReadTime: undefined});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      service.updateLastReadTime(1);

      const updated = bookStateService.getCurrentBookState().books![0];
      expect(updated.lastReadTime).toBeDefined();
      expect(new Date(updated.lastReadTime!).getTime()).toBeGreaterThan(0);
    });
  });
});
