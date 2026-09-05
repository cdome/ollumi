import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {firstValueFrom, of} from 'rxjs';
import {SecurePipe} from './secure-pipe';

describe('SecurePipe', () => {
  let pipe: SecurePipe;
  let http: { get: ReturnType<typeof vi.fn> };
  let sanitizer: { bypassSecurityTrustUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    http = {get: vi.fn()};
    sanitizer = {bypassSecurityTrustUrl: vi.fn((url: string) => `safe:${url}`)};

    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url');

    TestBed.configureTestingModule({
      providers: [
        SecurePipe,
        {provide: HttpClient, useValue: http},
        {provide: DomSanitizer, useValue: sanitizer}
      ]
    });

    pipe = TestBed.inject(SecurePipe);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should return an Observable that emits a SafeUrl created from the blob response', async () => {
    const blob = new Blob(['image-data']);
    http.get.mockReturnValue(of(blob));

    const result$ = pipe.transform('http://localhost:6060/api/v1/cover/1');

    expect(result$).toBeDefined();
    const result = await firstValueFrom(result$);

    expect(http.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/cover/1', {responseType: 'blob'});
    expect(sanitizer.bypassSecurityTrustUrl).toHaveBeenCalledWith('blob:mock-url');
    expect(result).toBe('safe:blob:mock-url');
  });
});
