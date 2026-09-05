import {BehaviorSubject, of} from 'rxjs';
import {vi} from 'vitest';
import {ActivatedRoute} from '@angular/router';
import {SortDirection} from '../app/features/book/model/sort.model';
import {createMockBook, createMockUser} from './factories';

export const mockBook = createMockBook();
export const mockUser = createMockUser();

export function createParamMap(values: Record<string, string | null> = {}): {
  get: (key: string) => string | null;
  getAll: (key: string) => string[];
  has: (key: string) => boolean;
} {
  return {
    get: (key: string) => values[key] ?? null,
    getAll: (key: string) => (values[key] ? [values[key]!] : []),
    has: (key: string) => key in values && values[key] !== null,
  };
}

export function createMockBookService(overrides: Record<string, unknown> = {}) {
  const bookStateSubject = new BehaviorSubject<{loaded: boolean; books: unknown[]; error: unknown}>({
    loaded: true,
    books: [mockBook],
    error: null,
  });

  return {
    bookState$: bookStateSubject.asObservable(),
    bookStateSubject,
    getCurrentBookState: vi.fn(() => ({loaded: true, books: [mockBook], error: null})),
    getBooksByIdsFromState: vi.fn(() => []),
    getBookByIdFromAPI: vi.fn(() => of(mockBook)),
    readBook: vi.fn(),
    deleteBooks: vi.fn(() => of({})),
    updateBookReadStatus: vi.fn(() => of({})),
    updateBookShelves: vi.fn(() => of({})),
    resetProgress: vi.fn(() => of({})),
    refreshBooks: vi.fn(),
    ...overrides,
  };
}

export function createMockUserService(overrides: Record<string, unknown> = {}) {
  const userStateSubject = new BehaviorSubject<{user: typeof mockUser | null; loaded: boolean}>({
    user: mockUser,
    loaded: true,
  });

  return {
    userState$: userStateSubject.asObservable(),
    userStateSubject,
    getCurrentUser: vi.fn(() => mockUser),
    updateUserSetting: vi.fn(() => of({})),
    ...overrides,
  };
}

export const mockUrlHelperService = {
  getThumbnailUrl: vi.fn(() => 'thumbnail-url'),
  getAudiobookThumbnailUrl: vi.fn(() => 'audiobook-thumbnail-url'),
  getCoverUrl: vi.fn(() => 'cover-url'),
  getAudiobookCoverUrl: vi.fn(() => 'audiobook-cover-url'),
  getBookUrl: vi.fn((book) => ['/book', book.id]),
  getBookPrimaryReadingUrl: vi.fn((book) => ['/ebook-reader/book', book.id]),
  filterBooksBy: vi.fn(() => ['/all-books']),
};

export const mockBookDialogHelperService = {
  openShelfAssignerDialog: vi.fn(() => ({onClose: of({assigned: true})})),
  openLockUnlockMetadataDialog: vi.fn(() => ({onClose: of(null)})),
  openMetadataRefreshDialog: vi.fn(),
  openBulkMetadataEditDialog: vi.fn(() => ({onClose: of(null)})),
  openMultibookMetadataEditorDialog: vi.fn(() => ({onClose: of(null)})),
  openFileMoverDialog: vi.fn(),
  openCustomSendDialog: vi.fn(),
  openBookDetailsDialog: vi.fn(),
};

export const mockBookMenuService = {
  getMetadataMenuItems: vi.fn(() => []),
  getMoreActionsMenu: vi.fn(() => []),
};

export const mockLibraryShelfMenuService = {
  initializeLibraryMenuItems: vi.fn(() => []),
  initializeShelfMenuItems: vi.fn(() => []),
  initializeMagicShelfMenuItems: vi.fn(() => []),
};

export const mockConfirmationService = {
  confirm: vi.fn((config) => {
    if (config.accept) {
      config.accept();
    }
  }),
};

export const mockTaskHelperService = {
  refreshMetadataTask: vi.fn(() => of({success: true})),
};

export const mockLoadingService = {
  show: vi.fn(() => document.createElement('div')),
  hide: vi.fn(),
};

export const mockBookMetadataManageService = {
  regenerateCoversForBooks: vi.fn(() => of(void 0)),
  generateCustomCoversForBooks: vi.fn(() => of(void 0)),
  regenerateCover: vi.fn(() => of(void 0)),
  generateCustomCover: vi.fn(() => of(void 0)),
  toggleAllLock: vi.fn(() => of(void 0)),
  updateBooksMetadata: vi.fn(() => of(void 0)),
};

export const mockBookFileService = {
  downloadFile: vi.fn(),
  downloadAdditionalFile: vi.fn(),
  deleteAdditionalFile: vi.fn(() => of(void 0)),
};

export const mockEmailService = {
  emailBookQuick: vi.fn(() => of(void 0)),
};

export const mockBookNavigationService = {
  getAvailableBookIds: vi.fn(() => []),
  setAvailableBookIds: vi.fn(),
  setNavigationContext: vi.fn(),
};

export const mockAppSettingsService = {
  appSettings$: new BehaviorSubject({diskType: 'LOCAL'}).asObservable(),
};

export const mockCoverScalePreferenceService = {
  scaleFactor: 1,
  currentCardSize: {width: 135, height: 220},
  gridColumnMinWidth: '135px',
  getCardHeight: vi.fn(() => 220),
  scaleChange$: new BehaviorSubject(1).asObservable(),
  initScaleValue: vi.fn(),
  setScale: vi.fn(),
};

export const mockTableColumnPreferenceService = {
  allColumns: [
    {field: 'title', header: 'Title'},
    {field: 'authors', header: 'Authors'},
  ],
  visibleColumns: [{field: 'title', header: 'Title'}],
  initPreferences: vi.fn(),
  saveVisibleColumns: vi.fn(),
};

export const mockSidebarFilterTogglePrefService = {
  showFilter$: new BehaviorSubject(true).asObservable(),
  selectedShowFilter: true,
  toggle: vi.fn(),
};

export const mockBookCardOverlayPreferenceService = {
  showBookTypePill$: new BehaviorSubject(true).asObservable(),
  showBookTypePill: true,
  setShowBookTypePill: vi.fn(),
};

export const mockBookSelectionService = {
  selectedBooks: new Set<number>(),
  selectedBooks$: new BehaviorSubject(new Set<number>()).asObservable(),
  selectedCount: 0,
  selectAll: vi.fn(),
  deselectAll: vi.fn(),
  hasSelection: vi.fn(() => false),
  handleBookSelection: vi.fn(),
  handleCheckboxClick: vi.fn(),
  setSelectedBooks: vi.fn(),
  setCurrentBooks: vi.fn(),
};

export const mockPageTitleService = {
  setPageTitle: vi.fn(),
};

export const mockLocalStorageService = {
  get: vi.fn(() => null),
  set: vi.fn(),
  remove: vi.fn(),
};

export const mockBookBrowserScrollService = {
  createKey: vi.fn((path, params) => `${path}:${JSON.stringify(params)}`),
  savePosition: vi.fn(),
  getPosition: vi.fn(),
  clearPosition: vi.fn(),
};

export const mockBookBrowserQueryParamsService = {
  parseQueryParams: vi.fn(() => ({
    viewMode: 'grid',
    sortOption: {field: 'title', direction: SortDirection.ASCENDING, label: 'Title'},
    sortCriteria: [{field: 'title', direction: SortDirection.ASCENDING, label: 'Title'}],
    filters: {},
    filterMode: 'and' as const,
    viewModeFromToggle: false,
  })),
  updateViewMode: vi.fn(),
  updateMultiSort: vi.fn(),
  updateFilters: vi.fn(),
  updateFilterMode: vi.fn(),
  syncQueryParams: vi.fn(),
};

export const mockBookBrowserEntityService = {
  getEntityInfoFromRoute: vi.fn(() => of({entityId: NaN, entityType: 'All Books' as any})),
  fetchEntity: vi.fn(() => of(null)),
  fetchBooksByEntity: vi.fn(() => of({loaded: true, books: [mockBook], error: null})),
  fetchAllBooks: vi.fn(() => of({loaded: true, books: [mockBook], error: null})),
  fetchUnshelvedBooks: vi.fn(() => of({loaded: true, books: [mockBook], error: null})),
  isLibrary: vi.fn((entity) => entity && 'paths' in entity),
  isMagicShelf: vi.fn((entity) => entity && 'filterJson' in entity),
};

export const mockBookFilterOrchestrationService = {
  applyFilters: vi.fn((bookState) => of(bookState)),
  shouldForceExpandSeries: vi.fn(() => false),
};

export const mockSortService = {
  applySort: vi.fn((books) => books),
  applyMultiSort: vi.fn((books) => books),
};

export const mockReadStatusHelper = {
  getReadStatusIcon: vi.fn(() => 'pi pi-book'),
  getReadStatusClass: vi.fn(() => 'status-unset'),
  getReadStatusTooltip: vi.fn(() => 'Unset'),
  shouldShowStatusIcon: vi.fn(() => true),
};

export const mockBookMetadataHostService = {
  requestBookSwitch: vi.fn(),
  bookSwitches$: new BehaviorSubject<number | null>(null).asObservable(),
};

export function setupJsdomMocks() {
  if (typeof window !== 'undefined' && !window.matchMedia) {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  }

  if (typeof window !== 'undefined' && !window.ResizeObserver) {
    class ResizeObserverMock {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
    }
    Object.defineProperty(window, 'ResizeObserver', {
      writable: true,
      value: ResizeObserverMock,
    });
  }
}

export function createMockActivatedRoute(options: {
  path?: string;
  params?: Record<string, string | null>;
  queryParams?: Record<string, string | null>;
} = {}) {
  const paramMapSubject = new BehaviorSubject(createParamMap(options.params ?? {}));
  const queryParamMapSubject = new BehaviorSubject(createParamMap(options.queryParams ?? {}));

  return {
    provide: ActivatedRoute,
    useValue: {
      params: of(options.params ?? {}),
      queryParams: of(options.queryParams ?? {}),
      paramMap: paramMapSubject.asObservable(),
      queryParamMap: queryParamMapSubject.asObservable(),
      snapshot: {
        routeConfig: {path: options.path ?? ''},
        paramMap: createParamMap(options.params ?? {}),
        queryParamMap: createParamMap(options.queryParams ?? {}),
        queryParams: options.queryParams ?? {},
        params: options.params ?? {},
      },
      _paramMapSubject: paramMapSubject,
      _queryParamMapSubject: queryParamMapSubject,
    },
  };
}
