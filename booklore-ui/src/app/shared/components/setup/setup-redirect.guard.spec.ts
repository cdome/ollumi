import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {firstValueFrom, of} from 'rxjs';
import {SetupRedirectGuard} from './setup-redirect.guard';

describe('SetupRedirectGuard', () => {
  let guard: SetupRedirectGuard;
  let http: { get: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    http = {get: vi.fn()};
    router = {navigate: vi.fn(() => Promise.resolve(true))};

    TestBed.configureTestingModule({
      providers: [
        SetupRedirectGuard,
        {provide: HttpClient, useValue: http},
        {provide: Router, useValue: router}
      ]
    });

    guard = TestBed.inject(SetupRedirectGuard);
  });

  it('should redirect to dashboard when setup is complete', async () => {
    http.get.mockReturnValue(of({data: true}));

    const result = await firstValueFrom(guard.canActivate());

    expect(http.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/setup/status');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(result).toBe(false);
  });

  it('should redirect to setup when setup is not complete', async () => {
    http.get.mockReturnValue(of({data: false}));

    const result = await firstValueFrom(guard.canActivate());

    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
    expect(result).toBe(false);
  });
});
