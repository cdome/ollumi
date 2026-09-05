import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {Router, UrlTree} from '@angular/router';
import {firstValueFrom, of, throwError} from 'rxjs';
import {SetupGuard} from './setup.guard';

describe('SetupGuard', () => {
  let guard: SetupGuard;
  let http: { get: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    http = {get: vi.fn()};
    router = {createUrlTree: vi.fn((segments: string[]) => ({segments} as UrlTree))};

    TestBed.configureTestingModule({
      providers: [
        SetupGuard,
        {provide: HttpClient, useValue: http},
        {provide: Router, useValue: router}
      ]
    });

    guard = TestBed.inject(SetupGuard);
  });

  it('should redirect to login when setup is already complete', async () => {
    http.get.mockReturnValue(of({data: true}));

    const result = await firstValueFrom(guard.canActivate());

    expect(http.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/setup/status');
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).not.toBe(false);
  });

  it('should allow activation when setup is not complete', async () => {
    http.get.mockReturnValue(of({data: false}));

    const result = await firstValueFrom(guard.canActivate());

    expect(result).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });
});
