import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';
import {BookMarkService, BookMark, CreateBookMarkRequest, UpdateBookMarkRequest} from './book-mark.service';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('BookMarkService', () => {
  let service: BookMarkService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BookMarkService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(BookMarkService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch bookmarks for a book', async () => {
    const bookmarks: BookMark[] = [
      {id: 1, bookId: 42, cfi: 'cfi-1', title: 'Chapter 1', createdAt: '2026-01-01T00:00:00Z'},
      {id: 2, bookId: 42, positionMs: 12000, title: 'Track mark', createdAt: '2026-01-01T00:00:00Z'}
    ];
    httpClient.get.mockReturnValue(of(bookmarks));

    const result = await firstValueFrom(service.getBookmarksForBook(42));

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/bookmarks/book/42');
    expect(result).toEqual(bookmarks);
  });

  it('should create a bookmark', async () => {
    const request: CreateBookMarkRequest = {bookId: 42, cfi: 'cfi-2', title: 'New mark'};
    const bookmark: BookMark = {
      id: 3,
      bookId: 42,
      cfi: 'cfi-2',
      title: 'New mark',
      createdAt: '2026-01-01T00:00:00Z'
    };
    httpClient.post.mockReturnValue(of(bookmark));

    const result = await firstValueFrom(service.createBookmark(request));

    expect(httpClient.post).toHaveBeenCalledWith('http://localhost:6060/api/v1/bookmarks', request);
    expect(result).toEqual(bookmark);
  });

  it('should update a bookmark', async () => {
    const request: UpdateBookMarkRequest = {title: 'Updated', color: 'red'};
    const bookmark: BookMark = {
      id: 1,
      bookId: 42,
      cfi: 'cfi-1',
      title: 'Updated',
      color: 'red',
      createdAt: '2026-01-01T00:00:00Z'
    };
    httpClient.put.mockReturnValue(of(bookmark));

    const result = await firstValueFrom(service.updateBookmark(1, request));

    expect(httpClient.put).toHaveBeenCalledWith('http://localhost:6060/api/v1/bookmarks/1', request);
    expect(result).toEqual(bookmark);
  });

  it('should delete a bookmark', async () => {
    httpClient.delete.mockReturnValue(of(undefined));

    const result = await firstValueFrom(service.deleteBookmark(1));

    expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v1/bookmarks/1');
    expect(result).toBeUndefined();
  });

  it('should propagate errors from the backend', () => {
    httpClient.get.mockReturnValue(throwError(() => ({message: 'fetch failed'})));

    let error: any;
    service.getBookmarksForBook(42).subscribe({error: e => error = e});

    expect(error).toEqual({message: 'fetch failed'});
  });
});
