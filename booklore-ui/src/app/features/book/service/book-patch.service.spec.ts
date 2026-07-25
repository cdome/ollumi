import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';

import {BookPatchService} from './book-patch.service';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {Book, ReadStatus} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {makeBook} from '../../../testing/book.fixture';

const PROGRESS_URL = `${API_CONFIG.BASE_URL}/api/v1/books/progress`;

describe('BookPatchService', () => {
  let service: BookPatchService;
  let httpMock: HttpTestingController;
  let qc: QueryClient;

  beforeEach(() => {
    qc = new QueryClient();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTanStackQuery(qc),
        BookPatchService,
      ],
    });
    service = TestBed.inject(BookPatchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const cache = () => qc.getQueryData(BOOKS_QUERY_KEY) as Book[];

  describe('updateBookShelves', () => {
    it('posts the id/assign/unassign payload and patches returned books into the cache', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1, metadata: {title: 'Old'}})]);

      service.updateBookShelves(new Set([1]), new Set([10]), new Set([20])).subscribe();

      const req = httpMock.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/shelves`);
      expect(req.request.body).toEqual({bookIds: [1], shelvesToAssign: [10], shelvesToUnassign: [20]});
      req.flush([makeBook({id: 1, metadata: {title: 'New'}})]);

      expect(cache()[0].metadata!.title).toBe('New');
    });
  });

  describe('progress bodies', () => {
    it('savePdfProgress omits fileProgress when no bookFileId is given', () => {
      service.savePdfProgress(1, 5, 42).subscribe();
      const req = httpMock.expectOne(PROGRESS_URL);
      expect(req.request.body).toEqual({bookId: 1, pdfProgress: {page: 5, percentage: 42}});
      req.flush(null);
    });

    it('saveCbxProgress includes fileProgress when a bookFileId is given', () => {
      service.saveCbxProgress(1, 7, 30, 99).subscribe();
      const req = httpMock.expectOne(PROGRESS_URL);
      expect(req.request.body).toEqual({
        bookId: 1,
        cbxProgress: {page: 7, percentage: 30},
        fileProgress: {bookFileId: 99, positionData: '7', progressPercent: 30},
      });
      req.flush(null);
    });
  });

  describe('saveEpubProgress (debounced subject)', () => {
    it('posts an epub progress body without fileProgress when no bookFileId', () => {
      service.saveEpubProgress(1, 'epubcfi(/6/2)', '/ch1', 12);
      const req = httpMock.expectOne(PROGRESS_URL);
      expect(req.request.body).toEqual({
        bookId: 1,
        epubProgress: {cfi: 'epubcfi(/6/2)', href: '/ch1', percentage: 12},
      });
      req.flush(null);
    });

    it('adds fileProgress when a bookFileId is supplied', () => {
      service.saveEpubProgress(1, 'epubcfi(/6/2)', '/ch1', 12, 55);
      const req = httpMock.expectOne(PROGRESS_URL);
      expect(req.request.body).toMatchObject({
        fileProgress: {bookFileId: 55, positionData: 'epubcfi(/6/2)', positionHref: '/ch1', progressPercent: 12},
      });
      req.flush(null);
    });

    it('dedupes identical consecutive saves into a single request', () => {
      service.saveEpubProgress(1, 'cfi', '/c', 10);
      httpMock.expectOne(PROGRESS_URL).flush(null);
      // identical payload after the first settles → distinctUntilChanged drops it
      service.saveEpubProgress(1, 'cfi', '/c', 10);
      httpMock.expectNone(PROGRESS_URL);
    });
  });

  describe('updateBookReadStatus', () => {
    it('posts the status change and patches read-status fields into the cache', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1, readStatus: ReadStatus.UNREAD})]);

      service.updateBookReadStatus(1, ReadStatus.READ).subscribe();

      const req = httpMock.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/status`);
      expect(req.request.body).toEqual({bookIds: [1], status: ReadStatus.READ});
      req.flush([{bookId: 1, readStatus: ReadStatus.READ, readStatusModifiedTime: 't', dateFinished: '2026-01-01'}]);

      expect(cache()[0].readStatus).toBe(ReadStatus.READ);
      expect(cache()[0].dateFinished).toBe('2026-01-01');
    });
  });

  describe('resetProgress', () => {
    it('sends the type as a query param and clears the reader progress fields in the cache', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [
        makeBook({id: 1, epubProgress: {percentage: 50} as never, readStatus: ReadStatus.READING}),
      ]);

      service.resetProgress(1, ResetProgressTypes.GRIMMORY).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_CONFIG.BASE_URL}/api/v1/books/reset-progress`);
      expect(req.request.params.get('type')).toBe(ResetProgressTypes.GRIMMORY);
      expect(req.request.body).toEqual([1]);
      req.flush([{bookId: 1, readStatus: ReadStatus.UNREAD, readStatusModifiedTime: 't', dateFinished: null}]);

      expect(cache()[0].epubProgress).toBeUndefined();
      expect(cache()[0].readStatus).toBe(ReadStatus.UNREAD);
    });

    it('accepts a single id or an array', () => {
      service.resetProgress([1, 2], ResetProgressTypes.KOBO).subscribe();
      const req = httpMock.expectOne(r => r.url.endsWith('/reset-progress'));
      expect(req.request.body).toEqual([1, 2]);
      req.flush([]);
    });
  });

  describe('updatePersonalRating', () => {
    it('PUTs the rating and patches personalRating into the cache', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1, personalRating: 2})]);

      service.updatePersonalRating(1, 9).subscribe();

      const req = httpMock.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/personal-rating`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ids: [1], rating: 9});
      req.flush([{bookId: 1, personalRating: 9}]);

      expect(cache()[0].personalRating).toBe(9);
    });
  });

  describe('updateLastReadTime', () => {
    it('stamps lastReadTime on the cached book without any HTTP call', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1}), makeBook({id: 2})]);

      service.updateLastReadTime(1);

      expect(cache().find(b => b.id === 1)!.lastReadTime).toBeTruthy();
      expect(cache().find(b => b.id === 2)!.lastReadTime).toBeUndefined();
      httpMock.expectNone(() => true);
    });
  });
});
