import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {lastValueFrom, Observable} from 'rxjs';
import {infiniteQueryOptions, queryOptions} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {PagedBooksBaseQuery, PagedBooksQuery, PagedBooksResponse} from '../model/paged-book.model';
import {
  buildPagedBooksParams,
  nextPageParam,
  pagedBooksQueryKey,
  previousPageParam,
} from './paged-book-query';

/**
 * Data-access layer for server-side book paging (`GET /api/v1/app/books`).
 *
 * Coexists with the current whole-library in-memory store (BookService): this
 * fetches one page at a time and never materializes the full library. Consumers
 * (book-browser, later) attach `injectInfiniteQuery(service.infiniteQueryOptions(...))`
 * for cursored scrolling, or `injectQuery(service.pageQueryOptions(...))` for a
 * single page.
 */
@Injectable({
  providedIn: 'root',
})
export class PagedBookService {
  private http = inject(HttpClient);

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/app/books`;

  fetchPage(query: PagedBooksQuery): Observable<PagedBooksResponse> {
    return this.http.get<PagedBooksResponse>(this.url, {params: buildPagedBooksParams(query)});
  }

  /** Options for a single-page `injectQuery`. */
  pageQueryOptions(query: PagedBooksQuery) {
    return queryOptions({
      queryKey: [...pagedBooksQueryKey(query), {page: query.page ?? 0}],
      queryFn: () => lastValueFrom(this.fetchPage(query)),
    });
  }

  /** Options for a cursored `injectInfiniteQuery` over all pages of a base query. */
  infiniteQueryOptions(baseQuery: PagedBooksBaseQuery) {
    return infiniteQueryOptions({
      queryKey: pagedBooksQueryKey(baseQuery),
      queryFn: ({pageParam}) => lastValueFrom(this.fetchPage({...baseQuery, page: pageParam})),
      initialPageParam: 0,
      getNextPageParam: nextPageParam,
      getPreviousPageParam: previousPageParam,
    });
  }
}
