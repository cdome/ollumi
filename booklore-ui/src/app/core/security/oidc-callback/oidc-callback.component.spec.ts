import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {OidcCallbackComponent} from './oidc-callback.component';
import {OidcService} from '../oidc.service';
import {AuthService} from '../../../shared/service/auth.service';
import {Router} from '@angular/router';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {of, throwError} from 'rxjs';

describe('OidcCallbackComponent', () => {
  let fixture: ComponentFixture<OidcCallbackComponent>;
  let component: OidcCallbackComponent;
  let oidcServiceMock: { retrievePkceState: ReturnType<typeof vi.fn>; exchangeCode: ReturnType<typeof vi.fn> };
  let authServiceMock: { saveInternalTokens: ReturnType<typeof vi.fn>; initializeWebSocketConnection: ReturnType<typeof vi.fn> };
  let routerMock: { navigate: ReturnType<typeof vi.fn> };
  let originalHref: string;

  const pkceState = {codeVerifier: 'verifier', state: 'state-value', nonce: 'nonce-value'};

  function setWindowSearch(search: string) {
    const url = new URL(window.location.href);
    url.search = search;
    window.history.replaceState({}, '', url.toString());
  }

  beforeEach(() => {
    originalHref = window.location.href;
    oidcServiceMock = {
      retrievePkceState: vi.fn(() => pkceState),
      exchangeCode: vi.fn(() => of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}))
    };
    authServiceMock = {saveInternalTokens: vi.fn(), initializeWebSocketConnection: vi.fn()};
    routerMock = {navigate: vi.fn(() => Promise.resolve(true))};

    TestBed.configureTestingModule({
      imports: [OidcCallbackComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: OidcService, useValue: oidcServiceMock},
        {provide: AuthService, useValue: authServiceMock},
        {provide: Router, useValue: routerMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(OidcCallbackComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
    window.history.replaceState({}, '', originalHref);
    vi.restoreAllMocks();
  });

  it('should create', () => {
    setWindowSearch('');
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should redirect to login when the provider returns an error', () => {
    setWindowSearch('?error=access_denied&error_description=user+denied');
    fixture.detectChanges();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login'], {queryParams: {oidcError: 'user denied'}});
  });

  it('should redirect to login when code or state is missing', () => {
    setWindowSearch('?code=only-code');
    fixture.detectChanges();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login'], {queryParams: {oidcError: 'missing_code'}});
  });

  it('should redirect to login when no PKCE state is found', () => {
    oidcServiceMock.retrievePkceState.mockReturnValue(null);
    setWindowSearch('?code=abc&state=unknown');
    fixture.detectChanges();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login'], {queryParams: {oidcError: 'missing_pkce_state'}});
  });

  it('should redirect to login when the state does not match', () => {
    oidcServiceMock.retrievePkceState.mockReturnValue({...pkceState, state: 'other'});
    setWindowSearch('?code=abc&state=state-value');
    fixture.detectChanges();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login'], {queryParams: {oidcError: 'state_mismatch'}});
  });

  it('should save tokens and navigate to the dashboard on successful exchange', () => {
    setWindowSearch('?code=abc&state=state-value');
    fixture.detectChanges();
    expect(oidcServiceMock.exchangeCode).toHaveBeenCalledWith('abc', 'verifier', 'nonce-value', 'state-value');
    expect(authServiceMock.saveInternalTokens).toHaveBeenCalledWith('a', 'r');
    expect(authServiceMock.initializeWebSocketConnection).toHaveBeenCalled();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should redirect to change-password for default password users', () => {
    oidcServiceMock.exchangeCode.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'true'}));
    setWindowSearch('?code=abc&state=state-value');
    fixture.detectChanges();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('should redirect to login when token exchange fails', () => {
    oidcServiceMock.exchangeCode.mockReturnValue(throwError(() => ({error: {message: 'exchange_failed'}})));
    setWindowSearch('?code=abc&state=state-value');
    fixture.detectChanges();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login'], {queryParams: {oidcError: 'exchange_failed'}});
  });
});
