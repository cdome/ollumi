import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {Subscription, firstValueFrom, of, throwError} from 'rxjs';
import {ContentRestrictionService} from './content-restriction.service';
import {ContentRestriction, ContentRestrictionMode, ContentRestrictionType} from './content-restriction.model';
import {mockHttpClientProvider} from '../../../../testing/providers';

describe('ContentRestrictionService', () => {
  let service: ContentRestrictionService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let subscriptions: Subscription[];
  const baseUrl = 'http://localhost:6060/api/v1/users';

  function createRestriction(overrides: Partial<ContentRestriction> = {}): ContentRestriction {
    return {
      userId: 1,
      restrictionType: ContentRestrictionType.CATEGORY,
      mode: ContentRestrictionMode.EXCLUDE,
      value: 'Horror',
      ...overrides
    };
  }

  beforeEach(() => {
    subscriptions = [];

    TestBed.configureTestingModule({
      providers: [
        ContentRestrictionService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(ContentRestrictionService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of([]));
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

  it('should fetch restrictions for a user', () => {
    const restrictions = [createRestriction({id: 1}), createRestriction({id: 2, value: 'Mature'})];
    httpClient.get.mockReturnValue(of(restrictions));

    let result: ContentRestriction[] | undefined;
    subscriptions.push(service.getUserRestrictions(9).subscribe(r => result = r));

    expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/9/content-restrictions`);
    expect(result).toEqual(restrictions);
  });

  it('should add a restriction for a user', () => {
    const restriction = createRestriction({id: 10});
    httpClient.post.mockReturnValue(of(restriction));

    let result: ContentRestriction | undefined;
    subscriptions.push(service.addRestriction(9, restriction).subscribe(r => result = r));

    expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/9/content-restrictions`, restriction);
    expect(result).toEqual(restriction);
  });

  it('should update all restrictions for a user', () => {
    const restrictions = [createRestriction({id: 1, value: 'Sci-Fi'}), createRestriction({id: 2, value: 'Fantasy'})];
    httpClient.put.mockReturnValue(of(restrictions));

    let result: ContentRestriction[] | undefined;
    subscriptions.push(service.updateRestrictions(9, restrictions).subscribe(r => result = r));

    expect(httpClient.put).toHaveBeenCalledWith(`${baseUrl}/9/content-restrictions`, restrictions);
    expect(result).toEqual(restrictions);
  });

  it('should delete a restriction for a user', () => {
    let completed = false;
    subscriptions.push(
      service.deleteRestriction(9, 5).subscribe({
        next: () => completed = true,
        error: () => {}
      })
    );

    expect(httpClient.delete).toHaveBeenCalledWith(`${baseUrl}/9/content-restrictions/5`);
    expect(completed).toBe(true);
  });

  it('should delete all restrictions for a user', () => {
    let completed = false;
    subscriptions.push(
      service.deleteAllRestrictions(9).subscribe({
        next: () => completed = true,
        error: () => {}
      })
    );

    expect(httpClient.delete).toHaveBeenCalledWith(`${baseUrl}/9/content-restrictions`);
    expect(completed).toBe(true);
  });

  it('should propagate errors from the backend', async () => {
    httpClient.get.mockReturnValue(throwError(() => new Error('load failed')));

    await expect(firstValueFrom(service.getUserRestrictions(9))).rejects.toThrow('load failed');
  });
});
