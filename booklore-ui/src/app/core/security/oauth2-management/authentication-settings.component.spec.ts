import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {BehaviorSubject, of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {AuthenticationSettingsComponent} from './authentication-settings.component';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {MessageService} from 'primeng/api';
import {LibraryService} from '../../../features/book/service/library.service';
import {OidcGroupMappingService} from '../../../shared/service/oidc-group-mapping.service';
import {commonComponentTestProviders} from '../../../../testing';

const createMockAppSettings = () => ({
  oidcEnabled: false,
  oidcProviderDetails: {
    providerName: '',
    clientId: '',
    clientSecret: '',
    issuerUri: '',
    scopes: '',
    claimMapping: {username: '', email: '', name: '', groups: ''}
  },
  oidcAutoProvisionDetails: {
    enableAutoProvisioning: false,
    allowLocalAccountLinking: true,
    defaultPermissions: [],
    defaultLibraryIds: []
  },
  oidcSessionDurationHours: null,
  oidcGroupSyncMode: 'DISABLED',
  oidcForceOnlyMode: false
} as any);

describe('AuthenticationSettingsComponent', () => {
  let fixture: ComponentFixture<AuthenticationSettingsComponent>;
  let component: AuthenticationSettingsComponent;
  let appSettingsSubject: BehaviorSubject<any>;

  beforeEach(() => {
    appSettingsSubject = new BehaviorSubject({
      ...createMockAppSettings(),
      oidcEnabled: true,
      oidcProviderDetails: {
        providerName: 'test',
        clientId: 'client',
        issuerUri: 'https://issuer',
        scopes: 'openid',
        claimMapping: {username: 'sub', email: 'email', name: 'name', groups: 'groups'}
      }
    });

    const groupMappingServiceMock = {
      getAll: vi.fn(() => of([]))
    };

    TestBed.configureTestingModule({
      imports: [AuthenticationSettingsComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: AppSettingsService, useValue: {
          appSettings$: appSettingsSubject.asObservable(),
          publicAppSettings$: new BehaviorSubject(null).asObservable(),
          currentPublicSettings: null,
          saveSettings: vi.fn(() => of(undefined)),
          toggleOidcEnabled: vi.fn(() => of(undefined)),
          testOidcConnection: vi.fn(() => of(undefined))
        }},
        {provide: LibraryService, useValue: {
          libraryState$: new BehaviorSubject({loaded: true, libraries: [], error: null}),
          libraryStateSubject: new BehaviorSubject({loaded: true, libraries: [], error: null})
        }},
        {provide: OidcGroupMappingService, useValue: groupMappingServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(AuthenticationSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load settings when appSettings emits', () => {
    expect(component.oidcEnabled).toBe(true);
    expect(component.oidcProvider.providerName).toBe('test');
  });
});
