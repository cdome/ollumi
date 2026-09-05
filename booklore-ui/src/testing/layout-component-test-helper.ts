import { signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { vi } from 'vitest';

import { LibraryService } from '../app/features/book/service/library.service';
import { LibraryHealthService } from '../app/features/book/service/library-health.service';
import { LibraryShelfMenuService } from '../app/features/book/service/library-shelf-menu.service';
import { ShelfService } from '../app/features/book/service/shelf.service';
import { BookService } from '../app/features/book/service/book.service';
import { AuthorService } from '../app/features/author-browser/service/author.service';
import { SeriesDataService } from '../app/features/series-browser/service/series-data.service';
import { MagicShelfService, MagicShelfState } from '../app/features/magic-shelf/service/magic-shelf.service';
import { UserService, User } from '../app/features/settings/user-management/user.service';
import { BookdropFileService } from '../app/features/bookdrop/service/bookdrop-file.service';

import { LayoutService } from '../app/shared/layout/component/layout-main/service/app.layout.service';
import { MenuService } from '../app/shared/layout/component/layout-menu/service/app.menu.service';
import { BackgroundUploadService } from '../app/shared/layout/component/theme-configurator/background-upload.service';
import { FaviconService } from '../app/shared/layout/component/theme-configurator/favicon-service';
import { AppConfigService } from '../app/shared/service/app-config.service';
import { AuthService } from '../app/shared/service/auth.service';
import { LocalStorageService } from '../app/shared/service/local-storage.service';
import { MetadataProgressService } from '../app/shared/service/metadata-progress.service';
import { VersionService } from '../app/shared/service/version.service';
import { DialogLauncherService } from '../app/shared/services/dialog-launcher.service';
import { NotificationEventService } from '../app/shared/websocket/notification-event.service';

import { DynamicDialogRef } from 'primeng/dynamicdialog';

import { createMockBook, createMockLibrary, createMockShelf, createMockUser } from './factories';

export function createLayoutServiceMock() {
  return {
    config: signal({ menuMode: 'static' } as any),
    state: {
      staticMenuDesktopInactive: false,
      overlayMenuActive: false,
      profileSidebarVisible: false,
      configSidebarVisible: false,
      staticMenuMobileActive: false,
      menuHoverActive: false,
    },
    overlayOpen$: new Subject().asObservable(),
    onMenuToggle: vi.fn(),
    isOverlay: vi.fn(() => false),
    isDesktop: vi.fn(() => true),
  };
}

export function createMenuServiceMock() {
  return {
    menuSource$: new Subject().asObservable(),
    resetSource$: new Subject().asObservable(),
    onMenuStateChange: vi.fn(),
    reset: vi.fn(),
  };
}

export function createRouterMock() {
  return {
    navigate: vi.fn(() => Promise.resolve(true)),
    navigateByUrl: vi.fn(() => Promise.resolve(true)),
    url: '/',
    events: of(),
    isActive: vi.fn(() => false),
    createUrlTree: vi.fn(),
    serializeUrl: vi.fn(),
  };
}

export function createActivatedRouteMock() {
  return {
    params: of({}),
    queryParams: of({}),
    paramMap: of({ get: vi.fn(() => null) }),
    queryParamMap: of({ get: vi.fn(() => null) }),
    snapshot: {
      paramMap: { get: vi.fn(() => null) },
      queryParamMap: { get: vi.fn(() => null) }
    }
  };
}

export function createAuthServiceMock() {
  return {
    token$: of(null),
    tokenSubject: { next: vi.fn(), value: null },
    internalLogin: vi.fn(),
    internalRefreshToken: vi.fn(),
    remoteLogin: vi.fn(),
    logout: vi.fn(),
    forceLogout: vi.fn(),
    getInternalAccessToken: vi.fn(() => null),
    getInternalRefreshToken: vi.fn(() => null),
    saveInternalTokens: vi.fn(),
  };
}

export function createUserServiceMock() {
  const userStateSubject = new BehaviorSubject<{ user: User | null; loaded: boolean; error: string | null }>({
    user: createMockUser(),
    loaded: true,
    error: null,
  });

  return {
    userState$: userStateSubject.asObservable(),
    userStateSubject,
    getCurrentUser: vi.fn(() => createMockUser()),
    setInitialUser: vi.fn(),
    getMyself: vi.fn(() => of(createMockUser())),
    createUser: vi.fn(() => of(undefined)),
    getUsers: vi.fn(() => of([])),
    updateUser: vi.fn(() => of({} as User)),
    deleteUser: vi.fn(() => of(undefined)),
    changeUserPassword: vi.fn(() => of(undefined)),
    changePassword: vi.fn(() => of(undefined)),
    updateUserSetting: vi.fn(),
  };
}

export function createNotificationEventServiceMock() {
  return {
    latestNotification$: of(),
    notificationHighlight$: of(false),
    handleNewNotification: vi.fn(),
  };
}

export function createMetadataProgressServiceMock() {
  return {
    activeTasks$: of({}),
    progressUpdates$: of({ status: 'IDLE' }),
    handleIncomingProgress: vi.fn(),
    clearTask: vi.fn(),
  };
}

export function createBookdropFileServiceMock() {
  return {
    hasPendingFiles$: of(false),
    summary$: of({ pendingCount: 0, totalCount: 0 }),
    handleIncomingFile: vi.fn(),
    refresh: vi.fn(),
  };
}

export function createDialogLauncherServiceMock() {
  return {
    openDialog: vi.fn(() => ({ onClose: of(null), close: vi.fn() })),
    openGithubSupportDialog: vi.fn(),
    openLibraryCreateDialog: vi.fn(),
    openFileUploadDialog: vi.fn(),
    openUserProfileDialog: vi.fn(),
    openVersionChangelogDialog: vi.fn(),
    openMagicShelfCreateDialog: vi.fn(),
    openMagicShelfEditDialog: vi.fn(),
    openShelfEditDialog: vi.fn(),
    openShelfCreatorDialog: vi.fn(),
    openLibraryEditDialog: vi.fn(),
    openLibraryMetadataFetchDialog: vi.fn(),
  };
}

export function createLocalStorageServiceMock() {
  return {
    get: vi.fn(() => null),
    set: vi.fn(),
    remove: vi.fn(),
  };
}

export function createVersionServiceMock() {
  return {
    getVersion: vi.fn(() => of({ current: 'v1.0.0', latest: 'v1.0.0' })),
    getChangelog: vi.fn(() => of([])),
  };
}

export function createLibraryServiceMock() {
  const libraryStateSubject = new BehaviorSubject({ loaded: true, libraries: [createMockLibrary()], error: null });

  return {
    libraryState$: libraryStateSubject.asObservable(),
    libraryStateSubject,
    largeLibraryLoading$: new BehaviorSubject({ isLoading: false, expectedCount: 0 }),
    getLibrariesFromState: vi.fn(() => []),
    findLibraryById: vi.fn(),
    doesLibraryExistByName: vi.fn(() => false),
    getBookCount: vi.fn(() => of(0)),
    refreshLibrary: vi.fn(() => of(undefined)),
    deleteLibrary: vi.fn(() => of(undefined)),
  };
}

export function createLibraryHealthServiceMock() {
  return {
    initialize: vi.fn(),
    isUnhealthy$: vi.fn(() => of(false)),
  };
}

export function createShelfServiceMock() {
  const shelfStateSubject = new BehaviorSubject({ loaded: true, shelves: [createMockShelf()], error: null });

  return {
    shelfState$: shelfStateSubject.asObservable(),
    shelfStateSubject,
    getShelvesFromState: vi.fn(() => []),
    getShelfById: vi.fn(),
    reloadShelves: vi.fn(),
    getBookCount: vi.fn(() => of(0)),
    getUnshelvedBookCount: vi.fn(() => of(0)),
    deleteShelf: vi.fn(() => of(undefined)),
  };
}

export function createBookServiceMock() {
  const bookStateSubject = new BehaviorSubject({ loaded: true, books: [createMockBook()], error: null });

  return {
    bookState$: bookStateSubject.asObservable(),
    bookStateSubject,
    getCurrentBookState: vi.fn(() => ({ loaded: true, books: [createMockBook()], error: null })),
    refreshBooks: vi.fn(),
    getBookByIdFromState: vi.fn(),
    getBooksByIdsFromState: vi.fn(() => []),
    getBookByIdFromAPI: vi.fn(() => of(createMockBook())),
    removeBooksByLibraryId: vi.fn(),
    removeBooksFromShelf: vi.fn(),
  };
}

export function createLibraryShelfMenuServiceMock() {
  return {
    initializeLibraryMenuItems: vi.fn(() => []),
    initializeShelfMenuItems: vi.fn(() => []),
    initializeMagicShelfMenuItems: vi.fn(() => []),
  };
}

export function createMagicShelfServiceMock() {
  const shelvesStateSubject = new BehaviorSubject<MagicShelfState>({ shelves: [], loaded: true, error: null });

  return {
    shelvesState$: shelvesStateSubject.asObservable(),
    shelvesStateSubject,
    getBookCount: vi.fn(() => of(0)),
  };
}

export function createSeriesDataServiceMock() {
  return {
    allSeries$: new BehaviorSubject([]),
  };
}

export function createAuthorServiceMock() {
  return {
    allAuthors$: new BehaviorSubject([]),
    getAllAuthors: vi.fn(() => of([])),
    getAuthorDetails: vi.fn(() => of({})),
    getAuthorByName: vi.fn(() => of({})),
  };
}

export function createAppConfigServiceMock() {
  return {
    appState: signal({ primary: 'green', surface: 'ash' }),
    surfaces: [{ name: 'ash', palette: { '500': '#999999' } }],
  };
}

export function createBackgroundUploadServiceMock() {
  return {
    uploadFile: vi.fn(() => of('')),
    uploadUrl: vi.fn(() => of('')),
  };
}

export function createFaviconServiceMock() {
  return {
    updateFavicon: vi.fn(),
  };
}

export function createDynamicDialogRefMock() {
  return {
    close: vi.fn(),
  };
}

export function createHttpClientMock() {
  return {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
    request: vi.fn(),
  };
}

export function createLayoutComponentTestProviders() {
  return [
    provideNoopAnimations(),
    { provide: LayoutService, useValue: createLayoutServiceMock() },
    { provide: MenuService, useValue: createMenuServiceMock() },
    { provide: Router, useValue: createRouterMock() },
    { provide: ActivatedRoute, useValue: createActivatedRouteMock() },
    { provide: AuthService, useValue: createAuthServiceMock() },
    { provide: UserService, useValue: createUserServiceMock() },
    { provide: NotificationEventService, useValue: createNotificationEventServiceMock() },
    { provide: MetadataProgressService, useValue: createMetadataProgressServiceMock() },
    { provide: BookdropFileService, useValue: createBookdropFileServiceMock() },
    { provide: DialogLauncherService, useValue: createDialogLauncherServiceMock() },
    { provide: LocalStorageService, useValue: createLocalStorageServiceMock() },
    { provide: VersionService, useValue: createVersionServiceMock() },
    { provide: LibraryService, useValue: createLibraryServiceMock() },
    { provide: LibraryHealthService, useValue: createLibraryHealthServiceMock() },
    { provide: ShelfService, useValue: createShelfServiceMock() },
    { provide: BookService, useValue: createBookServiceMock() },
    { provide: LibraryShelfMenuService, useValue: createLibraryShelfMenuServiceMock() },
    { provide: MagicShelfService, useValue: createMagicShelfServiceMock() },
    { provide: SeriesDataService, useValue: createSeriesDataServiceMock() },
    { provide: AuthorService, useValue: createAuthorServiceMock() },
    { provide: AppConfigService, useValue: createAppConfigServiceMock() },
    { provide: BackgroundUploadService, useValue: createBackgroundUploadServiceMock() },
    { provide: FaviconService, useValue: createFaviconServiceMock() },
    { provide: DynamicDialogRef, useValue: createDynamicDialogRefMock() },
    { provide: HttpClient, useValue: createHttpClientMock() },
  ];
}
