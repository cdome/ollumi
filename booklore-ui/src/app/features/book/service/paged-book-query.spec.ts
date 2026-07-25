import {describe, expect, it} from 'vitest';

import {
  buildPagedBooksParams,
  flattenPages,
  nextPageParam,
  PAGED_BOOKS_QUERY_KEY,
  pagedBooksQueryKey,
  previousPageParam,
} from './paged-book-query';
import {PagedBooksResponse} from '../model/paged-book.model';
import {ReadStatus} from '../model/book.model';

function pageResponse(overrides: Partial<PagedBooksResponse> = {}): PagedBooksResponse {
  return {
    content: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
    ...overrides,
  };
}

describe('buildPagedBooksParams', () => {
  it('serializes present params and stringifies numbers', () => {
    const params = buildPagedBooksParams({page: 2, size: 50, libraryId: 7, sort: 'title', dir: 'asc'});
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('50');
    expect(params.get('libraryId')).toBe('7');
    expect(params.get('sort')).toBe('title');
    expect(params.get('dir')).toBe('asc');
  });

  it('omits undefined, null and empty-string params', () => {
    const params = buildPagedBooksParams({
      page: 0,
      search: '',
      status: undefined,
      language: null as unknown as undefined,
    });
    expect(params.has('search')).toBe(false);
    expect(params.has('status')).toBe(false);
    expect(params.has('language')).toBe(false);
    expect(params.get('page')).toBe('0');
  });

  it('keeps a read status enum value', () => {
    const params = buildPagedBooksParams({status: ReadStatus.READING});
    expect(params.get('status')).toBe('READING');
  });
});

describe('pagedBooksQueryKey', () => {
  it('is prefixed with the paged-books namespace', () => {
    expect(pagedBooksQueryKey({}).slice(0, 2)).toEqual([...PAGED_BOOKS_QUERY_KEY]);
  });

  it('is independent of property order', () => {
    const a = pagedBooksQueryKey({libraryId: 1, sort: 'title'});
    const b = pagedBooksQueryKey({sort: 'title', libraryId: 1});
    expect(a).toEqual(b);
  });

  it('drops empty values so they do not fragment the cache', () => {
    const withEmpties = pagedBooksQueryKey({libraryId: 1, search: '', status: undefined});
    const clean = pagedBooksQueryKey({libraryId: 1});
    expect(withEmpties).toEqual(clean);
  });

  it('distinguishes different filters', () => {
    expect(pagedBooksQueryKey({libraryId: 1})).not.toEqual(pagedBooksQueryKey({libraryId: 2}));
  });
});

describe('infinite-scroll page cursors', () => {
  it('nextPageParam advances while there are more pages', () => {
    expect(nextPageParam(pageResponse({page: 0, hasNext: true}))).toBe(1);
    expect(nextPageParam(pageResponse({page: 3, hasNext: false}))).toBeUndefined();
  });

  it('previousPageParam steps back while not at the start', () => {
    expect(previousPageParam(pageResponse({page: 2, hasPrevious: true}))).toBe(1);
    expect(previousPageParam(pageResponse({page: 0, hasPrevious: false}))).toBeUndefined();
  });
});

describe('flattenPages', () => {
  it('concatenates the content of every accumulated page in order', () => {
    const pages = [
      pageResponse({content: [{id: 1}, {id: 2}]}),
      pageResponse({content: [{id: 3}]}),
    ];
    expect(flattenPages(pages).map(b => b.id)).toEqual([1, 2, 3]);
  });

  it('returns an empty list for undefined input', () => {
    expect(flattenPages(undefined)).toEqual([]);
  });
});
