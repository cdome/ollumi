import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {config, firstValueFrom, of, throwError} from 'rxjs';
import {SseClient} from 'ngx-sse-client';
import {BookMetadataService} from './book-metadata.service';
import {AuthService} from '../../../shared/service/auth.service';
import {mockHttpClientProvider, mockAuthServiceProvider} from '../../../../testing/providers';
import {createMockBookMetadata} from '../../../../testing/factories';
import {FetchMetadataRequest} from '../../metadata/model/request/fetch-metadata-request.model';

describe('BookMetadataService', () => {
  let service: BookMetadataService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let authService: {getInternalAccessToken: ReturnType<typeof vi.fn>};
  let sseClient: {stream: ReturnType<typeof vi.fn>};
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
    sseClient = {stream: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        BookMetadataService,
        mockHttpClientProvider,
        mockAuthServiceProvider,
        {provide: SseClient, useValue: sseClient}
      ]
    });

    service = TestBed.inject(BookMetadataService);
    httpClient = TestBed.inject(HttpClient) as any;
    authService = TestBed.inject(AuthService) as unknown as typeof authService;
    authService.getInternalAccessToken.mockReturnValue('test-token');

    vi.clearAllMocks();
    httpClient.get.mockReturnValue(of({}));
    httpClient.post.mockReturnValue(of({}));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('fetchBookMetadata', () => {
    it('should throw when no authentication token is available', () => {
      authService.getInternalAccessToken.mockReturnValue(null);
      const request: FetchMetadataRequest = {bookId: 1, providers: ['GOOGLE'], title: 'T', author: 'A', isbn: ''};

      expect(() => service.fetchBookMetadata(1, request)).toThrow('No authentication token available');
    });

    it('should stream metadata from SSE message events', async () => {
      const metadata = createMockBookMetadata({bookId: 1, title: 'Streamed'});
      const request: FetchMetadataRequest = {bookId: 1, providers: ['GOOGLE'], title: 'T', author: 'A', isbn: ''};
      sseClient.stream.mockReturnValue(of(new MessageEvent('message', {data: JSON.stringify(metadata)})));

      const result = await firstValueFrom(service.fetchBookMetadata(1, request));

      expect(sseClient.stream).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/metadata/prospective',
        expect.objectContaining({keepAlive: false, responseType: 'event'}),
        expect.objectContaining({
          headers: expect.any(Object),
          body: request,
          withCredentials: true
        }),
        'POST'
      );
      expect(result).toEqual(metadata);
    });

    it('should throw when the SSE event is an error event', async () => {
      const request: FetchMetadataRequest = {bookId: 1, providers: ['GOOGLE'], title: 'T', author: 'A', isbn: ''};
      sseClient.stream.mockReturnValue(of(new ErrorEvent('error', {message: 'SSE failure'})));

      await expect(firstValueFrom(service.fetchBookMetadata(1, request))).rejects.toThrow('SSE failure');
    });
  });

  describe('fetchMetadataDetail', () => {
    it('should fetch detailed metadata by provider and item id', async () => {
      const metadata = createMockBookMetadata({title: 'Detail'});
      httpClient.get.mockReturnValue(of(metadata));

      const result = await firstValueFrom(service.fetchMetadataDetail('GOOGLE', 'abc'));

      expect(httpClient.get).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/metadata/detail/GOOGLE/abc'
      );
      expect(result).toEqual(metadata);
    });
  });

  describe('lookupByIsbn', () => {
    it('should look up metadata by ISBN', async () => {
      const metadata = createMockBookMetadata({isbn13: '123'});
      httpClient.post.mockReturnValue(of(metadata));

      const result = await firstValueFrom(service.lookupByIsbn('123'));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/metadata/isbn-lookup',
        {isbn: '123'}
      );
      expect(result).toEqual(metadata);
    });
  });
});
