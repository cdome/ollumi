import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject, of} from 'rxjs';
import {MetadataProgressService} from './metadata-progress.service';
import {MetadataTaskService} from '../../features/book/service/metadata-task';
import {UserService, UserState} from '../../features/settings/user-management/user.service';
import {MetadataBatchProgressNotification, MetadataBatchStatus} from '../model/metadata-batch-progress.model';

describe('MetadataProgressService', () => {
  let service: MetadataProgressService;
  let userStateSubject: BehaviorSubject<UserState>;
  let getActiveTasksSpy: ReturnType<typeof vi.fn>;

  const adminUser = {
    id: 1,
    username: 'admin',
    name: 'Admin',
    email: 'admin@test.com',
    assignedLibraries: [],
    permissions: {
      admin: true,
      canUpload: false,
      canDownload: false,
      canEmailBook: false,
      canDeleteBook: false,
      canEditMetadata: false,
      canManageLibrary: false,
      canManageMetadataConfig: false,
      canSyncKoReader: false,
      canSyncKobo: false,
      canAccessOpds: false,
      canAccessBookdrop: false,
      canAccessLibraryStats: false,
      canAccessUserStats: false,
      canAccessTaskManager: false,
      canManageEmailConfig: false,
      canManageGlobalPreferences: false,
      canManageIcons: false,
      canManageFonts: false,
      demoUser: false,
      canBulkAutoFetchMetadata: false,
      canBulkCustomFetchMetadata: false,
      canBulkEditMetadata: false,
      canBulkRegenerateCover: false,
      canMoveOrganizeFiles: false,
      canBulkLockUnlockMetadata: false,
      canBulkResetBookloreReadProgress: false,
      canBulkResetKoReaderReadProgress: false,
      canBulkResetBookReadStatus: false,
    },
    userSettings: {} as any,
    provisioningMethod: 'LOCAL' as const
  };

  const editorUser = {
    ...adminUser,
    permissions: {
      ...adminUser.permissions,
      admin: false,
      canEditMetadata: true
    }
  };

  const noPermissionUser = {
    ...adminUser,
    permissions: {
      ...adminUser.permissions,
      admin: false,
      canEditMetadata: false
    }
  };

  function createTask(taskId: string, completed = 0): MetadataBatchProgressNotification {
    return {
      taskId,
      completed,
      total: 10,
      message: 'Progress message',
      status: MetadataBatchStatus.IN_PROGRESS,
      review: false
    };
  }

  beforeEach(() => {
    userStateSubject = new BehaviorSubject<UserState>({
      user: null,
      loaded: false,
      error: null
    });
    getActiveTasksSpy = vi.fn().mockReturnValue(of([]));

    TestBed.configureTestingModule({
      providers: [
        MetadataProgressService,
        {
          provide: MetadataTaskService,
          useValue: {getActiveTasks: getActiveTasksSpy}
        },
        {
          provide: UserService,
          useValue: {userState$: userStateSubject.asObservable()}
        }
      ]
    });

    service = TestBed.inject(MetadataProgressService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should not fetch active tasks when user lacks metadata permissions', () => {
    userStateSubject.next({user: noPermissionUser, loaded: true, error: null});

    expect(getActiveTasksSpy).not.toHaveBeenCalled();
  });

  it('should fetch active tasks for an admin user', () => {
    const activeTasks = [createTask('task-1'), createTask('task-2')];
    getActiveTasksSpy.mockReturnValue(of(activeTasks));

    userStateSubject.next({user: adminUser, loaded: true, error: null});

    expect(getActiveTasksSpy).toHaveBeenCalled();
    expectActiveTasks(activeTasks);
  });

  it('should fetch active tasks for a user with canEditMetadata permission', () => {
    const activeTasks = [createTask('task-1')];
    getActiveTasksSpy.mockReturnValue(of(activeTasks));

    userStateSubject.next({user: editorUser, loaded: true, error: null});

    expect(getActiveTasksSpy).toHaveBeenCalled();
    expectActiveTasks(activeTasks);
  });

  it('should handle incoming progress updates', () => {
    userStateSubject.next({user: adminUser, loaded: true, error: null});

    const progress1 = createTask('task-1', 3);
    const updates: MetadataBatchProgressNotification[] = [];

    service.progressUpdates$.subscribe(p => updates.push(p));
    service.handleIncomingProgress(progress1);

    expect(updates).toEqual([progress1]);
    expectActiveTasks([progress1]);
  });

  it('should update existing task progress and preserve active tasks list', () => {
    userStateSubject.next({user: adminUser, loaded: true, error: null});

    const progress1 = createTask('task-1', 2);
    const progress2 = createTask('task-1', 5);
    const progress3 = createTask('task-2', 1);

    service.handleIncomingProgress(progress1);
    service.handleIncomingProgress(progress3);
    service.handleIncomingProgress(progress2);

    expectActiveTasks([progress2, progress3]);
  });

  it('should clear a task and update active tasks', () => {
    userStateSubject.next({user: adminUser, loaded: true, error: null});

    const progress1 = createTask('task-1');
    const progress2 = createTask('task-2');

    service.handleIncomingProgress(progress1);
    service.handleIncomingProgress(progress2);
    expectActiveTasks([progress1, progress2]);

    service.clearTask('task-1');
    expectActiveTasks([progress2]);
  });

  it('should initialize active tasks returned from the backend', () => {
    const task1 = createTask('backend-1', 1);
    const task2 = createTask('backend-2', 2);
    getActiveTasksSpy.mockReturnValue(of([task1, task2]));

    userStateSubject.next({user: adminUser, loaded: true, error: null});

    expectActiveTasks([task1, task2]);
  });

  it('should complete subjects on destroy', () => {
    const progress = createTask('task-1');
    const completeSpy = vi.fn();

    service.progressUpdates$.subscribe({complete: completeSpy});
    service.activeTasks$.subscribe({complete: completeSpy});
    service.handleIncomingProgress(progress);

    service.ngOnDestroy();

    expect(completeSpy).toHaveBeenCalledTimes(2);
  });

  function expectActiveTasks(expected: MetadataBatchProgressNotification[]): void {
    let active: Record<string, MetadataBatchProgressNotification> = {};
    service.activeTasks$.subscribe(tasks => {
      active = tasks;
    });

    expect(Object.keys(active)).toHaveLength(expected.length);
    for (const task of expected) {
      expect(active[task.taskId]).toEqual(task);
    }
  }
});
