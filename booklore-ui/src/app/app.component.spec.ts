import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {AppComponent} from './app.component';
import {AuthInitializationService} from './core/security/auth-initialization-service';
import {BehaviorSubject, of, Subject} from 'rxjs';
import {RxStompService} from './shared/websocket/rx-stomp.service';
import {BookService} from './features/book/service/book.service';
import {NotificationEventService} from './shared/websocket/notification-event.service';
import {AppConfigService} from './shared/service/app-config.service';
import {MetadataProgressService} from './shared/service/metadata-progress.service';
import {BookdropFileService} from './features/bookdrop/service/bookdrop-file.service';
import {TaskService} from './features/settings/task-management/task.service';
import {LibraryService} from './features/book/service/library.service';
import {LibraryHealthService} from './features/book/service/library-health.service';
import {LibraryLoadingService} from './features/library-creator/library-loading.service';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {AuthService} from './shared/service/auth.service';
import {ConfirmationService, MessageService} from 'primeng/api';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  let authInitSubject: BehaviorSubject<boolean>;

  let topicSubjects: Map<string, Subject<{ body: string }>>;
  let rxStompWatch: ReturnType<typeof vi.fn>;
  let bookServiceMock: { [key: string]: ReturnType<typeof vi.fn> };
  let notificationEventServiceMock: { handleNewNotification: ReturnType<typeof vi.fn> };
  let metadataProgressServiceMock: { handleIncomingProgress: ReturnType<typeof vi.fn> };
  let bookdropFileServiceMock: { handleIncomingFile: ReturnType<typeof vi.fn> };
  let taskServiceMock: { handleTaskProgress: ReturnType<typeof vi.fn> };
  let libraryLoadingServiceMock: { showBookLoadingProgress: ReturnType<typeof vi.fn>; hide: ReturnType<typeof vi.fn> };
  let largeLibraryLoadingSubject: BehaviorSubject<{ isLoading: boolean; expectedCount: number }>;
  let authServiceMock: { forceLogout: ReturnType<typeof vi.fn>; token$: ReturnType<typeof of> };
  let libraryHealthServiceMock: { initialize: ReturnType<typeof vi.fn> };

  function createFixture(authReady: boolean): void {
    if (fixture) {
      fixture.destroy();
    }
    TestBed.resetTestingModule();

    authInitSubject = new BehaviorSubject<boolean>(authReady);

    topicSubjects = new Map<string, Subject<{ body: string }>>();
    rxStompWatch = vi.fn((topic: string) => {
      if (!topicSubjects.has(topic)) {
        topicSubjects.set(topic, new Subject<{ body: string }>());
      }
      return topicSubjects.get(topic)!.asObservable();
    });

    bookServiceMock = {
      handleNewlyCreatedBook: vi.fn(),
      handleBookUpdate: vi.fn(),
      handleMultipleBookCoverPatches: vi.fn(),
      handleRemovedBookIds: vi.fn(),
      handleMultipleBookUpdates: vi.fn(),
    };

    notificationEventServiceMock = {handleNewNotification: vi.fn()};
    metadataProgressServiceMock = {handleIncomingProgress: vi.fn()};
    bookdropFileServiceMock = {handleIncomingFile: vi.fn()};
    taskServiceMock = {handleTaskProgress: vi.fn()};
    libraryLoadingServiceMock = {showBookLoadingProgress: vi.fn(), hide: vi.fn()};
    largeLibraryLoadingSubject = new BehaviorSubject<{ isLoading: boolean; expectedCount: number }>({
      isLoading: false,
      expectedCount: 0
    });
    authServiceMock = {forceLogout: vi.fn(), token$: of(null)};
    libraryHealthServiceMock = {initialize: vi.fn()};

    TestBed.configureTestingModule({
      imports: [TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: AuthInitializationService, useValue: {initialized$: authInitSubject.asObservable()}},
        {provide: RxStompService, useValue: {watch: rxStompWatch, publish: vi.fn(), activate: vi.fn(), deactivate: vi.fn()}},
        {provide: BookService, useValue: bookServiceMock},
        {provide: NotificationEventService, useValue: notificationEventServiceMock},
        {provide: AppConfigService, useValue: {}},
        {provide: MetadataProgressService, useValue: metadataProgressServiceMock},
        {provide: BookdropFileService, useValue: bookdropFileServiceMock},
        {provide: TaskService, useValue: taskServiceMock},
        {provide: LibraryService, useValue: {largeLibraryLoading$: largeLibraryLoadingSubject.asObservable(), setLargeLibraryLoading: vi.fn()}},
        {provide: LibraryHealthService, useValue: libraryHealthServiceMock},
        {provide: LibraryLoadingService, useValue: libraryLoadingServiceMock},
        {provide: AuthService, useValue: authServiceMock},
        {provide: MessageService, useValue: {add: vi.fn(), clear: vi.fn()}},
        {provide: ConfirmationService, useValue: {confirm: vi.fn()}},
      ]
    });

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
  }

  beforeEach(() => {
    createFixture(false);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
    topicSubjects.clear();
    vi.restoreAllMocks();
  });

  function emit(topic: string, body: unknown): void {
    const subject = topicSubjects.get(topic);
    if (subject) {
      subject.next({body: typeof body === 'string' ? body : JSON.stringify(body)});
    }
  }

  describe('offline detection', () => {
    it('should initialize with offline set to false', () => {
      expect(component.offline).toBe(false);
    });

    it('should set offline to false when online event fires', () => {
      component.offline = true;
      window.dispatchEvent(new Event('online'));
      expect(component.offline).toBe(false);
    });

    it('should not show offline when server is reachable despite browser offline event', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {status: 200}));

      window.dispatchEvent(new Event('offline'));

      await vi.waitFor(() => {
        expect(component.offline).toBe(false);
      });
    });

    it('should show offline when server is unreachable on browser offline event', async () => {
      vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

      window.dispatchEvent(new Event('offline'));

      await vi.waitFor(() => {
        expect(component.offline).toBe(true);
      });
    });

    it('should ping server with HEAD method and no-store cache', async () => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {status: 200}));

      window.dispatchEvent(new Event('offline'));

      await vi.waitFor(() => {
        expect(fetchSpy).toHaveBeenCalledWith('/api/public/settings', {method: 'HEAD', cache: 'no-store'});
      });
    });

    it('should treat server errors as reachable', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {status: 500}));

      window.dispatchEvent(new Event('offline'));

      await vi.waitFor(() => {
        expect(component.offline).toBe(false);
      });
    });

    it('should treat network timeout as unreachable', async () => {
      vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('network timeout'));

      window.dispatchEvent(new Event('offline'));

      await vi.waitFor(() => {
        expect(component.offline).toBe(true);
      });
    });
  });

  describe('WebSocket topic handlers', () => {
    beforeEach(() => {
      createFixture(true);
      component.ngOnInit();
    });

    it('should subscribe to all expected topics once auth is ready', () => {
      const expectedTopics = [
        '/user/queue/book-add',
        '/user/queue/book-update',
        '/user/queue/books-cover-update',
        '/user/queue/books-remove',
        '/user/queue/book-metadata-update',
        '/user/queue/book-metadata-batch-update',
        '/user/queue/book-metadata-batch-progress',
        '/user/queue/log',
        '/user/queue/bookdrop-file',
        '/user/queue/task-progress',
        '/user/queue/session-revoked'
      ];
      expectedTopics.forEach(topic => {
        expect(rxStompWatch).toHaveBeenCalledWith(topic);
      });
      expect(libraryHealthServiceMock.initialize).toHaveBeenCalled();
    });

    it('should handle /user/queue/book-add by creating the new book', () => {
      emit('/user/queue/book-add', {id: 1, metadata: {title: 'New Book'}});
      expect(bookServiceMock.handleNewlyCreatedBook).toHaveBeenCalledWith({id: 1, metadata: {title: 'New Book'}});
    });

    it('should handle /user/queue/book-update by updating the book', () => {
      emit('/user/queue/book-update', {id: 2, metadata: {title: 'Updated Book'}});
      expect(bookServiceMock.handleBookUpdate).toHaveBeenCalledWith({id: 2, metadata: {title: 'Updated Book'}});
    });

    it('should handle /user/queue/books-cover-update by patching covers', () => {
      const patches = [{id: 3, coverUpdatedOn: '2024-01-01T00:00:00Z'}];
      emit('/user/queue/books-cover-update', patches);
      expect(bookServiceMock.handleMultipleBookCoverPatches).toHaveBeenCalledWith(patches);
    });

    it('should handle /user/queue/books-remove by removing the books', () => {
      emit('/user/queue/books-remove', [4, 5]);
      expect(bookServiceMock.handleRemovedBookIds).toHaveBeenCalledWith([4, 5]);
    });

    it('should handle /user/queue/book-metadata-update by updating the book metadata', () => {
      emit('/user/queue/book-metadata-update', {id: 6, metadata: {title: 'Metadata Update'}});
      expect(bookServiceMock.handleBookUpdate).toHaveBeenCalledWith({id: 6, metadata: {title: 'Metadata Update'}});
    });

    it('should handle /user/queue/book-metadata-batch-update by updating multiple books', () => {
      emit('/user/queue/book-metadata-batch-update', [{id: 7}]);
      expect(bookServiceMock.handleMultipleBookUpdates).toHaveBeenCalledWith([{id: 7}]);
    });

    it('should handle /user/queue/book-metadata-batch-progress by delegating to MetadataProgressService', () => {
      const progress = {taskId: 'task-1', current: 5, total: 10};
      emit('/user/queue/book-metadata-batch-progress', progress);
      expect(metadataProgressServiceMock.handleIncomingProgress).toHaveBeenCalledWith(progress);
    });

    it('should handle /user/queue/log by forwarding a parsed notification', () => {
      emit('/user/queue/log', JSON.stringify({message: 'hello', severity: 'INFO'}));
      expect(notificationEventServiceMock.handleNewNotification).toHaveBeenCalledWith(
        expect.objectContaining({message: 'hello', severity: 'INFO'})
      );
    });

    it('should handle /user/queue/bookdrop-file by forwarding the file notification', () => {
      const notification = {pendingCount: 2, totalCount: 5};
      emit('/user/queue/bookdrop-file', notification);
      expect(bookdropFileServiceMock.handleIncomingFile).toHaveBeenCalledWith(notification);
    });

    it('should handle /user/queue/task-progress by delegating to TaskService', () => {
      const progress = {taskId: 'task-2', taskType: 'REFRESH_LIBRARY_METADATA', message: 'working', progress: 42, taskStatus: 'IN_PROGRESS'};
      emit('/user/queue/task-progress', progress);
      expect(taskServiceMock.handleTaskProgress).toHaveBeenCalledWith(progress);
    });

    it('should handle /user/queue/session-revoked by forcing a logout', () => {
      emit('/user/queue/session-revoked', '');
      expect(authServiceMock.forceLogout).toHaveBeenCalledWith('session_revoked');
    });
  });
});
