import {HttpClient} from '@angular/common/http';
import {Router, ActivatedRoute} from '@angular/router';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService, ConfirmationService} from 'primeng/api';
import {DialogService, DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BehaviorSubject, of} from 'rxjs';
import {vi} from 'vitest';
import {RxStompService} from '../app/shared/websocket/rx-stomp.service';
import {AuthService} from '../app/shared/service/auth.service';
import {IconPickerService} from '../app/shared/service/icon-picker.service';
import {PageTitleService} from '../app/shared/service/page-title.service';
import {BookService} from '../app/features/book/service/book.service';
import {BookState} from '../app/features/book/model/state/book-state.model';
import {LibraryService} from '../app/features/book/service/library.service';
import {LibraryState} from '../app/features/book/model/state/library-state.model';
import {ShelfService} from '../app/features/book/service/shelf.service';
import {ShelfState} from '../app/features/book/model/state/shelf-state.model';
import {UserService, User} from '../app/features/settings/user-management/user.service';
import {AuthorService} from '../app/features/author-browser/service/author.service';
import {AuthorSummary, AuthorDetails} from '../app/features/author-browser/model/author.model';
import {SeriesDataService} from '../app/features/series-browser/service/series-data.service';
import {SeriesSummary} from '../app/features/series-browser/model/series.model';
import {NotebookService} from '../app/features/notebook/service/notebook.service';
import {NotebookPage} from '../app/features/notebook/model/notebook.model';
import {BookdropService, BookdropFinalizeResult, Page, BookdropFile} from '../app/features/bookdrop/service/bookdrop.service';
import {BookdropFileService, BookdropFileNotification} from '../app/features/bookdrop/service/bookdrop-file.service';
import {BookMetadataManageService} from '../app/features/book/service/book-metadata-manage.service';
import {DashboardConfigService} from '../app/features/dashboard/services/dashboard-config.service';
import {DEFAULT_DASHBOARD_CONFIG} from '../app/features/dashboard/models/dashboard-config.model';
import {MagicShelfService, MagicShelfState} from '../app/features/magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../app/features/magic-shelf/service/book-rule-evaluator.service';
import {SortService} from '../app/features/book/service/sort.service';
import {MetadataUtilsService} from '../app/shared/metadata/metadata-utils.service';
import {AppSettingsService, PublicAppSettings} from '../app/shared/service/app-settings.service';
import {AppSettings} from '../app/shared/model/app-settings.model';
import {UrlHelperService} from '../app/shared/service/url-helper.service';
import {DialogLauncherService} from '../app/shared/services/dialog-launcher.service';
import {BookBrowserScrollService} from '../app/features/book/components/book-browser/book-browser-scroll.service';
import {AuthorScalePreferenceService} from '../app/features/author-browser/service/author-scale-preference.service';
import {SeriesScalePreferenceService} from '../app/features/series-browser/service/series-scale-preference.service';
import {CoverScalePreferenceService} from '../app/features/book/components/book-browser/cover-scale-preference.service';
import {BookCardOverlayPreferenceService} from '../app/features/book/components/book-browser/book-card-overlay-preference.service';
import {AuthorSelectionService} from '../app/features/author-browser/service/author-selection.service';
import {createMockBook, createMockLibrary, createMockShelf, createMockUser} from './factories';

export const mockHttpClientProvider = {
  provide: HttpClient,
  useValue: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
    request: vi.fn()
  }
};

export const mockRouterProvider = {
  provide: Router,
  useValue: {
    navigate: vi.fn(() => Promise.resolve(true)),
    navigateByUrl: vi.fn(() => Promise.resolve(true)),
    url: '/',
    events: of(),
    createUrlTree: vi.fn(),
    serializeUrl: vi.fn()
  }
};

export const mockActivatedRouteProvider = {
  provide: ActivatedRoute,
  useValue: {
    params: of({}),
    queryParams: of({}),
    snapshot: {
      paramMap: {get: vi.fn(() => null)},
      queryParamMap: {get: vi.fn(() => null)}
    }
  }
};

export const mockTranslocoServiceProvider = {
  provide: TranslocoService,
  useValue: {
    translate: vi.fn((key: string) => key),
    selectTranslation: vi.fn(() => of({})),
    langChanges$: of('en'),
    getActiveLang: vi.fn(() => 'en'),
    setActiveLang: vi.fn(),
    config: {reRenderOnLangChange: true},
    _loadDependencies: vi.fn(() => of({}))
  }
};

export const mockMessageServiceProvider = {
  provide: MessageService,
  useValue: {
    add: vi.fn(),
    clear: vi.fn()
  }
};

export const mockRxStompServiceProvider = {
  provide: RxStompService,
  useValue: {
    watch: vi.fn(() => of()),
    publish: vi.fn(),
    activate: vi.fn(),
    deactivate: vi.fn()
  }
};

export const mockAuthServiceProvider = {
  provide: AuthService,
  useValue: {
    token$: of(null),
    tokenSubject: {next: vi.fn(), value: null},
    internalLogin: vi.fn(),
    internalRefreshToken: vi.fn(),
    remoteLogin: vi.fn(),
    logout: vi.fn(),
    forceLogout: vi.fn(),
    getInternalAccessToken: vi.fn(() => null),
    getInternalRefreshToken: vi.fn(() => null),
    saveInternalTokens: vi.fn()
  }
};

export const mockDynamicDialogRefProvider = {
  provide: DynamicDialogRef,
  useValue: {
    close: vi.fn()
  }
};

export const mockDynamicDialogConfigProvider = {
  provide: DynamicDialogConfig,
  useValue: {
    data: null
  }
};

export const mockIconPickerServiceProvider = {
  provide: IconPickerService,
  useValue: {
    open: vi.fn(() => of(null))
  }
};

export const mockPageTitleServiceProvider = {
  provide: PageTitleService,
  useValue: {
    setPageTitle: vi.fn(),
    setBookPageTitle: vi.fn()
  }
};

export const mockBookServiceProvider = {
  provide: BookService,
  useValue: {
    bookState$: new BehaviorSubject<BookState>({loaded: true, books: [], error: null}),
    getCurrentBookState: vi.fn(() => ({loaded: true, books: [], error: null})),
    refreshBooks: vi.fn(),
    getBookByIdFromState: vi.fn(),
    getBooksByIdsFromState: vi.fn(() => []),
    getBookByIdFromAPI: vi.fn(() => of(createMockBook())),
    removeBooksByLibraryId: vi.fn(),
    removeBooksFromShelf: vi.fn()
  }
};

export const mockLibraryServiceProvider = {
  provide: LibraryService,
  useValue: {
    libraryState$: new BehaviorSubject<LibraryState>({loaded: true, libraries: [], error: null}),
    largeLibraryLoading$: new BehaviorSubject({isLoading: false, expectedCount: 0}),
    getLibrariesFromState: vi.fn(() => []),
    findLibraryById: vi.fn(),
    doesLibraryExistByName: vi.fn(() => false),
    scanLibraryPaths: vi.fn(() => of(0)),
    createLibrary: vi.fn(() => of(createMockLibrary())),
    updateLibrary: vi.fn(() => of(createMockLibrary())),
    refreshLibrary: vi.fn(() => of(undefined)),
    deleteLibrary: vi.fn(() => of(undefined)),
    setLargeLibraryLoading: vi.fn(),
    getBookCountsByFormat: vi.fn(() => of({}))
  }
};

export const mockShelfServiceProvider = {
  provide: ShelfService,
  useValue: {
    shelfState$: new BehaviorSubject<ShelfState>({loaded: true, shelves: [], error: null}),
    getShelvesFromState: vi.fn(() => []),
    getShelfById: vi.fn(),
    reloadShelves: vi.fn()
  }
};

const mockUserStateSubject = new BehaviorSubject<{user: User | null; loaded: boolean; error: string | null}>({user: createMockUser(), loaded: true, error: null});
export const mockUserServiceProvider = {
  provide: UserService,
  useValue: {
    userState$: mockUserStateSubject.asObservable(),
    userStateSubject: mockUserStateSubject,
    getCurrentUser: vi.fn(() => createMockUser()),
    setInitialUser: vi.fn(),
    getMyself: vi.fn(() => of(createMockUser())),
    createUser: vi.fn(() => of(undefined)),
    getUsers: vi.fn(() => of([])),
    updateUser: vi.fn(() => of({} as User)),
    deleteUser: vi.fn(() => of(undefined)),
    changeUserPassword: vi.fn(() => of(undefined)),
    changePassword: vi.fn(() => of(undefined)),
    updateUserSetting: vi.fn()
  }
};

export const mockConfirmationServiceProvider = {
  provide: ConfirmationService,
  useValue: {confirm: vi.fn()}
};

export const mockDialogServiceProvider = {
  provide: DialogService,
  useValue: {
    open: vi.fn(() => ({onClose: of(null), close: vi.fn()}))
  }
};

export const mockBookBrowserScrollServiceProvider = {
  provide: BookBrowserScrollService,
  useValue: {
    savePosition: vi.fn(),
    getPosition: vi.fn(),
    clearPosition: vi.fn(),
    createKey: vi.fn((path: string) => path)
  }
};

export const mockAuthorScalePreferenceServiceProvider = {
  provide: AuthorScalePreferenceService,
  useValue: {scaleFactor: 1, setScale: vi.fn()}
};

export const mockSeriesScalePreferenceServiceProvider = {
  provide: SeriesScalePreferenceService,
  useValue: {scaleFactor: 1, setScale: vi.fn()}
};

export const mockCoverScalePreferenceServiceProvider = {
  provide: CoverScalePreferenceService,
  useValue: {
    scaleFactor: 1,
    scaleChange$: new BehaviorSubject(1).asObservable(),
    currentCardSize: {width: 135, height: 220},
    gridColumnMinWidth: '135px',
    setScale: vi.fn(),
    initScaleValue: vi.fn(),
    getCardHeight: vi.fn(() => 220)
  }
};

export const mockBookCardOverlayPreferenceServiceProvider = {
  provide: BookCardOverlayPreferenceService,
  useValue: {
    showBookTypePill$: new BehaviorSubject(true).asObservable(),
    showBookTypePill: true,
    setShowBookTypePill: vi.fn()
  }
};

export const mockAuthorSelectionServiceProvider = {
  provide: AuthorSelectionService,
  useValue: {
    selectedAuthors$: new BehaviorSubject(new Set<number>()).asObservable(),
    selectedAuthors: new Set<number>(),
    selectedCount: 0,
    setCurrentAuthors: vi.fn(),
    handleCheckboxClick: vi.fn(),
    selectAll: vi.fn(),
    deselectAll: vi.fn(),
    getSelectedIds: vi.fn(() => [])
  }
};

export const mockAuthorServiceProvider = {
  provide: AuthorService,
  useValue: {
    allAuthors$: new BehaviorSubject<AuthorSummary[] | null>(null),
    getAuthorDetails: vi.fn(() => of({id: 1, name: 'Test Author'} as AuthorDetails)),
    getAuthorByName: vi.fn(() => of({} as AuthorDetails)),
    searchAuthorMetadata: vi.fn(() => of([])),
    matchAuthor: vi.fn(() => of({} as AuthorDetails)),
    quickMatchAuthor: vi.fn(() => of({} as AuthorDetails)),
    autoMatchAuthors: vi.fn(() => of()),
    updateAuthor: vi.fn(() => of({} as AuthorDetails)),
    unmatchAuthors: vi.fn(() => of(undefined)),
    deleteAuthors: vi.fn(() => of(undefined)),
    searchAuthorPhotos: vi.fn(() => of([])),
    uploadAuthorPhotoFromUrl: vi.fn(() => of(undefined)),
    getAuthorPhotoUrl: vi.fn(() => ''),
    getAuthorThumbnailUrl: vi.fn(() => ''),
    getUploadAuthorPhotoUrl: vi.fn(() => '')
  }
};

export const mockSeriesDataServiceProvider = {
  provide: SeriesDataService,
  useValue: {allSeries$: new BehaviorSubject<SeriesSummary[]>([])}
};

export const mockNotebookServiceProvider = {
  provide: NotebookService,
  useValue: {
    getNotebookEntries: vi.fn(() => of({content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 0}} as NotebookPage)),
    getExportEntries: vi.fn(() => of([])),
    getBooksWithAnnotations: vi.fn(() => of([]))
  }
};

const EMPTY_BOOKDROP_PAGE: Page<BookdropFile> = {content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 0}};
export const mockBookdropServiceProvider = {
  provide: BookdropService,
  useValue: {
    getPendingFiles: vi.fn(() => of(EMPTY_BOOKDROP_PAGE)),
    finalizeImport: vi.fn(() => of({totalFiles: 0, successfullyImported: 0, failed: 0, processedAt: '', results: []} as BookdropFinalizeResult)),
    discardFiles: vi.fn(() => of(undefined)),
    rescan: vi.fn(() => of(undefined)),
    extractFromPattern: vi.fn(() => of({totalFiles: 0, successfullyExtracted: 0, failed: 0, results: []})),
    bulkEditMetadata: vi.fn(() => of({totalFiles: 0, successfullyUpdated: 0, failed: 0}))
  }
};

export const mockBookMetadataManageServiceProvider = {
  provide: BookMetadataManageService,
  useValue: {
    updateBookMetadata: vi.fn(() => of({})),
    updateBooksMetadata: vi.fn(() => of(undefined)),
    toggleAllLock: vi.fn(() => of(undefined)),
    toggleFieldLocks: vi.fn(() => of(undefined)),
    consolidateMetadata: vi.fn(() => of(undefined)),
    deleteMetadata: vi.fn(() => of(undefined)),
    regenerateCovers: vi.fn(() => of(undefined)),
    regenerateCover: vi.fn(() => of(undefined)),
    generateCustomCover: vi.fn(() => of(undefined)),
    getFileMetadata: vi.fn(() => of({})),
    uploadCoverFromUrl: vi.fn(() => of({})),
    supportsDualCovers: vi.fn(() => false)
  }
};

export const mockDashboardConfigServiceProvider = {
  provide: DashboardConfigService,
  useValue: {
    config$: new BehaviorSubject(DEFAULT_DASHBOARD_CONFIG),
    saveConfig: vi.fn(),
    resetToDefault: vi.fn()
  }
};

export const mockMagicShelfServiceProvider = {
  provide: MagicShelfService,
  useValue: {
    shelvesState$: new BehaviorSubject<MagicShelfState>({shelves: [], loaded: true, error: null}),
    getShelf: vi.fn(() => of(undefined)),
    saveShelf: vi.fn(() => of({})),
    deleteShelf: vi.fn(() => of(undefined)),
    getBookCount: vi.fn(() => of(0))
  }
};

export const mockBookRuleEvaluatorServiceProvider = {
  provide: BookRuleEvaluatorService,
  useValue: {
    evaluateGroup: vi.fn(() => true)
  }
};

export const mockSortServiceProvider = {
  provide: SortService,
  useValue: {
    applySort: vi.fn((books: any[]) => books),
    applyMultiSort: vi.fn((books: any[]) => books)
  }
};

export const mockBookdropFileServiceProvider = {
  provide: BookdropFileService,
  useValue: {
    summary$: new BehaviorSubject<BookdropFileNotification>({pendingCount: 0, totalCount: 0}),
    hasPendingFiles$: new BehaviorSubject(false).asObservable(),
    handleIncomingFile: vi.fn(),
    refresh: vi.fn()
  }
};

export const mockMetadataUtilsServiceProvider = {
  provide: MetadataUtilsService,
  useValue: {
    copyFieldToForm: vi.fn(() => true),
    copyMissingFields: vi.fn(),
    copyAllFields: vi.fn(),
    isValueEmpty: vi.fn(() => false),
    areFieldsEqual: vi.fn(() => false),
    normalizeForComparison: vi.fn(() => [undefined, undefined]),
    isValueChanged: vi.fn(() => false),
    isFetchedDifferent: vi.fn(() => false),
    resetField: vi.fn(),
    patchMetadataToForm: vi.fn()
  }
};

export const mockAppSettingsServiceProvider = {
  provide: AppSettingsService,
  useValue: {
    appSettings$: new BehaviorSubject<AppSettings | null>(null).asObservable(),
    publicAppSettings$: new BehaviorSubject<PublicAppSettings | null>(null).asObservable(),
    currentPublicSettings: null,
    saveSettings: vi.fn(() => of(undefined)),
    toggleOidcEnabled: vi.fn(() => of(undefined))
  }
};

export const mockUrlHelperServiceProvider = {
  provide: UrlHelperService,
  useValue: {
    getThumbnailUrl: vi.fn(() => ''),
    getDirectThumbnailUrl: vi.fn(() => ''),
    getCoverUrl: vi.fn(() => ''),
    getBackupCoverUrl: vi.fn(() => ''),
    getAudiobookCoverUrl: vi.fn(() => ''),
    getAudiobookThumbnailUrl: vi.fn(() => ''),
    getBookdropCoverUrl: vi.fn(() => ''),
    getBookUrl: vi.fn(() => ''),
    getBookPrimaryReadingUrl: vi.fn(() => ''),
    filterBooksBy: vi.fn(() => '')
  }
};

export const mockDialogLauncherServiceProvider = {
  provide: DialogLauncherService,
  useValue: {
    openDialog: vi.fn(() => ({onClose: of(null), close: vi.fn()})),
    openBookdropFinalizeResultDialog: vi.fn(() => ({onClose: of(null), close: vi.fn()})),
    openDirectoryPickerDialog: vi.fn(() => ({onClose: of(null), close: vi.fn()})),
    openDashboardSettingsDialog: vi.fn(() => ({onClose: of(null), close: vi.fn()})),
    openLibraryCreateDialog: vi.fn(() => ({onClose: of(null), close: vi.fn()}))
  }
};

export const commonComponentTestProviders = [
  mockHttpClientProvider,
  mockRouterProvider,
  mockActivatedRouteProvider,
  mockTranslocoServiceProvider,
  mockMessageServiceProvider,
  mockRxStompServiceProvider,
  mockAuthServiceProvider,
  mockIconPickerServiceProvider,
  mockPageTitleServiceProvider,
  mockBookServiceProvider,
  mockLibraryServiceProvider,
  mockShelfServiceProvider,
  mockUserServiceProvider,
  mockConfirmationServiceProvider,
  mockDialogServiceProvider,
  mockDynamicDialogRefProvider,
  mockDynamicDialogConfigProvider,
  mockBookBrowserScrollServiceProvider,
  mockAuthorScalePreferenceServiceProvider,
  mockSeriesScalePreferenceServiceProvider,
  mockCoverScalePreferenceServiceProvider,
  mockBookCardOverlayPreferenceServiceProvider,
  mockAuthorSelectionServiceProvider,
  mockAuthorServiceProvider,
  mockSeriesDataServiceProvider,
  mockNotebookServiceProvider,
  mockBookdropServiceProvider,
  mockBookMetadataManageServiceProvider,
  mockDashboardConfigServiceProvider,
  mockMagicShelfServiceProvider,
  mockBookRuleEvaluatorServiceProvider,
  mockSortServiceProvider,
  mockBookdropFileServiceProvider,
  mockMetadataUtilsServiceProvider,
  mockAppSettingsServiceProvider,
  mockUrlHelperServiceProvider,
  mockDialogLauncherServiceProvider
];
