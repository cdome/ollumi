import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {firstValueFrom} from 'rxjs';

import {BookService} from './book.service';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {AuthService} from '../../../shared/service/auth.service';
import {Book} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {makeBook} from '../../../testing/book.fixture';

const BOOKS_URL = `${API_CONFIG.BASE_URL}/api/v1/books`;

describe('BookService', () => {
  let service: BookService;
  let httpMock: HttpTestingController;
  let qc: QueryClient;
  let messages: {add: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    qc = new QueryClient();
    messages = {add: vi.fn()};
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTanStackQuery(qc),
        // token null → the injectQuery-backed books query stays disabled (no auto-fetch)
        {provide: AuthService, useValue: {token: signal(null)}},
        {provide: MessageService, useValue: messages},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
        {provide: Router, useValue: {navigate: vi.fn().mockResolvedValue(true)}},
        BookService,
      ],
    });
    service = TestBed.inject(BookService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const cache = () => qc.getQueryData(BOOKS_QUERY_KEY) as Book[];

  describe('deleteBooks', () => {
    it('sends a comma-joined id list, prunes queries and shows a success toast', () => {
      const invalidate = vi.spyOn(qc, 'invalidateQueries');
      service.deleteBooks(new Set([1, 2])).subscribe();

      const req = httpMock.expectOne(r => r.url === BOOKS_URL && r.method === 'DELETE');
      expect(req.request.params.get('ids')).toBe('1,2');
      req.flush({deleted: [1, 2], failedFileDeletions: []});

      expect(invalidate).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY, exact: true});
      expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('warns when some files could not be deleted', () => {
      service.deleteBooks(new Set([1])).subscribe();
      httpMock.expectOne(r => r.method === 'DELETE').flush({deleted: [1], failedFileDeletions: [1]});
      expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'warn'}));
    });

    it('shows an error toast and rethrows on failure', () => {
      let errored = false;
      service.deleteBooks(new Set([1])).subscribe({error: () => (errored = true)});
      httpMock.expectOne(r => r.method === 'DELETE').flush(
        {message: 'boom'}, {status: 500, statusText: 'Server Error'},
      );
      expect(errored).toBe(true);
      expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });

  describe('togglePhysicalFlag', () => {
    it('PATCHes the physical flag and patches the returned book into the cache', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1, isPhysical: false})]);

      service.togglePhysicalFlag(1, true).subscribe();

      const req = httpMock.expectOne(r => r.url === `${BOOKS_URL}/1/physical` && r.method === 'PATCH');
      expect(req.request.params.get('physical')).toBe('true');
      req.flush(makeBook({id: 1, isPhysical: true}));

      expect(cache()[0].isPhysical).toBe(true);
    });
  });

  describe('removeBooksFromShelf', () => {
    it('strips the shelf from every cached book without any HTTP call', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [
        makeBook({id: 1, shelves: [{id: 5, name: 'A'}, {id: 6, name: 'B'}]}),
        makeBook({id: 2, shelves: [{id: 5, name: 'A'}]}),
      ]);

      service.removeBooksFromShelf(5);

      expect(cache()[0].shelves!.map(s => s.id)).toEqual([6]);
      expect(cache()[1].shelves).toEqual([]);
      httpMock.expectNone(() => true);
    });
  });

  describe('getBooksInSeries', () => {
    it('returns all books sharing the target book\'s series (case-insensitive)', async () => {
      // ensureQueryData starts the fetch synchronously when the method is called
      const obs = service.getBooksInSeries(1);
      const req = httpMock.expectOne(BOOKS_URL);
      req.flush([
        makeBook({id: 1, metadata: {seriesName: 'Dune'}}),
        makeBook({id: 2, metadata: {seriesName: 'dune'}}),
        makeBook({id: 3, metadata: {seriesName: 'Amber'}}),
      ]);

      const result = await firstValueFrom(obs);
      expect(result.map(b => b.id)).toEqual([1, 2]);
    });

    it('returns an empty list when the target book has no series', async () => {
      const obs = service.getBooksInSeries(1);
      httpMock.expectOne(BOOKS_URL).flush([makeBook({id: 1, metadata: {seriesName: undefined}})]);

      expect(await firstValueFrom(obs)).toEqual([]);
    });
  });
});
