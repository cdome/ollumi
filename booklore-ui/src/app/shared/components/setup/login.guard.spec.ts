import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {firstValueFrom, of, throwError} from 'rxjs';
import {LoginGuard} from './login.guard';

describe('LoginGuard', () => {
  let guard: LoginGuard;
  let http: { get: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    http = {get: vi.fn()};
    router = {navigate: vi.fn(() => Promise.resolve(true))};

    TestBed.configureTestingModule({
      providers: [
        LoginGuard,
        {provide: HttpClient, useValue: http},
        {provide: Router, useValue: router}
      ]
    });

    guard = TestBed.inject(LoginGuard);
  });

  it('should allow activation when setup is complete', async () => {
    http.get.mockReturnValue(of({data: true}));

    const result = await firstValueFrom(guard.canActivate());

    expect(http.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/setup/status');
    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should redirect to setup when setup is not complete', async () => {
    http.get.mockReturnValue(of({data: false}));

    const result = await firstValueFrom(guard.canActivate());

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
  });

  it('should redirect to setup when the status request fails', async () => {
    http.get.mockReturnValue(throwError(() => new Error('network error')));

    const result = await firstValueFrom(guard.canActivate());

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
  });
});
