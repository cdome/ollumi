import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import type {HttpHandlerFn, HttpInterceptorFn} from '@angular/common/http';
import {HttpErrorResponse, HttpRequest, HttpResponse} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';
import {Router} from '@angular/router';
import {AuthService} from '../../shared/service/auth.service';
import {AuthInterceptorService} from './auth-interceptor.service';

describe('AuthInterceptorService', () => {
  let interceptor: HttpInterceptorFn;
  let authService: {
    getInternalAccessToken: ReturnType<typeof vi.fn>;
    internalRefreshToken: ReturnType<typeof vi.fn>;
    saveInternalTokens: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    interceptor = AuthInterceptorService;

    authService = {
      getInternalAccessToken: vi.fn(),
      internalRefreshToken: vi.fn(),
      saveInternalTokens: vi.fn(),
      logout: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        {provide: AuthService, useValue: authService},
        {provide: Router, useValue: {navigate: vi.fn(() => Promise.resolve(true))}}
      ]
    });

    router = TestBed.inject(Router);
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function run(req: HttpRequest<unknown>, next: HttpHandlerFn): Promise<unknown> {
    return TestBed.runInInjectionContext(() => firstValueFrom(interceptor(req, next)));
  }

  it('should add an Authorization header for API requests when a token is present', async () => {
    authService.getInternalAccessToken.mockReturnValue('token');
    const next = vi.fn((req: HttpRequest<unknown>) => of(new HttpResponse({status: 200, url: req.url})));

    await run(new HttpRequest('GET', 'http://localhost:6060/api/v1/test'), next as HttpHandlerFn);

    expect(next).toHaveBeenCalledTimes(1);
    expect(next.mock.calls[0][0].headers.get('Authorization')).toBe('Bearer token');
  });

  it('should not add an Authorization header for non-API requests', async () => {
    authService.getInternalAccessToken.mockReturnValue('token');
    const next = vi.fn((req: HttpRequest<unknown>) => of(new HttpResponse({status: 200, url: req.url})));

    await run(new HttpRequest('GET', 'http://other.host/api/v1/test'), next as HttpHandlerFn);

    expect(next.mock.calls[0][0].headers.has('Authorization')).toBe(false);
  });

  it('should not add an Authorization header when no token is available', async () => {
    authService.getInternalAccessToken.mockReturnValue(null);
    const next = vi.fn((req: HttpRequest<unknown>) => of(new HttpResponse({status: 200, url: req.url})));

    await run(new HttpRequest('GET', 'http://localhost:6060/api/v1/test'), next as HttpHandlerFn);

    expect(next.mock.calls[0][0].headers.has('Authorization')).toBe(false);
  });

  it('should refresh the token on 401 and retry the request with the new token', async () => {
    authService.getInternalAccessToken.mockReturnValue('old-token');
    authService.internalRefreshToken.mockReturnValue(of({accessToken: 'new-access', refreshToken: 'new-refresh'}));

    let callCount = 0;
    const next = vi.fn((req: HttpRequest<unknown>) => {
      callCount++;
      if (callCount === 1) {
        return throwError(() => new HttpErrorResponse({status: 401, statusText: 'Unauthorized'}));
      }
      return of(new HttpResponse({status: 200, url: req.url}));
    });

    await run(new HttpRequest('GET', 'http://localhost:6060/api/v1/test'), next as HttpHandlerFn);

    expect(authService.internalRefreshToken).toHaveBeenCalledTimes(1);
    expect(authService.saveInternalTokens).toHaveBeenCalledWith('new-access', 'new-refresh');
    expect(callCount).toBe(2);
    expect(next.mock.calls[1][0].headers.get('Authorization')).toBe('Bearer new-access');
  });

  it('should not trigger a refresh for non-401 errors', async () => {
    authService.getInternalAccessToken.mockReturnValue('token');
    const next = vi.fn(() => throwError(() => new HttpErrorResponse({status: 500, statusText: 'Server Error'})));

    await expect(run(new HttpRequest('GET', 'http://localhost:6060/api/v1/test'), next as HttpHandlerFn))
      .rejects.toThrow();

    expect(authService.internalRefreshToken).not.toHaveBeenCalled();
  });

  it('should logout when token refresh fails', async () => {
    authService.getInternalAccessToken.mockReturnValue('old-token');
    authService.internalRefreshToken.mockReturnValue(throwError(() => new Error('refresh failed')));
    const next = vi.fn(() => throwError(() => new HttpErrorResponse({status: 401, statusText: 'Unauthorized'})));

    await expect(run(new HttpRequest('GET', 'http://localhost:6060/api/v1/test'), next as HttpHandlerFn))
      .rejects.toThrow('refresh failed');

    expect(authService.logout).toHaveBeenCalledTimes(1);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
