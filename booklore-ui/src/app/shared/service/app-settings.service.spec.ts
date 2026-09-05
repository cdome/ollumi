import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject} from 'rxjs';
import {AppSettingsService, PublicAppSettings} from './app-settings.service';
import {AppSettings, OidcTestResult} from '../model/app-settings.model';
import {mockAuthServiceProvider, mockHttpClientProvider} from '../../../testing/providers';
import {createMockPublicAppSettings} from '../../../testing/factories';
import {firstValueFrom, lastValueFrom, of, throwError} from 'rxjs';
import {defaultIfEmpty, filter} from 'rxjs/operators';

/* eslint-disable @typescript-eslint/no-explicit-any */
function createMockAppSettings(overrides: Partial<AppSettings> = {}): AppSettings {
  return {
    autoBookSearch: false,
    similarBookRecommendation: false,
    defaultMetadataRefreshOptions: {} as any,
    libraryMetadataRefreshOptions: [],
    uploadPattern: '',
    opdsServerEnabled: false,
    komgaApiEnabled: false,
    komgaGroupUnknown: false,
    remoteAuthEnabled: false,
    oidcEnabled: false,
    oidcProviderDetails: createMockPublicAppSettings().oidcProviderDetails,
    oidcAutoProvisionDetails: {
      enableAutoProvisioning: false,
      allowLocalAccountLinking: false,
      defaultPermissions: [],
      defaultLibraryIds: []
    },
    maxFileUploadSizeInMb: 0,
    metadataProviderSettings: {} as any,
    metadataMatchWeights: {} as any,
    metadataPersistenceSettings: {} as any,
    metadataPublicReviewsSettings: {downloadEnabled: false, autoDownloadEnabled: false, providers: []},
    koboSettings: {} as any,
    coverCroppingSettings: {} as any,
    metadataDownloadOnBookdrop: false,
    telemetryEnabled: false,
    metadataProviderSpecificFields: {} as any,
    oidcSessionDurationHours: null,
    oidcGroupSyncMode: null,
    oidcForceOnlyMode: false,
    diskType: '',
    ...overrides
  };
}
/* eslint-enable @typescript-eslint/no-explicit-any */

describe('AppSettingsService', () => {
  let service: AppSettingsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AppSettingsService,
        mockHttpClientProvider,
        mockAuthServiceProvider
      ]
    });

    service = TestBed.inject(AppSettingsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch app settings on first subscription', async () => {
    const settings = createMockAppSettings({oidcEnabled: true});
    const getSpy = vi.fn().mockReturnValue(of(settings));
    Object.assign(service['http'], {get: getSpy});

    const result = await firstValueFrom(service.appSettings$.pipe(filter(value => !!value)));

    expect(result).toEqual(settings);
    expect(getSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/settings');
  });

  it('should not re-fetch app settings when already loaded', () => {
    const settings = createMockAppSettings();
    (service['appSettingsSubject'] as unknown as BehaviorSubject<AppSettings | null>).next(settings);

    const getSpy = vi.fn();
    Object.assign(service['http'], {get: getSpy});

    const values: (AppSettings | null)[] = [];
    service.appSettings$.subscribe(value => values.push(value));

    expect(values).toEqual([settings]);
    expect(getSpy).not.toHaveBeenCalled();
  });

  it('should handle fetch errors for app settings', async () => {
    const getSpy = vi.fn().mockReturnValue(throwError(() => new Error('load failed')));
    Object.assign(service['http'], {get: getSpy});

    await expect(firstValueFrom(service['fetchAppSettings']())).rejects.toThrow('load failed');
    expect(service['appSettingsSubject'].value).toBeNull();
  });

  it('should fetch public settings on first subscription', async () => {
    const settings = createMockPublicAppSettings({remoteAuthEnabled: true});
    const getSpy = vi.fn().mockReturnValue(of(settings));
    Object.assign(service['http'], {get: getSpy});

    const result = await firstValueFrom(service.publicAppSettings$.pipe(filter(value => !!value)));

    expect(result).toEqual(settings);
    expect(getSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/public-settings');
  });

  it('should not re-fetch public settings when already loaded', () => {
    const settings = createMockPublicAppSettings();
    (service['publicAppSettingsSubject'] as unknown as BehaviorSubject<PublicAppSettings | null>).next(settings);

    const getSpy = vi.fn();
    Object.assign(service['http'], {get: getSpy});

    const values: (PublicAppSettings | null)[] = [];
    service.publicAppSettings$.subscribe(value => values.push(value));

    expect(values).toEqual([settings]);
    expect(getSpy).not.toHaveBeenCalled();
  });

  it('should expose current public settings', () => {
    const settings = createMockPublicAppSettings({oidcEnabled: true});
    (service['publicAppSettingsSubject'] as unknown as BehaviorSubject<PublicAppSettings | null>).next(settings);

    expect(service.currentPublicSettings).toEqual(settings);
  });

  it('should test OIDC connection', async () => {
    const providerDetails = createMockPublicAppSettings().oidcProviderDetails;
    const result: OidcTestResult = {success: true, checks: []};
    const postSpy = vi.fn().mockReturnValue(of(result));
    Object.assign(service['http'], {post: postSpy});

    const response = await firstValueFrom(service.testOidcConnection(providerDetails));

    expect(postSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/settings/oidc/test', providerDetails);
    expect(response).toEqual(result);
  });

  it('should save settings and refresh app settings', async () => {
    const updatedSettings = createMockAppSettings({autoBookSearch: true});
    const putSpy = vi.fn().mockReturnValue(of(undefined));
    const getSpy = vi.fn().mockReturnValue(of(updatedSettings));
    Object.assign(service['http'], {put: putSpy, get: getSpy});

    await firstValueFrom(service.saveSettings([{key: 'AUTO_BOOK_SEARCH', newValue: true}]));

    expect(putSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/settings', [{name: 'AUTO_BOOK_SEARCH', value: true}]);
    expect(getSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/settings');
  });

  it('should return empty observable when saving settings fails', async () => {
    const putSpy = vi.fn().mockReturnValue(throwError(() => new Error('save failed')));
    Object.assign(service['http'], {put: putSpy});

    const result = await lastValueFrom(
      service.saveSettings([{key: 'AUTO_BOOK_SEARCH', newValue: true}]).pipe(defaultIfEmpty(undefined))
    );

    expect(result).toBeUndefined();
    expect(putSpy).toHaveBeenCalled();
  });

  it('should toggle OIDC enabled and sync public settings', () => {
    const settings = createMockAppSettings({oidcEnabled: false});
    (service['appSettingsSubject'] as unknown as BehaviorSubject<AppSettings | null>).next(settings);

    const putSpy = vi.fn().mockReturnValue(of(undefined));
    Object.assign(service['http'], {put: putSpy});

    const publicNextSpy = vi.spyOn(
      service['publicAppSettingsSubject'] as unknown as BehaviorSubject<PublicAppSettings | null>,
      'next'
    );

    service.toggleOidcEnabled(true).subscribe();

    expect(putSpy).toHaveBeenCalledWith('http://localhost:6060/api/v1/settings', [{name: 'OIDC_ENABLED', value: true}]);
    expect(service['appSettingsSubject'].value?.oidcEnabled).toBe(true);
    expect(publicNextSpy).toHaveBeenCalledWith(expect.objectContaining({oidcEnabled: true}));
  });

  it('should not sync public settings when unchanged', () => {
    const publicSettings = createMockPublicAppSettings({oidcEnabled: false});
    (service['publicAppSettingsSubject'] as unknown as BehaviorSubject<PublicAppSettings | null>).next(publicSettings);

    const nextSpy = vi.spyOn(
      service['publicAppSettingsSubject'] as unknown as BehaviorSubject<PublicAppSettings | null>,
      'next'
    );

    service['syncPublicSettings'](createMockAppSettings({
      oidcEnabled: false,
      remoteAuthEnabled: false,
      oidcForceOnlyMode: false,
      oidcProviderDetails: publicSettings.oidcProviderDetails
    }));

    expect(nextSpy).not.toHaveBeenCalled();
  });
});
