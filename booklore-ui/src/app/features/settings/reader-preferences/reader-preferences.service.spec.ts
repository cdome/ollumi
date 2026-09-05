import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject} from 'rxjs';
import {ReaderPreferencesService} from './reader-preferences.service';
import {UserService, type UserState} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {mockMessageServiceProvider, mockTranslocoServiceProvider} from '../../../../testing/providers';
import {createMockUser} from '../../../../testing/factories';

describe('ReaderPreferencesService', () => {
  let service: ReaderPreferencesService;
  let userStateSubject: BehaviorSubject<UserState>;
  let userService: {
    userState$: ReturnType<BehaviorSubject<UserState>['asObservable']>;
    updateUserSetting: Mock;
  };
  let messageService: {add: Mock; clear: Mock};
  let destroyCompleteSpy: Mock;

  beforeEach(() => {
    userStateSubject = new BehaviorSubject<UserState>({user: null, loaded: false, error: null});
    userService = {
      userState$: userStateSubject.asObservable(),
      updateUserSetting: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        ReaderPreferencesService,
        {provide: UserService, useValue: userService},
        mockMessageServiceProvider,
        mockTranslocoServiceProvider
      ]
    });

    service = TestBed.inject(ReaderPreferencesService);
    messageService = TestBed.inject(MessageService) as unknown as {add: Mock; clear: Mock};

    vi.resetAllMocks();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should not set current user when state is not loaded', () => {
    const user = createMockUser();
    userStateSubject.next({user, loaded: false, error: null});

    service.updatePreference(['epubReaderSetting', 'fontSize'], 14);

    expect(userService.updateUserSetting).not.toHaveBeenCalled();
  });

  it('should set current user on loaded state and persist preference updates', () => {
    const user = createMockUser();
    userStateSubject.next({user, loaded: true, error: null});

    service.updatePreference(['epubReaderSetting', 'fontSize'], 16);

    expect(userService.updateUserSetting).toHaveBeenCalledWith(
      user.id,
      'epubReaderSetting',
      expect.objectContaining({fontSize: 16})
    );
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });

  it('should create nested objects when updating a deep path', () => {
    const user = createMockUser();
    userStateSubject.next({user, loaded: true, error: null});

    service.updatePreference(['cbxReaderSetting', 'pageSpread'], 'EVEN');

    expect(userService.updateUserSetting).toHaveBeenCalledWith(
      user.id,
      'cbxReaderSetting',
      expect.objectContaining({pageSpread: 'EVEN'})
    );
  });

  it('should do nothing when no user is loaded', () => {
    userStateSubject.next({user: null, loaded: true, error: null});

    service.updatePreference(['epubReaderSetting', 'fontSize'], 14);

    expect(userService.updateUserSetting).not.toHaveBeenCalled();
    expect(messageService.add).not.toHaveBeenCalled();
  });

  it('should complete destroy subject on ngOnDestroy', () => {
    destroyCompleteSpy = vi.fn();
    (service as any).destroy$.complete = destroyCompleteSpy;

    service.ngOnDestroy();

    expect(destroyCompleteSpy).toHaveBeenCalled();
  });
});
