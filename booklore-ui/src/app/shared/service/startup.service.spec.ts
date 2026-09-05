import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject, of, throwError} from 'rxjs';
import {StartupService} from './startup.service';
import {AuthService} from './auth.service';
import {UserService} from '../../features/settings/user-management/user.service';
import {createMockUser} from '../../../testing/factories';

describe('StartupService', () => {
  let service: StartupService;
  let tokenSubject: BehaviorSubject<string | null>;
  let authService: { token$: BehaviorSubject<string | null> };
  let userService: {
    getMyself: ReturnType<typeof vi.fn>;
    setInitialUser: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    tokenSubject = new BehaviorSubject<string | null>(null);
    authService = {token$: tokenSubject};
    userService = {
      getMyself: vi.fn(),
      setInitialUser: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        StartupService,
        {provide: AuthService, useValue: authService},
        {provide: UserService, useValue: userService}
      ]
    });

    service = TestBed.inject(StartupService);
  });

  it('should resolve load() immediately', async () => {
    await expect(service.load()).resolves.toBeUndefined();
  });

  it('should fetch and set the initial user when the auth token becomes available', async () => {
    const user = createMockUser({id: 42, username: 'startup-user'});
    userService.getMyself.mockReturnValue(of(user));

    await service.load();
    tokenSubject.next('token');
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(userService.getMyself).toHaveBeenCalledTimes(1);
    expect(userService.setInitialUser).toHaveBeenCalledWith(user);
  });

  it('should ignore null token emissions', async () => {
    await service.load();
    tokenSubject.next(null);
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(userService.getMyself).not.toHaveBeenCalled();
  });

  it('should swallow getMyself errors without setting the initial user', async () => {
    userService.getMyself.mockReturnValue(throwError(() => new Error('fetch failed')));

    await service.load();
    tokenSubject.next('token');
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(userService.getMyself).toHaveBeenCalledTimes(1);
    expect(userService.setInitialUser).not.toHaveBeenCalled();
  });
});
