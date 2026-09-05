import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';
import {MetadataMatchWeightsService} from './metadata-match-weights.service';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('MetadataMatchWeightsService', () => {
  let service: MetadataMatchWeightsService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  const endpoint = 'http://localhost:6060/api/v1/books/metadata/recalculate-match-scores';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MetadataMatchWeightsService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(MetadataMatchWeightsService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.post.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should recalculate all match scores', () => {
    let emitted = false;
    service.recalculateAll().subscribe(() => emitted = true);

    expect(httpClient.post).toHaveBeenCalledWith(endpoint, {});
    expect(emitted).toBe(true);
  });

  it('should propagate recalculation errors', async () => {
    httpClient.post.mockReturnValue(throwError(() => new Error('recalculation failed')));

    await expect(firstValueFrom(service.recalculateAll())).rejects.toThrow('recalculation failed');
    expect(httpClient.post).toHaveBeenCalledWith(endpoint, {});
  });
});
