import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {of} from 'rxjs';

import {AuthService} from './auth.service';
import {RxStompService} from '../websocket/rx-stomp.service';
import {PostLoginInitializerService} from '../../core/services/post-login-initializer.service';
import {API_CONFIG} from '../../core/config/api-config';

const AUTH_URL = `${API_CONFIG.BASE_URL}/api/v1/auth`;
const ACCESS_KEY = 'accessToken_Internal';
const REFRESH_KEY = 'refreshToken_Internal';

class MemoryStorage {
  private store = new Map<string, string>();
  getItem(k: string) { return this.store.has(k) ? this.store.get(k)! : null; }
  setItem(k: string, v: string) { this.store.set(k, String(v)); }
  removeItem(k: string) { this.store.delete(k); }
  clear() { this.store.clear(); }
  key() { return null; }
  get length() { return this.store.size; }
}

describe('AuthService', () => {
  let httpMock: HttpTestingController;
  let router: {navigate: ReturnType<typeof vi.fn>};
  let stomp: {deactivate: ReturnType<typeof vi.fn>; activate: ReturnType<typeof vi.fn>; updateConfig: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    vi.stubGlobal('localStorage', new MemoryStorage());
    router = {navigate: vi.fn().mockResolvedValue(true)};
    stomp = {deactivate: vi.fn(), activate: vi.fn(), updateConfig: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: Router, useValue: router},
        {provide: RxStompService, useValue: stomp},
        {provide: PostLoginInitializerService, useValue: {initialize: () => of(undefined)}},
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.unstubAllGlobals();
  });

  // Injected lazily so a test can seed localStorage before the token signal reads it.
  const create = () => TestBed.inject(AuthService);

  describe('token signal', () => {
    it('initializes from an existing stored access token', () => {
      localStorage.setItem(ACCESS_KEY, 'stored-token');
      expect(create().token()).toBe('stored-token');
    });

    it('is null when nothing is stored', () => {
      expect(create().token()).toBeNull();
    });
  });

  describe('saveInternalTokens', () => {
    it('persists both tokens and updates the token signal', () => {
      const auth = create();
      auth.saveInternalTokens('access-1', 'refresh-1');

      expect(localStorage.getItem(ACCESS_KEY)).toBe('access-1');
      expect(localStorage.getItem(REFRESH_KEY)).toBe('refresh-1');
      expect(auth.token()).toBe('access-1');
    });
  });

  describe('internalLogin', () => {
    it('saves the returned tokens and reflects them in the signal', () => {
      const auth = create();
      auth.internalLogin({username: 'u', password: 'p'}).subscribe();

      const req = httpMock.expectOne(`${AUTH_URL}/login`);
      expect(req.request.body).toEqual({username: 'u', password: 'p'});
      req.flush({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'});

      expect(auth.token()).toBe('a');
      expect(localStorage.getItem(REFRESH_KEY)).toBe('r');
    });

    it('does not store anything when the response omits tokens', () => {
      const auth = create();
      auth.internalLogin({username: 'u', password: 'p'}).subscribe();
      httpMock.expectOne(`${AUTH_URL}/login`).flush({accessToken: '', refreshToken: '', isDefaultPassword: 'false'});

      expect(auth.token()).toBeNull();
      expect(localStorage.getItem(ACCESS_KEY)).toBeNull();
    });
  });

  describe('internalRefreshToken', () => {
    it('sends the stored refresh token and saves the rotated pair', () => {
      localStorage.setItem(REFRESH_KEY, 'old-refresh');
      const auth = create();

      auth.internalRefreshToken().subscribe();

      const req = httpMock.expectOne(`${AUTH_URL}/refresh`);
      expect(req.request.body).toEqual({refreshToken: 'old-refresh'});
      req.flush({accessToken: 'a2', refreshToken: 'r2'});

      expect(auth.token()).toBe('a2');
      expect(localStorage.getItem(REFRESH_KEY)).toBe('r2');
    });
  });

  describe('forceLogout', () => {
    it('clears the session, resets the token, deactivates the socket and routes to login with a reason', () => {
      localStorage.setItem(ACCESS_KEY, 'a');
      localStorage.setItem(REFRESH_KEY, 'r');
      const auth = create();

      auth.forceLogout('session_revoked');

      expect(localStorage.getItem(ACCESS_KEY)).toBeNull();
      expect(localStorage.getItem(REFRESH_KEY)).toBeNull();
      expect(auth.token()).toBeNull();
      expect(stomp.deactivate).toHaveBeenCalledOnce();
      expect(router.navigate).toHaveBeenCalledWith(['/login'], {queryParams: {reason: 'session_revoked'}});
    });
  });
});
