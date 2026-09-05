import type {Book, BookMetadata, BookType} from '../app/features/book/model/book.model';
import {ReadStatus} from '../app/features/book/model/book.model';
import type {Library} from '../app/features/book/model/library.model';
import type {Shelf} from '../app/features/book/model/shelf.model';
import type {User} from '../app/features/settings/user-management/user.service';
import type {PublicAppSettings} from '../app/shared/service/app-settings.service';

export function createMockBookMetadata(overrides: Partial<BookMetadata> = {}): BookMetadata {
  return {
    bookId: 1,
    title: 'Test Book',
    authors: ['Test Author'],
    categories: ['Fiction'],
    language: 'en',
    ...overrides
  };
}

export function createMockBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 1,
    bookType: 'EPUB' as BookType,
    libraryId: 1,
    libraryName: 'Test Library',
    fileName: 'test.epub',
    filePath: '/path/to/test.epub',
    readStatus: ReadStatus.UNREAD,
    shelves: [],
    metadata: createMockBookMetadata(),
    ...overrides
  };
}

export function createMockShelf(overrides: Partial<Shelf> = {}): Shelf {
  return {
    id: 1,
    name: 'Test Shelf',
    icon: '',
    iconType: 'PRIME_NG',
    bookCount: 0,
    ...overrides
  };
}

export function createMockLibrary(overrides: Partial<Library> = {}): Library {
  return {
    id: 1,
    name: 'Test Library',
    watch: false,
    paths: [{id: 1, path: '/books/test'}],
    ...overrides
  };
}

export function createMockUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    username: 'testuser',
    name: 'Test User',
    email: 'test@example.com',
    assignedLibraries: [],
    permissions: {
      admin: false,
      canUpload: true,
      canDownload: true,
      canEmailBook: true,
      canDeleteBook: true,
      canEditMetadata: true,
      canManageLibrary: false,
      canManageMetadataConfig: false,
      canSyncKoReader: false,
      canSyncKobo: false,
      canAccessOpds: false,
      canAccessBookdrop: true,
      canAccessLibraryStats: true,
      canAccessUserStats: true,
      canAccessTaskManager: true,
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
      canBulkResetBookReadStatus: false
    },
    userSettings: {
      perBookSetting: {} as any,
      pdfReaderSetting: {} as any,
      epubReaderSetting: {} as any,
      ebookReaderSetting: {} as any,
      cbxReaderSetting: {} as any,
      newPdfReaderSetting: {} as any,
      sidebarLibrarySorting: {} as any,
      sidebarShelfSorting: {} as any,
      sidebarMagicShelfSorting: {} as any,
      filterMode: 'AND' as any,
      metadataCenterViewMode: 'route',
      enableSeriesView: true,
      entityViewPreferences: {} as any,
      koReaderEnabled: false,
      autoSaveMetadata: true
    },
    ...overrides
  };
}

export function createMockPublicAppSettings(overrides: Partial<PublicAppSettings> = {}): PublicAppSettings {
  return {
    oidcEnabled: false,
    remoteAuthEnabled: false,
    oidcProviderDetails: {
      providerName: '',
      clientId: '',
      issuerUri: '',
      claimMapping: {username: '', email: '', name: '', groups: ''}
    },
    oidcForceOnlyMode: false,
    ...overrides
  };
}
