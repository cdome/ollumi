import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {AuthService, websocketInitializer} from './auth.service';
import {PostLoginInitializerService} from '../../core/services/post-login-initializer.service';
import {RxStompService} from '../websocket/rx-stomp.service';
import {Router} from '@angular/router';
import {mockHttpClientProvider, mockRouterProvider, mockRxStompServiceProvider} from '../../../testing/providers';
import {firstValueFrom, of, throwError} from 'rxjs';

function configureAuthService(initializer: { initialize: ReturnType<typeof vi.fn> }) {
  TestBed.configureTestingModule({
    providers: [
      AuthService,
      mockHttpClientProvider,
      mockRouterProvider,
      mockRxStompServiceProvider,
      {provide: PostLoginInitializerService, useValue: initializer}
    ]
  });
}

describe('AuthService', () => {
  let service: AuthService;
  let store: Record<string, string>;
  let storage: { getItem: ReturnType<typeof vi.fn>; setItem: ReturnType<typeof vi.fn>; removeItem: ReturnType<typeof vi.fn> };
  let postLoginInitializer: { initialize: ReturnType<typeof vi.fn> };
  let rxStompMock: { updateConfig: ReturnType<typeof vi.fn>; activate: ReturnType<typeof vi.fn>; deactivate: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(() => {
    store = {};
    storage = {
      getItem: vi.fn((key: string) => store[key] ?? null),
      setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
      removeItem: vi.fn((key: string) => { delete store[key]; })
    };
    vi.stubGlobal('localStorage', storage);

    postLoginInitializer = {initialize: vi.fn(() => of(undefined))};

    configureAuthService(postLoginInitializer);

    service = TestBed.inject(AuthService);
    const rxStomp = TestBed.inject(RxStompService);
    router = TestBed.inject(Router);
    rxStompMock = rxStomp as unknown as typeof rxStompMock;
    rxStompMock.updateConfig = vi.fn();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('should create and initialize token from localStorage', () => {
    store['accessToken_Internal'] = 'stored-token';
    TestBed.resetTestingModule();
    configureAuthService(postLoginInitializer);
    const freshStomp = TestBed.inject(RxStompService) as unknown as typeof rxStompMock;
    freshStomp.updateConfig = vi.fn();

    const freshService = TestBed.inject(AuthService);

    expect(freshService.tokenSubject.value).toBe('stored-token');
  });

  it('should login internally and initialize the session', async () => {
    const postSpy = vi.fn().mockReturnValue(of({
      accessToken: 'access',
      refreshToken: 'refresh',
      isDefaultPassword: 'false'
    }));
    Object.assign(service['http'], {post: postSpy});

    const result = await firstValueFrom(service.internalLogin({username: 'user', password: 'pass'}));

    expect(postSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/auth/login', {username: 'user', password: 'pass'});
    expect(result).toEqual({accessToken: 'access', refreshToken: 'refresh', isDefaultPassword: 'false'});
    expect(storage.setItem).toHaveBeenCalledWith('accessToken_Internal', 'access');
    expect(storage.setItem).toHaveBeenCalledWith('refreshToken_Internal', 'refresh');
    expect(service.tokenSubject.value).toBe('access');
    expect(rxStompMock.updateConfig).toHaveBeenCalled();
    expect(rxStompMock.activate).toHaveBeenCalled();
    expect(postLoginInitializer.initialize).toHaveBeenCalledTimes(1);
  });

  it('should refresh the internal token', async () => {
    store['refreshToken_Internal'] = 'old-refresh';
    const postSpy = vi.fn().mockReturnValue(of({accessToken: 'new-access', refreshToken: 'new-refresh'}));
    Object.assign(service['http'], {post: postSpy});

    const result = await firstValueFrom(service.internalRefreshToken());

    expect(postSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/auth/refresh', {refreshToken: 'old-refresh'});
    expect(result).toEqual({accessToken: 'new-access', refreshToken: 'new-refresh'});
    expect(storage.setItem).toHaveBeenCalledWith('accessToken_Internal', 'new-access');
    expect(storage.setItem).toHaveBeenCalledWith('refreshToken_Internal', 'new-refresh');
  });

  it('should login remotely and initialize the session', async () => {
    const getSpy = vi.fn().mockReturnValue(of({
      accessToken: 'remote-access',
      refreshToken: 'remote-refresh',
      isDefaultPassword: 'false'
    }));
    Object.assign(service['http'], {get: getSpy});

    const result = await firstValueFrom(service.remoteLogin());

    expect(getSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/auth/remote');
    expect(result).toEqual({accessToken: 'remote-access', refreshToken: 'remote-refresh', isDefaultPassword: 'false'});
    expect(storage.setItem).toHaveBeenCalledWith('accessToken_Internal', 'remote-access');
    expect(storage.setItem).toHaveBeenCalledWith('refreshToken_Internal', 'remote-refresh');
    expect(rxStompMock.updateConfig).toHaveBeenCalled();
    expect(rxStompMock.activate).toHaveBeenCalled();
    expect(postLoginInitializer.initialize).toHaveBeenCalledTimes(1);
  });

  it('should save internal tokens and update the token subject', () => {
    service.saveInternalTokens('access-token', 'refresh-token');

    expect(storage.setItem).toHaveBeenCalledWith('accessToken_Internal', 'access-token');
    expect(storage.setItem).toHaveBeenCalledWith('refreshToken_Internal', 'refresh-token');
    expect(service.tokenSubject.value).toBe('access-token');
  });

  it('should read tokens from storage', () => {
    store['accessToken_Internal'] = 'access';
    store['refreshToken_Internal'] = 'refresh';

    expect(service.getInternalAccessToken()).toBe('access');
    expect(service.getInternalRefreshToken()).toBe('refresh');
  });

  it('should logout without an external logout URL', async () => {
    const postSpy = vi.fn().mockReturnValue(of({logoutUrl: null}));
    Object.assign(service['http'], {post: postSpy});

    service.logout();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(postSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/auth/logout', {refreshToken: null});
    expect(storage.removeItem).toHaveBeenCalledWith('accessToken_Internal');
    expect(storage.removeItem).toHaveBeenCalledWith('refreshToken_Internal');
    expect(service.tokenSubject.value).toBeNull();
    expect(rxStompMock.deactivate).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should redirect to an external logout URL when provided', async () => {
    const postSpy = vi.fn().mockReturnValue(of({logoutUrl: 'https://idp/logout'}));
    Object.assign(service['http'], {post: postSpy});

    service.logout();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(storage.removeItem).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should clear session and navigate on logout error', async () => {
    const postSpy = vi.fn().mockReturnValue(throwError(() => new Error('logout failed')));
    Object.assign(service['http'], {post: postSpy});

    service.logout();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(storage.removeItem).toHaveBeenCalledWith('accessToken_Internal');
    expect(storage.removeItem).toHaveBeenCalledWith('refreshToken_Internal');
    expect(service.tokenSubject.value).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should force logout with a reason', () => {
    service.forceLogout('expired');

    expect(storage.removeItem).toHaveBeenCalledWith('accessToken_Internal');
    expect(storage.removeItem).toHaveBeenCalledWith('refreshToken_Internal');
    expect(service.tokenSubject.value).toBeNull();
    expect(rxStompMock.deactivate).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {queryParams: {reason: 'expired'}});
  });

  it('should clear session when on the login page', () => {
    service.clearSessionOnLoginPage();

    expect(storage.removeItem).toHaveBeenCalledWith('accessToken_Internal');
    expect(storage.removeItem).toHaveBeenCalledWith('refreshToken_Internal');
  });

  it('should not initialize websocket when no token is available', () => {
    service.initializeWebSocketConnection();

    expect(rxStompMock.updateConfig).not.toHaveBeenCalled();
    expect(rxStompMock.activate).not.toHaveBeenCalled();
  });

  it('should initialize websocket connection when a token is available', () => {
    store['accessToken_Internal'] = 'token';

    service.initializeWebSocketConnection();

    expect(rxStompMock.updateConfig).toHaveBeenCalled();
    expect(rxStompMock.activate).toHaveBeenCalled();
    expect(postLoginInitializer.initialize).toHaveBeenCalledTimes(1);
  });

  it('should cache the RxStompService instance', () => {
    const stomp1 = service.getRxStompService();
    const stomp2 = service.getRxStompService();

    expect(stomp1).toBe(stomp2);
    expect(stomp1).toBe(TestBed.inject(RxStompService));
  });

  it('should provide a websocket initializer factory', () => {
    store['accessToken_Internal'] = 'token';

    const initializer = websocketInitializer(service);
    initializer();

    expect(rxStompMock.updateConfig).toHaveBeenCalled();
    expect(rxStompMock.activate).toHaveBeenCalled();
  });
});
