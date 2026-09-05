import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {Subscription, firstValueFrom, of, throwError} from 'rxjs';
import {OidcGroupMappingService} from './oidc-group-mapping.service';
import type {OidcGroupMapping} from '../model/oidc-group-mapping.model';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('OidcGroupMappingService', () => {
  let service: OidcGroupMappingService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let subscriptions: Subscription[];
  const baseUrl = 'http://localhost:6060/api/v1/admin/oidc-group-mappings';

  function createMapping(overrides: Partial<OidcGroupMapping> = {}): OidcGroupMapping {
    return {
      oidcGroupClaim: 'admins',
      isAdmin: false,
      permissions: [],
      libraryIds: [],
      description: 'Test mapping',
      ...overrides
    };
  }

  beforeEach(() => {
    subscriptions = [];

    TestBed.configureTestingModule({
      providers: [
        OidcGroupMappingService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(OidcGroupMappingService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    subscriptions.forEach(s => s.unsubscribe());
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch all group mappings', () => {
    const mappings = [createMapping({id: 1}), createMapping({id: 2, oidcGroupClaim: 'users'})];
    httpClient.get.mockReturnValue(of(mappings));

    let result: OidcGroupMapping[] | undefined;
    subscriptions.push(service.getAll().subscribe(m => result = m));

    expect(httpClient.get).toHaveBeenCalledWith(baseUrl);
    expect(result).toEqual(mappings);
  });

  it('should create a group mapping', () => {
    const mapping = createMapping({id: 3});
    httpClient.post.mockReturnValue(of(mapping));

    let result: OidcGroupMapping | undefined;
    subscriptions.push(service.create(mapping).subscribe(m => result = m));

    expect(httpClient.post).toHaveBeenCalledWith(baseUrl, mapping);
    expect(result).toEqual(mapping);
  });

  it('should update a group mapping', () => {
    const mapping = createMapping({id: 4, isAdmin: true});
    httpClient.put.mockReturnValue(of(mapping));

    let result: OidcGroupMapping | undefined;
    subscriptions.push(service.update(4, mapping).subscribe(m => result = m));

    expect(httpClient.put).toHaveBeenCalledWith(`${baseUrl}/4`, mapping);
    expect(result).toEqual(mapping);
  });

  it('should delete a group mapping', () => {
    let completed = false;
    subscriptions.push(
      service.delete(5).subscribe({
        next: () => completed = true,
        error: () => {}
      })
    );

    expect(httpClient.delete).toHaveBeenCalledWith(`${baseUrl}/5`);
    expect(completed).toBe(true);
  });

  it('should propagate errors from the backend', async () => {
    httpClient.put.mockReturnValue(throwError(() => new Error('update failed')));

    await expect(firstValueFrom(service.update(1, createMapping()))).rejects.toThrow('update failed');
  });
});
