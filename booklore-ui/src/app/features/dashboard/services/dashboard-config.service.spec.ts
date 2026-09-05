import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject, firstValueFrom, Subscription} from 'rxjs';
import {DashboardConfigService} from './dashboard-config.service';
import {UserService, type UserState} from '../../settings/user-management/user.service';
import {MagicShelfService, type MagicShelfState, type MagicShelf} from '../../magic-shelf/service/magic-shelf.service';
import {DEFAULT_DASHBOARD_CONFIG, ScrollerType} from '../models/dashboard-config.model';
import {createMockUser} from '../../../../testing/factories';

function createUserServiceMock() {
  const userStateSubject = new BehaviorSubject<UserState>({user: null, loaded: false, error: null});
  return {
    userState$: userStateSubject.asObservable(),
    userStateSubject,
    getCurrentUser: vi.fn(() => userStateSubject.value.user),
    updateUserSetting: vi.fn()
  };
}

function createMagicShelfServiceMock() {
  const shelvesStateSubject = new BehaviorSubject<MagicShelfState>({shelves: null, loaded: false, error: null});
  return {
    shelvesState$: shelvesStateSubject.asObservable(),
    shelvesStateSubject
  };
}

describe('DashboardConfigService', () => {
  let service: DashboardConfigService;
  let userServiceMock: ReturnType<typeof createUserServiceMock>;
  let magicShelfServiceMock: ReturnType<typeof createMagicShelfServiceMock>;
  let subscriptions: Subscription[];

  beforeEach(() => {
    subscriptions = [];
    userServiceMock = createUserServiceMock();
    magicShelfServiceMock = createMagicShelfServiceMock();

    TestBed.configureTestingModule({
      providers: [
        DashboardConfigService,
        {provide: UserService, useValue: userServiceMock},
        {provide: MagicShelfService, useValue: magicShelfServiceMock}
      ]
    });

    service = TestBed.inject(DashboardConfigService);
  });

  afterEach(() => {
    subscriptions.forEach(sub => sub.unsubscribe());
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should expose the default dashboard config', async () => {
    const config = await firstValueFrom(service.config$);
    expect(config).toEqual(DEFAULT_DASHBOARD_CONFIG);
  });

  it('should load the dashboard config from the current user settings', async () => {
    const customConfig = {
      scrollers: [
        {id: 'custom-1', type: ScrollerType.LAST_READ, title: 'My Reading', enabled: true, order: 1, maxItems: 5}
      ]
    };
    const user = createMockUser({
      userSettings: {...createMockUser().userSettings, dashboardConfig: customConfig}
    });

    userServiceMock.userStateSubject.next({user, loaded: true, error: null});

    const config = await firstValueFrom(service.config$);
    expect(config).toEqual(customConfig);
  });

  it('should ignore user state that has not finished loading', async () => {
    userServiceMock.userStateSubject.next({user: createMockUser(), loaded: false, error: null});

    const config = await firstValueFrom(service.config$);
    expect(config).toEqual(DEFAULT_DASHBOARD_CONFIG);
  });

  it('should ignore user state without a user', async () => {
    userServiceMock.userStateSubject.next({user: null, loaded: true, error: null});

    const config = await firstValueFrom(service.config$);
    expect(config).toEqual(DEFAULT_DASHBOARD_CONFIG);
  });

  it('should update magic-shelf scroller titles and persist the config', async () => {
    const customConfig = {
      scrollers: [
        {id: 'ms-1', type: ScrollerType.MAGIC_SHELF, title: 'Old name', enabled: true, order: 1, maxItems: 10, magicShelfId: 5}
      ]
    };
    const user = createMockUser({
      userSettings: {...createMockUser().userSettings, dashboardConfig: customConfig}
    });

    userServiceMock.userStateSubject.next({user, loaded: true, error: null});

    const shelf: MagicShelf = {id: 5, name: 'Updated name', filterJson: '{}'};
    magicShelfServiceMock.shelvesStateSubject.next({shelves: [shelf], loaded: true, error: null});

    const config = await firstValueFrom(service.config$);
    expect(config.scrollers[0].title).toBe('Updated name');
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      user.id,
      'dashboardConfig',
      expect.objectContaining({
        scrollers: expect.arrayContaining([
          expect.objectContaining({title: 'Updated name', magicShelfId: 5})
        ])
      })
    );
  });

  it('should not persist when the magic-shelf title has not changed', async () => {
    const customConfig = {
      scrollers: [
        {id: 'ms-1', type: ScrollerType.MAGIC_SHELF, title: 'Unchanged', enabled: true, order: 1, maxItems: 10, magicShelfId: 5}
      ]
    };
    const user = createMockUser({
      userSettings: {...createMockUser().userSettings, dashboardConfig: customConfig}
    });

    userServiceMock.userStateSubject.next({user, loaded: true, error: null});

    const shelf: MagicShelf = {id: 5, name: 'Unchanged', filterJson: '{}'};
    magicShelfServiceMock.shelvesStateSubject.next({shelves: [shelf], loaded: true, error: null});

    const config = await firstValueFrom(service.config$);
    expect(config.scrollers[0].title).toBe('Unchanged');
    expect(userServiceMock.updateUserSetting).not.toHaveBeenCalled();
  });

  it('should not persist magic-shelf title updates when there is no current user', async () => {
    const customConfig = {
      scrollers: [
        {id: 'ms-1', type: ScrollerType.MAGIC_SHELF, title: 'Old', enabled: true, order: 1, maxItems: 10, magicShelfId: 5}
      ]
    };
    const user = createMockUser({
      userSettings: {...createMockUser().userSettings, dashboardConfig: customConfig}
    });

    userServiceMock.userStateSubject.next({user, loaded: true, error: null});
    userServiceMock.getCurrentUser.mockReturnValue(null);

    const shelf: MagicShelf = {id: 5, name: 'Updated', filterJson: '{}'};
    magicShelfServiceMock.shelvesStateSubject.next({shelves: [shelf], loaded: true, error: null});

    const config = await firstValueFrom(service.config$);
    expect(config.scrollers[0].title).toBe('Updated');
    expect(userServiceMock.updateUserSetting).not.toHaveBeenCalled();
  });

  it('should save a new dashboard config and persist it', async () => {
    const user = createMockUser();
    userServiceMock.userStateSubject.next({user, loaded: true, error: null});
    userServiceMock.getCurrentUser.mockReturnValue(user);

    const newConfig = {
      scrollers: [
        {id: 'saved-1', type: ScrollerType.RANDOM, title: 'Saved', enabled: false, order: 1, maxItems: 5}
      ]
    };
    service.saveConfig(newConfig);

    const config = await firstValueFrom(service.config$);
    expect(config).toEqual(newConfig);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      user.id,
      'dashboardConfig',
      newConfig
    );
  });

  it('should not call updateUserSetting when saving without a current user', async () => {
    userServiceMock.getCurrentUser.mockReturnValue(null);

    service.saveConfig(DEFAULT_DASHBOARD_CONFIG);

    const config = await firstValueFrom(service.config$);
    expect(config).toEqual(DEFAULT_DASHBOARD_CONFIG);
    expect(userServiceMock.updateUserSetting).not.toHaveBeenCalled();
  });

  it('should reset to the default dashboard config', async () => {
    const user = createMockUser();
    userServiceMock.userStateSubject.next({user, loaded: true, error: null});
    userServiceMock.getCurrentUser.mockReturnValue(user);

    service.resetToDefault();

    const config = await firstValueFrom(service.config$);
    expect(config).toEqual(DEFAULT_DASHBOARD_CONFIG);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      user.id,
      'dashboardConfig',
      DEFAULT_DASHBOARD_CONFIG
    );
  });
});
