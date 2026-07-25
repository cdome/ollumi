import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {of, Subject} from 'rxjs';

import {AuthInterceptorService, resetAuthInterceptorForTesting} from './auth-interceptor.service';
import {AuthService} from '../../shared/service/auth.service';
import {API_CONFIG} from '../config/api-config';

interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

describe('AuthInterceptorService', () => {
  const apiUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;
  const authUrl = `${API_CONFIG.BASE_URL}/api/v1/auth/login`;
  const externalUrl = 'https://example.org/data';

  let http: HttpClient;
  let httpMock: HttpTestingController;
  let getInternalAccessToken: ReturnType<typeof vi.fn>;
  let internalRefreshToken: ReturnType<typeof vi.fn>;
  let saveInternalTokens: ReturnType<typeof vi.fn>;
  let logout: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetAuthInterceptorForTesting();
    getInternalAccessToken = vi.fn().mockReturnValue('old-token');
    internalRefreshToken = vi.fn();
    saveInternalTokens = vi.fn();
    logout = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([AuthInterceptorService])),
        provideHttpClientTesting(),
        {provide: AuthService, useValue: {getInternalAccessToken, internalRefreshToken, saveInternalTokens, logout}},
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds a Bearer header to API requests when a token exists', () => {
    http.get(apiUrl).subscribe();

    const req = httpMock.expectOne(apiUrl);
    expect(req.request.headers.get('Authorization')).toBe('Bearer old-token');
    req.flush([]);
  });

  it('does not add a header to non-API requests', () => {
    http.get(externalUrl).subscribe();

    const req = httpMock.expectOne(externalUrl);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('does not add a header when no token is stored', () => {
    getInternalAccessToken.mockReturnValue(null);

    http.get(apiUrl).subscribe();

    const req = httpMock.expectOne(apiUrl);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('refreshes on 401 and retries with the new token', () => {
    internalRefreshToken.mockReturnValue(of<TokenPair>({accessToken: 'new-token', refreshToken: 'new-refresh'}));
    let result: unknown;
    http.get(apiUrl).subscribe(body => (result = body));

    httpMock.expectOne(apiUrl).flush(null, {status: 401, statusText: 'Unauthorized'});

    const retried = httpMock.expectOne(apiUrl);
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-token');
    retried.flush(['ok']);

    expect(result).toEqual(['ok']);
    expect(saveInternalTokens).toHaveBeenCalledWith('new-token', 'new-refresh');
    expect(logout).not.toHaveBeenCalled();
  });

  it('shares a single refresh across concurrent 401s', () => {
    const refresh$ = new Subject<TokenPair>();
    internalRefreshToken.mockReturnValue(refresh$.asObservable());

    http.get(`${apiUrl}/1`).subscribe();
    http.get(`${apiUrl}/2`).subscribe();
    httpMock.expectOne(`${apiUrl}/1`).flush(null, {status: 401, statusText: 'Unauthorized'});
    httpMock.expectOne(`${apiUrl}/2`).flush(null, {status: 401, statusText: 'Unauthorized'});

    refresh$.next({accessToken: 'new-token', refreshToken: 'new-refresh'});
    refresh$.complete();

    expect(internalRefreshToken).toHaveBeenCalledTimes(1);
    for (const id of [1, 2]) {
      const retried = httpMock.expectOne(`${apiUrl}/${id}`);
      expect(retried.request.headers.get('Authorization')).toBe('Bearer new-token');
      retried.flush([]);
    }
  });

  it('errors every queued request and logs out once when the refresh fails', () => {
    const refresh$ = new Subject<TokenPair>();
    internalRefreshToken.mockReturnValue(refresh$.asObservable());

    const errors: unknown[] = [];
    http.get(`${apiUrl}/1`).subscribe({error: e => errors.push(e)});
    http.get(`${apiUrl}/2`).subscribe({error: e => errors.push(e)});
    httpMock.expectOne(`${apiUrl}/1`).flush(null, {status: 401, statusText: 'Unauthorized'});
    httpMock.expectOne(`${apiUrl}/2`).flush(null, {status: 401, statusText: 'Unauthorized'});

    refresh$.error(new Error('refresh expired'));

    expect(errors).toHaveLength(2);
    expect(logout).toHaveBeenCalledTimes(1);
    httpMock.expectNone(`${apiUrl}/1`);
    httpMock.expectNone(`${apiUrl}/2`);
  });

  it('treats a refresh response without tokens as a failure', () => {
    internalRefreshToken.mockReturnValue(of({accessToken: '', refreshToken: ''}));

    let error: Error | undefined;
    http.get(apiUrl).subscribe({error: e => (error = e)});
    httpMock.expectOne(apiUrl).flush(null, {status: 401, statusText: 'Unauthorized'});

    expect(error?.message).toContain('no tokens');
    expect(logout).toHaveBeenCalledTimes(1);
    expect(saveInternalTokens).not.toHaveBeenCalled();
    httpMock.expectNone(apiUrl);
  });

  it('allows a later refresh to succeed after a failed one', () => {
    internalRefreshToken
      .mockReturnValueOnce(of({accessToken: '', refreshToken: ''}))
      .mockReturnValueOnce(of<TokenPair>({accessToken: 'second-token', refreshToken: 'second-refresh'}));

    http.get(apiUrl).subscribe({error: () => undefined});
    httpMock.expectOne(apiUrl).flush(null, {status: 401, statusText: 'Unauthorized'});

    http.get(apiUrl).subscribe();
    httpMock.expectOne(apiUrl).flush(null, {status: 401, statusText: 'Unauthorized'});

    const retried = httpMock.expectOne(apiUrl);
    expect(retried.request.headers.get('Authorization')).toBe('Bearer second-token');
    retried.flush([]);
    expect(internalRefreshToken).toHaveBeenCalledTimes(2);
  });

  it('passes non-401 errors through without refreshing', () => {
    let error: HttpErrorResponse | undefined;
    http.get(apiUrl).subscribe({error: e => (error = e)});

    httpMock.expectOne(apiUrl).flush(null, {status: 500, statusText: 'Server Error'});

    expect(error?.status).toBe(500);
    expect(internalRefreshToken).not.toHaveBeenCalled();
  });

  it('does not attempt a refresh for 401s from auth endpoints', () => {
    let error: HttpErrorResponse | undefined;
    http.post(authUrl, {}).subscribe({error: e => (error = e)});

    httpMock.expectOne(authUrl).flush(null, {status: 401, statusText: 'Unauthorized'});

    expect(error?.status).toBe(401);
    expect(internalRefreshToken).not.toHaveBeenCalled();
    expect(logout).not.toHaveBeenCalled();
  });
});
