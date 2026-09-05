import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {VersionService, type AppVersion, type ReleaseNote} from './version.service';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('VersionService', () => {
  let service: VersionService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  const baseUrl = 'http://localhost:6060/api/v1/version';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        VersionService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(VersionService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of({}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should fetch version info', () => {
    const version: AppVersion = {current: '1.0.0', latest: '1.1.0'};
    httpClient.get.mockReturnValue(of(version));

    let result: AppVersion | undefined;
    service.getVersion().subscribe(v => result = v);

    expect(httpClient.get).toHaveBeenCalledWith(baseUrl);
    expect(result).toEqual(version);
  });

  it('should fetch the changelog', () => {
    const notes: ReleaseNote[] = [{
      version: '1.1.0',
      name: 'Release',
      changelog: 'Fixes',
      url: '',
      publishedAt: ''
    }];
    httpClient.get.mockReturnValue(of(notes));

    let result: ReleaseNote[] | undefined;
    service.getChangelog().subscribe(n => result = n);

    expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/changelog`);
    expect(result).toEqual(notes);
  });
});
