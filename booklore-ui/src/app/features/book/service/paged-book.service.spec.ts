import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';

import {PagedBookService} from './paged-book.service';
import {PagedBooksResponse} from '../model/paged-book.model';
import {API_CONFIG} from '../../../core/config/api-config';

const BOOKS_URL = `${API_CONFIG.BASE_URL}/api/v1/app/books`;

function pageResponse(overrides: Partial<PagedBooksResponse> = {}): PagedBooksResponse {
  return {content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false, ...overrides};
}

describe('PagedBookService', () => {
  let service: PagedBookService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), PagedBookService],
    });
    service = TestBed.inject(PagedBookService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('fetchPage', () => {
    it('GETs the paged endpoint with the query serialized as params', () => {
      let result: PagedBooksResponse | undefined;
      service.fetchPage({page: 1, size: 50, libraryId: 3, sort: 'title', dir: 'asc'}).subscribe(r => (result = r));

      const req = httpMock.expectOne(r => r.url === BOOKS_URL);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('page')).toBe('1');
      expect(req.request.params.get('libraryId')).toBe('3');
      expect(req.request.params.get('sort')).toBe('title');

      const body = pageResponse({content: [{id: 1}], totalElements: 1, hasNext: true});
      req.flush(body);
      expect(result).toEqual(body);
    });

    it('omits absent filters from the request URL', () => {
      service.fetchPage({page: 0}).subscribe();
      const req = httpMock.expectOne(r => r.url === BOOKS_URL);
      expect(req.request.params.has('search')).toBe(false);
      expect(req.request.params.has('libraryId')).toBe(false);
      req.flush(pageResponse());
    });
  });

  describe('infiniteQueryOptions', () => {
    it('fetches the requested page cursor through queryFn and advances via getNextPageParam', async () => {
      const options = service.infiniteQueryOptions({libraryId: 3, sort: 'title'});

      expect(options.initialPageParam).toBe(0);

      const pagePromise = options.queryFn!({pageParam: 2} as never) as Promise<PagedBooksResponse>;
      const req = httpMock.expectOne(r => r.url === BOOKS_URL);
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('libraryId')).toBe('3');

      const body = pageResponse({page: 2, hasNext: true, hasPrevious: true});
      req.flush(body);
      const page = await pagePromise;

      expect(options.getNextPageParam(page, [page], 2, [2])).toBe(3);
      expect(options.getPreviousPageParam!(page, [page], 2, [2])).toBe(1);
    });
  });

  describe('pageQueryOptions', () => {
    it('builds a page-scoped key and fetches a single page', async () => {
      const options = service.pageQueryOptions({page: 0, libraryId: 5});
      expect(options.queryKey.at(-1)).toEqual({page: 0});

      const promise = options.queryFn!({} as never) as Promise<PagedBooksResponse>;
      httpMock.expectOne(r => r.url === BOOKS_URL).flush(pageResponse({content: [{id: 9}]}));
      const result = await promise;
      expect(result.content[0].id).toBe(9);
    });
  });
});
