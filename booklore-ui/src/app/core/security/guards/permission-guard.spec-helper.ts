import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import type {CanActivateFn} from '@angular/router';
import {Router} from '@angular/router';
import {BehaviorSubject} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {UserService, User, UserState} from '../../../features/settings/user-management/user.service';
import {createMockUser} from '../../../../testing/factories';

export function runPermissionGuardTests(
  name: string,
  guard: CanActivateFn,
  permissionKey: keyof User['permissions']
): void {
  describe(name, () => {
    let userStateSubject: BehaviorSubject<UserState>;
    let router: { navigate: ReturnType<typeof vi.fn> };

    beforeEach(() => {
      userStateSubject = new BehaviorSubject<UserState>({
        user: createMockUser(),
        loaded: true,
        error: null
      });
      router = {navigate: vi.fn(() => Promise.resolve(true))};

      TestBed.configureTestingModule({
        providers: [
          {provide: UserService, useValue: {userState$: userStateSubject.asObservable()}},
          {provide: Router, useValue: router}
        ]
      });
    });

    async function runGuard(): Promise<boolean> {
      return TestBed.runInInjectionContext(() => firstValueFrom(guard({} as any, {} as any)));
    }

    it('should allow activation for admin users', async () => {
      userStateSubject.next({
        user: createMockUser({permissions: {...createMockUser().permissions, admin: true, [permissionKey]: false}}),
        loaded: true,
        error: null
      });

      const result = await runGuard();

      expect(result).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should allow activation for users with the required permission', async () => {
      userStateSubject.next({
        user: createMockUser({permissions: {...createMockUser().permissions, admin: false, [permissionKey]: true}}),
        loaded: true,
        error: null
      });

      const result = await runGuard();

      expect(result).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should redirect to dashboard when the user lacks access', async () => {
      userStateSubject.next({
        user: createMockUser({permissions: {...createMockUser().permissions, admin: false, [permissionKey]: false}}),
        loaded: true,
        error: null
      });

      const result = await runGuard();

      expect(result).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('should redirect to dashboard when no user is loaded', async () => {
      userStateSubject.next({user: null, loaded: true, error: null});

      const result = await runGuard();

      expect(result).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });
  });
}
