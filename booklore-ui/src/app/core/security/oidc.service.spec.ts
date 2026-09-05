import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {OidcService} from './oidc.service';
import {AppSettingsService} from '../../shared/service/app-settings.service';
import {mockAuthServiceProvider, mockHttpClientProvider} from '../../../testing/providers';
import {createMockPublicAppSettings} from '../../../testing/factories';
import {firstValueFrom, of} from 'rxjs';
import {webcrypto} from 'node:crypto';

describe('OidcService', () => {
  let service: OidcService;

  beforeEach(() => {
    if (!globalThis.crypto?.subtle) {
      vi.stubGlobal('crypto', webcrypto);
    }

    TestBed.configureTestingModule({
      providers: [
        OidcService,
        mockHttpClientProvider,
        mockAuthServiceProvider,
        {
          provide: AppSettingsService,
          useValue: {
            publicAppSettings$: of(createMockPublicAppSettings()),
            currentPublicSettings: createMockPublicAppSettings()
          }
        }
      ]
    });

    service = TestBed.inject(OidcService);
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should generate a PKCE pair', async () => {
    const pkce = await service.generatePkce();

    expect(pkce.codeVerifier).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(pkce.codeChallenge).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(pkce.codeVerifier).not.toBe(pkce.codeChallenge);
  });

  it('should generate a random base64url string', () => {
    const value = service.generateRandomString();

    expect(value).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });

  it('should build an authorization URL with a provided endpoint', async () => {
    const url = await service.buildAuthUrl(
      'https://issuer',
      'client-id',
      'challenge',
      'state-value',
      'nonce-value',
      'https://idp.example.com/auth',
      'openid email'
    );

    expect(url).toContain('https://idp.example.com/auth?');
    expect(url).toContain('response_type=code');
    expect(url).toContain('client_id=client-id');
    expect(url).toContain('code_challenge=challenge');
    expect(url).toContain('code_challenge_method=S256');
    expect(url).toContain('state=state-value');
    expect(url).toContain('nonce=nonce-value');
    expect(url).toContain('scope=openid+email');
  });

  it('should use default scopes when none are provided', async () => {
    const url = await service.buildAuthUrl(
      'https://issuer',
      'client-id',
      'challenge',
      'state-value',
      'nonce-value',
      'https://idp.example.com/auth'
    );

    expect(url).toContain('scope=openid+profile+email+groups+offline_access');
  });

  it('should discover the authorization endpoint when not provided', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      json: () => Promise.resolve({authorization_endpoint: 'https://idp.example.com/discovery/auth'})
    } as Response);

    const url = await service.buildAuthUrl(
      'https://issuer/',
      'client-id',
      'challenge',
      'state-value',
      'nonce-value'
    );

    expect(fetchSpy).toHaveBeenCalledWith('https://issuer/.well-known/openid-configuration');
    expect(url).toContain('https://idp.example.com/discovery/auth?');
    expect(url).toContain('client_id=client-id');
  });

  it('should fetch OIDC state from the backend', async () => {
    const getSpy = vi.fn().mockReturnValue(of({state: 'server-state'}));
    Object.assign(service['http'], {get: getSpy});

    const state = await service.fetchState();

    expect(getSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/auth/oidc/state');
    expect(state).toBe('server-state');
  });

  it('should exchange the authorization code for tokens', async () => {
    const tokenResponse = {
      accessToken: 'access',
      refreshToken: 'refresh',
      isDefaultPassword: 'false'
    };
    const postSpy = vi.fn().mockReturnValue(of(tokenResponse));
    Object.assign(service['http'], {post: postSpy});

    const result = await firstValueFrom(service.exchangeCode('code', 'verifier', 'nonce', 'state'));

    expect(postSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/auth/oidc/callback', {
      code: 'code',
      codeVerifier: 'verifier',
      redirectUri: `${window.location.origin}/oauth2-callback`,
      nonce: 'nonce',
      state: 'state'
    });
    expect(result).toEqual(tokenResponse);
  });

  it('should store and retrieve PKCE state', () => {
    const pkceState = {codeVerifier: 'verifier', state: 'state', nonce: 'nonce'};

    service.storePkceState(pkceState);

    expect(sessionStorage.getItem('oidc_pkce_state')).toBe(JSON.stringify(pkceState));

    const retrieved = service.retrievePkceState('state');

    expect(retrieved).toEqual(pkceState);
    expect(sessionStorage.getItem('oidc_pkce_state')).toBeNull();
  });

  it('should return null when retrieving missing PKCE state', () => {
    expect(service.retrievePkceState('missing')).toBeNull();
  });

  it('should return null when stored PKCE state is invalid JSON', () => {
    sessionStorage.setItem('oidc_pkce_bad', 'not-json');

    expect(service.retrievePkceState('bad')).toBeNull();
    expect(sessionStorage.getItem('oidc_pkce_bad')).toBeNull();
  });
});
