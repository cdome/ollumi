import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {catchError, firstValueFrom} from 'rxjs';
import {SseClient} from 'ngx-sse-client';
import {AuthorService} from './author.service';
import {AuthService} from '../../../shared/service/auth.service';
import {mockHttpClientProvider} from '../../../../testing/providers';
import type {AuthorDetails, AuthorMatchRequest, AuthorPhotoResult, AuthorSearchResult, AuthorSummary, AuthorUpdateRequest} from '../model/author.model';

const baseUrl = 'http://localhost:6060/api/v1/authors';
const mediaBaseUrl = 'http://localhost:6060/api/v1/media';

function createMockAuthorSummary(overrides: Partial<AuthorSummary> = {}): AuthorSummary {
  return {
    id: 1,
    name: 'Test Author',
    asin: 'TEST123',
    bookCount: 5,
    hasPhoto: false,
    ...overrides
  };
}

function createMockAuthorDetails(overrides: Partial<AuthorDetails> = {}): AuthorDetails {
  return {
    id: 1,
    name: 'Test Author',
    description: 'A test author',
    nameLocked: false,
    descriptionLocked: false,
    asinLocked: false,
    photoLocked: false,
    ...overrides
  };
}

describe('AuthorService', () => {
  let service: AuthorService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let sseStream: ReturnType<typeof vi.fn>;
  let authService: {getInternalAccessToken: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    sseStream = vi.fn();
    authService = {
      getInternalAccessToken: vi.fn(() => 'auth-token')
    };

    TestBed.configureTestingModule({
      providers: [
        AuthorService,
        mockHttpClientProvider,
        {
          provide: AuthService,
          useValue: authService
        },
        {
          provide: SseClient,
          useValue: {stream: sseStream}
        }
      ]
    });

    service = TestBed.inject(AuthorService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of({}));
    httpClient.patch.mockReturnValue(of({}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAllAuthors', () => {
    it('should fetch authors and update the allAuthors subject', async () => {
      const authors = [createMockAuthorSummary({id: 1, name: 'Alpha'}), createMockAuthorSummary({id: 2, name: 'Beta'})];
      httpClient.get.mockReturnValue(of(authors));

      const result = await firstValueFrom(service.getAllAuthors());

      expect(httpClient.get).toHaveBeenCalledWith(baseUrl);
      expect(result).toEqual(authors);
      expect(await firstValueFrom(service.allAuthors$)).toEqual(authors);
    });
  });

  describe('getAuthorDetails', () => {
    it('should fetch author details by id', async () => {
      const details = createMockAuthorDetails();
      httpClient.get.mockReturnValue(of(details));

      const result = await firstValueFrom(service.getAuthorDetails(1));

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/1`);
      expect(result).toEqual(details);
    });
  });

  describe('getAuthorByName', () => {
    it('should fetch author details by name', async () => {
      const details = createMockAuthorDetails();
      httpClient.get.mockReturnValue(of(details));

      const result = await firstValueFrom(service.getAuthorByName('Test Author'));

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/by-name`, {params: {name: 'Test Author'}});
      expect(result).toEqual(details);
    });
  });

  describe('searchAuthorMetadata', () => {
    it('should search metadata with query when no asin is provided', async () => {
      const results: AuthorSearchResult[] = [{source: 'Test', asin: 'ASIN', name: 'Result'}];
      httpClient.get.mockReturnValue(of(results));

      const result = await firstValueFrom(service.searchAuthorMetadata(1, 'query', 'us'));

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/1/search-metadata`, {params: {region: 'us', q: 'query'}});
      expect(result).toEqual(results);
    });

    it('should search metadata with asin when provided', async () => {
      const results: AuthorSearchResult[] = [{source: 'Test', asin: 'ASIN', name: 'Result'}];
      httpClient.get.mockReturnValue(of(results));

      const result = await firstValueFrom(service.searchAuthorMetadata(1, 'query', 'us', 'ASIN'));

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/1/search-metadata`, {params: {region: 'us', asin: 'ASIN'}});
      expect(result).toEqual(results);
    });
  });

  describe('matchAuthor', () => {
    it('should post a match request', async () => {
      const details = createMockAuthorDetails();
      const request: AuthorMatchRequest = {source: 'Test', asin: 'ASIN', region: 'us'};
      httpClient.post.mockReturnValue(of(details));

      const result = await firstValueFrom(service.matchAuthor(1, request));

      expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/1/match`, request);
      expect(result).toEqual(details);
    });
  });

  describe('quickMatchAuthor', () => {
    it('should post a quick match request with default region', async () => {
      const details = createMockAuthorDetails();
      httpClient.post.mockReturnValue(of(details));

      const result = await firstValueFrom(service.quickMatchAuthor(1));

      expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/1/quick-match`, null, {params: {region: 'us'}});
      expect(result).toEqual(details);
    });

    it('should post a quick match request with custom region', async () => {
      const details = createMockAuthorDetails();
      httpClient.post.mockReturnValue(of(details));

      const result = await firstValueFrom(service.quickMatchAuthor(1, 'uk'));

      expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/1/quick-match`, null, {params: {region: 'uk'}});
      expect(result).toEqual(details);
    });
  });

  describe('autoMatchAuthors', () => {
    it('should stream author summaries from the SSE endpoint', async () => {
      const summary = createMockAuthorSummary();
      const event = {type: 'message', data: JSON.stringify(summary)} as MessageEvent;
      sseStream.mockReturnValue(of(event));

      const result = await firstValueFrom(service.autoMatchAuthors([1, 2]));

      expect(sseStream).toHaveBeenCalledWith(
        `${baseUrl}/auto-match`,
        {keepAlive: false, reconnectionDelay: 1000, responseType: 'event'},
        expect.objectContaining({headers: expect.anything(), body: [1, 2], withCredentials: true}),
        'POST'
      );
      expect(result).toEqual(summary);
    });

    it('should throw when the SSE event is an error', async () => {
      const errorEvent = {type: 'error', message: 'match failed'} as unknown as ErrorEvent;
      sseStream.mockReturnValue(of(errorEvent));

      const result$ = service.autoMatchAuthors([1]).pipe(catchError(err => of(err.message)));
      const result = await firstValueFrom(result$);

      expect(result).toBe('match failed');
    });
  });

  describe('updateAuthor', () => {
    it('should update an author', async () => {
      const details = createMockAuthorDetails({name: 'Updated'});
      const request: AuthorUpdateRequest = {name: 'Updated'};
      httpClient.put.mockReturnValue(of(details));

      const result = await firstValueFrom(service.updateAuthor(1, request));

      expect(httpClient.put).toHaveBeenCalledWith(`${baseUrl}/1`, request);
      expect(result).toEqual(details);
    });
  });

  describe('unmatchAuthors', () => {
    it('should unmatch authors', async () => {
      httpClient.post.mockReturnValue(of(undefined));

      await firstValueFrom(service.unmatchAuthors([1, 2]));

      expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/unmatch`, [1, 2]);
    });
  });

  describe('deleteAuthors', () => {
    it('should delete authors', async () => {
      httpClient.delete.mockReturnValue(of(undefined));

      await firstValueFrom(service.deleteAuthors([1, 2]));

      expect(httpClient.delete).toHaveBeenCalledWith(baseUrl, {body: [1, 2]});
    });
  });

  describe('searchAuthorPhotos', () => {
    it('should search author photos', async () => {
      const results: AuthorPhotoResult[] = [{url: 'http://photo', width: 100, height: 100, index: 0}];
      httpClient.get.mockReturnValue(of(results));

      const result = await firstValueFrom(service.searchAuthorPhotos(1, 'query'));

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/1/search-photos`, {params: {q: 'query'}});
      expect(result).toEqual(results);
    });
  });

  describe('uploadAuthorPhotoFromUrl', () => {
    it('should upload a photo from a URL', async () => {
      httpClient.post.mockReturnValue(of(undefined));

      await firstValueFrom(service.uploadAuthorPhotoFromUrl(1, 'http://image'));

      expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/1/photo/url`, null, {params: {url: 'http://image'}});
    });
  });

  describe('URL helpers', () => {
    it('should return the upload photo URL', () => {
      expect(service.getUploadAuthorPhotoUrl(1)).toBe(`${baseUrl}/1/photo/upload`);
    });

    it('should return the author photo URL with token', () => {
      authService.getInternalAccessToken.mockReturnValue('token');

      expect(service.getAuthorPhotoUrl(1)).toBe(`${mediaBaseUrl}/author/1/photo?token=token`);
    });

    it('should return the author photo URL without token', () => {
      authService.getInternalAccessToken.mockReturnValue(null);

      expect(service.getAuthorPhotoUrl(1)).toBe(`${mediaBaseUrl}/author/1/photo`);
    });

    it('should return the author thumbnail URL with token and cache buster', () => {
      authService.getInternalAccessToken.mockReturnValue('token');

      expect(service.getAuthorThumbnailUrl(1, 123)).toBe(`${mediaBaseUrl}/author/1/thumbnail?token=token&t=123`);
    });

    it('should return the author thumbnail URL with only cache buster when no token', () => {
      authService.getInternalAccessToken.mockReturnValue(null);

      expect(service.getAuthorThumbnailUrl(1, 123)).toBe(`${mediaBaseUrl}/author/1/thumbnail?t=123`);
    });
  });
});
