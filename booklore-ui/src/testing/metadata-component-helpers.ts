import type {Type} from '@angular/core';
import {Component, Input} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {ConfirmationService, DynamicDialogConfig, DynamicDialogRef, MessageService} from 'primeng/api';
import {BehaviorSubject, of} from 'rxjs';
import {vi} from 'vitest';
import type {Book, BookMetadata, BookRecommendation, BookState} from '../app/features/book/model/book.model';
import type {Library} from '../app/features/book/model/library.model';
import {BookFileService} from '../app/features/book/service/book-file.service';
import {BookMetadataManageService} from '../app/features/book/service/book-metadata-manage.service';
import {BookMetadataService} from '../app/features/book/service/book-metadata.service';
import {BookNavigationService} from '../app/features/book/service/book-navigation.service';
import {BookService} from '../app/features/book/service/book.service';
import {LibraryService} from '../app/features/book/service/library.service';
import {BookDialogHelperService} from '../app/features/book/components/book-browser/book-dialog-helper.service';
import {AuthorService} from '../app/features/author-browser/service/author.service';
import {TaskHelperService} from '../app/features/settings/task-management/task-helper.service';
import {EmailService} from '../app/features/settings/email-v2/email.service';
import type {UserState} from '../app/features/settings/user-management/user.service';
import {UserService} from '../app/features/settings/user-management/user.service';
import {SidecarService} from '../app/features/metadata/service/sidecar.service';
import type {AppSettings, MetadataProviderSpecificFields} from '../app/shared/model/app-settings.model';
import {AppSettingsService} from '../app/shared/service/app-settings.service';
import {BookMetadataHostService} from '../app/shared/service/book-metadata-host.service';
import {UrlHelperService} from '../app/shared/service/url-helper.service';
import {
  mockDynamicDialogConfigProvider,
  mockDynamicDialogRefProvider,
  mockHttpClientProvider,
  mockMessageServiceProvider,
  mockRouterProvider
} from './providers';
import {createMockBook, createMockBookMetadata, createMockUser} from './factories';

export {createMockBook, createMockBookMetadata, createMockUser};

export const defaultMetadataProviderSpecificFields: MetadataProviderSpecificFields = {
  asin: true,
  amazonRating: true,
  amazonReviewCount: true,
  googleId: true,
  goodreadsId: true,
  goodreadsRating: true,
  goodreadsReviewCount: true,
  hardcoverId: true,
  hardcoverBookId: true,
  hardcoverRating: true,
  hardcoverReviewCount: true,
  comicvineId: true,
  lubimyczytacId: true,
  lubimyczytacRating: true,
  ranobedbId: true,
  ranobedbRating: true,
  audibleId: true,
  audibleRating: true,
  audibleReviewCount: true,
};

export function createMockAppSettings(overrides: Partial<AppSettings> = {}): AppSettings {
  return {
    autoBookSearch: false,
    similarBookRecommendation: false,
    defaultMetadataRefreshOptions: {} as any,
    libraryMetadataRefreshOptions: [],
    uploadPattern: '',
    opdsServerEnabled: false,
    komgaApiEnabled: false,
    komgaGroupUnknown: false,
    remoteAuthEnabled: false,
    oidcEnabled: false,
    oidcProviderDetails: {
      providerName: '',
      clientId: '',
      issuerUri: '',
      claimMapping: {username: '', email: '', name: '', groups: ''}
    },
    oidcAutoProvisionDetails: {
      enableAutoProvisioning: false,
      allowLocalAccountLinking: false,
      defaultPermissions: [],
      defaultLibraryIds: []
    },
    maxFileUploadSizeInMb: 100,
    metadataProviderSettings: {
      amazon: {enabled: true, cookie: '', domain: 'com'},
      google: {enabled: true, language: 'en', apiKey: ''},
      goodReads: {enabled: true},
      ranobedb: {enabled: true},
      hardcover: {enabled: true, apiKey: ''},
      comicvine: {enabled: true, apiKey: ''},
      douban: {enabled: true},
      lubimyczytac: {enabled: true},
      audible: {enabled: true, domain: 'com'}
    },
    metadataMatchWeights: {} as any,
    metadataPersistenceSettings: {} as any,
    metadataPublicReviewsSettings: {} as any,
    koboSettings: {} as any,
    coverCroppingSettings: {} as any,
    metadataDownloadOnBookdrop: false,
    telemetryEnabled: false,
    metadataProviderSpecificFields: defaultMetadataProviderSpecificFields,
    oidcSessionDurationHours: null,
    oidcGroupSyncMode: null,
    oidcForceOnlyMode: false,
    diskType: 'LOCAL',
    ...overrides
  };
}

export function createMockBookState(books: Book[] = [createMockBook()]): BookState {
  return {books, loaded: true, error: null};
}

export function createMockUserState(): UserState {
  return {user: createMockUser(), loaded: true, error: null};
}

export function mockActivatedRouteProvider(paramMap: Record<string, string | null> = {}, queryParamMap: Record<string, string | null> = {}) {
  const paramMapLike = {
    get: (key: string) => paramMap[key] ?? null,
    getAll: () => [],
    has: (key: string) => key in paramMap,
    keys: Object.keys(paramMap)
  };
  const queryParamMapLike = {
    get: (key: string) => queryParamMap[key] ?? null,
    getAll: () => [],
    has: (key: string) => key in queryParamMap,
    keys: Object.keys(queryParamMap)
  };
  return {
    provide: ActivatedRoute,
    useValue: {
      params: of(paramMap),
      queryParams: of(queryParamMap),
      paramMap: of(paramMapLike),
      queryParamMap: of(queryParamMapLike),
      snapshot: {
        paramMap: paramMapLike,
        queryParamMap: queryParamMapLike
      }
    }
  };
}

export function mockBookServiceProvider(book: Book = createMockBook()) {
  const bookState$ = new BehaviorSubject<BookState>(createMockBookState([book]));
  return {
    provide: BookService,
    useValue: {
      bookState$: bookState$.asObservable(),
      getBookByIdFromState: vi.fn(() => book),
      getBookByIdFromAPI: vi.fn(() => of(book)),
      getBooksInSeries: vi.fn(() => of([])),
      getBookRecommendations: vi.fn(() => of([] as BookRecommendation[])),
      handleBookUpdate: vi.fn(),
      readBook: vi.fn(),
      updateBookReadStatus: vi.fn(() => of([])),
      updatePersonalRating: vi.fn(() => of([])),
      resetPersonalRating: vi.fn(() => of([])),
      resetProgress: vi.fn(() => of([])),
      updateDateFinished: vi.fn(() => of(undefined)),
      deleteBooks: vi.fn(() => of({})),
      togglePhysicalFlag: vi.fn(() => of(book))
    }
  };
}

export function mockAppSettingsServiceProvider(settings: AppSettings = createMockAppSettings()) {
  const appSettings$ = new BehaviorSubject<AppSettings | null>(settings);
  return {
    provide: AppSettingsService,
    useValue: {appSettings$: appSettings$.asObservable()}
  };
}

export function mockUserServiceProvider(userState: UserState = createMockUserState()) {
  const userState$ = new BehaviorSubject<UserState>(userState);
  return {
    provide: UserService,
    useValue: {
      userState$: userState$.asObservable(),
      getCurrentUser: vi.fn(() => userState.user)
    }
  };
}

export function mockBookMetadataServiceProvider() {
  return {
    provide: BookMetadataService,
    useValue: {
      fetchBookMetadata: vi.fn(() => of({} as BookMetadata)),
      fetchMetadataDetail: vi.fn(() => of({} as BookMetadata)),
      lookupByIsbn: vi.fn(() => of({} as BookMetadata))
    }
  };
}

export function mockBookMetadataManageServiceProvider() {
  return {
    provide: BookMetadataManageService,
    useValue: {
      updateBookMetadata: vi.fn(() => of({} as BookMetadata)),
      updateBooksMetadata: vi.fn(() => of(undefined)),
      toggleAllLock: vi.fn(() => of(undefined)),
      toggleFieldLocks: vi.fn(() => of(undefined)),
      consolidateMetadata: vi.fn(() => of({})),
      deleteMetadata: vi.fn(() => of({})),
      getUploadCoverUrl: vi.fn(() => '/api/v1/books/1/metadata/cover/upload'),
      uploadCoverFromUrl: vi.fn(() => of({} as BookMetadata)),
      regenerateCovers: vi.fn(() => of(undefined)),
      regenerateCover: vi.fn(() => of(undefined)),
      getFileMetadata: vi.fn(() => of({} as BookMetadata)),
      generateCustomCover: vi.fn(() => of(undefined)),
      generateCustomCoversForBooks: vi.fn(() => of(undefined)),
      regenerateCoversForBooks: vi.fn(() => of(undefined)),
      uploadAudiobookCoverFromUrl: vi.fn(() => of({} as BookMetadata)),
      uploadAudiobookCoverFromFile: vi.fn(() => of(undefined)),
      getUploadAudiobookCoverUrl: vi.fn(() => '/api/v1/books/1/metadata/audiobook-cover/upload'),
      regenerateAudiobookCover: vi.fn(() => of(undefined)),
      generateCustomAudiobookCover: vi.fn(() => of(undefined)),
      supportsDualCovers: vi.fn(() => false),
      bulkUploadCover: vi.fn(() => of(undefined))
    }
  };
}

export function mockUrlHelperServiceProvider() {
  return {
    provide: UrlHelperService,
    useValue: {
      getThumbnailUrl: vi.fn((bookId: number) => `/api/v1/media/book/${bookId}/thumbnail`),
      getDirectThumbnailUrl: vi.fn((bookId: number) => `/api/v1/media/book/${bookId}/thumbnail`),
      getCoverUrl: vi.fn((bookId: number) => `/api/v1/media/book/${bookId}/cover`),
      getBackupCoverUrl: vi.fn((bookId: number) => `/api/v1/media/book/${bookId}/backup-cover`),
      getAudiobookCoverUrl: vi.fn((bookId: number) => `/api/v1/media/book/${bookId}/audiobook-cover`),
      getAudiobookThumbnailUrl: vi.fn((bookId: number) => `/api/v1/media/book/${bookId}/audiobook-thumbnail`),
      getBookdropCoverUrl: vi.fn(() => ''),
      getBookUrl: vi.fn(),
      getBookPrimaryReadingUrl: vi.fn(),
      filterBooksBy: vi.fn()
    }
  };
}

export function mockBookFileServiceProvider() {
  return {
    provide: BookFileService,
    useValue: {
      getFileContent: vi.fn(() => of(new Blob())),
      downloadFile: vi.fn(),
      downloadAllFiles: vi.fn(),
      deleteAdditionalFile: vi.fn(() => of(undefined)),
      deleteBookFile: vi.fn(() => of(undefined)),
      uploadAdditionalFile: vi.fn(() => of({})),
      downloadAdditionalFile: vi.fn(),
      detachBookFile: vi.fn(() => of({sourceBook: createMockBook(), newBook: createMockBook({id: 2})})),
      findDuplicates: vi.fn(() => of([])),
      attachBookFiles: vi.fn(() => of({updatedBook: createMockBook(), deletedSourceBookIds: []}))
    }
  };
}

export function mockBookNavigationServiceProvider() {
  const navigationState$ = new BehaviorSubject<{bookIds: number[]; currentIndex: number} | null>(null);
  return {
    provide: BookNavigationService,
    useValue: {
      navigationState$: navigationState$.asObservable(),
      getNavigationState: vi.fn(() => navigationState$.asObservable()),
      setAvailableBookIds: vi.fn(),
      getAvailableBookIds: vi.fn(() => []),
      setNavigationContext: vi.fn(),
      canNavigatePrevious: vi.fn(() => false),
      canNavigateNext: vi.fn(() => false),
      getPreviousBookId: vi.fn(() => null),
      getNextBookId: vi.fn(() => null),
      updateCurrentBook: vi.fn(),
      getCurrentPosition: vi.fn(() => null)
    }
  };
}

export function mockBookDialogHelperServiceProvider() {
  return {
    provide: BookDialogHelperService,
    useValue: {
      openBookDetailsDialog: vi.fn(() => null),
      openShelfAssignerDialog: vi.fn(() => null),
      openShelfCreatorDialog: vi.fn(() => ({}) as any),
      openLockUnlockMetadataDialog: vi.fn(() => null),
      openMetadataRefreshDialog: vi.fn(() => null),
      openBulkMetadataEditDialog: vi.fn(() => null),
      openMultibookMetadataEditorDialog: vi.fn(() => null),
      openFileMoverDialog: vi.fn(() => null),
      openCustomSendDialog: vi.fn(() => null),
      openCoverSearchDialog: vi.fn(() => ({onClose: of(null)}) as any),
      openAdditionalFileUploaderDialog: vi.fn(() => null),
      openBookFileAttacherDialog: vi.fn(() => null),
      openBulkBookFileAttacherDialog: vi.fn(() => null),
      openDuplicateMergerDialog: vi.fn(() => null),
      openAddPhysicalBookDialog: vi.fn(() => null),
      openBulkIsbnImportDialog: vi.fn(() => null)
    }
  };
}

export function mockTaskHelperServiceProvider() {
  return {
    provide: TaskHelperService,
    useValue: {
      refreshMetadataTask: vi.fn(() => of({success: true}))
    }
  };
}

export function mockEmailServiceProvider() {
  return {
    provide: EmailService,
    useValue: {
      emailBook: vi.fn(() => of(undefined)),
      emailBookQuick: vi.fn(() => of(undefined))
    }
  };
}

export function mockAuthorServiceProvider() {
  return {
    provide: AuthorService,
    useValue: {
      getAllAuthors: vi.fn(() => of([])),
      getAuthorDetails: vi.fn(() => of({} as any)),
      getAuthorByName: vi.fn(() => of({id: 1} as any)),
      searchAuthorMetadata: vi.fn(() => of([])),
      matchAuthor: vi.fn(() => of({} as any)),
      quickMatchAuthor: vi.fn(() => of({} as any)),
      autoMatchAuthors: vi.fn(() => of({} as any)),
      updateAuthor: vi.fn(() => of({} as any)),
      unmatchAuthors: vi.fn(() => of(undefined)),
      deleteAuthors: vi.fn(() => of(undefined)),
      searchAuthorPhotos: vi.fn(() => of([])),
      uploadAuthorPhotoFromUrl: vi.fn(() => of(undefined)),
      getUploadAuthorPhotoUrl: vi.fn(() => ''),
      getAuthorPhotoUrl: vi.fn(() => ''),
      getAuthorThumbnailUrl: vi.fn(() => '')
    }
  };
}

export function mockLibraryServiceProvider(libraries: Library[] = []) {
  return {
    provide: LibraryService,
    useValue: {
      libraryState$: of({libraries, loaded: true, error: null}),
      largeLibraryLoading$: of({isLoading: false, expectedCount: 0}),
      findLibraryById: vi.fn(() => undefined),
      getLibrariesFromState: vi.fn(() => libraries),
      getBookCount: vi.fn(() => of(0)),
      getBookCountsByFormat: vi.fn(() => of({}))
    }
  };
}

export function mockConfirmationServiceProvider() {
  return {
    provide: ConfirmationService,
    useValue: {
      confirm: vi.fn()
    }
  };
}

export function mockSidecarServiceProvider() {
  return {
    provide: SidecarService,
    useValue: {
      getSidecarContent: vi.fn(() => of({} as any)),
      getSyncStatus: vi.fn(() => of({status: 'NOT_APPLICABLE' as const})),
      exportToSidecar: vi.fn(() => of({message: 'Exported'})),
      importFromSidecar: vi.fn(() => of({message: 'Imported'})),
      bulkExport: vi.fn(() => of({message: 'Bulk export complete', exported: 0})),
      bulkImport: vi.fn(() => of({message: 'Bulk import complete', imported: 0}))
    }
  };
}

export function mockBookMetadataHostServiceProvider() {
  const bookSwitches$ = new BehaviorSubject<number | null>(null);
  return {
    provide: BookMetadataHostService,
    useValue: {
      bookSwitches$: bookSwitches$.asObservable(),
      requestBookSwitch: vi.fn((bookId: number) => bookSwitches$.next(bookId)),
      switchBook: vi.fn((bookId: number) => bookSwitches$.next(bookId))
    }
  };
}

export const commonMetadataProviders = [
  mockHttpClientProvider,
  mockRouterProvider,
  mockMessageServiceProvider,
  mockDynamicDialogConfigProvider,
  mockDynamicDialogRefProvider,
  mockBookServiceProvider(),
  mockAppSettingsServiceProvider(),
  mockUserServiceProvider(),
  mockBookMetadataServiceProvider(),
  mockBookMetadataManageServiceProvider(),
  mockUrlHelperServiceProvider(),
  mockBookFileServiceProvider(),
  mockBookNavigationServiceProvider(),
  mockBookDialogHelperServiceProvider(),
  mockTaskHelperServiceProvider(),
  mockEmailServiceProvider(),
  mockAuthorServiceProvider(),
  mockLibraryServiceProvider(),
  mockConfirmationServiceProvider(),
  mockSidecarServiceProvider(),
  mockBookMetadataHostServiceProvider()
];

export function provideBookSubject(book: Book = createMockBook()) {
  return new BehaviorSubject<Book | null>(book);
}

export function mockBrowserGlobals() {
  vi.stubGlobal('IntersectionObserver', class {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
  });

  vi.stubGlobal('ResizeObserver', class {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
  });

  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn()
  } as any)));
}

/* -------------------- Stub components -------------------- */

@Component({
  selector: 'app-metadata-viewer',
  standalone: true,
  template: ''
})
export class MetadataViewerStubComponent {
  @Input() book$?: any;
  @Input() recommendedBooks?: any;
}

@Component({
  selector: 'app-metadata-editor',
  standalone: true,
  template: ''
})
export class MetadataEditorStubComponent {
  @Input() book$?: any;
}

@Component({
  selector: 'app-metadata-searcher',
  standalone: true,
  template: ''
})
export class MetadataSearcherStubComponent {
  @Input() book$?: any;
  @Input() isActiveTab?: boolean;
}

@Component({
  selector: 'app-sidecar-viewer',
  standalone: true,
  template: ''
})
export class SidecarViewerStubComponent {
  @Input() book$?: any;
}

@Component({
  selector: 'app-metadata-tabs',
  standalone: true,
  template: ''
})
export class MetadataTabsStubComponent {
  @Input() book?: any;
  @Input() bookInSeries?: any;
  @Input() recommendedBooks?: any;
}

@Component({
  selector: 'app-metadata-picker',
  standalone: true,
  template: ''
})
export class MetadataPickerStubComponent {
  @Input() fetchedMetadata?: any;
  @Input() book$?: any;
  @Input() detailLoading?: boolean;
}
