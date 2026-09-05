import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {initializeAuthFactory} from './auth-initializer';
import {AuthInitializationService} from './auth-initialization-service';
import {AuthService, websocketInitializer} from '../../shared/service/auth.service';
import {AppSettingsService, PublicAppSettings} from '../../shared/service/app-settings.service';
import {BehaviorSubject, of, throwError} from 'rxjs';

vi.mock('../../shared/service/auth.service', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../shared/service/auth.service')>();
  return {
    ...actual,
    websocketInitializer: vi.fn(() => vi.fn())
  };
});

describe('initializeAuthFactory', () => {
  let authInitService: AuthInitializationService;
  let publicSettingsSubject: BehaviorSubject<PublicAppSettings | null>;
  let authService: {
    tokenSubject: { next: ReturnType<typeof vi.fn> };
    getInternalAccessToken: ReturnType<typeof vi.fn>;
    remoteLogin: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    publicSettingsSubject = new BehaviorSubject<PublicAppSettings | null>(null);
    authService = {
      tokenSubject: {next: vi.fn()},
      getInternalAccessToken: vi.fn(),
      remoteLogin: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        {provide: AuthService, useValue: authService},
        {provide: AppSettingsService, useValue: {publicAppSettings$: publicSettingsSubject.asObservable()}},
        AuthInitializationService,
      ]
    });

    authInitService = TestBed.inject(AuthInitializationService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should proceed with auth initialization when navigator.onLine is false', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');

    Object.defineProperty(navigator, 'onLine', {value: false, configurable: true});

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    publicSettingsSubject.next({oidcEnabled: false, remoteAuthEnabled: false, oidcProviderDetails: null!, oidcForceOnlyMode: false});

    await initPromise;

    expect(markSpy).toHaveBeenCalled();

    Object.defineProperty(navigator, 'onLine', {value: true, configurable: true});
  });

  it('should initialize normally when navigator.onLine is true', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');

    Object.defineProperty(navigator, 'onLine', {value: true, configurable: true});

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    publicSettingsSubject.next({oidcEnabled: false, remoteAuthEnabled: false, oidcProviderDetails: null!, oidcForceOnlyMode: false});

    await initPromise;

    expect(markSpy).toHaveBeenCalled();
  });

  it('should call remoteLogin when remoteAuthEnabled is true', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');
    authService.remoteLogin.mockReturnValue(of({accessToken: 'remote', refreshToken: 'token', isDefaultPassword: 'false'}));

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    publicSettingsSubject.next({
      oidcEnabled: false,
      remoteAuthEnabled: true,
      oidcProviderDetails: null!,
      oidcForceOnlyMode: false
    });

    await initPromise;

    expect(authService.remoteLogin).toHaveBeenCalledTimes(1);
    expect(markSpy).toHaveBeenCalled();
  });

  it('should call websocketInitializer when an internal token is present', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');
    authService.getInternalAccessToken.mockReturnValue('internal-token');

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    publicSettingsSubject.next({
      oidcEnabled: false,
      remoteAuthEnabled: false,
      oidcProviderDetails: null!,
      oidcForceOnlyMode: false
    });

    await initPromise;

    expect(websocketInitializer).toHaveBeenCalledWith(authService);
    const initializer = (websocketInitializer as ReturnType<typeof vi.fn>).mock.results[0].value;
    expect(initializer).toHaveBeenCalledTimes(1);
    expect(markSpy).toHaveBeenCalled();
  });

  it('should mark as initialized when public settings time out', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');
    vi.useFakeTimers();

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    vi.advanceTimersByTime(10001);

    await initPromise;

    expect(markSpy).toHaveBeenCalled();
  });

  it('should resolve and mark as initialized when remoteLogin fails', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');
    authService.remoteLogin.mockReturnValue(throwError(() => new Error('remote login failed')));
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    publicSettingsSubject.next({
      oidcEnabled: false,
      remoteAuthEnabled: true,
      oidcProviderDetails: null!,
      oidcForceOnlyMode: false
    });

    await initPromise;

    expect(consoleSpy).toHaveBeenCalled();
    expect(markSpy).toHaveBeenCalled();
  });
});
