import {HttpParams} from '@angular/common/http';

import {PagedBooksBaseQuery, PagedBooksQuery, PagedBooksResponse} from '../model/paged-book.model';

export const PAGED_BOOKS_QUERY_KEY = ['books', 'paged'] as const;

/**
 * Order-independent cache key for a paged-books query. The params are sorted so
 * that two queries differing only in property order share a cache entry.
 */
export function pagedBooksQueryKey(query: PagedBooksBaseQuery): readonly unknown[] {
  const normalized: Record<string, unknown> = {};
  for (const key of Object.keys(query).sort()) {
    const value = (query as Record<string, unknown>)[key];
    if (value !== undefined && value !== null && value !== '') {
      normalized[key] = value;
    }
  }
  return [...PAGED_BOOKS_QUERY_KEY, normalized];
}

/**
 * Serializes a query to HttpParams, omitting empty values so the request URL
 * carries only the filters actually in effect.
 */
export function buildPagedBooksParams(query: PagedBooksQuery): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

/** getNextPageParam for an infinite query: the next page index, or undefined at the end. */
export function nextPageParam(lastPage: PagedBooksResponse): number | undefined {
  return lastPage.hasNext ? lastPage.page + 1 : undefined;
}

/** getPreviousPageParam for an infinite query: the previous page index, or undefined at the start. */
export function previousPageParam(firstPage: PagedBooksResponse): number | undefined {
  return firstPage.hasPrevious ? firstPage.page - 1 : undefined;
}

/** Flattens the accumulated pages of an infinite query into a single row list. */
export function flattenPages(pages: PagedBooksResponse[] | undefined): PagedBooksResponse['content'] {
  return (pages ?? []).flatMap(p => p.content);
}
