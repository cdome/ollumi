import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {LoginComponent} from './login.component';
import {AuthService} from '../../service/auth.service';
import {AppSettingsService, PublicAppSettings} from '../../service/app-settings.service';
import {OidcService} from '../../../core/security/oidc.service';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {BehaviorSubject, of, throwError} from 'rxjs';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authServiceMock: { internalLogin: ReturnType<typeof vi.fn>; clearSessionOnLoginPage: ReturnType<typeof vi.fn> };
  let appSettingsSubject: BehaviorSubject<PublicAppSettings | null>;
  let queryParamsSubject: BehaviorSubject<Record<string, string>>;
  let routerMock: { navigate: ReturnType<typeof vi.fn> };

  const baseOidcDetails = {
    providerName: 'Test OIDC',
    issuerUri: 'https://issuer.example.com',
    clientId: 'client-id',
    scopes: 'openid profile email'
  };

  function createOidcServiceMock() {
    return {
      generatePkce: vi.fn(() => Promise.resolve({codeVerifier: 'verifier', codeChallenge: 'challenge'})),
      fetchState: vi.fn(() => Promise.resolve('state-value')),
      generateRandomString: vi.fn(() => 'nonce-value'),
      storePkceState: vi.fn(),
      buildAuthUrl: vi.fn(() => Promise.resolve('https://auth.example.com?state=state-value'))
    };
  }

  beforeEach(() => {
    authServiceMock = {
      internalLogin: vi.fn(),
      clearSessionOnLoginPage: vi.fn()
    };
    appSettingsSubject = new BehaviorSubject<PublicAppSettings | null>(null);
    queryParamsSubject = new BehaviorSubject<Record<string, string>>({});
    routerMock = {navigate: vi.fn(() => Promise.resolve(true))};

    TestBed.configureTestingModule({
      imports: [LoginComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: AuthService, useValue: authServiceMock},
        {
          provide: AppSettingsService,
          useValue: {
            publicAppSettings$: appSettingsSubject.asObservable(),
            currentPublicSettings: null
          }
        },
        {provide: OidcService, useValue: createOidcServiceMock()},
        {provide: Router, useValue: routerMock},
        {
          provide: ActivatedRoute,
          useValue: {
            params: of({}),
            queryParams: queryParamsSubject.asObservable(),
            snapshot: {
              paramMap: {get: vi.fn(() => null)},
              queryParamMap: {get: vi.fn(() => null)}
            }
          }
        }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should clear session on init', () => {
    fixture.detectChanges();
    expect(authServiceMock.clearSessionOnLoginPage).toHaveBeenCalled();
  });

  it('should display a session revoked info message', () => {
    queryParamsSubject.next({reason: 'session_revoked'});
    fixture.detectChanges();
    expect(component.infoMessage).toContain('auth.login.sessionRevoked');
  });

  it('should display an OIDC error message', () => {
    queryParamsSubject.next({oidcError: 'exchange_failed'});
    fixture.detectChanges();
    expect(component.errorMessage).toContain('auth.login.oidcErrors.exchangeFailed');
  });

  it('should log in and navigate to the dashboard', () => {
    component.username = 'user';
    component.password = 'pass';
    authServiceMock.internalLogin.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}));

    component.login();

    expect(authServiceMock.internalLogin).toHaveBeenCalledWith({username: 'user', password: 'pass'});
    expect(routerMock.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should redirect to change-password for default password users', () => {
    component.username = 'user';
    component.password = 'pass';
    authServiceMock.internalLogin.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'true'}));

    component.login();

    expect(routerMock.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('should show a connection error when the server is unreachable', () => {
    component.username = 'user';
    component.password = 'pass';
    authServiceMock.internalLogin.mockReturnValue(throwError(() => ({status: 0})));

    component.login();

    expect(component.errorMessage).toContain('auth.login.connectionError');
  });

  it('should show a rate limited error on 429', () => {
    component.username = 'user';
    component.password = 'pass';
    authServiceMock.internalLogin.mockReturnValue(throwError(() => ({status: 429})));

    component.login();

    expect(component.errorMessage).toContain('auth.login.rateLimited');
  });

  it('should initiate OIDC login when the OIDC button is used', async () => {
    const publicSettings: PublicAppSettings = {
      oidcEnabled: true,
      remoteAuthEnabled: false,
      oidcProviderDetails: baseOidcDetails,
      oidcForceOnlyMode: false
    };
    appSettingsSubject.next(publicSettings);
    (TestBed.inject(AppSettingsService) as any).currentPublicSettings = publicSettings;
    fixture.detectChanges();

    await component.loginWithOidc();

    const oidcService = TestBed.inject(OidcService) as any;
    expect(oidcService.storePkceState).toHaveBeenCalled();
    expect(oidcService.buildAuthUrl).toHaveBeenCalled();
  });
});
