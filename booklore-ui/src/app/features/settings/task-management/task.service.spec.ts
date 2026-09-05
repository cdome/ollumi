import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {TaskService, TaskStatus, TaskType, type TaskProgressPayload} from './task.service';
import {mockHttpClientProvider} from '../../../../testing/providers';

describe('TaskService', () => {
  let service: TaskService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  const baseUrl = 'http://localhost:6060/api/v1/tasks';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TaskService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(TaskService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of({}));
    httpClient.patch.mockReturnValue(of({}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should fetch available tasks', () => {
    const tasks = [{
      taskType: TaskType.SYNC_LIBRARY_FILES,
      name: 'Sync',
      description: 'Sync library files',
      parallel: false,
      async: false,
      cronSupported: false,
      cronConfig: null
    }];
    httpClient.get.mockReturnValue(of(tasks));

    let result: unknown;
    service.getAvailableTasks().subscribe(t => result = t);

    expect(httpClient.get).toHaveBeenCalledWith(baseUrl);
    expect(result).toEqual(tasks);
  });

  it('should start a task', () => {
    const request = {taskType: TaskType.CLEANUP_DELETED_BOOKS};
    const response = {id: '1', type: TaskType.CLEANUP_DELETED_BOOKS, status: TaskStatus.ACCEPTED};
    httpClient.post.mockReturnValue(of(response));

    let result: unknown;
    service.startTask(request).subscribe(r => result = r);

    expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/start`, request);
    expect(result).toEqual(response);
  });

  it('should get the latest tasks for each type', () => {
    const response = {
      taskHistories: [{
        id: 'h1',
        type: TaskType.REFRESH_LIBRARY_METADATA,
        status: TaskStatus.COMPLETED,
        progressPercentage: 100,
        message: null,
        createdAt: null,
        updatedAt: null,
        completedAt: null
      }]
    };
    httpClient.get.mockReturnValue(of(response));

    let result: unknown;
    service.getLatestTasksForEachType().subscribe(r => result = r);

    expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/last`);
    expect(result).toEqual(response);
  });

  it('should cancel a task', () => {
    const response = {taskId: 't1', cancelled: true, message: 'Cancelled'};
    httpClient.delete.mockReturnValue(of(response));

    let result: unknown;
    service.cancelTask('t1').subscribe(r => result = r);

    expect(httpClient.delete).toHaveBeenCalledWith(`${baseUrl}/t1/cancel`);
    expect(result).toEqual(response);
  });

  it('should update cron config', () => {
    const response = {
      id: 1,
      taskType: TaskType.SYNC_LIBRARY_FILES,
      cronExpression: '0 0 * * *',
      enabled: true,
      options: null,
      createdAt: null,
      updatedAt: null
    };
    httpClient.patch.mockReturnValue(of(response));

    const request = {cronExpression: '0 0 * * *', enabled: true};
    let result: unknown;
    service.updateCronConfig(TaskType.SYNC_LIBRARY_FILES, request).subscribe(r => result = r);

    expect(httpClient.patch).toHaveBeenCalledWith(`${baseUrl}/${TaskType.SYNC_LIBRARY_FILES}/cron`, request);
    expect(result).toEqual(response);
  });

  it('should emit task progress updates', () => {
    const payload: TaskProgressPayload = {
      taskId: 't1',
      taskType: TaskType.REFRESH_LIBRARY_METADATA,
      message: 'Done',
      progress: 100,
      taskStatus: TaskStatus.COMPLETED
    };
    const received: (TaskProgressPayload | null)[] = [];

    service.taskProgress$.subscribe(p => received.push(p));
    service.handleTaskProgress(payload);

    expect(received[received.length - 1]).toEqual(payload);
  });
});
