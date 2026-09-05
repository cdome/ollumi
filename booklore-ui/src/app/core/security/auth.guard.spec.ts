import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {AuthService} from '../../shared/service/auth.service';
import {AuthGuard} from './auth.guard';

function createToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({alg: 'none'}));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('AuthGuard', () => {
  let authService: { getInternalAccessToken: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn>; createUrlTree: ReturnType<typeof vi.fn> };
  let store: Record<string, string>;
  let storage: { getItem: ReturnType<typeof vi.fn>; setItem: ReturnType<typeof vi.fn>; removeItem: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authService = {getInternalAccessToken: vi.fn()};
    router = {
      navigate: vi.fn(() => Promise.resolve(true)),
      createUrlTree: vi.fn((segments: string[]) => ({segments} as UrlTree))
    };

    store = {};
    storage = {
      getItem: vi.fn((key: string) => store[key] ?? null),
      setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
      removeItem: vi.fn((key: string) => { delete store[key]; })
    };
    vi.stubGlobal('localStorage', storage);

    TestBed.configureTestingModule({
      providers: [
        {provide: AuthService, useValue: authService},
        {provide: Router, useValue: router}
      ]
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  function runGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      AuthGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
  }

  it('should allow activation when a valid unexpired token exists and the password is not default', () => {
    authService.getInternalAccessToken.mockReturnValue(createToken({
      exp: Math.floor(Date.now() / 1000) + 1000,
      isDefaultPassword: false
    }));

    const result = runGuard();

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(storage.removeItem).not.toHaveBeenCalled();
  });

  it('should redirect to login when the token is expired and remove the stored token', () => {
    authService.getInternalAccessToken.mockReturnValue(createToken({
      exp: Math.floor(Date.now() / 1000) - 1000
    }));

    const result = runGuard();

    expect(result).not.toBe(false);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(storage.removeItem).toHaveBeenCalledWith('accessToken_Internal');
  });

  it('should navigate to change-password when the default-password flag is set', () => {
    authService.getInternalAccessToken.mockReturnValue(createToken({
      exp: Math.floor(Date.now() / 1000) + 1000,
      isDefaultPassword: true
    }));

    const result = runGuard();

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('should remove the token and navigate to login for a malformed token', () => {
    authService.getInternalAccessToken.mockReturnValue('header.bad-payload.signature');

    const result = runGuard();

    expect(result).toBe(false);
    expect(storage.removeItem).toHaveBeenCalledWith('accessToken_Internal');
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should navigate to login when no token is present', () => {
    authService.getInternalAccessToken.mockReturnValue(null);

    const result = runGuard();

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
